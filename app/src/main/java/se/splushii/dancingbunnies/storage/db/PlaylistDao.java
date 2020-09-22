package se.splushii.dancingbunnies.storage.db;

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
public abstract class PlaylistDao {
    private static final String LC = Util.getLogContext(PlaylistDao.class);

    private static final String isSpecifiedPlaylist =
            Playlist.COLUMN_SRC + " = :src"
                    + " AND " + Playlist.COLUMN_ID + " = :id"
                    + " AND " + Playlist.COLUMN_TYPE + " = :type";

    @Query("SELECT COUNT(" + Playlist.COLUMN_ID + ")"
            + " FROM " + DB.TABLE_PLAYLISTS)
    abstract int _num_entries();

    private long[] getPlaylistPositions(List<Playlist> playlists) {
        return DB.getPositions(playlists, playlist -> {
            PlaylistID playlistID = playlist.playlistID();
            return _get_position(playlistID.src, playlistID.id, playlist.type);
        });
    }
    @Query("SELECT " + Playlist.COLUMN_POS
            + " FROM " + DB.TABLE_PLAYLISTS
            + " WHERE " + isSpecifiedPlaylist)
    abstract long _get_position(String src, String id, int type);

    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS + " ORDER BY " + Playlist.COLUMN_POS)
    abstract public LiveData<List<Playlist>> getAll();
    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS + " ORDER BY " + Playlist.COLUMN_POS)
    abstract public List<Playlist> getAllSync();

    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS
            + " WHERE " + isSpecifiedPlaylist)
    abstract public LiveData<Playlist> get(String src, String id, int type);

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
            + " WHERE " + isSpecifiedPlaylist)
    abstract void _delete(String src, String id, int type);
    @Query(" UPDATE " + DB.TABLE_PLAYLISTS
            + " SET " + Playlist.COLUMN_POS + " = " + Playlist.COLUMN_POS + " - 1"
            + " WHERE " + Playlist.COLUMN_POS + " > :position")
    abstract void _update_pos_after_delete(long position);
    @Transaction
    public void delete(List<Playlist> playlists) {
        long[] playlistPositions = getPlaylistPositions(playlists);
        for (int i = 0; i < playlistPositions.length; i++) {
            Playlist playlist = playlists.get(i);
            _delete(
                    playlist.src,
                    playlist.id,
                    playlist.type
            );
            _update_pos_after_delete(playlistPositions[i]);
        }
    }
    @Transaction
    @Query("DELETE FROM " + DB.TABLE_PLAYLISTS + " WHERE " + Playlist.COLUMN_SRC + " = :src")
    public void deleteWhereSourceIs(String src) {
        List<Playlist> playlists = getAllSync();
        delete(playlists.stream()
                .filter(playlist -> playlist.src.equals(src))
                .collect(Collectors.toList())
        );
    }

    // Move
    @Query("UPDATE " + DB.TABLE_PLAYLISTS
            + " SET " + Playlist.COLUMN_POS + " ="
            + " CASE WHEN :newPos <= " + Playlist.COLUMN_POS + " AND " + Playlist.COLUMN_POS + " < :oldPos THEN"
            + " " + Playlist.COLUMN_POS  + " + 1"
            + " ELSE CASE WHEN :oldPos < " + Playlist.COLUMN_POS + " AND " + Playlist.COLUMN_POS + " <= :newPos THEN"
            + " " + Playlist.COLUMN_POS + " - 1"
            + " ELSE CASE WHEN " + Playlist.COLUMN_POS + " = :oldPos THEN"
            + " :newPos"
            + " ELSE"
            + " " + Playlist.COLUMN_POS
            + " END END END"
            + " WHERE " + Playlist.COLUMN_POS + " BETWEEN MIN(:newPos, :oldPos) AND MAX(:newPos, :oldPos)"
    )
    abstract void _move(long oldPos, long newPos);
    @Transaction
    public void move(List<Playlist> playlists, PlaylistID idAfterTargetPos) {
        long[] playlistEntryPositions = getPlaylistPositions(playlists);
        long targetPos = idAfterTargetPos != null ?
                _get_position(idAfterTargetPos.src, idAfterTargetPos.id, idAfterTargetPos.type)
                :
                _num_entries();
        DB.movePositions(
                Arrays.stream(playlistEntryPositions).boxed().collect(Collectors.toList()),
                targetPos,
                this::_move
        );
    }

    @Query("SELECT DISTINCT \"" + Playlist.COLUMN_SRC + "\" FROM " + DB.TABLE_PLAYLISTS)
    public abstract LiveData<List<String>> getSources();
}
