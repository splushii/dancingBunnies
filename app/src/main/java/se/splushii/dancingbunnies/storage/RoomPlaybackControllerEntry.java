package se.splushii.dancingbunnies.storage;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;

@Entity(tableName = RoomDB.TABLE_PLAYBACK_CONTROLLER_ENTRIES,
        indices = @Index(value = {
                RoomPlaybackControllerEntry.COLUMN_QUEUE_ID,
                RoomPlaybackControllerEntry.COLUMN_POS
        }, unique = true),
        primaryKeys = {
                RoomPlaybackControllerEntry.COLUMN_QUEUE_ID,
                RoomPlaybackControllerEntry.COLUMN_POS
        }
)
public class RoomPlaybackControllerEntry {
    static final String COLUMN_QUEUE_ID = "queue_id";
    static final String COLUMN_POS = "pos";
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";

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