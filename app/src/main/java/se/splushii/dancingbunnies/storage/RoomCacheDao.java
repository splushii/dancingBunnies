package se.splushii.dancingbunnies.storage;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
interface RoomCacheDao {
    @Insert(onConflict = REPLACE)
    void insert(RoomCacheEntry... cacheEntries);
    @Query("SELECT * FROM " + RoomDB.TABLE_CACHE)
    LiveData<List<RoomCacheEntry>> getAll();
}
