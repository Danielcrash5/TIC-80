package com.nesbox.tic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TICFileReceiver extends Activity {
    private final static String TAG = "TICFileReceiver";
    private static File UserDirectory;

    @Override
    protected void onStart()
    {
        super.onStart();
        Log.v(TAG, "onStart");

        UserDirectory = TIC.getUserDirectory(getApplicationContext());
        if (UserDirectory == null) {
            showResult(false, null);
            return;
        }
        if (!UserDirectory.isDirectory() && !UserDirectory.mkdirs()) {
            showResult(false, null);
            return;
        }

        final Intent intent = getIntent();
        if (intent == null) return;
        final String action = intent.getAction();
        final String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Log.v(TAG, "received intent of type " + type);
            Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (fileUri != null) {
                final String fileName = SaveFile(fileUri);
                showResult(fileName != null, fileName);
            } else {
                showResult(false, null);
            }
        }
    }

    private void showResult(boolean succeeded, String fileName) {
        String message = succeeded
                ? getString(R.string.file_saved_message, fileName, UserDirectory)
                : getString(R.string.file_save_error);
        new AlertDialog.Builder(this)
                .setTitle("TIC-80")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private String SaveFile(Uri uri) {
        String baseFileName = getDisplayName(uri);
        if (baseFileName == null || baseFileName.isEmpty()) baseFileName = "cart.tic";
        baseFileName = new File(baseFileName.replace('\\', '/')).getName();
        File dstFile = new File(UserDirectory, baseFileName);
        int noConflictIndex = 1;
        int extensionIndex = baseFileName.lastIndexOf('.');
        String stem = extensionIndex > 0 ? baseFileName.substring(0, extensionIndex) : baseFileName;
        String extension = extensionIndex > 0 ? baseFileName.substring(extensionIndex) : "";
        while (dstFile.exists())
            dstFile = new File(UserDirectory, stem + "_" + noConflictIndex++ + extension);
        if (Copy(uri, dstFile))
            return dstFile.getName();
        else
            return null;
    }

    private boolean Copy(Uri src, File dst) {
        final int CopyBufferLength = 1024;
        ContentResolver resolver = getApplicationContext().getContentResolver();
        try (InputStream in = resolver.openInputStream(src)) {
            if (in == null) {
                Log.e(TAG, "Could not open shared file stream: " + src);
                return false;
            }
            try (OutputStream out = new FileOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[CopyBufferLength];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Copying file failed. \n" + e.getMessage());
            return false;
        }
        Log.v(TAG, "Saved file to " + dst.getPath());
        return true;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[] { android.provider.OpenableColumns.DISPLAY_NAME },
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) return cursor.getString(nameIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read shared file name", e);
        }
        return null;
    }
}
