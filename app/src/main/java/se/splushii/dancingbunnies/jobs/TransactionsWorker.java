package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
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
        HashSet<String> sourcesToSync = new HashSet<>();
        Throwable e = TransactionStorage.getInstance(getApplicationContext())
                .getTransactionsOnce()
                .thenAccept(transactions -> {
                    int total = transactions.size();
                    final AtomicInteger successCount = new AtomicInteger();
                    List<Transaction> transactionBatch = new ArrayList<>();
                    String transactionBatchSource = "";
                    Throwable error = null;
                    for (int i = 0; i < transactions.size(); i++) {
                        if (isStopped()) {
                            throw new Util.FutureException("Processing stopped."
                                    + "Successfully applied "
                                    + successCount + "/" + (total - successCount.get()) + " transactions."
                            );
                        }
                        Transaction transaction = transactions.get(i);
                        // Batch transactions per source
                        if (transactionBatch.isEmpty()
                                || transactionBatchSource.equals(transaction.getSrc())) {
                            transactionBatch.add(transaction);
                            transactionBatchSource = transaction.getSrc();
                            if (i < transactions.size() - 1) {
                                continue;
                            }
                        }
                        // Apply source-batched transactions
                        error = APIClient
                                .getAPIClient(getApplicationContext(), transactionBatchSource)
                                .applyTransactions(getApplicationContext(), transactionBatch)
                                .handle((transactionResults, throwable) -> {
                                    if (transactionResults == null) {
                                        return throwable;
                                    }
                                    transactionResults.forEach(transactionResult -> {
                                        if (transactionResult.error == null) {
                                            TransactionStorage.getInstance(getApplicationContext())
                                                    .deleteTransactions(Collections.singletonList(
                                                            transactionResult.transaction
                                                    ));
                                            successCount.incrementAndGet();
                                            sourcesToSync.add(transactionResult.transaction.getSrc());
                                        } else {
                                            TransactionStorage.getInstance(getApplicationContext())
                                                    .setError(
                                                            transactionResult.transaction,
                                                            transactionResult.error
                                                    );
                                        }
                                    });
                                    return throwable;
                                })
                                .join();
//                        Throwable error = transaction.apply(getApplicationContext())
//                                .handle((aVoid, throwable) -> throwable)
//                                .join();
//                        if (error == null) {
//                            TransactionStorage.getInstance(getApplicationContext())
//                                    .deleteTransactions(Collections.singletonList(transaction));
//                            successCount++;
//                            sourcesToSync.add(transaction.getSrc());
//                        } else {
//                            TransactionStorage.getInstance(getApplicationContext())
//                                    .setError(transaction, error.getMessage());
//                            failureTransaction = transaction;
//                            break;
//                        }
                        if (error != null) {
                            break;
                        }
                        setProgress(
                                "Processing... " + successCount + "/" + (total - successCount.get())
                        );
                        transactionBatch.clear();
                        transactionBatch.add(transaction);
                        transactionBatchSource = transaction.getSrc();
                    }
                    if (error != null) {
                        throw new Util.FutureException("Successfully applied "
                                + successCount + "/" + (total - successCount.get()) + " transactions."
                        );
                    }
                })
                .handle((aVoid, throwable) -> throwable)
                .join();
        scheduleSync(sourcesToSync);
        if (e != null) {
            Log.e(LC, "Failure: " + e.getMessage());
            e.printStackTrace();
            return Result.failure(data("Failure: " + e.getMessage()));
        }
        return Result.success(data("Success"));
    }

    private void scheduleSync(Set<String> sources) {
        // TODO: Keep track of what needs to be synced. Only library or only playlists? Both?
        for (String src: sources) {
            LibrarySyncWorker.runNow(
                    getApplicationContext(),
                    SettingsActivityFragment.getBackendConfigIDFromSource(
                            getApplicationContext(),
                            src
                    ),
                    true,
                    true,
                    true
            );
        }
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
