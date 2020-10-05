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

    private static final String isSpecifiedPlaylistEntry =
            isSpecifiedPlaylist + " AND " + PlaylistEntry.COLUMN_ID + " = :playlistEntryID";

    @Query("SELECT COUNT(" + PlaylistEntry.COLUMN_ID + ")"
            + " FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + isSpecifiedPlaylist)
    abstract int _num_entries(String playlistSrc, String playlistId, String playlistType);

    private long[] getPlaylistEntryPositions(PlaylistID playlistID, List<String> playlistEntryIDs) {
        return DB.getPositions(
                playlistEntryIDs,
                playlistEntryID -> _get_position(
                        playlistID.src,
                        playlistID.id,
                        playlistID.type,
                        playlistEntryID
                )
        );
    }

    @Query("SELECT " + PlaylistEntry.COLUMN_POS
            + " FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + isSpecifiedPlaylistEntry)
    abstract long _get_position(String playlistSrc,
                                String playlistId,
                                String playlistType,
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
                                                             String playlistType);
    @Query(getEntries)
    public abstract List<PlaylistEntry> getEntriesOnce(String playlistSrc,
                                                       String playlistId,
                                                       String playlistType);
    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE "  + isSpecifiedPlaylistEntry
            + " LIMIT 1")
    abstract PlaylistEntry getEntry(String playlistSrc,
                                    String playlistId,
                                    String playlistType,
                                    String playlistEntryID);

    // Insert
    @Query("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " = "
            + PlaylistEntry.COLUMN_POS + " + :increment"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " >= :fromPosition"
            + " AND " + isSpecifiedPlaylist)
    public abstract void _update_pos_before_insert(String playlistSrc,
                                                   String playlistId,
                                                   String playlistType,
                                                   long fromPosition,
                                                   long increment);
    @Insert(onConflict = REPLACE)
    abstract void _insert(List<PlaylistEntry> entries);
    @Transaction
    public void add(PlaylistID playlistID,
                    List<PlaylistEntry> playlistEntries,
                    String beforePlaylistEntryID) {
        int numNewEntries = playlistEntries.size();
        PlaylistEntry entry = beforePlaylistEntryID == null
                ? null
                : getEntry(playlistID.src, playlistID.id, playlistID.type, beforePlaylistEntryID);
        long toPosition = entry == null
                ? _num_entries(playlistID.src, playlistID.id, playlistID.type)
                : entry.pos;
        List<PlaylistEntry> entries = new ArrayList<>();
        long entryPosition = toPosition;
        for (PlaylistEntry playlistEntry: playlistEntries) {
            entries.add(PlaylistEntry.from(playlistEntry, entryPosition++));
        }
        _update_pos_before_insert(
                playlistID.src,
                playlistID.id,
                playlistID.type,
                toPosition,
                numNewEntries
        );
        _insert(entries);
    }

    // Remove
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_ENTRIES
            + " WHERE " + isSpecifiedPlaylistEntry)
    abstract void _delete(String playlistSrc,
                          String playlistId,
                          String playlistType,
                          String playlistEntryID);
    @Query("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
            + " SET " + PlaylistEntry.COLUMN_POS + " = "
            + PlaylistEntry.COLUMN_POS + " - 1"
            + " WHERE " + PlaylistEntry.COLUMN_POS + " > :position"
            + " AND " + isSpecifiedPlaylist)
    abstract void _update_pos_after_delete(String playlistSrc,
                                           String playlistId,
                                           String playlistType,
                                           long position);
    @Transaction
    public void remove(PlaylistID playlistID, List<String> playlistEntryIDs) {
        long[] playlistEntryPositions = getPlaylistEntryPositions(playlistID, playlistEntryIDs);
        for (int i = 0; i < playlistEntryPositions.length; i++) {
            _delete(
                    playlistID.src,
                    playlistID.id,
                    playlistID.type,
                    playlistEntryIDs.get(i)
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
                        String playlistType,
                        long oldPos,
                        long newPos);
    @Transaction
    public void move(PlaylistID playlistID,
                     List<String> playlistEntryIDs,
                     String idAfterTargetPos) {
        long[] playlistEntryPositions = getPlaylistEntryPositions(playlistID, playlistEntryIDs);
        long targetPos = idAfterTargetPos == null
                ? _num_entries(playlistID.src, playlistID.id, playlistID.type)
                : _get_position(playlistID.src, playlistID.id, playlistID.type, idAfterTargetPos);
        DB.movePositions(
                Arrays.stream(playlistEntryPositions).boxed().collect(Collectors.toList()),
                targetPos,
                (source, target) ->
                        _move(playlistID.src, playlistID.id, playlistID.type, source, target)
        );
    }
}
