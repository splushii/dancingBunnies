package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.ui.settings.TimePreference;
import se.splushii.dancingbunnies.util.Util;

public class Jobs {
    private static final String LC = Util.getLogContext(Jobs.class);

    private static final String WORKER_CLASS_PREFIX = "se.splushii.dancingbunnies.jobs.";

    public static final String WORK_NAME_BACKEND_SYNC_TAG =
            "se.splushii.dancingbunnies.work_name.backend_sync";
    public static final String WORK_NAME_BACKEND_SYNC_NOW_TAG =
            "se.splushii.dancingbunnies.work_name.backend_sync_now";
    public static final String WORK_NAME_BACKEND_SYNC_SCHEDULED_TAG =
            "se.splushii.dancingbunnies.work_name.backend_sync_scheduled";
    public static final String WORK_NAME_BACKEND_SYNC_REQUEUE_TAG =
            "se.splushii.dancingbunnies.work_name.backend_sync_requeue";
    public static final String WORK_NAME_BACKEND_SYNC_LIBRARY_FETCH_TAG =
            "se.splushii.dancingbunnies.work_name.backnend_sync_library_fetch";
    public static final String WORK_NAME_BACKEND_SYNC_LIBRARY_INDEX_TAG =
            "se.splushii.dancingbunnies.work_name.backend_sync_library_index";
    public static final String WORK_NAME_BACKEND_SYNC_PLAYLISTS_FETCH_TAG =
            "se.splushii.dancingbunnies.work_name.backend_sync_playlists_fetch";
    public static final String WORK_NAME_TRANSACTIONS_TAG =
            "se.splushii.dancingbunnies.work_name.transactions";

    public static final String DATA_KEY_STATUS = "status";

    static final String DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BACKEND_ID =
            "se.splushii.dancingbunnies.input_key.backend_sync_library_fetch.backend_id";
    static final String DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BOOL =
            "se.splushii.dancingbunnies.input_key.backend_sync_library_fetch.bool";

    static final String DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BACKEND_ID =
            "se.splushii.dancingbunnies.input_key.backend_sync_library_index.backend_id";
    static final String DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BOOL =
            "se.splushii.dancingbunnies.input_key.backend_sync_library_index.bool";

    static final String DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BACKEND_ID =
            "se.splushii.dancingbunnies.input_key.backend_sync_playlists_fetch.backend_id";
    static final String DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BOOL =
            "se.splushii.dancingbunnies.input_key.backend_sync_playlists_fetch.bool";

    static final String DATA_KEY_BACKEND_SYNC_REQUEUE_BACKEND_ID =
            "se.splushii.dancingbunnies.input_key.backend_sync_requeue.backend_id";

    public enum WorkerType {
        LIBRARY_FETCH,
        LIBRARY_INDEX,
        BACKEND_SYNC_REQUEUE,
        PLAYLIST_FETCH,
        TRANSACTIONS,
        UNKNOWN
    }

    public static void runBackendSyncNow(Context context,
                                         long preferenceBackendID,
                                         boolean fetchLibrary,
                                         boolean indexLibrary,
                                         boolean fetchPlaylists) {
        requeueBackendSync(context, preferenceBackendID, fetchLibrary, indexLibrary, fetchPlaylists, false);
    }

    public static void requeueBackendSync(Context context,
                                          long preferenceBackendID,
                                          boolean fetchLibrary,
                                          boolean indexLibrary,
                                          boolean fetchPlaylists) {
        requeueBackendSync(context, preferenceBackendID, fetchLibrary, indexLibrary, fetchPlaylists, true);
    }


