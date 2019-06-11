package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;

@Entity(tableName = DB.TABLE_PLAYBACK_CONTROLLER_ENTRIES
// The constraints below makes inserts impossible, because incrementing COLUMN_POS
// needs to be done in a TEMP table, something not supported in Room as far as I know.
// See: https://stackoverflow.com/questions/22494148/incrementing-value-in-table-with-unique-key-causes-constraint-error?noredirect=1&lq=1
//        indices = @Index(value = {
//                PlaybackControllerEntry.COLUMN_QUEUE_ID,
//                PlaybackControllerEntry.COLUMN_POS
//        }, unique = true),
//        primaryKeys = {
//                PlaybackControllerEntry.COLUMN_QUEUE_ID,
//                PlaybackControllerEntry.COLUMN_POS
//        }
)
public class PlaybackControllerEntry {
    private static final String COLUMN_ROW_ID = "rowid";
    static final String COLUMN_PLAYBACK_ID = "playback_id";
    static final String COLUMN_PLAYLIST_POS = "playlist_pos";
    static final String COLUMN_QUEUE_ID = "queue_id";
    static final String COLUMN_POS = "pos";
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ROW_ID)
    int rowId;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYBACK_ID)
    public long playbackID;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_POS)
    public long playlistPos;
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

    @Override
    public String toString() {
        return "PlaybackControllerEntry{" +
                "rowId=" + rowId +
                ", playbackID=" + playbackID +
                ", playbackPos=" + playlistPos +
                ", queueID=" + queueID +
                ", pos=" + pos +
                ", api='" + api + '\'' +
                ", id='" + id + '\'' +
                '}';
    }

    public static PlaybackControllerEntry from(int queueID,
                                               PlaybackEntry playbackEntry,
                                               int pos) {
        PlaybackControllerEntry entry = new PlaybackControllerEntry();
        entry.playbackID = playbackEntry.playbackID;
        entry.playlistPos = playbackEntry.playlistPos;
        entry.pos = pos;
        entry.queueID = queueID;
        entry.api = playbackEntry.entryID.src;
        entry.id = playbackEntry.entryID.id;
        return entry;
    }
}