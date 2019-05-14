package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = DB.TABLE_ENTRY_ID,
        indices = @Index(value = {
                DB.COLUMN_API,
                DB.COLUMN_ID
        }, unique = true),
        primaryKeys = {
                DB.COLUMN_API,
                DB.COLUMN_ID
        }
)
public class Entry {
    @NonNull
    @ColumnInfo(name = DB.COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;

    public static Entry from(String api, String id) {
        Entry entry = new Entry();
        entry.api = api;
        entry.id = id;
        return entry;
    }
}