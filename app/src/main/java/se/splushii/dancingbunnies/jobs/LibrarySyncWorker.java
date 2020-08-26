package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import java.time.Duration;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.ui.settings.TimePreference;
import se.splushii.dancingbunnies.util.Util;

public class LibrarySyncWorker extends Worker {
    private static final String LC = Util.getLogContext(LibrarySyncWorker.class);

    private static final String DATA_KEY_PREF_BACKEND_ID =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.pref_backend_id";
    private static final String DATA_KEY_PREF_FETCH_LIBRARY =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.pref_fetch_library";
    private static final String DATA_KEY_PREF_INDEX_LIBRARY =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.pref_index_library";
    private static final String DATA_KEY_PREF_FETCH_PLAYLISTS =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.pref_fetch_playlists";

    private static final String UNIQUE_WORK_NAME_PREFIX = Jobs.WORK_NAME_LIBRARY_SYNC_TAG + ".backend_id_";

    public static final String DATA_KEY_STATUS = "status";

    private final long preferenceBackendID;
    private final boolean fetchLibrary;
    private final boolean indexLibrary;
    private final boolean fetchPlaylists;

    public LibrarySyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Data inputData = workerParams.getInputData();
        preferenceBackendID = inputData.getLong(DATA_KEY_PREF_BACKEND_ID, SettingsActivityFragment.BACKEND_ID_INVALID);
        fetchLibrary = inputData.getBoolean(DATA_KEY_PREF_FETCH_LIBRARY, true);
        indexLibrary = inputData.getBoolean(DATA_KEY_PREF_INDEX_LIBRARY, true);
        fetchPlaylists = inputData.getBoolean(DATA_KEY_PREF_FETCH_PLAYLISTS, true);
    }

    @NonNull
    @Override
    public Result doWork() {
        String src = SettingsActivityFragment.getSourceFromConfig(
                getApplicationContext(),
                preferenceBackendID
        );
        if (src == null || preferenceBackendID == SettingsActivityFragment.BACKEND_ID_INVALID) {
            return Result.failure(baseData()
                    .putString(DATA_KEY_STATUS, "Failed to sync. Missing internal data.")
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
                            String msg = "Library sync started";
                            setProgress(msg);
                        }

                        @Override
                        public void onProgress(String status) {
                            String msg = "Library sync progress: " + status;
                            setProgress(msg);
                        }
                    }
            ).handle((aVoid, throwable) -> throwable);
            e = fetchLibraryFuture.join();
            if (e != null) {
                return Result.failure(baseData()
                        .putString(DATA_KEY_STATUS, "Failed to sync library: " + e.getMessage())
                        .build()
                );
            }
            SettingsActivityFragment.setSyncCompleteLastRun(getApplicationContext(), preferenceBackendID);
        }
        if (indexLibrary) {
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
                        .putString(DATA_KEY_STATUS, "Failed to index library: " + e.getMessage())
                        .build()
                );
            }
        }
        if (fetchPlaylists) {
            CompletableFuture<Throwable> fetchPlaylistsFuture = MusicLibraryService.fetchPlayLists(
                    getApplicationContext(),
                    src,
                    new MusicLibraryRequestHandler() {
                        @Override
                        public void onStart() {
                            String msg = "Playlist sync started";
                            setProgress(msg);
                        }

                        @Override
                        public void onProgress(String status) {
                            String msg = "Playlist sync progress: " + status;
                            setProgress(msg);
                        }
                    }
            ).handle((aVoid, throwable) -> throwable);
            e = fetchPlaylistsFuture.join();
            if (e != null) {
                return Result.failure(baseData()
                        .putString(DATA_KEY_STATUS, "Failed to sync playlists: " + e.getMessage())
                        .build()
                );
            }
        }
        // Always requeue with all actions enabled despite last run's settings
        requeue(
                getApplicationContext(),
                preferenceBackendID,
                true,
                true,
                true
        );
        return Result.success(baseData()
                .putString(DATA_KEY_STATUS, "Successfully synced")
                .build());
    }

    private void setProgress(String status) {
        setProgressAsync(
                baseData()
                        .putString(DATA_KEY_STATUS, status)
                        .build()
        );
    }

    private Data.Builder baseData() {
        return baseData(
                preferenceBackendID,
                fetchLibrary,
                indexLibrary,
                fetchPlaylists
        );
    }

    public static void runNow(Context context,
                              long preferenceBackendID,
                              boolean fetchLibrary,
                              boolean indexLibrary,
                              boolean fetchPlaylists) {
        requeue(context, preferenceBackendID, fetchLibrary, indexLibrary, fetchPlaylists, true);
    }

    public static void requeue(Context context,
                               long preferenceBackendID,
                               boolean fetchLibrary,
                               boolean indexLibrary,
                               boolean fetchPlaylists) {
        requeue(context, preferenceBackendID, fetchLibrary, indexLibrary, fetchPlaylists, false);
    }

    private static void requeue(Context context,
                                long preferenceBackendID,
                                boolean fetchLibrary,
                                boolean indexLibrary,
                                boolean fetchPlaylists,
                                boolean forceRunNow) {
        Constraints.Builder workConstraintsBuilder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        if (!forceRunNow) {
            workConstraintsBuilder
//                    .setRequiresDeviceIdle(true)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(true);
        }
        Constraints workConstraints = workConstraintsBuilder.build();
        OneTimeWorkRequest.Builder workRequestBuilder =
                new OneTimeWorkRequest.Builder(LibrarySyncWorker.class)
                        .setConstraints(workConstraints);
        if (forceRunNow) {
            Log.d(LC, "enqueueing work (now)");
        } else {
            boolean enabled = SettingsActivityFragment.getScheduledSyncEnabled(
                    context,
                    preferenceBackendID
            );
            if (!enabled) {
                Log.d(LC, "scheduled refresh disabled, not enqueueing work");
                return;
            }
            String timeValue = SettingsActivityFragment.getScheduledSyncTime(
                    context,
                    preferenceBackendID
            );
            int nextHour = TimePreference.parseHour(timeValue);
            int nextMinute = TimePreference.parseMinute(timeValue);

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, nextHour);
            calendar.set(Calendar.MINUTE, nextMinute);
            calendar.set(Calendar.SECOND, 0);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            long currentTime = System.currentTimeMillis();
            long tomorrowTime = calendar.getTimeInMillis();
            long timeDiffBetweenNowAndTomorrow = tomorrowTime - currentTime;
            workRequestBuilder.setInitialDelay(timeDiffBetweenNowAndTomorrow, TimeUnit.MILLISECONDS);
            Log.d(LC, "enqueueing work ("
                    + "delayed " + Duration.ofMillis(timeDiffBetweenNowAndTomorrow).toMinutes()  + " min"
                    + " to " + calendar.getTime()
                    + ")");
        }
        OneTimeWorkRequest workRequest = workRequestBuilder
                .setInputData(
                        baseData(
                                preferenceBackendID,
                                fetchLibrary,
                                indexLibrary,
                                fetchPlaylists
                        ).build()
                )
                .addTag(Jobs.WORK_NAME_LIBRARY_SYNC_TAG)
                .addTag(uniqueWorkName(preferenceBackendID))
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(
                        uniqueWorkName(preferenceBackendID),
                        ExistingWorkPolicy.KEEP,
                        workRequest
                );
    }

    public static void cancel(Context context, long preferenceBackendID) {
        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork(uniqueWorkName(preferenceBackendID));
    }

    private static String uniqueWorkName(long preferenceBackendID) {
        return UNIQUE_WORK_NAME_PREFIX + preferenceBackendID;
    }


    public static long getBackendIDFromTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return SettingsActivityFragment.BACKEND_ID_INVALID;
        }
        for (String tag: tags) {
            String backendIDString = tag.replace(UNIQUE_WORK_NAME_PREFIX, "");
            try {
                return Long.parseLong(backendIDString);
            } catch (NumberFormatException ignored) {}
        }
        return SettingsActivityFragment.BACKEND_ID_INVALID;
    }

    private static Data.Builder baseData(long preferenceBackendID,
                                         boolean fetchLibrary,
                                         boolean indexLibrary,
                                         boolean fetchPlaylists) {
        return new Data.Builder()
                .putLong(DATA_KEY_PREF_BACKEND_ID, preferenceBackendID)
                .putBoolean(DATA_KEY_PREF_FETCH_LIBRARY, fetchLibrary)
                .putBoolean(DATA_KEY_PREF_INDEX_LIBRARY, indexLibrary)
                .putBoolean(DATA_KEY_PREF_FETCH_PLAYLISTS, fetchPlaylists);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
