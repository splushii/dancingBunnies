package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.util.Util;

public class LibraryIndexWorker extends Worker {
    private static final String LC = Util.getLogContext(LibraryIndexWorker.class);
    private static final Jobs.WorkerType WORKER_TYPE = Jobs.WorkerType.LIBRARY_INDEX;

    private final long preferenceBackendID;
    private final boolean indexLibrary;

    public LibraryIndexWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Data inputData = workerParams.getInputData();
        preferenceBackendID = inputData.getLong(
                Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BACKEND_ID,
                SettingsActivityFragment.BACKEND_ID_INVALID
        );
        indexLibrary = inputData.getBoolean(Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BOOL, true);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(LC, "doWork");
        String src = SettingsActivityFragment.getSourceFromConfig(
                getApplicationContext(),
                preferenceBackendID
        );
        if (src == null || preferenceBackendID == SettingsActivityFragment.BACKEND_ID_INVALID) {
            return Result.failure(baseData()
                    .putString(Jobs.DATA_KEY_STATUS, "Failed to index library. Missing internal data.")
                    .build());
        }
        Throwable e;
        CompletableFuture<Throwable> indexLibraryFuture = MusicLibraryService.indexLibrary(
                getApplicationContext(),
                src,
                new MusicLibraryRequestHandler() {
                    @Override
                    public void onStart() {
                        String msg = "Library index started";
                        setProgress(msg);
                    }

                    @Override
                    public void onProgress(String status) {
                        String msg = "Library index progress: " + status;
                        setProgress(msg);
                    }
                }
        ).handle((aVoid, throwable) -> throwable);
        e = indexLibraryFuture.join();
        if (e != null) {
            return Result.failure(baseData()
                    .putString(Jobs.DATA_KEY_STATUS, "Failed to index library: " + e.getMessage())
                    .build()
            );
        }
        SettingsActivityFragment.setLastRun(getApplicationContext(), preferenceBackendID, WORKER_TYPE);
        return Result.success(baseData()
                .putString(Jobs.DATA_KEY_STATUS, "Successfully indexed library")
                .build());
    }

    private void setProgress(String status) {
        setProgressAsync(
                baseData()
                        .putString(Jobs.DATA_KEY_STATUS, status)
                        .build()
        );
    }

    private Data.Builder baseData() {
        return new Data.Builder()
                .putLong(Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BACKEND_ID, preferenceBackendID)
                .putBoolean(Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BOOL, indexLibrary);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
