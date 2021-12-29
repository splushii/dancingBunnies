package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.util.Util;

public class BackendSyncReqeueWorker extends Worker {
    private static final String LC = Util.getLogContext(BackendSyncReqeueWorker.class);

    private final long preferenceBackendID;

    public BackendSyncReqeueWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Data inputData = workerParams.getInputData();
        preferenceBackendID = inputData.getLong(
                Jobs.DATA_KEY_BACKEND_SYNC_REQUEUE_BACKEND_ID,
                SettingsActivityFragment.BACKEND_ID_INVALID
        );
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
                    .putString(Jobs.DATA_KEY_STATUS, "Failed to requeue. Missing internal data.")
                    .build());
        }
        // Always requeue with all actions enabled despite last run's settings
        Jobs.requeueBackendSync(
                getApplicationContext(),
                preferenceBackendID,
                true,
                true,
                true
        );
        return Result.success(baseData()
                .putString(Jobs.DATA_KEY_STATUS, "Successfully requeued")
                .build());
    }

    private Data.Builder baseData() {
        return new Data.Builder()
                .putLong(Jobs.DATA_KEY_BACKEND_SYNC_REQUEUE_BACKEND_ID, preferenceBackendID);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
