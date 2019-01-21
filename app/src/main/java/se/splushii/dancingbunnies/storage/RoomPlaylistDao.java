package se.splushii.dancingbunnies.storage;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
interface RoomPlaylistDao {
    @Insert(onConflict = REPLACE)
    void insert(RoomPlaylist... playlists);
    @Query("SELECT * FROM " + RoomDB.TABLE_PLAYLISTS)
    LiveData<List<RoomPlaylist>> getAll();
    @Query("SELECT * FROM " + RoomDB.TABLE_PLAYLISTS
            + " WHERE " + RoomPlaylist.COLUMN_API + " = :src "
            + "AND " + RoomPlaylist.COLUMN_ID + " = :id")
    RoomPlaylist get(String id, String src);
    @Query("DELETE FROM " + RoomDB.TABLE_PLAYLISTS + " WHERE " + RoomPlaylist.COLUMN_API + " = :src")
    void deleteWhereSourceIs(String src);

}