    private static void requeueBackendSync(Context context,
                                           long preferenceBackendID,
                                           boolean fetchLibrary,
                                           boolean indexLibrary,
                                           boolean fetchPlaylists,
                                           boolean scheduled) {
        String scheduledTag = scheduled
                ? WORK_NAME_BACKEND_SYNC_SCHEDULED_TAG
                : WORK_NAME_BACKEND_SYNC_NOW_TAG;
        String uniqueWorkName = uniqueWorkName(scheduledTag, preferenceBackendID);
        Constraints.Builder workConstraintsBuilder = new Constraints.Builder()
                // TODO: Optionally only run on unmetered / non-roaming connection
                // .setRequiredNetworkType(NetworkType.UNMETERED)
                // .setRequiredNetworkType(NetworkType.NOT_ROAMING)
                .setRequiredNetworkType(NetworkType.CONNECTED);
        if (scheduled) {
            workConstraintsBuilder
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true);
        }
        Constraints workConstraints = workConstraintsBuilder.build();
        OneTimeWorkRequest.Builder fetchLibraryRequestBuilder =
                new OneTimeWorkRequest.Builder(LibraryFetchWorker.class)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                        .setConstraints(workConstraints);
        OneTimeWorkRequest.Builder fetchPlaylistsRequestBuilder =
                new OneTimeWorkRequest.Builder(PlaylistFetchWorker.class)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                        .setConstraints(workConstraints);
        if (scheduled) {
            boolean enabled = SettingsActivityFragment.getScheduledSyncEnabled(
                    context,
                    preferenceBackendID
            );
            if (!enabled) {
                Log.d(LC, "scheduled refresh disabled, not enqueueing backend sync");
                return;
            }
            String timeValue = SettingsActivityFragment.getScheduledSyncTime(
                    context,
                    preferenceBackendID
            );
            int nextHour = TimePreference.parseHour(timeValue);
            int nextMinute = TimePreference.parseMinute(timeValue);

            Calendar now = Calendar.getInstance();
            Calendar nextTime = Calendar.getInstance();
            nextTime.set(Calendar.HOUR_OF_DAY, nextHour);
            nextTime.set(Calendar.MINUTE, nextMinute);
            if (nextTime.before(now) || nextTime.equals(now)) {
                nextTime.add(Calendar.DAY_OF_YEAR, 1);
            }
            long timeDiffBetweenNowAndTomorrow = nextTime.getTimeInMillis() - now.getTimeInMillis();
            fetchLibraryRequestBuilder.setInitialDelay(timeDiffBetweenNowAndTomorrow, TimeUnit.MILLISECONDS);
            fetchPlaylistsRequestBuilder.setInitialDelay(timeDiffBetweenNowAndTomorrow, TimeUnit.MILLISECONDS);
            Log.d(LC, "enqueueing backend sync ("
                    + "delayed " + Duration.ofMillis(timeDiffBetweenNowAndTomorrow).toMinutes()  + " min"
                    + " to " + nextTime.getTime()
                    + ")");
            SettingsActivityFragment.setScheduledSyncTimeNext(
                    context,
                    preferenceBackendID,
                    nextTime.getTimeInMillis()
            );
        } else {
            Log.d(LC, "enqueueing backend sync (now)");
        }
        WorkContinuation workContinuation = null;
        List<OneTimeWorkRequest> fetchWorkRequests = new ArrayList<>();
        if (fetchLibrary) {
            // Always index library if fetching library
            indexLibrary = true;
            OneTimeWorkRequest fetchLibraryRequest = fetchLibraryRequestBuilder
                    .setInputData(new Data.Builder()
                            .putLong(DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BACKEND_ID, preferenceBackendID)
                            .putBoolean(DATA_KEY_BACKEND_SYNC_LIBRARY_FETCH_BOOL, fetchLibrary)
                            .build()
                    )
                    .addTag(WORK_NAME_BACKEND_SYNC_TAG)
                    .addTag(uniqueWorkName(WORK_NAME_BACKEND_SYNC_TAG, preferenceBackendID))
                    .addTag(scheduledTag)
                    .addTag(WORK_NAME_BACKEND_SYNC_LIBRARY_FETCH_TAG)
                    .build();
            fetchWorkRequests.add(fetchLibraryRequest);
        }
        if (fetchPlaylists) {
            OneTimeWorkRequest fetchPlaylistsRequest = fetchPlaylistsRequestBuilder
                    .setInputData(new Data.Builder()
                            .putLong(DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BACKEND_ID, preferenceBackendID)
                            .putBoolean(DATA_KEY_BACKEND_SYNC_PLAYLISTS_FETCH_BOOL, fetchPlaylists)
                            .build()
                    )
                    .addTag(WORK_NAME_BACKEND_SYNC_TAG)
                    .addTag(uniqueWorkName(WORK_NAME_BACKEND_SYNC_TAG, preferenceBackendID))
                    .addTag(scheduledTag)
                    .addTag(WORK_NAME_BACKEND_SYNC_PLAYLISTS_FETCH_TAG)
                    .build();
            fetchWorkRequests.add(fetchPlaylistsRequest);
        }
        if (!fetchWorkRequests.isEmpty()) {
            workContinuation = WorkManager.getInstance(context.getApplicationContext())
                    .beginUniqueWork(
                            uniqueWorkName,
                            ExistingWorkPolicy.REPLACE,
                            fetchWorkRequests
                    );
        }
        if (indexLibrary) {
            OneTimeWorkRequest indexLibraryRequest = new OneTimeWorkRequest.Builder(LibraryIndexWorker.class)
                    .setInputData(new Data.Builder()
                            .putLong(DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BACKEND_ID, preferenceBackendID)
                            .putBoolean(DATA_KEY_BACKEND_SYNC_LIBRARY_INDEX_BOOL, indexLibrary)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                    .setConstraints(workConstraints)
                    .addTag(WORK_NAME_BACKEND_SYNC_TAG)
                    .addTag(uniqueWorkName(WORK_NAME_BACKEND_SYNC_TAG, preferenceBackendID))
                    .addTag(scheduledTag)
                    .addTag(WORK_NAME_BACKEND_SYNC_LIBRARY_INDEX_TAG)
                    .build();
            if (workContinuation == null) {
                workContinuation = WorkManager.getInstance(context.getApplicationContext())
                        .beginUniqueWork(
                                uniqueWorkName,
                                ExistingWorkPolicy.REPLACE,
                                indexLibraryRequest
                        );
            } else {
                workContinuation = workContinuation.then(indexLibraryRequest);
            }
        }
        if (scheduled) {
            OneTimeWorkRequest requeueRequest = new OneTimeWorkRequest.Builder(BackendSyncReqeueWorker.class)
                    .setInputData(new Data.Builder()
                            .putLong(DATA_KEY_BACKEND_SYNC_REQUEUE_BACKEND_ID, preferenceBackendID)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
                    .setConstraints(workConstraints)
                    .addTag(WORK_NAME_BACKEND_SYNC_TAG)
                    .addTag(uniqueWorkName(WORK_NAME_BACKEND_SYNC_TAG, preferenceBackendID))
                    .addTag(scheduledTag)
                    .addTag(WORK_NAME_BACKEND_SYNC_REQUEUE_TAG)
                    .build();
            if (workContinuation == null) {
                workContinuation = WorkManager.getInstance(context.getApplicationContext())
                        .beginUniqueWork(
                                uniqueWorkName,
                                ExistingWorkPolicy.REPLACE,
                                requeueRequest
                        );
            } else {
                workContinuation = workContinuation.then(requeueRequest);
            }
        }
        if (workContinuation != null) {
            workContinuation.enqueue();
        } else {
            Log.e(LC, "No work requests to enqueue");
        }
    }

    public static void cancelLibrarySync(Context context,
                                         boolean scheduled,
                                         long preferenceBackendID) {
        String uniqueWorkName = scheduled
                ? uniqueWorkName(WORK_NAME_BACKEND_SYNC_SCHEDULED_TAG, preferenceBackendID)
                : uniqueWorkName(WORK_NAME_BACKEND_SYNC_NOW_TAG, preferenceBackendID);
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(uniqueWorkName);
    }

    static String uniqueWorkName(String uniqueNamePrefix, long preferenceBackendID) {
        return uniqueNamePrefix + preferenceBackendID;
    }

    public static long getBackendIDFromTags(String uniqueNamePrefix, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return SettingsActivityFragment.BACKEND_ID_INVALID;
        }
        for (String tag: tags) {
            String backendIDString = tag.replace(uniqueNamePrefix, "");
            try {
                return Long.parseLong(backendIDString);
            } catch (NumberFormatException ignored) {}
        }
        return SettingsActivityFragment.BACKEND_ID_INVALID;
    }

