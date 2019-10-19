package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = DB.TABLE_META_LONG,
        indices = {
                @Index(value = {
                        DB.COLUMN_API,
                        DB.COLUMN_ID,
                        DB.COLUMN_KEY,
                        DB.COLUMN_VALUE
                }, unique = true),
                @Index({
                        DB.COLUMN_KEY,
                        DB.COLUMN_VALUE,
                        DB.COLUMN_API
                }),
                @Index({
                        DB.COLUMN_VALUE,
                        DB.COLUMN_KEY,
                        DB.COLUMN_API
                }),
                @Index(DB.COLUMN_VALUE)
        },
        primaryKeys = {
                DB.COLUMN_API,
                DB.COLUMN_ID,
                DB.COLUMN_KEY,
                DB.COLUMN_VALUE
        },
        foreignKeys = @ForeignKey(
                entity = Entry.class,
                parentColumns = { DB.COLUMN_API, DB.COLUMN_ID },
                childColumns = { DB.COLUMN_API, DB.COLUMN_ID },
                onDelete = CASCADE
        )
)
public class MetaLong {
    @NonNull
    @ColumnInfo(name = DB.COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_KEY)
    public String key;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_VALUE)
    public long value;

    public static MetaLong from(String api, String id, String key, long value) {
        MetaLong t = new MetaLong();
        t.api = api;
        t.id = id;
        t.key = key;
        t.value = value;
        return t;
    }
}