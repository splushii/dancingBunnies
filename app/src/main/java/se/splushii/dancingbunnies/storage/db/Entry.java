package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = DB.TABLE_ENTRY_ID,
        indices = {
                @Index(value = {
                        DB.COLUMN_SRC,
                        DB.COLUMN_ID
                }, unique = true)
        },
        primaryKeys = {
                DB.COLUMN_SRC,
                DB.COLUMN_ID
        }
)
public class Entry {
    @NonNull
    @ColumnInfo(name = DB.COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;

    public static Entry from(String src, String id) {
        Entry entry = new Entry();
        entry.src = src;
        entry.id = id;
        return entry;
    }
}