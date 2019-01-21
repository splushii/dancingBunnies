package se.splushii.dancingbunnies.storage;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = RoomDB.TABLE_PLAYLIST_ENTRIES,
        foreignKeys = @ForeignKey(
                parentColumns = {
                        RoomPlaylist.COLUMN_API,
                        RoomPlaylist.COLUMN_ID
                },
                childColumns = {
                        RoomPlaylistEntry.COLUMN_PLAYLIST_API,
                        RoomPlaylistEntry.COLUMN_PLAYLIST_ID
                },
                entity = RoomPlaylist.class,
                onDelete = CASCADE
        ),
        indices = @Index(value = {
                RoomPlaylistEntry.COLUMN_PLAYLIST_API,
                RoomPlaylistEntry.COLUMN_PLAYLIST_ID,
                RoomPlaylistEntry.COLUMN_POS
        }, unique = true),
        primaryKeys = {
                RoomPlaylistEntry.COLUMN_PLAYLIST_API,
                RoomPlaylistEntry.COLUMN_PLAYLIST_ID,
                RoomPlaylistEntry.COLUMN_POS
        }
)
public class RoomPlaylistEntry {
    static final String COLUMN_PLAYLIST_API = "playlist_api";
    static final String COLUMN_PLAYLIST_ID = "playlist_id";
    static final String COLUMN_POS = "pos";
    private static final String COLUMN_API = "api";
    private static final String COLUMN_ID = "id";

    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_API)
    public
    String playlist_api;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_ID)
    public
    String playlist_id;
    @NonNull
    @ColumnInfo(name = COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = COLUMN_POS)
    long pos;


    public static RoomPlaylistEntry from(PlaylistID playlistID, EntryID entryID) {
        return from(playlistID, entryID, 1);
    }

    public static RoomPlaylistEntry from(PlaylistID playlistID, EntryID entryID, int pos) {
        RoomPlaylistEntry roomPlaylistEntry = new RoomPlaylistEntry();
        roomPlaylistEntry.playlist_api = playlistID.src;
        roomPlaylistEntry.playlist_id = playlistID.id;
        roomPlaylistEntry.api = entryID.src;
        roomPlaylistEntry.id = entryID.id;
        roomPlaylistEntry.pos = pos;
        return roomPlaylistEntry;
    }
}