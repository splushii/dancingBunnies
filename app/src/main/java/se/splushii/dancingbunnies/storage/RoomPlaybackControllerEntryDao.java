package se.splushii.dancingbunnies.storage;

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

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
abstract class RoomPlaybackControllerEntryDao {
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<RoomPlaybackControllerEntry> entries);
    @Query("SELECT * FROM " + RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + RoomPlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " ORDER BY " + RoomPlaybackControllerEntry.COLUMN_POS + " ASC")
    abstract LiveData<List<RoomPlaybackControllerEntry>> getEntries(int queueID);
    @Query("SELECT * FROM " + RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + RoomPlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " ORDER BY " + RoomPlaybackControllerEntry.COLUMN_POS + " ASC")
    abstract List<RoomPlaybackControllerEntry> getEntriesSync(int queueID);
    @Query("DELETE FROM " + RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + RoomPlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + RoomPlaybackControllerEntry.COLUMN_POS + " = :position;")
    abstract void _delete(int queueID, int position);
    @Query("UPDATE " + RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + RoomPlaybackControllerEntry.COLUMN_POS + " = "
            + RoomPlaybackControllerEntry.COLUMN_POS + " - 1"
            + " WHERE " + RoomPlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + RoomPlaybackControllerEntry.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(int queueID, int position);
    @Transaction
    void remove(int queueID, List<Integer> positions) {
        // Start to remove highest pos (otherwise items at highest positions change position)
        Collections.sort(positions, Comparator.reverseOrder());
        for (int pos: positions) {
            _delete(queueID, pos);
            _update_pos_after_delete(queueID, pos);
        }
    }
    @Query("DELETE FROM " + RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + RoomPlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID;")
    abstract void removeAll(int queueID);
    @Query("UPDATE " + RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + RoomPlaybackControllerEntry.COLUMN_POS + " = "
            + RoomPlaybackControllerEntry.COLUMN_POS + " + :increment"
            + " WHERE " + RoomPlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID"
            + " AND " + RoomPlaybackControllerEntry.COLUMN_POS + " >= :afterPosition")
    abstract void _update_pos_before_insert(int queueID, int afterPosition, int increment);
    @Transaction
    void insert(int queueID, int position, List<EntryID> entryIDs) {
        List<RoomPlaybackControllerEntry> entries = new ArrayList<>();
        int entryPosition = position;
        for (EntryID entryID: entryIDs) {
            entries.add(RoomPlaybackControllerEntry.from(queueID, entryID, entryPosition++));
        }
        int numNewEntries = entries.size();
        _update_pos_before_insert(queueID, position, numNewEntries);
        _insert(entries);
    }
}
