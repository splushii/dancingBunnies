package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;

@Entity(tableName = DB.TABLE_CACHE,
        indices = {
                @Index(value = {CacheEntry.COLUMN_API, CacheEntry.COLUMN_ID}, unique = true)
        },
        primaryKeys = {CacheEntry.COLUMN_API, CacheEntry.COLUMN_ID}
)
public class CacheEntry {
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";

    @NonNull
    @ColumnInfo(name = COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    public String id;

    public static CacheEntry from(EntryID entryID) {
        CacheEntry cacheEntry = new CacheEntry();
        cacheEntry.api = entryID.src;
        cacheEntry.id = entryID.id;
        return cacheEntry;
    }
}