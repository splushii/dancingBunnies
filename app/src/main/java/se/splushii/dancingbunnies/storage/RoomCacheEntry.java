package se.splushii.dancingbunnies.storage;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;

@Entity(tableName = RoomDB.TABLE_CACHE,
        indices = {
                @Index(value = {RoomCacheEntry.COLUMN_API, RoomCacheEntry.COLUMN_ID}, unique = true)
        },
        primaryKeys = {RoomCacheEntry.COLUMN_API, RoomCacheEntry.COLUMN_ID}
)
public class RoomCacheEntry {
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";

    @NonNull
    @ColumnInfo(name = COLUMN_API)
    String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    String id;

    public static RoomCacheEntry from(EntryID entryID) {
        RoomCacheEntry cacheEntry = new RoomCacheEntry();
        cacheEntry.api = entryID.src;
        cacheEntry.id = entryID.id;
        return cacheEntry;
    }
}