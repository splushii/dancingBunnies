package se.splushii.dancingbunnies.storage.db;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public
interface CacheDao {
    @Insert(onConflict = REPLACE)
    void insert(CacheEntry... cacheEntries);
    @Query("SELECT * FROM " + DB.TABLE_CACHE)
    LiveData<List<CacheEntry>> getAll();
}
