package se.splushii.dancingbunnies.storage;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.jobs.TransactionsWorker;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.db.TransactionDao;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.storage.transactions.TransactionMetaAdd;
import se.splushii.dancingbunnies.storage.transactions.TransactionMetaDelete;
import se.splushii.dancingbunnies.storage.transactions.TransactionMetaEdit;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistAdd;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistDelete;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistEntryAdd;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistEntryDelete;
import se.splushii.dancingbunnies.storage.transactions.TransactionPlaylistEntryMove;
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

    public CompletableFuture<Void> addMeta(Context context,
                                           String src,
                                           EntryID entryID,
                                           String key,
                                           String value) {
        Transaction t = new TransactionMetaAdd(
                Transaction.ID_NONE,
                src,
                new Date(),
                0,
                null,
                entryID,
                key,
                value
        );
        return addTransaction(context, t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> editMeta(Context context,
                                            String src,
                                            EntryID entryID,
                                            String key,
                                            String oldValue,
                                            String newValue
    ) {
        Transaction t = new TransactionMetaEdit(
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
        return addTransaction(context, t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> deleteMeta(Context context,
                                              String src,
                                              EntryID entryID,
                                              String key,
                                              String value) {
        Transaction t = new TransactionMetaDelete(
                Transaction.ID_NONE,
                src,
                new Date(),
                0,
                null,
                entryID,
                key,
                value
        );
        return addTransaction(context, t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> addPlaylist(Context context,
                                               EntryID playlistID,
                                               String name,
                                               String query) {
        Transaction t = new TransactionPlaylistAdd(
                Transaction.ID_NONE,
                playlistID.src,
                new Date(),
                0,
                null,
                playlistID,
                name,
                query
        );
        return addTransaction(context, t).thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> deletePlaylists(Context context,
                                                   List<EntryID> playlistIDs) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (EntryID playlistID: playlistIDs) {
            future = future.thenCompose(aVoid -> addTransaction(
                    context,
                    new TransactionPlaylistDelete(
                            Transaction.ID_NONE,
                            playlistID.src,
                            new Date(),
                            0,
                            null,
                            playlistID
                    )
            ));
        }
        return future.thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> addPlaylistEntries(Context context,
                                                      String src,
                                                      EntryID playlistID,
                                                      List<EntryID> entryIDs,
                                                      String beforePlaylistEntryID) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (EntryID entryID: entryIDs) {
            future = future
                    .thenCompose(aVoid ->
                            MetaStorage.getInstance(context).getTrackMetaOnce(entryID)
                    )
                    .thenCompose(meta -> addTransaction(
                            context,
                            new TransactionPlaylistEntryAdd(
                                    Transaction.ID_NONE,
                                    src,
                                    new Date(),
                                    0,
                                    null,
                                    playlistID,
                                    entryID,
                                    beforePlaylistEntryID,
                                    meta
                            )
                    ));
        }
        return future.thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> deletePlaylistEntry(Context context,
                                                       String src,
                                                       EntryID playlistID,
                                                       PlaylistEntry playlistEntry) {
        return deletePlaylistEntries(
                context,
                src,
                playlistID,
                Collections.singletonList(playlistEntry)
        );
    }

    public CompletableFuture<Void> deletePlaylistEntries(Context context,
                                                         String src,
                                                         EntryID playlistID,
                                                         List<PlaylistEntry> playlistEntries) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (PlaylistEntry playlistEntry: playlistEntries) {
            future = future.thenCompose(aVoid -> addTransaction(
                    context,
                    new TransactionPlaylistEntryDelete(
                            Transaction.ID_NONE,
                            src,
                            new Date(),
                            0,
                            null,
                            playlistID,
                            playlistEntry.playlistEntryID(),
                            playlistEntry.entryID()
                    )
            ));
        }
        return future.thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    public CompletableFuture<Void> movePlaylistEntries(Context context,
                                                       String src,
                                                       EntryID playlistID,
                                                       ArrayList<PlaylistEntry> playlistEntries,
                                                       String beforePlaylistEntryID) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (PlaylistEntry playlistEntry: playlistEntries) {
            future = future.thenCompose(aVoid -> addTransaction(
                    context,
                    new TransactionPlaylistEntryMove(
                            Transaction.ID_NONE,
                            src,
                            new Date(),
                            0,
                            null,
                            playlistID,
                            playlistEntry.playlistEntryID(),
                            playlistEntry.entryID(),
                            beforePlaylistEntryID
                    )
            ));
        }
        return future.thenRun(() ->
                TransactionsWorker.requeue(context, true)
        );
    }

    private CompletableFuture<Void> addTransaction(Context context, Transaction t) {
        return CompletableFuture.runAsync(() ->
                transactionDao.insert(se.splushii.dancingbunnies.storage.db.Transaction.from(
                        t.getSrc(),
                        t.getDate(),
                        t.getGroup(),
                        t.getAction(),
                        t.getArgs()
                ))
        ).thenCompose(aVoid -> t.applyLocally(context));
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
                        dbTransaction.group,
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

    public CompletableFuture<List<Transaction>> getTransactionsOnce(String src, String group) {
        return CompletableFuture.supplyAsync(() ->
                dbTransactionsToTransactions(transactionDao.getTransactionsOnce(src, group))
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