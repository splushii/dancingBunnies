package se.splushii.dancingbunnies.storage;

import androidx.room.ColumnInfo;

class RoomMetaValue {
    static final String VALUE = "value";
    @ColumnInfo(name = VALUE)
    String value;
}