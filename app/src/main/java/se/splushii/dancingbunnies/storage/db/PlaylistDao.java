package se.splushii.dancingbunnies.storage.db;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import se.splushii.dancingbunnies.util.Util;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class PlaylistDao {
    private static final String LC = Util.getLogContext(PlaylistDao.class);

    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS + " ORDER BY " + Playlist.COLUMN_POS)
    abstract public LiveData<List<Playlist>> getAll();
    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS + " ORDER BY " + Playlist.COLUMN_POS)
    abstract public List<Playlist> getAllSync();

    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS
            + " WHERE " + Playlist.COLUMN_API + " = :src"
            + " AND " + Playlist.COLUMN_ID + " = :id")
    abstract public LiveData<Playlist> get(String src, String id);

    // Insert
    @Query("UPDATE " + DB.TABLE_PLAYLISTS
            + " SET " + Playlist.COLUMN_POS + " = " + Playlist.COLUMN_POS + " + :increment"
            + " WHERE " + Playlist.COLUMN_POS + " >= :fromPosition")
    abstract void _update_pos_before_insert(int fromPosition, int increment);
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<Playlist> entries);
    @Transaction
    public void insert(int toPosition, List<Playlist> roomPlaylists) {
        int numNewPlaylists = roomPlaylists.size();
        _update_pos_before_insert(toPosition, numNewPlaylists);
        _insert(roomPlaylists);
    }

    // Delete
    @Query("DELETE FROM " + DB.TABLE_PLAYLISTS
            + " WHERE " + Playlist.COLUMN_POS + " = :position;")
    abstract void _delete(long position);
    @Query(" UPDATE " + DB.TABLE_PLAYLISTS
            + " SET " + Playlist.COLUMN_POS + " = " + Playlist.COLUMN_POS + " - 1"
            + " WHERE " + Playlist.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(long position);
    @Transaction
    public void delete(List<Long> positions) {
        // Start to remove highest pos (otherwise items at highest positions change position)
        Collections.sort(positions, Comparator.reverseOrder());
        for (long pos: positions) {
            _delete(pos);
            _update_pos_after_delete(pos);
        }
    }
    @Transaction
    @Query("DELETE FROM " + DB.TABLE_PLAYLISTS + " WHERE " + Playlist.COLUMN_API + " = :src")
    public void deleteWhereSourceIs(String src) {
        List<Playlist> playlists = getAllSync();
        delete(playlists.stream()
                .filter(playlist -> playlist.api.equals(src))
                .map(playlist -> playlist.pos)
                .collect(Collectors.toList())
        );
    }

    // Move
    @Query("UPDATE " + DB.TABLE_PLAYLISTS
            + " SET " + Playlist.COLUMN_POS + " ="
            + " CASE WHEN :newPos < :oldPos THEN "
            + " CASE WHEN " + Playlist.COLUMN_POS + " >= :newPos AND " + Playlist.COLUMN_POS + " < :oldPos"
            + " THEN " + Playlist.COLUMN_POS  + " + 1 ELSE "
            + " CASE WHEN " + Playlist.COLUMN_POS + " = :oldPos THEN :newPos ELSE " + Playlist.COLUMN_POS + " END END"
            + " ELSE"
            + " CASE WHEN " + Playlist.COLUMN_POS + " <= :newPos AND " + Playlist.COLUMN_POS + " > :oldPos"
            + " THEN " + Playlist.COLUMN_POS + " - 1 ELSE "
            + " CASE WHEN " + Playlist.COLUMN_POS + " = :oldPos THEN :newPos ELSE " + Playlist.COLUMN_POS + " END END"
            + " END"
            + " WHERE " + Playlist.COLUMN_POS + " BETWEEN MIN(:newPos, :oldPos) AND MAX(:newPos, :oldPos)")
    abstract void _move(long oldPos, long newPos);
    @Transaction
    public void move(List<Long> positions, int pos) {
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
            _move(oldPos, newPos + extraSteps);
            newPos++;
        }
    }
}
