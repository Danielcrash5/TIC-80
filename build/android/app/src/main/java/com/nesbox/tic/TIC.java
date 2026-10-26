package com.nesbox.tic;

import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.libsdl.app.SDLActivity;

public class TIC extends SDLActivity
{
    private static String sGameToLaunch = null;

    public static File getUserDirectory(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && Environment.isExternalStorageManager()) {
            return new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "TIC-80");
        }

        File baseDir = context.getExternalFilesDir(null);
        return baseDir == null ? null : new File(baseDir, "TIC-80");
    }

    public static File getLegacyUserDirectory(Context context) {
        Context appContext = context.getApplicationContext();
        File baseDir = appContext.getExternalFilesDir(null);
        return baseDir == null ? null : new File(baseDir, "TIC-80");
    }

    public static synchronized int migrateLegacyUserDirectory(Context context, File destination)
            throws IOException {
        Context appContext = context.getApplicationContext();
        String preferenceName = "tic80_storage";
        String migrationKey = "legacy_directory_migrated";
        if (appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                .getBoolean(migrationKey, false)) {
            return 0;
        }

        File source = getLegacyUserDirectory(appContext);
        int conflicts = 0;
        if (source != null && source.isDirectory()
                && !source.getCanonicalFile().equals(destination.getCanonicalFile())) {
            conflicts = copyDirectory(source, destination);
        }
        appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(migrationKey, true)
                .apply();
        return conflicts;
    }

    private static int copyDirectory(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (destination.exists() && !destination.isDirectory()) {
                return 1;
            }
            if (!destination.isDirectory() && !destination.mkdirs()) {
                throw new IOException("Could not create " + destination);
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Could not read " + source);
            }
            int conflicts = 0;
            for (File child : children) {
                conflicts += copyDirectory(child, new File(destination, child.getName()));
            }
            return conflicts;
        }

        if (destination.exists()) {
            return 1;
        }

        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Could not create parent folder for " + destination);
        }
        File temporary = File.createTempFile(".tic80-migrate-", ".tmp", parent);
        try {
            try (InputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (destination.exists()) {
                return 1;
            }
            if (!temporary.renameTo(destination)) {
                if (destination.exists()) {
                    return 1;
                }
                throw new IOException("Could not move migrated file to " + destination);
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                throw new IOException("Could not clean up " + temporary);
            }
        }
        return 0;
    }

    // SDL's Android JNI asks the Activity for this directory before appending TIC-80.
    @Override
    public File getExternalFilesDir(String type) {
        if (type == null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && Environment.isExternalStorageManager()) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        }
        return super.getExternalFilesDir(type);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Check for a file intent before starting the SDLActivity's native code.
        Intent intent = getIntent();
        Uri uri = intent.getData();

        if (uri != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            sGameToLaunch = copyUriToCache(uri);
        }

        super.onCreate(savedInstanceState);
    }

    private String copyUriToCache(Uri uri) {
        String fileName = "cart.tic"; // A fallback filename.
        // Get the original filename from the URI's metadata.
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex > -1) {
                    String displayName = cursor.getString(nameIndex);
                    if (displayName != null && !displayName.isEmpty()) {
                        fileName = new File(displayName.replace('\\', '/')).getName();
                    }
                }
            }
        }

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                Log.e("TIC", "Could not open input stream for URI: " + uri);
                return null;
            }

            // Create a file in the app's cache directory using the original filename.
            File tempFile = new File(getCacheDir(), fileName);

            // Copy the content from the URI to the temporary file.
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            String filePath = tempFile.getAbsolutePath();
            Log.i("TIC", "Copied content URI to cache file: " + filePath);
            return filePath;

        } catch (Exception e) {
            Log.e("TIC", "Error copying URI to cache", e);
            return null;
        }
    }

    @Override
    protected String[] getArguments() {
        if (sGameToLaunch != null) {
            String[] args = { sGameToLaunch };
            sGameToLaunch = null; // Clear it after use so it's not reused.
            return args;
        }
        return new String[0];
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "tic80" };
    }
}
