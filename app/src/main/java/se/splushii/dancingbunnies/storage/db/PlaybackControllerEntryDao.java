package se.splushii.dancingbunnies.storage.db;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class PlaybackControllerEntryDao {
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " = "
            + PlaybackControllerEntry.COLUMN_POS + " + :increment"
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " >= :fromPosition")
    public abstract void _update_pos_before_insert(int queueID, int fromPosition, int increment);
    @Insert(onConflict = REPLACE)
    public abstract void _insert(List<PlaybackControllerEntry> entries);
    @Query("SELECT * FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " ORDER BY " + PlaybackControllerEntry.COLUMN_POS + " ASC")
    public abstract LiveData<List<PlaybackControllerEntry>> getEntries(int queueID);
    @Query("SELECT * FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " ORDER BY " + PlaybackControllerEntry.COLUMN_POS + " ASC")
    public abstract List<PlaybackControllerEntry> getEntriesSync(int queueID);
    @Query("DELETE FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " = :position;")
    abstract void _delete(int queueID, int position);
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " = "
            + PlaybackControllerEntry.COLUMN_POS + " - 1"
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(int queueID, int position);
    @Transaction
    public void remove(int queueID, List<Integer> positions) {
        // Start to remove highest pos (otherwise items at highest positions change position)
        Collections.sort(positions, Comparator.reverseOrder());
        for (int pos: positions) {
            _delete(queueID, pos);
            _update_pos_after_delete(queueID, pos);
        }
    }
    @Query("DELETE FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID;")
    public abstract void removeAll(int queueID);
}
