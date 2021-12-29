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

public class PlaylistFetchWorker extends Worker {
    private static final String LC = Util.getLogContext(PlaylistFetchWorker.class);
    private static final Jobs.WorkerType WORKER_TYPE = Jobs.WorkerType.PLAYLIST_FETCH;

    private final long preferenceBackendID;
    private final boolean fetchPlaylists;

    public PlaylistFetchWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Data inputData = workerParams.getInputData();
        preferenceBackendID = inputData.getLong(
                Jobs.DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BACKEND_ID,
                SettingsActivityFragment.BACKEND_ID_INVALID
        );
        fetchPlaylists = inputData.getBoolean(Jobs.DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BOOL, true);
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
                    .putString(Jobs.DATA_KEY_STATUS, "Failed to fetch playlists. Missing internal data.")
                    .build());
        }
        Throwable e;
        if (fetchPlaylists) {
            CompletableFuture<Throwable> fetchPlaylistsFuture = MusicLibraryService.fetchPlayLists(
                    getApplicationContext(),
                    src,
                    new MusicLibraryRequestHandler() {
                        @Override
                        public void onStart() {
                            String msg = "Playlist fetch started";
                            setProgress(msg);
                        }

                        @Override
                        public void onProgress(String status) {
                            String msg = "Playlist fetch progress: " + status;
                            setProgress(msg);
                        }
                    }
            ).thenRun(() -> TransactionStorage.getInstance(getApplicationContext())
                    .markTransactionsAppliedLocally(src, Transaction.GROUP_PLAYLISTS, false)
            ).handle((aVoid, throwable) -> throwable);
            e = fetchPlaylistsFuture.join();
            if (e != null) {
                return Result.failure(baseData()
                        .putString(Jobs.DATA_KEY_STATUS, "Failed to fetch playlists: " + e.getMessage())
                        .build()
                );
            }
        }
        SettingsActivityFragment.setLastRun(getApplicationContext(), preferenceBackendID, WORKER_TYPE);
        return Result.success(baseData()
                .putString(Jobs.DATA_KEY_STATUS, "Successfully fetched playlists")
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
                .putLong(Jobs.DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BACKEND_ID, preferenceBackendID)
                .putBoolean(Jobs.DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BOOL, fetchPlaylists);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
