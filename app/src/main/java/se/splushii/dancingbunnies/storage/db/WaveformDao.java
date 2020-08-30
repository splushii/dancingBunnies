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
    String isEntryID = WaveformEntry.COLUMN_SRC + " = :src "
            + " AND " + WaveformEntry.COLUMN_ID + " = :id";
    String getQuery = "SELECT * FROM " + DB.TABLE_WAVEFORM + " WHERE " + isEntryID;
    @Query(getQuery)
    LiveData<WaveformEntry> get(String src, String id);
    @Query(getQuery)
    WaveformEntry getSync(String src, String id);
    @Query("DELETE FROM " + DB.TABLE_WAVEFORM + " WHERE " + isEntryID)
    void delete(String src, String id);
}