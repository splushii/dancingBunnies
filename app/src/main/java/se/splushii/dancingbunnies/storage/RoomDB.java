package se.splushii.dancingbunnies.storage;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                RoomMetaSong.class,
                RoomCacheEntry.class,
                RoomPlaylist.class,
                RoomPlaylistEntry.class
        },
        version = 1
)
abstract class RoomDB extends RoomDatabase {
    private static final String DB_NAME = "dB";
    static final String TABLE_SONGS = "songs";
    static final String TABLE_CACHE = "cache";
    static final String TABLE_PLAYLISTS = "playlists";
    static final String TABLE_PLAYLIST_ENTRIES = "playlist_entries";
    private static volatile RoomDB instance;

    static RoomDB getDB(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, RoomDB.class, DB_NAME).build();
        }
        return instance;
    }

    abstract RoomMetaDao metaModel();
    abstract RoomCacheDao cacheModel();
    abstract RoomPlaylistDao playlistModel();
    abstract RoomPlaylistEntryDao playlistEntryModel();
}