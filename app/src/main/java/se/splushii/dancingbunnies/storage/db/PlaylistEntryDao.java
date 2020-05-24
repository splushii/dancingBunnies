package se.splushii.dancingbunnies.storage.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final String isSpecifiedPlaylist =
            PlaylistEntry.COLUMN_PLAYLIST_SRC + " = :playlistSrc"
                    + " AND " + PlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId";

    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " ORDER BY " + PlaylistEntry.COLUMN_POS + " ASC")
    public abstract LiveData<List<PlaylistEntry>> getAllEntries();

    private static final String getEntries = "SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + isSpecifiedPlaylist
            + " ORDER BY " + PlaylistEntry.COLUMN_POS + " ASC";
    @Query(getEntries)
    public abstract LiveData<List<PlaylistEntry>> getEntries(String playlistSrc, String playlistId);
    @Query(getEntries)
    public abstract List<PlaylistEntry> getEntriesOnce(String playlistSrc, String playlistId);

    // Insert
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<PlaylistEntry> entries);
    @Transaction
    public void addLast(PlaylistID playlistID, List<EntryID> entryIDs) {
        int size = getEntriesOnce(playlistID.src, playlistID.id).size();
        List<PlaylistEntry> entries = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            entries.add(PlaylistEntry.from(playlistID, entryID, size++));
        }
        _insert(entries);
    }

    // Remove
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + PlaylistEntry.COLUMN_POS + " = :position"
            + " AND " + isSpecifiedPlaylist)
    abstract void _delete(String playlistSrc, String playlistId, long position);
    @Query(" UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " = "
            + PlaylistEntry.COLUMN_POS + " - 1"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " > :position"
            + " AND " + isSpecifiedPlaylist)
    abstract void _update_pos_after_delete(String playlistSrc, String playlistId, long position);
    @Transaction
    public void remove(PlaylistID playlistID, List<Long> positions) {
        // Start to remove highest pos (otherwise items at highest positions change position)
        Collections.sort(positions, Comparator.reverseOrder());
        for (long pos: positions) {
            _delete(playlistID.src, playlistID.id, pos);
            _update_pos_after_delete(playlistID.src, playlistID.id, pos);
        }
    }

    // Move
    @Query("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " ="
            + " CASE WHEN :newPos < :oldPos THEN "
            + " CASE WHEN " + PlaylistEntry.COLUMN_POS + " >= :newPos AND " + PlaylistEntry.COLUMN_POS + " < :oldPos"
            + " THEN " + PlaylistEntry.COLUMN_POS  + " + 1 ELSE "
            + " CASE WHEN " + PlaylistEntry.COLUMN_POS + " = :oldPos THEN :newPos ELSE " + PlaylistEntry.COLUMN_POS + " END END"
            + " ELSE"
            + " CASE WHEN " + PlaylistEntry.COLUMN_POS + " <= :newPos AND " + PlaylistEntry.COLUMN_POS + " > :oldPos"
            + " THEN " + PlaylistEntry.COLUMN_POS + " - 1 ELSE "
            + " CASE WHEN " + PlaylistEntry.COLUMN_POS + " = :oldPos THEN :newPos ELSE " + PlaylistEntry.COLUMN_POS + " END END"
            + " END"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " BETWEEN MIN(:newPos, :oldPos) AND MAX(:newPos, :oldPos)"
            + " AND " + isSpecifiedPlaylist)
    abstract void _move(String playlistSrc, String playlistId, long oldPos, long newPos);
    @Transaction
    public void move(String playlistSrc, String playlistId, List<Long> positions, int pos) {
        Collections.sort(positions);
        int newPos = pos;
        for (int i = 0; i < positions.size(); i++) {
            long oldPos = positions.get(i);
            long extraSteps = 0;
            // Update position of remaining entries in same way as in _move()
            for (int j = i + 1; j < positions.size(); j++) {
                long otherPos = positions.get(j);
                if (oldPos < otherPos && otherPos <= newPos + extraSteps) {
                    positions.set(j, otherPos - 1);
                    // If oldPos moves forward past otherPos, otherPos will move past newPos later,
                    // because positions are sorted. So, need to move oldPos an extra step.
                    extraSteps++;
                } else if (newPos + extraSteps <= otherPos && otherPos < oldPos) {
                    positions.set(j, otherPos + 1);
                }
            }
            _move(playlistSrc, playlistId, oldPos, newPos + extraSteps);
            newPos++;
        }
    }
}
