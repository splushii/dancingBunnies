package se.splushii.dancingbunnies.storage.transactions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.util.Util;

public class TransactionPlaylistEntryMove extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistEntryMove.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_ENTRY_MOVE;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";
    private static final String JSON_KEY_PLAYLIST_ENTRY_ID = "playlist_entry_id";
    private static final String JSON_KEY_ENTRY_ID = "entry_id";
    private static final String JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID = "before_playlist_entry_id";

    private final PlaylistID playlistID;
    private final String playlistEntryID;
    private final EntryID entryID;
    private final String beforePlaylistEntryID;

    public TransactionPlaylistEntryMove(long id,
                                        String src,
                                        Date date,
                                        long errorCount,
                                        String errorMessage,
                                        JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        playlistID = PlaylistID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
        playlistEntryID = args.getString(JSON_KEY_PLAYLIST_ENTRY_ID);
        entryID = EntryID.from(args.getJSONObject(JSON_KEY_ENTRY_ID));
        beforePlaylistEntryID = args.has(JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID)
                ? args.getString(JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID)
                : null;
    }

    public TransactionPlaylistEntryMove(long id,
                                        String src,
                                        Date date,
                                        long errorCount,
                                        String errorMessage,
                                        PlaylistID playlistID,
                                        String playlistEntryID,
                                        EntryID entryID,
                                        String beforePlaylistEntryID
    ) {
        super(id, src, date, errorCount, errorMessage, GROUP, ACTION);
        this.playlistID = playlistID;
        this.playlistEntryID = playlistEntryID;
        this.entryID = entryID;
        this.beforePlaylistEntryID = beforePlaylistEntryID;
    }

    @Override
    JSONObject jsonArgs() {
        JSONObject args = new JSONObject();
        try {
            JSONObject playlistIDJSON = playlistID.toJSON();
            if (playlistIDJSON == null) {
                return null;
            }
            JSONObject entryIDJSON = entryID.toJSON();
            if (entryIDJSON == null) {
                return null;
            }
            args.put(JSON_KEY_PLAYLIST_ID, playlistIDJSON);
            args.put(JSON_KEY_PLAYLIST_ENTRY_ID, playlistEntryID);
            args.put(JSON_KEY_ENTRY_ID, entryIDJSON);
            args.put(JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID, beforePlaylistEntryID);
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
        TransactionPlaylistEntryMove that = (TransactionPlaylistEntryMove) o;
        return playlistID.equals(that.playlistID)
                && playlistEntryID.equals(that.playlistEntryID)
                && entryID.equals(that.entryID)
                && (beforePlaylistEntryID == null && that.beforePlaylistEntryID == null
                    || beforePlaylistEntryID != null && beforePlaylistEntryID.equals(that.beforePlaylistEntryID));
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                playlistID,
                playlistEntryID,
                entryID,
                beforePlaylistEntryID
        );
    }

    @Override
    public String getDisplayableAction() {
        return "Move playlist entry";
    }

    @Override
    public String getDisplayableDetails() {
        return "Move " + playlistEntryID + "(" + entryID.getDisplayableString() + ")"
                + " in playlist " + playlistID.getDisplayableString()
                + (beforePlaylistEntryID == null
                ? " to the last position"
                : " before playlist entry with id " + beforePlaylistEntryID);
    }

    @Override
    public CompletableFuture<Void> applyLocally(Context context) {
        return PlaylistStorage.getInstance(context)
                .movePlaylistEntries(
                        playlistID,
                        Collections.singletonList(playlistEntryID),
                        beforePlaylistEntryID
                );
    }

    @Override
    public void addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        batch.movePlaylistEntry(
                context,
                playlistID,
                playlistEntryID,
                entryID,
                beforePlaylistEntryID
        );
    }
}
