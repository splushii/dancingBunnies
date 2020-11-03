package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.util.Util;

public class TransactionPlaylistAdd extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistAdd.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_ADD;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";
    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_QUERY = "query";

    private final EntryID playlistID;
    private final String name;
    private final String query;

    public TransactionPlaylistAdd(long id,
                                  String src,
                                  Date date,
                                  long errorCount,
                                  String errorMessage,
                                  JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        playlistID = EntryID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
        name = args.getString(JSON_KEY_NAME);
        query = args.has(JSON_KEY_QUERY)
                ? args.getString(JSON_KEY_QUERY)
                : null;
    }

    public TransactionPlaylistAdd(long id,
                                  String src,
                                  Date date,
                                  long errorCount,
                                  String errorMessage,
                                  EntryID playlistID,
                                  String name,
                                  String query
    ) {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        this.playlistID = playlistID;
        this.name = name;
        this.query = query;
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
            args.put(JSON_KEY_NAME, name);
            args.put(JSON_KEY_QUERY, query);
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
        TransactionPlaylistAdd that = (TransactionPlaylistAdd) o;
        return playlistID.equals(that.playlistID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), playlistID);
    }

    @Override
    public String getDisplayableAction() {
        return "Add playlist";
    }

    @Override
    public String getDisplayableDetails() {
        return "Add playlist " + name + "(" + playlistID.getDisplayableString() + ")";
    }

    @Override
    public CompletableFuture<Void> applyLocally(Context context) {
        return MetaStorage.getInstance(context)
                .addPlaylist(
                        playlistID,
                        name,
                        query
                );
    }

    @Override
    public void addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        batch.addPlaylist(
                context,
                playlistID,
                name,
                query
        );
    }
}
