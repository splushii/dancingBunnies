package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

public class TransactionUnknown extends Transaction {
    private final String args;

    public TransactionUnknown(long id,
                              String src,
                              Date date,
                              String action,
                              String args,
                              long errorCount,
                              String errorMessage
    ) {
        super(id, src, date, errorCount, errorMessage, "unknown: " + action);
        this.args = args;
    }

    @Override
    JSONObject jsonArgs() {
        return new JSONObject();
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), args);
    }

    @Override
    public String getDisplayableAction() {
        return "Unknown";
    }

    @Override
    public String getDisplayableDetails() {
        return "Unknown";
    }

    @Override
    String apply(Context context, String api) {
        return "Unknown";
    }
}
