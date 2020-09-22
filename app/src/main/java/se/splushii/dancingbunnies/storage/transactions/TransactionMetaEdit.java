package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.util.Util;

public class TransactionMetaEdit extends Transaction {
    private static final String LC = Util.getLogContext(TransactionMetaEdit.class);

    private static final String JSON_KEY_ENTRYID = "entryid";
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_OLD_VALUE = "oldValue";
    private static final String JSON_KEY_NEW_VALUE = "newValue";

    private final EntryID entryID;
    private final String key;
    private final String oldValue;
    private final String newValue;

    public TransactionMetaEdit(long id,
                               String src,
                               Date date,
                               long errorCount,
                               String errorMessage,
                               JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, Transaction.META_EDIT);
        entryID = EntryID.from(args.getJSONObject(JSON_KEY_ENTRYID));
        key = args.getString(JSON_KEY_KEY);
        oldValue = args.getString(JSON_KEY_OLD_VALUE);
        newValue = args.getString(JSON_KEY_NEW_VALUE);
    }

    public TransactionMetaEdit(long id,
                               String src,
                               Date date,
                               long errorCount,
                               String errorMessage,
                               EntryID entryID,
                               String key,
                               String oldValue,
                               String newValue
    ) {
        super(id, src, date, errorCount, errorMessage, Transaction.META_EDIT);
        this.entryID = entryID;
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
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
            args.put(JSON_KEY_OLD_VALUE, oldValue);
            args.put(JSON_KEY_NEW_VALUE, newValue);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return args;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionMetaEdit that = (TransactionMetaEdit) o;
        return entryID.equals(that.entryID)
                && key.equals(that.key)
                && oldValue.equals(that.oldValue)
                && newValue.equals(that.newValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), entryID, key, oldValue, newValue);
    }

    @Override
    public String getDisplayableAction() {
        return "Edit entry metadata";
    }

    @Override
    public String getDisplayableDetails() {
        return "\"" + Meta.getDisplayKey(key) + "\""
                + " = \"" + Meta.getDisplayValue(key, oldValue) + "\""
                + " -> \"" + Meta.getDisplayValue(key, newValue) + "\""
                + " for " + entryID.getDisplayableString();
    }

    @Override
    String apply(Context context, String api) {
        switch (api) {
            case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
                MetaStorage.getInstance(context).replaceLocalUserMeta(
                        entryID,
                        key,
                        oldValue,
                        newValue
                );
                return null;
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
            case MusicLibraryService.API_SRC_ID_GIT:
            default:
                return "Transaction not supported for api: " + api;
        }
    }
}
