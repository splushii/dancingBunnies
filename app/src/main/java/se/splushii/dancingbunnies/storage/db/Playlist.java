package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;

@Entity(tableName = DB.TABLE_PLAYLISTS,
        indices = @Index(value = {
                Playlist.COLUMN_API,
                Playlist.COLUMN_ID
        }, unique = true),
        primaryKeys = {
                Playlist.COLUMN_API,
                Playlist.COLUMN_ID
        }
)
public class Playlist {
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";

    @NonNull
    @ColumnInfo(name = COLUMN_API)
    public
    String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    public
    String id;
    @NonNull
    @ColumnInfo(name = COLUMN_NAME)
    public
    String name;

    public static Playlist from(StupidPlaylist playlist) {
        Playlist roomPlaylist = new Playlist();
        roomPlaylist.api = playlist.id.src;
        roomPlaylist.id = playlist.id.id;
        roomPlaylist.name = playlist.name;
        return roomPlaylist;
    }
}