package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = DB.TABLE_META_LOCAL_STRING,
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
        }
)
public class MetaLocalString {
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
    public String value;

    public static MetaLocalString from(String api, String id, String key, String value) {
        MetaLocalString t = new MetaLocalString();
        t.api = api;
        t.id = id;
        t.key = key;
        t.value = value;
        return t;
    }
}