package se.splushii.dancingbunnies.storage.db;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class PlaylistEntryDao {
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<PlaylistEntry> entries);
    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " ORDER BY " + PlaylistEntry.COLUMN_POS + " ASC")
    public abstract LiveData<List<PlaylistEntry>> getAllEntries();
    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + PlaylistEntry.COLUMN_PLAYLIST_API+ " = :playlistSrc"
            + " AND " + PlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
            + " ORDER BY " + PlaylistEntry.COLUMN_POS + " ASC")
    public abstract List<PlaylistEntry> getEntries(String playlistSrc, String playlistId);
    @Transaction
    public void addLast(PlaylistID playlistID, List<EntryID> entryIDs) {
        int size = getEntries(playlistID.src, playlistID.id).size();
        List<PlaylistEntry> entries = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            entries.add(PlaylistEntry.from(playlistID, entryID, size++));
        }
        _insert(entries);
    }
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + PlaylistEntry.COLUMN_PLAYLIST_API + " = :playlistSrc"
            + " AND " + PlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
            + " AND " + PlaylistEntry.COLUMN_POS+ " = :position;")
    abstract void _delete(String playlistSrc, String playlistId, int position);
    @Query(" UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " = "
            + PlaylistEntry.COLUMN_POS + " - 1"
            + " WHERE " + PlaylistEntry.COLUMN_PLAYLIST_API + " = :playlistSrc"
            + " AND " + PlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
            + " AND " + PlaylistEntry.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(String playlistSrc, String playlistId, int position);
    @Transaction
    public void remove(String playlistSrc, String playlistId, int position) {
        _delete(playlistSrc, playlistId, position);
        _update_pos_after_delete(playlistSrc, playlistId, position);
    }
}
