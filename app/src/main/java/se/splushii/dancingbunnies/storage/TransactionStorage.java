package se.splushii.dancingbunnies.storage;

import android.content.Context;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.jobs.TransactionsWorker;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.TransactionDao;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.storage.transactions.TransactionMetaAdd;
import se.splushii.dancingbunnies.storage.transactions.TransactionMetaDelete;
import se.splushii.dancingbunnies.storage.transactions.TransactionMetaEdit;
import se.splushii.dancingbunnies.util.Util;

public class TransactionStorage {
    private static final String LC = Util.getLogContext(TransactionStorage.class);

    private static TransactionStorage instance;

    private final TransactionDao transactionDao;

    public static synchronized TransactionStorage getInstance(Context context) {
        if (instance == null) {
            instance = new TransactionStorage(context);
        }
        return instance;
    }

    private TransactionStorage(Context context) {
        transactionDao = DB.getDB(context).transactionModel();
    }

    public void addMeta(Context context, String src, EntryID entryID, String key, String value) {
        TransactionMetaAdd t = new TransactionMetaAdd(
                Transaction.ID_NONE,
                src,
                new Date(),
                0,
                null,
                entryID,
                key,
                value
        );
        addTransaction(t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public void editMeta(Context context,
                         String src,
                         EntryID entryID,
                         String key,
                         String oldValue,
                         String newValue
    ) {
        TransactionMetaEdit t = new TransactionMetaEdit(
                Transaction.ID_NONE,
                src,
                new Date(),
                0,
                null,
                entryID,
                key,
                oldValue,
                newValue
        );
        addTransaction(t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public void deleteMeta(Context context, String src, EntryID entryID, String key, String value) {
        TransactionMetaDelete t = new TransactionMetaDelete(
                Transaction.ID_NONE,
                src,
                new Date(),
                0,
                null,
                entryID,
                key,
                value
        );
        addTransaction(t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    private CompletableFuture<Void> addTransaction(Transaction t) {
        return CompletableFuture.runAsync(() ->
                transactionDao.insert(se.splushii.dancingbunnies.storage.db.Transaction.from(
                        t.getSrc(),
                        t.getDate(),
                        t.getType(),
                        t.getArgs()
                ))
        );
    }

    private List<Transaction> dbTransactionsToTransactions(
            List<se.splushii.dancingbunnies.storage.db.Transaction> dbTransactions
    ) {
        return dbTransactions.stream()
                .map(dbTransaction -> Transaction.from(
                        dbTransaction.rowid,
                        dbTransaction.src,
                        dbTransaction.date,
                        dbTransaction.numErrors,
                        dbTransaction.error,
                        dbTransaction.action,
                        dbTransaction.args
                ))
                .collect(Collectors.toList());
    }

    public LiveData<List<Transaction>> getTransactions() {
        return Transformations.map(
                transactionDao.getTransactions(),
                this::dbTransactionsToTransactions
        );
    }

    public CompletableFuture<List<Transaction>> getTransactionsOnce() {
        return CompletableFuture.supplyAsync(() ->
                dbTransactionsToTransactions(transactionDao.getTransactionsOnce())
        );
    }

    public CompletableFuture<Void> deleteTransactions(List<Transaction> transactions) {
        return CompletableFuture.runAsync(() ->
                transactionDao.delete(
                        transactions.stream()
                                .map(Transaction::getID)
                                .collect(Collectors.toList())
                )
        );
    }

    public CompletableFuture<Void> setError(Transaction transaction, String error) {
        return CompletableFuture.runAsync(() -> {
            transactionDao.setError(transaction.getID(), error);
        });
    }
}