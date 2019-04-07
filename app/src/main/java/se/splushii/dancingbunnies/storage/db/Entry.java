package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.storage.RoomDB;

@Entity(tableName = RoomDB.TABLE_ENTRY_ID,
        indices = @Index(value = {
                RoomDB.COLUMN_API,
                RoomDB.COLUMN_ID
        }, unique = true),
        primaryKeys = {
                RoomDB.COLUMN_API,
                RoomDB.COLUMN_ID
        }
)
public class Entry {
    @NonNull
    @ColumnInfo(name = RoomDB.COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = RoomDB.COLUMN_ID)
    public String id;

    public static Entry from(String api, String id) {
        Entry entry = new Entry();
        entry.api = api;
        entry.id = id;
        return entry;
    }
}