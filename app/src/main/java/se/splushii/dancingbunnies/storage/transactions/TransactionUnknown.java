package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import se.splushii.dancingbunnies.backend.APIClient;

public class TransactionUnknown extends Transaction {
    private final String args;

    public TransactionUnknown(long id,
                              String src,
                              Date date,
                              String group,
                              String action,
                              String args,
                              long errorCount,
                              String errorMessage,
                              boolean appliedLocally
    ) {
        super(
                id,
                src,
                date,
                errorCount,
                errorMessage,
                appliedLocally,
                "unknown: " + group,
                "unknown: " + action
        );
        this.args = args;
    }

    @Override
    JSONObject jsonArgs() {
        return new JSONObject();
    }

    @Override
    public String getArgsSource() {
        return null;
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
    public boolean addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        throw new APIClient.BatchException("Unknown transaction");
    }
}
