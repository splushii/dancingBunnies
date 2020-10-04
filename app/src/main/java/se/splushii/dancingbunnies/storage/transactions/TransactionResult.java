package se.splushii.dancingbunnies.storage.transactions;

import se.splushii.dancingbunnies.util.Util;

public class TransactionResult {
    private static final String LC = Util.getLogContext(TransactionResult.class);

    public final Transaction transaction;
    public final String error;

    public TransactionResult(Transaction transaction, String error) {
        this.transaction = transaction;
        this.error = error;
    }
}