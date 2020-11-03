package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = DB.TABLE_PLAYLIST_META_LOCAL_LONG,
        indices = {
                @Index(value = {
                        DB.COLUMN_SRC,
                        DB.COLUMN_ID,
                        DB.COLUMN_KEY,
                        DB.COLUMN_VALUE
                }, unique = true),
                @Index({
                        DB.COLUMN_KEY,
                        DB.COLUMN_VALUE,
                        DB.COLUMN_SRC
                }),
                @Index({
                        DB.COLUMN_VALUE,
                        DB.COLUMN_KEY,
                        DB.COLUMN_SRC
                }),
                @Index(DB.COLUMN_VALUE)
        },
        primaryKeys = {
                DB.COLUMN_SRC,
                DB.COLUMN_ID,
                DB.COLUMN_KEY,
                DB.COLUMN_VALUE
        }
)
public class PlaylistMetaLocalLong {
    @NonNull
    @ColumnInfo(name = DB.COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_KEY)
    public String key;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_VALUE)
    public long value;

    public static PlaylistMetaLocalLong from(String src, String id, String key, long value) {
        PlaylistMetaLocalLong t = new PlaylistMetaLocalLong();
        t.src = src;
        t.id = id;
        t.key = key;
        t.value = value;
        return t;
    }
}