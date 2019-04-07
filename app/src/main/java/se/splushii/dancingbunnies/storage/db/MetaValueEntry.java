package se.splushii.dancingbunnies.storage.db;

import androidx.room.ColumnInfo;
import se.splushii.dancingbunnies.storage.RoomDB;

public class MetaValueEntry {
    @ColumnInfo(name = RoomDB.COLUMN_API)
    public String api;
    @ColumnInfo(name = RoomDB.COLUMN_ID)
    public String id;
    @ColumnInfo(name = RoomDB.COLUMN_VALUE)
    public String value;
}