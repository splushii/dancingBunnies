package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import java.time.Duration;
import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.util.Util;

public class TransactionsWorker extends Worker {
    private static final String LC = Util.getLogContext(TransactionsWorker.class);

    public static final String DATA_KEY_STATUS = "status";

    public TransactionsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Throwable e = TransactionStorage.getInstance(getApplicationContext())
                .getTransactionsOnce()
                .thenAccept(transactions -> {
                    int total = transactions.size();
                    int successCount = 0;
                    Transaction failureTransaction = null;
                    for (Transaction transaction: transactions) {
                        if (isStopped()) {
                            throw new Util.FutureException("Processing stopped."
                                    + "Successfully applied "
                                    + successCount + "/" + (total - successCount) + " transactions."
                            );
                        }
                        String error = transaction.apply(getApplicationContext());
                        if (error == null) {
                            TransactionStorage.getInstance(getApplicationContext())
                                    .deleteTransactions(Collections.singletonList(transaction));
                            successCount++;
                        } else {
                            TransactionStorage.getInstance(getApplicationContext())
                                    .setError(transaction, error);
                            failureTransaction = transaction;
                            break;
                        }
                        setProgress("Processing... " + successCount + "/" + (total - successCount));
                    }
                    if (failureTransaction != null) {
                        throw new Util.FutureException("Successfully applied "
                                + successCount + "/" + (total - successCount) + " transactions."
                                + " Failed at transaction: " + failureTransaction
                        );
                    }
                })
                .handle((aVoid, throwable) -> throwable)
                .join();
        if (e != null) {
            return Result.failure(data("Failure: " + e.getMessage()));
        }
        return Result.success(data("Success"));
    }

    private Data data(String msg) {
        return new Data.Builder()
                .putString(DATA_KEY_STATUS, msg)
                .build();
    }

    private void setProgress(String status) {
        setProgressAsync(data(status));
    }

    public static void requeue(Context context, boolean forceRunNow) {
        Constraints.Builder workConstraintsBuilder = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED);
        ExistingPeriodicWorkPolicy existingPolicy;
        if (forceRunNow) {
            Log.d(LC, "enqueueing work (now)");
            existingPolicy = ExistingPeriodicWorkPolicy.REPLACE;
        } else {
            Log.d(LC, "enqueueing work (on schedule)");
            existingPolicy = ExistingPeriodicWorkPolicy.KEEP;
            workConstraintsBuilder
//                    .setRequiresDeviceIdle(true)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresCharging(true);
        }
        Constraints workConstraints = workConstraintsBuilder.build();
        PeriodicWorkRequest.Builder workRequestBuilder =
                new PeriodicWorkRequest.Builder(TransactionsWorker.class, Duration.ofDays(1))
                        .setConstraints(workConstraints);
        PeriodicWorkRequest workRequest = workRequestBuilder
                .addTag(Jobs.WORK_NAME_TRANSACTIONS_TAG)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        Jobs.WORK_NAME_TRANSACTIONS_TAG,
                        existingPolicy,
                        workRequest
                );
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork(Jobs.WORK_NAME_TRANSACTIONS_TAG);
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.e(LC, "onStopped");
    }
}
