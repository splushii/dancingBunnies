package se.splushii.dancingbunnies.storage.db;

import androidx.room.ColumnInfo;

public class MetaValueEntry {
    @ColumnInfo(name = DB.COLUMN_API)
    public String api;
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @ColumnInfo(name = DB.COLUMN_VALUE)
    public String value;
    @ColumnInfo(name = "sort1")
    public String sort1;
}