package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public class TransactionPlaylistDelete extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistDelete.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_DELETE;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";

    private final EntryID playlistID;

    public TransactionPlaylistDelete(long id,
                                     String src,
                                     Date date,
                                     long errorCount,
                                     String errorMessage,
                                     boolean appliedLocally,
                                     JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, appliedLocally, GROUP, ACTION);
        playlistID = EntryID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
    }

    public TransactionPlaylistDelete(long id,
                                     String src,
                                     Date date,
                                     long errorCount,
                                     String errorMessage,
                                     boolean appliedLocally,
                                     EntryID playlistID
    ) {
        super(id, src, date, errorCount, errorMessage, appliedLocally, GROUP, ACTION);
        this.playlistID = playlistID;
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
        TransactionPlaylistDelete that = (TransactionPlaylistDelete) o;
        return playlistID.equals(that.playlistID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), playlistID);
    }

    @Override
    public String getDisplayableAction() {
        return "Delete playlist";
    }

    @Override
    public String getDisplayableDetails() {
        return "Delete playlist " + playlistID.getDisplayableString();
    }

    @Override
    public boolean addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        return batch.deletePlaylist(context, playlistID);
    }
}
