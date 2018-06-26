package se.splushii.dancingbunnies.jobs;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import android.widget.Toast;

import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class BackendRefreshJob extends JobService {
    private static final String LC = Util.getLogContext(BackendRefreshJob.class);

    public static final String ACTION = "dancingbunnies.action";
    public static final String API = "dancingbunnies.api";
    public static final String ACTION_FETCH_LIBRARY = "dancingbunnies.action.fetch_library";

    private String action;
    private String api;
    private MusicLibraryService service;
    private Toast toast;
    private JobParameters jobParams;
    private Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        jobParams = params;
        PersistableBundle extras = params.getExtras();
        action = extras.getString(ACTION);
        api = extras.getString(API);
        Intent intent = new Intent(this, MusicLibraryService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.e(LC, "Job stopped");
        stopJob(true);
        return true;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(LC, "Connected to service yeah!");
            MusicLibraryService.MusicLibraryBinder binder = (MusicLibraryService.MusicLibraryBinder) service;
            BackendRefreshJob.this.service = binder.getService();
            BackendRefreshJob.this.doJob();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(LC, "Disconnected from service ooo!");
            jobFinished(jobParams, true);
        }
    };

    private void updateToast(String msg) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG);
        toast.show();
    }

    private class BackendRefreshThread extends Thread {
        Handler handler;
        @Override
        public void run() {
            Looper.prepare();
            handler = new Handler(Looper.getMainLooper());
            handler.post(() -> updateToast("hej"));
            MusicLibraryRequestHandler musicLibraryRequestHandler = new MusicLibraryRequestHandler() {
                @Override
                public void onProgress(String status) {
                    handler.post(() -> updateToast("Status: " + status));
                }

                @Override
                public void onStart() {
                    handler.post(() -> updateToast("ML req onStart"));
                }

                @Override
                public void onSuccess(String status) {
                    handler.post(() -> updateToast("ML req onSuccess: " + status));
                    stopJob(false);
                }

                @Override
                public void onFailure(String status) {
                    handler.post(() -> updateToast("ML req onFailure: " + status));
                    stopJob(false);
                }
            };
            switch (action) {
                case ACTION_FETCH_LIBRARY:
                    service.fetchAPILibrary(api, musicLibraryRequestHandler);
                    break;
                default:
                    Log.w(LC, "Unsupported action: " + action);
                    break;
            }
            Looper.loop();
        }
    }

    private void doJob() {
        worker = new BackendRefreshThread();
        worker.start();
    }

    private void stopJob(boolean needsReschedule) {
        if (worker != null) {
            worker.interrupt();
        }
        unbindService(serviceConnection);
        jobFinished(jobParams, needsReschedule);
    }
}
