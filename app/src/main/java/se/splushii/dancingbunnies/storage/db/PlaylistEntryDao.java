package se.splushii.dancingbunnies.storage.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class PlaylistEntryDao {
    private static final String LC = Util.getLogContext(PlaylistDao.class);

    private static final String isSpecifiedPlaylist =
            PlaylistEntry.COLUMN_PLAYLIST_SRC + " = :playlistSrc"
                    + " AND " + PlaylistEntry.COLUMN_PLAYLIST_ID + " = :playlistId"
                    + " AND " + PlaylistEntry.COLUMN_PLAYLIST_TYPE + " = :playlistType";

    @Query("SELECT COUNT(" + PlaylistEntry.COLUMN_ID + ")"
            + " FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + isSpecifiedPlaylist)
    abstract int _num_entries(String playlistSrc, String playlistId, int playlistType);

    private long[] getPlaylistEntryPositions(PlaylistID playlistID, List<PlaylistEntry> playlistEntries) {
        return DB.getPositions(
                playlistEntries,
                playlistEntry -> _get_position(
                        playlistID.src,
                        playlistID.id,
                        playlistID.type,
                        playlistEntry.playlist_entry_id
                )
        );
    }
    @Query("SELECT " + PlaylistEntry.COLUMN_POS
            + " FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + PlaylistEntry.COLUMN_ID + " = :playlistEntryID"
            + " AND " + isSpecifiedPlaylist)
    abstract long _get_position(String playlistSrc,
                                String playlistId,
                                int playlistType,
                                String playlistEntryID);

    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " ORDER BY " + PlaylistEntry.COLUMN_POS + " ASC")
    public abstract LiveData<List<PlaylistEntry>> getAllEntries();

    private static final String getEntries = "SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + isSpecifiedPlaylist
            + " ORDER BY " + PlaylistEntry.COLUMN_POS + " ASC";
    @Query(getEntries)
    public abstract LiveData<List<PlaylistEntry>> getEntries(String playlistSrc,
                                                             String playlistId,
                                                             int playlistType);
    @Query(getEntries)
    public abstract List<PlaylistEntry> getEntriesOnce(String playlistSrc,
                                                       String playlistId,
                                                       int playlistType);

    // Insert
    @Query("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " = "
            + PlaylistEntry.COLUMN_POS + " + :increment"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " >= :fromPosition"
            + " AND " + isSpecifiedPlaylist)
    public abstract void _update_pos_before_insert(String playlistSrc,
                                                   String playlistId,
                                                   int playlistType,
                                                   long fromPosition,
                                                   long increment);
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<PlaylistEntry> entries);
    @Transaction
    public void addLast(PlaylistID playlistID, List<PlaylistEntry> playlistEntries) {
        int size = _num_entries(playlistID.src, playlistID.id, playlistID.type);
        List<PlaylistEntry> entries = new ArrayList<>();
        for (PlaylistEntry playlistEntry: playlistEntries) {
            entries.add(PlaylistEntry.from(playlistEntry, size++));
        }
        _insert(entries);
    }

    // Remove
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + PlaylistEntry.COLUMN_ID + " = :playlistEntryID"
            + " AND " + isSpecifiedPlaylist)
    abstract void _delete(String playlistSrc,
                          String playlistId,
                          int playlistType,
                          String playlistEntryID);
    @Query("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " = "
            + PlaylistEntry.COLUMN_POS + " - 1"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " > :position"
            + " AND " + isSpecifiedPlaylist)
    abstract void _update_pos_after_delete(String playlistSrc,
                                           String playlistId,
                                           int playlistType,
                                           long position);
    @Transaction
    public void remove(PlaylistID playlistID, List<PlaylistEntry> playlistEntries) {
        long[] playlistEntryPositions = getPlaylistEntryPositions(playlistID, playlistEntries);
        for (int i = 0; i < playlistEntryPositions.length; i++) {
            PlaylistEntry playlistEntry = playlistEntries.get(i);
            _delete(
                    playlistID.src,
                    playlistID.id,
                    playlistID.type,
                    playlistEntry.playlist_entry_id
            );
            _update_pos_after_delete(
                    playlistID.src,
                    playlistID.id,
                    playlistID.type,
                    playlistEntryPositions[i]
            );
        }
    }

    // Move
    @Query("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " ="
            + " CASE WHEN :newPos <= " + PlaylistEntry.COLUMN_POS + " AND " + PlaylistEntry.COLUMN_POS + " < :oldPos THEN"
            + " " + PlaylistEntry.COLUMN_POS  + " + 1"
            + " ELSE CASE WHEN :oldPos < " + PlaylistEntry.COLUMN_POS + " AND " + PlaylistEntry.COLUMN_POS + " <= :newPos THEN"
            + " " + PlaylistEntry.COLUMN_POS + " - 1"
            + " ELSE CASE WHEN " + PlaylistEntry.COLUMN_POS + " = :oldPos THEN"
            + " :newPos"
            + " ELSE"
            + " " + PlaylistEntry.COLUMN_POS
            + " END END END"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " BETWEEN MIN(:newPos, :oldPos) AND MAX(:newPos, :oldPos)"
            + " AND " + isSpecifiedPlaylist)
    abstract void _move(String playlistSrc,
                        String playlistId,
                        int playlistType,
                        long oldPos,
                        long newPos);
    @Transaction
    public void move(PlaylistID playlistID,
                     List<PlaylistEntry> playlistEntries,
                     String idAfterTargetPos) {
        long[] playlistEntryPositions = getPlaylistEntryPositions(playlistID, playlistEntries);
        long targetPos = idAfterTargetPos != null ?
                _get_position(playlistID.src, playlistID.id, playlistID.type, idAfterTargetPos)
                :
                _num_entries(playlistID.src, playlistID.id, playlistID.type);
        DB.movePositions(
                Arrays.stream(playlistEntryPositions).boxed().collect(Collectors.toList()),
                targetPos,
                (source, target) ->
                        _move(playlistID.src, playlistID.id, playlistID.type, source, target)
        );
    }
}
