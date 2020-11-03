package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

public class Entry {
    @NonNull
    @ColumnInfo(name = DB.COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
}