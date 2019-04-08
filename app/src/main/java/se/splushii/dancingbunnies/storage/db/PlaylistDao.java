package se.splushii.dancingbunnies.storage.db;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public
interface PlaylistDao {
    @Insert(onConflict = REPLACE)
    void insert(Playlist... playlists);
    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS)
    LiveData<List<Playlist>> getAll();
    @Query("SELECT * FROM " + DB.TABLE_PLAYLISTS
            + " WHERE " + Playlist.COLUMN_API + " = :src "
            + "AND " + Playlist.COLUMN_ID + " = :id")
    Playlist get(String id, String src);
    @Query("DELETE FROM " + DB.TABLE_PLAYLISTS + " WHERE " + Playlist.COLUMN_API + " = :src")
    void deleteWhereSourceIs(String src);

}
