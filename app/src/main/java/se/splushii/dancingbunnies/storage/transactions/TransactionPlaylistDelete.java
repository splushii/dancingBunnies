package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.util.Util;

public class TransactionPlaylistDelete extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistDelete.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_DELETE;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";

    private final PlaylistID playlistID;

    public TransactionPlaylistDelete(long id,
                                     String src,
                                     Date date,
                                     long errorCount,
                                     String errorMessage,
                                     JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        playlistID = PlaylistID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
    }

    public TransactionPlaylistDelete(long id,
                                     String src,
                                     Date date,
                                     long errorCount,
                                     String errorMessage,
                                     PlaylistID playlistID
    ) {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
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
    public CompletableFuture<Void> applyLocally(Context context) {
        return PlaylistStorage.getInstance(context)
                .deletePlaylists(Collections.singletonList(playlistID));
    }

    @Override
    public void addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        batch.deletePlaylist(context, playlistID);
    }
//
//    @Override
//    CompletableFuture<Void> applyViaAPI(Context context) {
//        return APIClient.getAPIClient(context, getSrc())
//                .deletePlaylist(context, playlistID);
//    }
}
