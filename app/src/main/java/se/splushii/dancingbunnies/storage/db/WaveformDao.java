package se.splushii.dancingbunnies.storage.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public
interface WaveformDao {
    @Insert(onConflict = REPLACE)
    void insert(WaveformEntry... waveformEntries);
    final String getQuery = "SELECT * FROM " + DB.TABLE_WAVEFORM
            + " WHERE " + WaveformEntry.COLUMN_API + " = :src "
            + " AND " + WaveformEntry.COLUMN_ID + " = :id";
    @Query(getQuery)
    LiveData<WaveformEntry> get(String src, String id);
    @Query(getQuery)
    WaveformEntry getSync(String src, String id);
}