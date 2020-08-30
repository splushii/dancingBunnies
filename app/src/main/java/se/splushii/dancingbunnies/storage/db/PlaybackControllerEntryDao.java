package se.splushii.dancingbunnies.storage.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    private static final String isQueue = PlaybackControllerEntry.COLUMN_QUEUE_ID + " = :queueID";
    private static final String isEntry = isQueue
            + " AND " + PlaybackControllerEntry.COLUMN_PLAYBACK_ID + " = :playbackID";

    private static final String getEntriesQuery = "SELECT * FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + isQueue
            + " ORDER BY " + PlaybackControllerEntry.COLUMN_POS + " ASC";
    @Query(getEntriesQuery)
    public abstract LiveData<List<PlaybackControllerEntry>> getEntries(int queueID);
    @Query(getEntriesQuery)
    public abstract List<PlaybackControllerEntry> getEntriesSync(int queueID);
    @Query("SELECT * FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE "  + isEntry
            + " LIMIT 1")
    abstract PlaybackControllerEntry getEntry(int queueID, long playbackID);

    @Query("SELECT COUNT(" + PlaybackControllerEntry.COLUMN_PLAYBACK_ID + ")"
            + " FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + isQueue)
    abstract int _num_entries(int queueID);

    private long[] getEntryPositions(int queueID, List<PlaybackEntry> entries) {
        return DB.getPositions(entries, entry -> _get_position(queueID, entry.playbackID));
    }
    @Query("SELECT " + PlaybackControllerEntry.COLUMN_POS
            + " FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + isEntry)
    abstract long _get_position(int queueID, long playbackID);

    // Delete
    @Delete
    abstract void _delete(PlaybackControllerEntry entry);
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " = "
            + PlaybackControllerEntry.COLUMN_POS + " - 1"
            + " WHERE " + isQueue
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(int queueID, long position);
    @Transaction
    public void removeEntries(int queueID, List<PlaybackEntry> playbackEntries) {
        for (PlaybackEntry playbackEntry: playbackEntries) {
            PlaybackControllerEntry entry = getEntry(queueID, playbackEntry.playbackID);
            if (entry == null) {
                continue;
            }
            _delete(entry);
            _update_pos_after_delete(queueID, entry.pos);
        }
    }
    @Query("DELETE FROM " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " WHERE " + isQueue)
    public abstract void removeAll(int queueID);

    // Insert
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " = "
            + PlaybackControllerEntry.COLUMN_POS + " + :increment"
            + " WHERE " + isQueue
            + " AND " + PlaybackControllerEntry.COLUMN_POS + " >= :fromPosition")
    public abstract void _update_pos_before_insert(int queueID, int fromPosition, int increment);
    @Insert(onConflict = REPLACE)
    public abstract void _insert(List<PlaybackControllerEntry> entries);
    @Transaction
    public void insert(int queueID, int toPosition, List<PlaybackEntry> entries) {
        List<PlaybackControllerEntry> roomEntries = new ArrayList<>();
        int entryPosition = toPosition;
        for (PlaybackEntry playbackEntry: entries) {
            roomEntries.add(PlaybackControllerEntry.from(queueID, playbackEntry, entryPosition++));
        }
        int numNewEntries = roomEntries.size();
        _update_pos_before_insert(queueID, toPosition, numNewEntries);
        _insert(roomEntries);
    }
    // TODO: Use insertBeforeID() instead of insert()
    @Transaction
    void insertBeforeID(int queueID,
                        long beforePlaybackID,
                        List<PlaybackEntry> entries) {
        int numNewEntries = entries.size();
        PlaybackControllerEntry entry = getEntry(queueID, beforePlaybackID);
        int toPosition = entry == null ? getEntriesSync(queueID).size() : entry.pos;
        List<PlaybackControllerEntry> roomEntries = new ArrayList<>();
        int entryPosition = toPosition;
        for (PlaybackEntry playbackEntry: entries) {
            roomEntries.add(PlaybackControllerEntry.from(queueID, playbackEntry, entryPosition++));
        }
        _update_pos_before_insert(queueID, toPosition, numNewEntries);
        _insert(roomEntries);
    }

    // Replace with
    @Transaction
    public void replaceWith(int queueID, List<PlaybackEntry> entries) {
        removeAll(queueID);
        if (entries != null && !entries.isEmpty()) {
            insert(queueID, 0, entries);
        }
    }

    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_PLAYLIST_POS + " = :playlistPos"
            + " WHERE " + isEntry)
    abstract void _update_positions(int queueID, long playbackID, int playlistPos);
    @Transaction
    public void updatePositions(int queueID, List<PlaybackEntry> entries) {
        for (PlaybackEntry playbackEntry: entries) {
            _update_positions(queueID, playbackEntry.playbackID, (int) playbackEntry.playlistPos);
        }
    }

    // Move
    @Query("UPDATE " + DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
            + " SET " + PlaybackControllerEntry.COLUMN_POS + " ="
            + " CASE WHEN :newPos <= " + PlaybackControllerEntry.COLUMN_POS + " AND " + PlaybackControllerEntry.COLUMN_POS + " < :oldPos THEN"
            + " " + PlaybackControllerEntry.COLUMN_POS  + " + 1"
            + " ELSE CASE WHEN :oldPos < " + PlaybackControllerEntry.COLUMN_POS + " AND " + PlaybackControllerEntry.COLUMN_POS + " <= :newPos THEN"
            + " " + PlaybackControllerEntry.COLUMN_POS + " - 1"
            + " ELSE CASE WHEN " + PlaybackControllerEntry.COLUMN_POS + " = :oldPos THEN"
            + " :newPos"
            + " ELSE"
            + " " + PlaybackControllerEntry.COLUMN_POS
            + " END END END"
            + " WHERE " + PlaybackControllerEntry.COLUMN_POS + " BETWEEN MIN(:newPos, :oldPos) AND MAX(:newPos, :oldPos)"
            + " AND " + isQueue)
    abstract void _move(int queueID, long oldPos, long newPos);
    @Transaction
    public void move(int queueID,
                     List<PlaybackEntry> entries,
                     long idAfterTargetPos) {
        long[] entryPositions = getEntryPositions(queueID, entries);
        long targetPos = idAfterTargetPos > PlaybackEntry.PLAYBACK_ID_INVALID ?
                _get_position(queueID, idAfterTargetPos) : _num_entries(queueID);
        DB.movePositions(
                Arrays.stream(entryPositions).boxed().collect(Collectors.toList()),
                targetPos,
                (source, target) -> _move(queueID, source, target)
        );
    }
}
