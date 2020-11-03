package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = DB.TABLE_PLAYLIST_META_DOUBLE,
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
        },
        foreignKeys = @ForeignKey(
                entity = Playlist.class,
                parentColumns = { DB.COLUMN_SRC, DB.COLUMN_ID },
                childColumns = { DB.COLUMN_SRC, DB.COLUMN_ID },
                onDelete = CASCADE
        )
)
public class PlaylistMetaDouble {
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
    public double value;

    public static PlaylistMetaDouble from(String src, String id, String key, double value) {
        PlaylistMetaDouble t = new PlaylistMetaDouble();
        t.src = src;
        t.id = id;
        t.key = key;
        t.value = value;
        return t;
    }
}