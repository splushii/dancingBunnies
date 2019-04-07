package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import se.splushii.dancingbunnies.storage.RoomDB;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = RoomDB.TABLE_META_DOUBLE,
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
public class MetaDouble {
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
    double value;

    public static MetaDouble from(String api, String id, String key, double value) {
        MetaDouble t = new MetaDouble();
        t.api = api;
        t.id = id;
        t.key = key;
        t.value = value;
        return t;
    }
}