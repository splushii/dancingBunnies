package se.splushii.dancingbunnies.storage;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
interface RoomMetaDao {
    @Insert(onConflict = REPLACE)
    void insert(RoomMetaSong... songs);
    @RawQuery(observedEntities = RoomMetaSong.class)
    LiveData<List<RoomMetaSong>> getSongsViaQuery(SupportSQLiteQuery query);
    @RawQuery(observedEntities = RoomMetaSong.class)
    LiveData<List<RoomMetaValue>> getMetaViaQuery(SupportSQLiteQuery query);
    @Query("DELETE FROM " + RoomDB.TABLE_SONGS + " WHERE " + RoomMetaSong.COLUMN_API + " = :src")
    void deleteWhereSourceIs(String src);

}