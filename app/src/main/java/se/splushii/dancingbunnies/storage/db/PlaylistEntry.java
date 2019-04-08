package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = DB.TABLE_PLAYLIST_ENTRIES,
        foreignKeys = @ForeignKey(
                parentColumns = {
                        Playlist.COLUMN_API,
                        Playlist.COLUMN_ID
                },
                childColumns = {
                        PlaylistEntry.COLUMN_PLAYLIST_API,
                        PlaylistEntry.COLUMN_PLAYLIST_ID
                },
                entity = Playlist.class,
                onDelete = CASCADE
        ),
        indices = @Index(value = {
                PlaylistEntry.COLUMN_PLAYLIST_API,
                PlaylistEntry.COLUMN_PLAYLIST_ID,
                PlaylistEntry.COLUMN_POS
        }, unique = true),
        primaryKeys = {
                PlaylistEntry.COLUMN_PLAYLIST_API,
                PlaylistEntry.COLUMN_PLAYLIST_ID,
                PlaylistEntry.COLUMN_POS
        }
)
public class PlaylistEntry {
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


    public static PlaylistEntry from(PlaylistID playlistID, EntryID entryID) {
        return from(playlistID, entryID, 1);
    }

    public static PlaylistEntry from(PlaylistID playlistID, EntryID entryID, int pos) {
        PlaylistEntry roomPlaylistEntry = new PlaylistEntry();
        roomPlaylistEntry.playlist_api = playlistID.src;
        roomPlaylistEntry.playlist_id = playlistID.id;
        roomPlaylistEntry.api = entryID.src;
        roomPlaylistEntry.id = entryID.id;
        roomPlaylistEntry.pos = pos;
        return roomPlaylistEntry;
    }
}