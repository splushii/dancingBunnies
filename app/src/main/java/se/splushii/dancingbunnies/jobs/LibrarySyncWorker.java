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
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.ui.TimePreference;
import se.splushii.dancingbunnies.util.Util;

public class LibrarySyncWorker extends Worker {
    private static final String LC = Util.getLogContext(LibrarySyncWorker.class);

    public LibrarySyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(LC, "doWork");
        setLastRefresh(getApplicationContext());
        String src = MusicLibraryService.API_ID_SUBSONIC;
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
        requeue(getApplicationContext());
        return Result.success();
    }

    private void setProgress(String status) {
        setProgressAsync(new Data.Builder().putString("status", status).build());
    }

    public static void runNow(Context context) {
        requeue(context, true);
    }

    public static void requeue(Context context) {
        requeue(context, false);
    }

    private static void requeue(Context context, boolean forceRunNow) {
        Constraints workConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
//                .setRequiresDeviceIdle(true)
                .build();
        OneTimeWorkRequest.Builder workRequestBuilder =
                new OneTimeWorkRequest.Builder(LibrarySyncWorker.class)
                        .setConstraints(workConstraints);
        if (forceRunNow) {
            Log.d(LC, "enqueueing work (now)");
        } else {
            SharedPreferences sharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(context.getApplicationContext());
            boolean enabled = sharedPreferences.getBoolean(
                    context.getApplicationContext().getResources().getString(
                            R.string.pref_key_subsonic_refresh_enabled
                    ),
                    false
            );
            if (!enabled) {
                Log.d(LC, "scheduled refresh disabled, not enqueueing work");
                return;
            }
            String timeValue = sharedPreferences.getString(
                    context.getApplicationContext().getResources().getString(
                            R.string.pref_key_subsonic_refresh_time
                    ),
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
        OneTimeWorkRequest workRequest = workRequestBuilder.build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(
                        Jobs.WORK_NAME_LIBRARY_SYNC,
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                );
    }

    private void setLastRefresh(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        sharedPreferences.edit()
                .putLong(
                        context.getApplicationContext().getResources().getString(
                                R.string.pref_key_subsonic_refresh_last_sync
                        ),
                        System.currentTimeMillis()
                )
                .apply();
    }

    public static long getLastRefresh(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        return sharedPreferences.getLong(
                        context.getApplicationContext().getResources().getString(
                                R.string.pref_key_subsonic_refresh_last_sync
                        ),
                        -1
                );
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
