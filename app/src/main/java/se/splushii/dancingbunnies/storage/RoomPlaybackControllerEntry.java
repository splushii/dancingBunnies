package se.splushii.dancingbunnies.storage;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import se.splushii.dancingbunnies.musiclibrary.EntryID;

@Entity(tableName = RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
// The constraints below makes inserts impossible, because incrementing COLUMN_POS
// needs to be done in a TEMP table, something not supported in Room as far as I know.
// See: https://stackoverflow.com/questions/22494148/incrementing-value-in-table-with-unique-key-causes-constraint-error?noredirect=1&lq=1
//        indices = @Index(value = {
//                RoomPlaybackControllerEntry.COLUMN_QUEUE_ID,
//                RoomPlaybackControllerEntry.COLUMN_POS
//        }, unique = true),
//        primaryKeys = {
//                RoomPlaybackControllerEntry.COLUMN_QUEUE_ID,
//                RoomPlaybackControllerEntry.COLUMN_POS
//        }
)
public class RoomPlaybackControllerEntry {
    private static final String COLUMN_ROW_ID = "rowid";
    static final String COLUMN_QUEUE_ID = "queue_id";
    static final String COLUMN_POS = "pos";
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ROW_ID)
    int rowId;
    @NonNull
    @ColumnInfo(name = COLUMN_QUEUE_ID)
    int queueID;
    @NonNull
    @ColumnInfo(name = COLUMN_POS)
    long pos;
    @NonNull
    @ColumnInfo(name = COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    public String id;

    public static RoomPlaybackControllerEntry from(int queueID, EntryID entryID) {
        return from(queueID, entryID, 1);
    }

    public static RoomPlaybackControllerEntry from(int queueID, EntryID entryID, int pos) {
        RoomPlaybackControllerEntry entry = new RoomPlaybackControllerEntry();
        entry.pos = pos;
        entry.queueID = queueID;
        entry.api = entryID.src;
        entry.id = entryID.id;
        return entry;
    }
}