package se.splushii.dancingbunnies.storage.db;

import androidx.room.ColumnInfo;

public class MetaValueEntry {
    @ColumnInfo(name = DB.COLUMN_SRC)
    public String src;
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @ColumnInfo(name = DB.COLUMN_VALUE)
    public String value;
    public static final int NUM_MAX_EXTRA_VALUES = 5;
    @ColumnInfo(name = "extra1")
    public String extra1;
    @ColumnInfo(name = "extra2")
    public String extra2;
    @ColumnInfo(name = "extra3")
    public String extra3;
    @ColumnInfo(name = "extra4")
    public String extra4;
    @ColumnInfo(name = "extra5")
    public String extra5;
}