package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import se.splushii.dancingbunnies.storage.RoomDB;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = RoomDB.TABLE_META_BOOLEAN,
        indices = @Index(value = {
                RoomDB.COLUMN_API,
                RoomDB.COLUMN_ID,
                RoomDB.COLUMN_KEY,
                RoomDB.COLUMN_VALUE
        }, unique = true),
        primaryKeys = {
                RoomDB.COLUMN_API,
                RoomDB.COLUMN_ID,
                RoomDB.COLUMN_KEY,
                RoomDB.COLUMN_VALUE
        },
        foreignKeys = @ForeignKey(
                entity = Entry.class,
                parentColumns = { RoomDB.COLUMN_API, RoomDB.COLUMN_ID },
                childColumns = { RoomDB.COLUMN_API, RoomDB.COLUMN_ID },
                onDelete = CASCADE
        )
)
public class MetaBoolean {
    @NonNull
    @ColumnInfo(name = RoomDB.COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = RoomDB.COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = RoomDB.COLUMN_KEY)
    public String key;
    @NonNull
    @ColumnInfo(name = RoomDB.COLUMN_VALUE)
    public
    boolean value;

    public static MetaBoolean from(String api, String id, String key, boolean value) {
        MetaBoolean t = new MetaBoolean();
        t.api = api;
        t.id = id;
        t.key = key;
        t.value = value;
        return t;
    }
}