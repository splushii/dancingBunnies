package se.splushii.dancingbunnies.storage.db;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.util.Util;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class PlaybackControllerEntryDao {
    private static final String LC = Util.getLogContext(PlaybackControllerEntryDao.class);

    private static final String getEntriesQuery = "SELECT * FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " ORDER BY " + PlaybackControllerEntry.COLUMN_POS + " ASC";
    @Query(getEntriesQuery)
    public abstract LiveData<List<PlaybackControllerEntry>> getEntries(int queueID);
    @Query(getEntriesQuery)
    public abstract List<PlaybackControllerEntry> getEntriesSync(int queueID);
    @Query("SELECT " + PlaybackControllerEntry.COLUMN_POS + " FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_PLAYBACK_ID + " = :playbackID"
            + " LIMIT 1")
    abstract long getPos(long playbackID);
    @Query("SELECT * FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_PLAYBACK_ID + " = :playbackID"
            + " LIMIT 1")
    abstract PlaybackControllerEntry getEntry(long playbackID);

    // Delete
    @Delete
    abstract void _delete(PlaybackControllerEntry entry);
    @Query("DELETE FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " = :position;")
    abstract void _delete(int queueID, long position);
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " = "
            + PlaybackControllerEntry.COLUMN_POS + " - 1"
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(int queueID, long position);
    @Transaction
    public void remove(int queueID, List<Integer> positions) {
        // Start to remove highest pos (otherwise items at highest positions change position)
        Collections.sort(positions, Comparator.reverseOrder());
        for (int pos: positions) {
            _delete(queueID, pos);
            _update_pos_after_delete(queueID, pos);
        }
    }
    @Transaction
    public void removeEntries(int queueID, List<PlaybackEntry> playbackEntries) {
        for (PlaybackEntry playbackEntry: playbackEntries) {
            PlaybackControllerEntry entry = getEntry(playbackEntry.playbackID);
            if (entry == null) {
                continue;
            }
            _delete(entry);
            _update_pos_after_delete(queueID, entry.pos);
        }
    }
    @Query("DELETE FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID;")
    public abstract void removeAll(int queueID);

    // Insert
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " = "
            + PlaybackControllerEntry.COLUMN_POS + " + :increment"
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " >= :fromPosition")
    public abstract void _update_pos_before_insert(int queueID, int fromPosition, int increment);
    @Insert(onConflict = REPLACE)
    public abstract void _insert(List<PlaybackControllerEntry> entries);
    @Transaction
    public void insert(int queueID, int toPosition, List<PlaybackControllerEntry> entries) {
        int numNewEntries = entries.size();
        _update_pos_before_insert(queueID, toPosition, numNewEntries);
        _insert(entries);
    }
}
