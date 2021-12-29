package se.splushii.dancingbunnies.jobs;

import android.content.Context;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.storage.transactions.TransactionResult;
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
                    int totalToSource = transactions.size();
                    List<Transaction> transactionsToApplyLocally = transactions.stream()
                            .filter(t -> !t.isAppliedLocally())
                            .filter(t ->
                                    // dB local will be applied locally when "applied to source"
                                    // so this source is skipped when applying locally
                                    // to avoid applying it twice
                                    !MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL
                                            .equals(t.getSrc()))
                            .collect(Collectors.toList());
                    int totalLocally = transactionsToApplyLocally.size();
                    final AtomicInteger appliedLocallyCount = new AtomicInteger();
                    final AtomicInteger appliedToSourceCount = new AtomicInteger();
                    Throwable error;
                    // Apply transactions locally
                    error = applyTransactions(
                            transactionsToApplyLocally,
                            successful -> TransactionStorage.getInstance(getApplicationContext())
                                    .markTransactionsAppliedLocally(successful),
                            appliedLocallyCount,
                            Transaction::getSrc,
                            batchValue -> MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                            () -> getNumAppliedStatus(
                                    appliedLocallyCount.get(),
                                    totalLocally,
                                    appliedToSourceCount.get(),
                                    totalToSource
                            )
                    );
                    if (error != null) {
                        throw new Util.FutureException(
                                "Error when applying transactions locally. "  + getNumAppliedStatus(
                                        appliedLocallyCount.get(),
                                        totalLocally,
                                        appliedToSourceCount.get(),
                                        totalToSource
                                )
                        );
                    }
                    // Apply transactions to source
                    error = applyTransactions(
                            transactions,
                            successful -> {
                                TransactionStorage.getInstance(getApplicationContext())
                                        .deleteTransactions(successful);
                                sourcesToSync.addAll(successful.stream()
                                        .map(Transaction::getSrc)
                                        .collect(Collectors.toList())
                                );
                            },
                            appliedToSourceCount,
                            Transaction::getSrc,
                            batchValue -> batchValue,
                            () -> getNumAppliedStatus(
                                    appliedLocallyCount.get(),
                                    totalLocally,
                                    appliedToSourceCount.get(),
                                    totalToSource
                            )
                    );
                    if (error != null) {
                        throw new Util.FutureException(
                                "Error when applying transactions to source. "  + getNumAppliedStatus(
                                        appliedLocallyCount.get(),
                                        totalLocally,
                                        appliedToSourceCount.get(),
                                        totalToSource
                                ) + "\nMessage: " + error.getMessage()
                                + "\nTrace: " + Arrays.toString(error.getStackTrace())
                        );
                    }
                })
                .handle((aVoid, throwable) -> throwable)
                .join();
        scheduleSync(sourcesToSync);
        if (e != null) {
            e.printStackTrace();
            return Result.failure(data("Failure: " + e.getMessage()));
        }
        return Result.success(data("Success"));
    }

    private Throwable applyTransactions(List<Transaction> transactions,
                                        Consumer<List<Transaction>> successConsumer,
                                        AtomicInteger successCounter,
                                        Function<Transaction, String> transactionBatchSelector,
                                        Function<String, String> sourceSelector,
                                        Supplier<String> statusSupplier) {
        Throwable error = null;
        List<Transaction> transactionBatch = new ArrayList<>();
        int total = transactions.size();
        String transactionBatchValue = "";
        for (int i = 0; i < total; i++) {
            if (isStopped()) {
                throw new Util.FutureException("Processing stopped. " + statusSupplier.get());
            }
            // Batch transactions
            Transaction transaction = transactions.get(i);
            if (transactionBatch.isEmpty()
                    || transactionBatchValue.equals(transactionBatchSelector.apply(transaction))) {
                transactionBatch.add(transaction);
                transactionBatchValue = transactionBatchSelector.apply(transaction);
                continue;
            }
            // Apply batched transactions
            error = applyTransactionBatch(
                    sourceSelector.apply(transactionBatchValue),
                    transactionBatch,
                    successConsumer,
                    successCounter
            );
            transactionBatch.clear();
            if (error != null) {
                break;
            }
            setProgress("Processing... " + statusSupplier.get());
            transactionBatch.add(transaction);
            transactionBatchValue = transactionBatchSelector.apply(transaction);
        }
        if (error == null && !transactionBatch.isEmpty()) {
            // Apply tail if present
            error = applyTransactionBatch(
                    sourceSelector.apply(transactionBatchValue),
                    transactionBatch,
                    successConsumer,
                    successCounter
            );
        }
        return error;
    }

    private Throwable applyTransactionBatch(String src,
                                            List<Transaction> batch,
                                            Consumer<List<Transaction>> successConsumer,
                                            AtomicInteger successCounter) {
        return APIClient
                .getAPIClient(getApplicationContext(), src)
                .applyTransactions(getApplicationContext(), batch)
                .handle((transactionResults, throwable) -> {
                    if (transactionResults == null) {
                        return throwable;
                    }
                    List<Transaction> successful = new ArrayList<>();
                    List<TransactionResult> failed = new ArrayList<>();
                    for (TransactionResult transactionResult: transactionResults) {
                        if (transactionResult.error == null) {
                            successful.add(transactionResult.transaction);
                        } else {
                            failed.add(transactionResult);
                        }
                    }
                    successConsumer.accept(successful);
                    successCounter.set(successCounter.intValue() + successful.size());
                    TransactionStorage.getInstance(getApplicationContext())
                            .setErrors(
                                    failed.stream()
                                            .map(f -> f.transaction)
                                            .collect(Collectors.toList()),
                                    failed.stream()
                                            .map(f -> f.error)
                                            .collect(Collectors.toList())
                            );
                    return throwable;
                })
                .join();
    }

    private String getNumAppliedStatus(int appliedLocally,
                                       int totalLocally,
                                       int appliedToSource,
                                       int totalToSource) {
        return "Successfully applied "
                + (totalLocally <= 0 ? "" : appliedLocally + "/" + totalLocally + " locally and ")
                + appliedToSource + "/" + totalToSource + " transactions to source.";
    }

    private void scheduleSync(Set<String> sources) {
        // TODO: Keep track of what needs to be synced. Only library or only playlists? Both?
        for (String src: sources) {
            Jobs.runBackendSyncNow(
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
                    .setRequiresBatteryNotLow(true);
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
