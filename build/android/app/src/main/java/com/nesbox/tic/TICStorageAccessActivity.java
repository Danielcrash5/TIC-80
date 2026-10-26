package com.nesbox.tic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class TICStorageAccessActivity extends Activity {
    private static final String TAG = "TICStorageAccess";
    private static final int REQUEST_STORAGE_ACCESS = 1;
    private boolean waitingForAccess;
    private boolean targetLaunched;
    private boolean destroyed;
    private ProgressDialog migrationProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            waitingForAccess = savedInstanceState.getBoolean("waitingForAccess");
            targetLaunched = savedInstanceState.getBoolean("targetLaunched");
        }
        if (!waitingForAccess && !targetLaunched) {
            continueWhenReady();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForAccess && hasStorageAccess()) {
            launchTarget();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("waitingForAccess", waitingForAccess);
        outState.putBoolean("targetLaunched", targetLaunched);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (migrationProgress != null && migrationProgress.isShowing()) {
            migrationProgress.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_STORAGE_ACCESS || !waitingForAccess || targetLaunched) {
            return;
        }
        waitingForAccess = false;
        continueWhenReady();
    }

    private boolean hasStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Environment.isExternalStorageManager();
    }

    private void continueWhenReady() {
        if (!hasStorageAccess()) {
            showStorageAccessDialog();
            return;
        }

        File userDirectory;
        try {
            userDirectory = TIC.getUserDirectory(this);
            if (userDirectory == null
                    || (!userDirectory.isDirectory() && !userDirectory.mkdirs())) {
                showStorageError(userDirectory == null
                        ? "Android did not provide a storage directory."
                        : userDirectory.toString());
                return;
            }
        } catch (SecurityException e) {
            showStorageError(e.getMessage());
            return;
        }

        migrateLegacyFiles(userDirectory);
    }

    private void migrateLegacyFiles(File userDirectory) {
        try {
            File legacyDirectory = TIC.getLegacyUserDirectory(this);
            if (legacyDirectory == null || !legacyDirectory.isDirectory()
                    || sameDirectory(legacyDirectory, userDirectory)) {
                launchTarget();
                return;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Could not access the previous TIC-80 storage folder", e);
            showMigrationWarning(getString(R.string.storage_migration_error, e.getMessage()));
            return;
        }

        migrationProgress = new ProgressDialog(this);
        migrationProgress.setMessage(getString(R.string.storage_migration_progress));
        migrationProgress.setIndeterminate(true);
        migrationProgress.setCancelable(false);
        migrationProgress.show();
        Context appContext = getApplicationContext();

        new Thread(() -> {
            int conflicts = 0;
            String failure = null;
            try {
                conflicts = TIC.migrateLegacyUserDirectory(appContext, userDirectory);
            } catch (IOException e) {
                failure = e.getMessage();
            } catch (SecurityException e) {
                failure = e.getMessage();
            }

            int copiedConflicts = conflicts;
            String migrationFailure = failure;
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) {
                    return;
                }
                migrationProgress.dismiss();
                if (migrationFailure != null) {
                    Log.e(TAG, "Could not migrate existing TIC-80 files: " + migrationFailure);
                    showMigrationWarning(
                            getString(R.string.storage_migration_error, migrationFailure));
                } else if (copiedConflicts > 0) {
                    showMigrationWarning(
                            getString(R.string.storage_migration_conflicts, copiedConflicts));
                } else {
                    launchTarget();
                }
            });
        }, "tic80-storage-migration").start();
    }

    private boolean sameDirectory(File first, File second) {
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (IOException e) {
            Log.e(TAG, "Could not compare TIC-80 storage paths", e);
            return false;
        }
    }

    private void showMigrationWarning(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.storage_migration_title)
                .setMessage(message)
                .setPositiveButton(R.string.storage_migration_continue, (dialog, which) -> launchTarget())
                .setNegativeButton(R.string.storage_access_exit, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showStorageError(String detail) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.storage_error_title)
                .setMessage(getString(R.string.storage_error_message, detail))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showStorageAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.storage_access_title)
                .setMessage(R.string.storage_access_message)
                .setPositiveButton(R.string.storage_access_settings, (dialog, which) -> openStorageSettings())
                .setNegativeButton(R.string.storage_access_exit, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void openStorageSettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            waitingForAccess = true;
            startActivityForResult(intent, REQUEST_STORAGE_ACCESS);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "App-specific storage settings are unavailable; opening general settings", e);
            try {
                intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                waitingForAccess = true;
                startActivityForResult(intent, REQUEST_STORAGE_ACCESS);
            } catch (ActivityNotFoundException fallbackError) {
                waitingForAccess = false;
                Log.e(TAG, "Android storage access settings are unavailable", fallbackError);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.storage_error_title)
                        .setMessage(R.string.storage_settings_error)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                        .show();
            }
        }
    }

    private void launchTarget() {
        if (targetLaunched) {
            return;
        }
        targetLaunched = true;
        waitingForAccess = false;

        Intent incoming = getIntent();
        Class<?> target = Intent.ACTION_SEND.equals(incoming.getAction())
                ? TICFileReceiver.class
                : TIC.class;
        Intent launch = new Intent(incoming);
        launch.setClass(this, target);
        startActivity(launch);
        finish();
    }
}
