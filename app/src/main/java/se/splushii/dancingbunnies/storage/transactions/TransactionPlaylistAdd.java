package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.util.Util;

public class TransactionPlaylistAdd extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistAdd.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_ADD;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";
    private static final String JSON_KEY_NAME = "name";
    private static final String JSON_KEY_QUERY = "query";
    private static final String JSON_KEY_BEFORE_PLAYLIST_ID = "before_playlist_id";

    private final PlaylistID playlistID;
    private final String name;
    private final String query;
    private final PlaylistID beforePlaylistID;

    public TransactionPlaylistAdd(long id,
                                  String src,
                                  Date date,
                                  long errorCount,
                                  String errorMessage,
                                  JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        playlistID = PlaylistID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
        name = args.getString(JSON_KEY_NAME);
        query = args.has(JSON_KEY_QUERY)
                ? args.getString(JSON_KEY_QUERY)
                : null;
        beforePlaylistID = args.has(JSON_KEY_BEFORE_PLAYLIST_ID)
                ? PlaylistID.from(args.getJSONObject(JSON_KEY_BEFORE_PLAYLIST_ID))
                : null;
    }

    public TransactionPlaylistAdd(long id,
                                  String src,
                                  Date date,
                                  long errorCount,
                                  String errorMessage,
                                  PlaylistID playlistID,
                                  String name,
                                  String query,
                                  PlaylistID beforePlaylistID
    ) {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        this.playlistID = playlistID;
        this.name = name;
        this.query = query;
        this.beforePlaylistID = beforePlaylistID;
    }

    @Override
    JSONObject jsonArgs() {
        JSONObject args = new JSONObject();
        try {
            JSONObject playlistIDJSON = playlistID.toJSON();
            if (playlistIDJSON == null) {
                return null;
            }
            JSONObject beforePlaylistIDJSON = beforePlaylistID == null
                    ? null
                    : beforePlaylistID.toJSON();
            args.put(JSON_KEY_PLAYLIST_ID, playlistIDJSON);
            args.put(JSON_KEY_NAME, name);
            args.put(JSON_KEY_QUERY, query);
            args.put(JSON_KEY_BEFORE_PLAYLIST_ID, beforePlaylistIDJSON);
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
        return "Add playlist " + playlistID.getDisplayableString()
                + (beforePlaylistID == null
                ? " to the last position"
                : " before " + beforePlaylistID.getDisplayableString());
    }

    @Override
    public CompletableFuture<Void> applyLocally(Context context) {
        Log.e(LC, "APPLYING THE ADD PLAYLIST YES: " + playlistID + " name: " + name);
        return PlaylistStorage.getInstance(context)
                .addPlaylist(
                        playlistID,
                        name,
                        query,
                        beforePlaylistID
                );
    }

    @Override
    public void addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        batch.addPlaylist(
                context,
                playlistID,
                name,
                query,
                beforePlaylistID
        );
    }
//
//    @Override
//    CompletableFuture<Void> applyViaAPI(Context context) {
//        return APIClient.getAPIClient(context, getSrc())
//                .deletePlaylist(context, playlistID);
//    }
}
