package se.splushii.dancingbunnies.storage.db;

import androidx.room.ColumnInfo;

public class MetaValueEntry {
    @ColumnInfo(name = DB.COLUMN_API)
    public String api;
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @ColumnInfo(name = DB.COLUMN_VALUE)
    public String value;
    public static final int NUM_SORT_VALUES = 5;
    @ColumnInfo(name = "sort1")
    public String sort1;
    @ColumnInfo(name = "sort2")
    public String sort2;
    @ColumnInfo(name = "sort3")
    public String sort3;
    @ColumnInfo(name = "sort4")
    public String sort4;
    @ColumnInfo(name = "sort5")
    public String sort5;
}