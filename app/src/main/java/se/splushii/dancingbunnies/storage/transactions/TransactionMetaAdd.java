package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.util.Util;

public class TransactionMetaAdd extends Transaction {
    private static final String LC = Util.getLogContext(TransactionMetaAdd.class);

    private static final String GROUP = Transaction.GROUP_LIBRARY;
    private static final String ACTION = Transaction.META_ADD;

    private static final String JSON_KEY_ENTRYID = "entryid";
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_VALUE = "value";

    private final EntryID entryID;
    private final String key;
    private final String value;

    public TransactionMetaAdd(long id,
                              String src,
                              Date date,
                              long errorCount,
                              String errorMessage,
                              JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        entryID = EntryID.from(args.getJSONObject(JSON_KEY_ENTRYID));
        key = args.getString(JSON_KEY_KEY);
        value = args.getString(JSON_KEY_VALUE);
    }

    public TransactionMetaAdd(long id,
                              String src,
                              Date date,
                              long errorCount,
                              String errorMessage,
                              EntryID entryID,
                              String key,
                              String value
    ) {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        this.entryID = entryID;
        this.key = key;
        this.value = value;
    }

    @Override
    JSONObject jsonArgs() {
        JSONObject args = new JSONObject();
        try {
            JSONObject entryIDJSON = entryID.toJSON();
            if (entryIDJSON == null) {
                return null;
            }
            args.put(JSON_KEY_ENTRYID, entryIDJSON);
            args.put(JSON_KEY_KEY, key);
            args.put(JSON_KEY_VALUE, value);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return args;
    }

    @Override
    public String getArgsSource() {
        return entryID.src;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionMetaAdd that = (TransactionMetaAdd) o;
        return entryID.equals(that.entryID) &&
                key.equals(that.key) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entryID, key, value);
    }

    @Override
    public String getDisplayableAction() {
        return "Add entry metadata";
    }

    @Override
    public String getDisplayableDetails() {
        return "\"" + Meta.getDisplayKey(key) + "\""
                + " = \"" + value + "\""
                + " for " + entryID.getDisplayableString();
    }

    @Override
    public CompletableFuture<Void> applyLocally(Context context) {
        return MetaStorage.getInstance(context)
                .insertMeta(entryID, key, value);
    }

    @Override
    public void addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        batch.addMeta(context, entryID, key, value);
    }

//    @Override
//    CompletableFuture<Void> applyViaAPI(Context context) {
//        return APIClient.getAPIClient(context, getSrc())
//                .addMeta(context, entryID, key, value);
//    }
}
