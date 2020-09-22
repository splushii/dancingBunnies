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

public class TransactionMetaDelete extends Transaction {
    private static final String LC = Util.getLogContext(TransactionMetaDelete.class);

    private static final String JSON_KEY_ENTRYID = "entryid";
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_VALUE = "value";

    private final EntryID entryID;
    private final String key;
    private final String value;

    public TransactionMetaDelete(long id,
                                 String src,
                                 Date date,
                                 long errorCount,
                                 String errorMessage,
                                 JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, Transaction.META_DELETE);
        entryID = EntryID.from(args.getJSONObject(JSON_KEY_ENTRYID));
        key = args.getString(JSON_KEY_KEY);
        value = args.getString(JSON_KEY_VALUE);
    }

    public TransactionMetaDelete(long id,
                                 String src,
                                 Date date,
                                 long errorCount,
                                 String errorMessage,
                                 EntryID entryID,
                                 String key,
                                 String value
    ) {
        super(id, src, date, errorCount, errorMessage, Transaction.META_DELETE);
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionMetaDelete that = (TransactionMetaDelete) o;
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
        return "Delete entry metadata";
    }

    @Override
    public String getDisplayableDetails() {
        return "\"" + Meta.getDisplayKey(key) + "\""
                + " = \"" + value + "\""
                + " for " + entryID.getDisplayableString();
    }

    @Override
    String apply(Context context, String api) {
        switch (api) {
            case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
                MetaStorage.getInstance(context).deleteLocalUserMeta(entryID, key, value);
                return null;
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
            case MusicLibraryService.API_SRC_ID_GIT:
            default:
                return "Transaction not supported for api: " + api;
        }
    }
}