    public static String getWorkerClassFromTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        for (String tag: tags) {
            if (tag.startsWith(WORKER_CLASS_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    public static String getWorkerDisplayName(WorkInfo workInfo) {
        switch (getWorkerType(workInfo)) {
            case LIBRARY_FETCH:
                return "Fetch library";
            case LIBRARY_INDEX:
                return "Index library";
            case PLAYLIST_FETCH:
                return "Fetch playlists";
            case BACKEND_SYNC_REQUEUE:
                return "Requeue sync";
            case TRANSACTIONS:
                return "Process transactions";
            default:
                return "UNKNOWN";
        }
    }

    public static WorkerType getWorkerType(WorkInfo workInfo) {
        String workerClass = getWorkerClassFromTags(workInfo.getTags());
        if (LibraryFetchWorker.class.getName().equals(workerClass)) {
            return WorkerType.LIBRARY_FETCH;
        } else if (LibraryIndexWorker.class.getName().equals(workerClass)) {
            return WorkerType.LIBRARY_INDEX;
        } else if (PlaylistFetchWorker.class.getName().equals(workerClass)) {
            return WorkerType.PLAYLIST_FETCH;
        } else if (BackendSyncReqeueWorker.class.getName().equals(workerClass)) {
            return WorkerType.BACKEND_SYNC_REQUEUE;
        } else if (TransactionsWorker.class.getName().equals(workerClass)) {
            return WorkerType.TRANSACTIONS;
        }
        return WorkerType.UNKNOWN;
    }
}
