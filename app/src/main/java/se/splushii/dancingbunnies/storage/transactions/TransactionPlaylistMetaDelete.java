package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class TransactionPlaylistMetaDelete extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistMetaDelete.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_META_DELETE;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_VALUE = "value";

    private final EntryID playlistID;
    private final String key;
    private final String value;

    public TransactionPlaylistMetaDelete(long id,
                                         String src,
                                         Date date,
                                         long errorCount,
                                         String errorMessage,
                                         boolean appliedLocally,
                                         JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, appliedLocally, GROUP, ACTION);
        playlistID = EntryID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
        key = args.getString(JSON_KEY_KEY);
        value = args.getString(JSON_KEY_VALUE);
    }

    public TransactionPlaylistMetaDelete(long id,
                                         String src,
                                         Date date,
                                         long errorCount,
                                         String errorMessage,
                                         boolean appliedLocally,
                                         EntryID playlistID,
                                         String key,
                                         String value
    ) {
        super(id, src, date, errorCount, errorMessage, appliedLocally, GROUP, ACTION);
        this.playlistID = playlistID;
        this.key = key;
        this.value = value;
    }

    @Override
    JSONObject jsonArgs() {
        JSONObject args = new JSONObject();
        try {
            JSONObject playlistIDJSON = playlistID.toJSON();
            if (playlistIDJSON == null) {
                return null;
            }
            args.put(JSON_KEY_PLAYLIST_ID, playlistIDJSON);
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
        return playlistID.src;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionPlaylistMetaDelete that = (TransactionPlaylistMetaDelete) o;
        return playlistID.equals(that.playlistID) &&
                key.equals(that.key) &&
                value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), playlistID, key, value);
    }

    @Override
    public String getDisplayableAction() {
        return "Delete playlist metadata";
    }

    @Override
    public String getDisplayableDetails() {
        return "\"" + Meta.getDisplayKey(key) + "\""
                + " = \"" + value + "\""
                + " for " + playlistID.getDisplayableString();
    }

    @Override
    public boolean addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        return batch.deletePlaylistMeta(context, playlistID, key, value);
    }
}
