package se.splushii.dancingbunnies.storage;

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
abstract class RoomPlaylistEntryDao {
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<RoomPlaylistEntry> entries);
    @Query("SELECT * FROM " + RoomDB.TABLE_PLAYLIST_ENTRIES
            + " ORDER BY " + RoomPlaylistEntry.COLUMN_POS + " ASC")
    abstract LiveData<List<RoomPlaylistEntry>> getAllEntries();
    @Query("SELECT * FROM " + RoomDB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + RoomPlaylistEntry.COLUMN_PLAYLIST_API+ " = :playlistSrc"
            + " AND " + RoomPlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
            + " ORDER BY " + RoomPlaylistEntry.COLUMN_POS + " ASC")
    abstract List<RoomPlaylistEntry> getEntries(String playlistSrc, String playlistId);
    @Transaction
    void addLast(PlaylistID playlistID, List<EntryID> entryIDs) {
        int size = getEntries(playlistID.src, playlistID.id).size();
        List<RoomPlaylistEntry> entries = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            entries.add(RoomPlaylistEntry.from(playlistID, entryID, size++));
        }
        _insert(entries);
    }
    @Query("DELETE FROM " + RoomDB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + RoomPlaylistEntry.COLUMN_PLAYLIST_API + " = :playlistSrc"
            + " AND " + RoomPlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
            + " AND " + RoomPlaylistEntry.COLUMN_POS+ " = :position;")
    abstract void _delete(String playlistSrc, String playlistId, int position);
    @Query(" UPDATE " + RoomDB.TABLE_PLAYLIST_ENTRIES
            + " SET " + RoomPlaylistEntry.COLUMN_POS + " = "
            + RoomPlaylistEntry.COLUMN_POS + " - 1"
            + " WHERE " + RoomPlaylistEntry.COLUMN_PLAYLIST_API + " = :playlistSrc"
            + " AND " + RoomPlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
            + " AND " + RoomPlaylistEntry.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(String playlistSrc, String playlistId, int position);
    @Transaction
    void remove(String playlistSrc, String playlistId, int position) {
        _delete(playlistSrc, playlistId, position);
        _update_pos_after_delete(playlistSrc, playlistId, position);
    }
}
