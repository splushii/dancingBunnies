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
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.util.Util;

public class LibraryFetchWorker extends Worker {
    private static final String LC = Util.getLogContext(LibraryFetchWorker.class);
    private static final Jobs.WorkerType WORKER_TYPE = Jobs.WorkerType.LIBRARY_FETCH;

    private final long preferenceBackendID;
    private final boolean fetchLibrary;

    public LibraryFetchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Data inputData = workerParams.getInputData();
        preferenceBackendID = inputData.getLong(
                Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BACKEND_ID,
                SettingsActivityFragment.BACKEND_ID_INVALID
        );
        fetchLibrary = inputData.getBoolean(Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BOOL, true);
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
                    .putString(Jobs.DATA_KEY_STATUS, "Failed to fetch library. Missing internal data.")
                    .build());
        }
        Throwable e;
        if (fetchLibrary) {
            CompletableFuture<Throwable> fetchLibraryFuture = MusicLibraryService.fetchLibrary(
                    getApplicationContext(),
                    src,
                    new MusicLibraryRequestHandler() {
                        @Override
                        public void onStart() {
                            String msg = "Library fetch started";
                            setProgress(msg);
                        }

                        @Override
                        public void onProgress(String status) {
                            String msg = "Progress: " + status;
                            setProgress(msg);
                        }
                    }
            ).thenRun(() -> TransactionStorage.getInstance(getApplicationContext())
                            .markTransactionsAppliedLocally(src, Transaction.GROUP_LIBRARY, false)
            ).handle((aVoid, throwable) -> throwable);
            e = fetchLibraryFuture.join();
            if (e != null) {
                return Result.failure(baseData()
                        .putString(Jobs.DATA_KEY_STATUS, "Failed to fetch library: " + e.getMessage())
                        .build()
                );
            }
            SettingsActivityFragment.setLastRun(getApplicationContext(), preferenceBackendID, WORKER_TYPE);
        }
        return Result.success(baseData()
                .putString(Jobs.DATA_KEY_STATUS, "Successfully fetched library")
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
                .putLong(Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BACKEND_ID, preferenceBackendID)
                .putBoolean(Jobs.DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BOOL, fetchLibrary);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
