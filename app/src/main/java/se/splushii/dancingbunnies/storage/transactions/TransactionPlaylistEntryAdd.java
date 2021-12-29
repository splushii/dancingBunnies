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

public class TransactionPlaylistEntryAdd extends Transaction {
    private static final String LC = Util.getLogContext(TransactionPlaylistEntryAdd.class);

    private static final String GROUP = Transaction.GROUP_PLAYLISTS;
    private static final String ACTION = Transaction.PLAYLIST_ENTRY_ADD;

    private static final String JSON_KEY_PLAYLIST_ID = "playlist_id";
    private static final String JSON_KEY_ENTRY_ID = "entry_id";
    private static final String JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID = "before_playlist_entry_id";
    private static final String JSON_KEY_META_SNAPSHOT = "meta_snapshot";

    private final EntryID playlistID;
    private final EntryID entryID;
    private final String beforePlaylistEntryID;
    private final Meta metaSnapshot;

    public TransactionPlaylistEntryAdd(long id,
                                       String src,
                                       Date date,
                                       long errorCount,
                                       String errorMessage,
                                       boolean appliedLocally,
                                       JSONObject args
    ) throws JSONException {
        super(id, src, date, errorCount, errorMessage, appliedLocally, GROUP, ACTION);
        playlistID = EntryID.from(args.getJSONObject(JSON_KEY_PLAYLIST_ID));
        entryID = EntryID.from(args.getJSONObject(JSON_KEY_ENTRY_ID));
        beforePlaylistEntryID = args.has(JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID)
                ? args.getString(JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID)
                : null;
        metaSnapshot = Meta.from(args.getJSONObject(JSON_KEY_META_SNAPSHOT));
    }

    public TransactionPlaylistEntryAdd(long id,
                                       String src,
                                       Date date,
                                       long errorCount,
                                       String errorMessage,
                                       boolean appliedLocally,
                                       EntryID playlistID,
                                       EntryID entryID,
                                       String beforePlaylistEntryID,
                                       Meta metaSnapshot
    ) {
        super(id, src, date, errorCount, errorMessage, appliedLocally, GROUP, ACTION);
        this.playlistID = playlistID;
        this.entryID = entryID;
        this.beforePlaylistEntryID = beforePlaylistEntryID;
        this.metaSnapshot = metaSnapshot;
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
            JSONObject metaSnapshotJSON = metaSnapshot.toJSON();
            if (metaSnapshotJSON == null) {
                return null;
            }
            args.put(JSON_KEY_PLAYLIST_ID, playlistIDJSON);
            args.put(JSON_KEY_ENTRY_ID, entryIDJSON);
            args.put(JSON_KEY_BEFORE_PLAYLIST_ENTRY_ID, beforePlaylistEntryID);
            args.put(JSON_KEY_META_SNAPSHOT, metaSnapshotJSON);
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
        TransactionPlaylistEntryAdd that = (TransactionPlaylistEntryAdd) o;
        return playlistID.equals(that.playlistID)
                && entryID.equals(that.entryID)
                && (beforePlaylistEntryID == null && that.beforePlaylistEntryID == null
                    || beforePlaylistEntryID != null && beforePlaylistEntryID.equals(that.beforePlaylistEntryID));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), playlistID, entryID, beforePlaylistEntryID);
    }

    @Override
    public String getDisplayableAction() {
        return "Add playlist entry";
    }

    @Override
    public String getDisplayableDetails() {
        return "Add " + entryID.getDisplayableString()
                + " (" + metaSnapshot.getAsString(Meta.FIELD_TITLE) + ")"
                + " to playlist " + playlistID.getDisplayableString()
                + (beforePlaylistEntryID == null
                ? " to the last position"
                : " before playlist entry with id " + beforePlaylistEntryID);
    }

    @Override
    public boolean addToBatch(Context context, APIClient.Batch batch) throws APIClient.BatchException {
        return batch.addPlaylistEntry(
                context,
                playlistID,
                entryID,
                beforePlaylistEntryID,
                metaSnapshot
        );
    }
}
