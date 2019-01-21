package se.splushii.dancingbunnies.storage;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;

@Entity(tableName = RoomDB.TABLE_PLAYLISTS,
        indices = @Index(value = {
                RoomPlaylist.COLUMN_API,
                RoomPlaylist.COLUMN_ID
        }, unique = true),
        primaryKeys = {
                RoomPlaylist.COLUMN_API,
                RoomPlaylist.COLUMN_ID
        }
)
public class RoomPlaylist {
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";

    @NonNull
    @ColumnInfo(name = COLUMN_API)
    String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    String id;
    @NonNull
    @ColumnInfo(name = COLUMN_NAME)
    String name;

    public static RoomPlaylist from(StupidPlaylist playlist) {
        RoomPlaylist roomPlaylist = new RoomPlaylist();
        roomPlaylist.api = playlist.id.src;
        roomPlaylist.id = playlist.id.id;
        roomPlaylist.name = playlist.name;
        return roomPlaylist;
    }
}