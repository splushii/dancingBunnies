package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.time.Duration;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.SettingsActivityFragment;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.ui.TimePreference;
import se.splushii.dancingbunnies.util.Util;

public class LibrarySyncWorker extends Worker {
    private static final String LC = Util.getLogContext(LibrarySyncWorker.class);

    private static final String INPUT_KEY_API =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.api";
    private static final String INPUT_KEY_PREF_BACKEND_ID =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.pref_backend_id";
    private static final String INPUT_KEY_REFRESH_LAST_SYNC_PREF_KEY =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.refresh_last_sync_pref_key";
    private static final String INPUT_KEY_REFRESH_SCHEDULE_ENABLED_PREF_KEY =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.refresh_schedule_enabled_pref_key";
    private static final String INPUT_KEY_REFRESH_SCHEDULE_TIME_PREF_KEY =
            "se.splushii.dancingbunnies.librarysyncworker.input_key.refresh_schedule_time_pref_key";

    public LibrarySyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(LC, "doWork");
        String api = getInputData().getString(INPUT_KEY_API);
        int preferenceBackendID = getInputData().getInt(INPUT_KEY_PREF_BACKEND_ID, SettingsActivityFragment.BACKEND_ID_INVALID);
        int refreshLastSyncPrefKey = getInputData().getInt(INPUT_KEY_REFRESH_LAST_SYNC_PREF_KEY, 0);
        int refreshScheduleEnabledPrefKey = getInputData().getInt(INPUT_KEY_REFRESH_SCHEDULE_ENABLED_PREF_KEY, 0);
        int refreshScheduleTimePrefKey = getInputData().getInt(INPUT_KEY_REFRESH_SCHEDULE_TIME_PREF_KEY, 0);
        String src = SettingsActivityFragment.getSourceFromConfig(
                getApplicationContext(),
                api,
                preferenceBackendID
        );
        if (src == null
                || preferenceBackendID == SettingsActivityFragment.BACKEND_ID_INVALID
                || refreshLastSyncPrefKey == 0
                || refreshScheduleEnabledPrefKey == 0
                || refreshScheduleTimePrefKey == 0) {
            return Result.failure();
        }
        setLastRefresh(getApplicationContext(), refreshLastSyncPrefKey);
        CompletableFuture<Void> fetchPlaylistsFuture = new CompletableFuture<>();
        MusicLibraryService.fetchAPILibrary(
                getApplicationContext(),
                src,
                new MusicLibraryRequestHandler() {
                    @Override
                    public void onProgress(String status) {
                        String msg = "onProgress: " + status;
                        Log.d(LC, msg);
                        setProgress(msg);
                    }

                    @Override
                    public void onStart() {
                        String msg = "onStart";
                        Log.d(LC, msg);
                        setProgress(msg);
                    }

                    @Override
                    public void onSuccess(String status) {
                        String msg = "onSuccess: " + status;
                        Log.d(LC, msg);
                        setProgress(msg);
                        MusicLibraryService.fetchPlayLists(
                                getApplicationContext(),
                                src,
                                new MusicLibraryRequestHandler() {
                                    @Override
                                    public void onStart() {
                                        String msg = "onStart fetchPlaylists";
                                        Log.d(LC, msg);
                                        setProgress(msg);
                                    }

                                    @Override
                                    public void onSuccess(String status) {
                                        String msg = "onSuccess fetchPlaylists: " + status;
                                        Log.d(LC, msg);
                                        setProgress(msg);
                                        fetchPlaylistsFuture.complete(null);
                                    }

                                    @Override
                                    public void onFailure(String status) {
                                        String msg = "onFailure fetchPlaylists: " + status;
                                        Log.e(LC, msg);
                                        setProgress(msg);
                                        fetchPlaylistsFuture.complete(null);
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String status) {
                        String msg = "onFailure: " + status;
                        Log.e(LC, msg);
                        setProgress(msg);
                    }
                }
        ).join();
        fetchPlaylistsFuture.join();
        requeue(
                getApplicationContext(),
                api,
                preferenceBackendID,
                refreshLastSyncPrefKey,
                refreshScheduleEnabledPrefKey,
                refreshScheduleTimePrefKey
        );
        return Result.success();
    }

    private void setProgress(String status) {
        setProgressAsync(new Data.Builder().putString("status", status).build());
    }

    public static void runNow(Context context,
                              String api,
                              int preferenceBackendID,
                              int refreshLastSyncPrefKey,
                              int refreshScheduleEnabledPrefKey,
                              int refreshScheduleTimePrefKey) {
        requeue(
                context,
                api,
                preferenceBackendID,
                refreshLastSyncPrefKey,
                refreshScheduleEnabledPrefKey,
                refreshScheduleTimePrefKey,
                true
        );
    }

    public static void requeue(Context context,
                               String api,
                               int preferenceBackendID,
                               int refreshLastSyncPrefKey,
                               int refreshScheduleEnabledPrefKey,
                               int refreshScheduleTimePrefKey) {
        requeue(
                context,
                api,
                preferenceBackendID,
                refreshLastSyncPrefKey,
                refreshScheduleEnabledPrefKey,
                refreshScheduleTimePrefKey,
                false
        );
    }

    private static void requeue(Context context,
                                String api,
                                int preferenceBackendID,
                                int refreshLastSyncPrefKey,
                                int refreshScheduleEnabledPrefKey,
                                int refreshScheduleTimePrefKey,
                                boolean forceRunNow) {
        String src = SettingsActivityFragment.getSourceFromConfig(context, api, preferenceBackendID);
        if (src == null) {
            Log.e(LC, "requeue: src is null");
            return;
        }
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
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        if (forceRunNow) {
            Log.d(LC, "enqueueing work (now)");
        } else {
            boolean enabled = sharedPreferences.getBoolean(
                    context.getApplicationContext()
                            .getResources()
                            .getString(refreshScheduleEnabledPrefKey),
                    false
            );
            if (!enabled) {
                Log.d(LC, "scheduled refresh disabled, not enqueueing work");
                return;
            }
            String timeValue = sharedPreferences.getString(
                    context.getApplicationContext()
                            .getResources()
                            .getString(refreshScheduleTimePrefKey),
                    TimePreference.DEFAULT
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
                        new Data.Builder()
                                .putString(INPUT_KEY_API, api)
                                .putInt(INPUT_KEY_PREF_BACKEND_ID, preferenceBackendID)
                                .putInt(INPUT_KEY_REFRESH_LAST_SYNC_PREF_KEY, refreshLastSyncPrefKey)
                                .putInt(INPUT_KEY_REFRESH_SCHEDULE_ENABLED_PREF_KEY, refreshScheduleEnabledPrefKey)
                                .putInt(INPUT_KEY_REFRESH_SCHEDULE_TIME_PREF_KEY, refreshScheduleTimePrefKey)
                                .build()
                )
                .addTag(Jobs.WORK_NAME_LIBRARY_SYNC_TAG)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(
                        src,
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                );
    }

    private void setLastRefresh(Context context, int prefKey) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        sharedPreferences.edit()
                .putLong(
                        context.getApplicationContext().getResources().getString(prefKey),
                        System.currentTimeMillis()
                )
                .apply();
    }

    public static long getLastRefresh(Context context, int prefKey) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        return sharedPreferences.getLong(
                        context.getApplicationContext().getResources().getString(prefKey),
                        -1
                );
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
