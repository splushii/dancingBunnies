package se.splushii.dancingbunnies.storage;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import se.splushii.dancingbunnies.storage.db.Entry;
import se.splushii.dancingbunnies.storage.db.MetaBoolean;
import se.splushii.dancingbunnies.storage.db.MetaDao;
import se.splushii.dancingbunnies.storage.db.MetaDouble;
import se.splushii.dancingbunnies.storage.db.MetaLong;
import se.splushii.dancingbunnies.storage.db.MetaString;

@Database(
        entities = {
                Entry.class,
                MetaString.class,
                MetaLong.class,
                MetaDouble.class,
                MetaBoolean.class,
                RoomCacheEntry.class,
                RoomPlaylist.class,
                RoomPlaylistEntry.class,
                RoomPlaybackControllerEntry.class
        },
        version = 1
)
public abstract class RoomDB extends RoomDatabase {
    private static final String DB_NAME = "dB";
    public static final String TABLE_ENTRY_ID = "entry_id";
    public static final String TABLE_META_STRING = "meta_string";
    public static final String TABLE_META_LONG = "meta_long";
    public static final String TABLE_META_DOUBLE = "meta_double";
    public static final String TABLE_META_BOOLEAN = "meta_boolean";
    public static final String COLUMN_API = "api";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_VALUE = "value";
    static final String TABLE_CACHE = "cache";
    static final String TABLE_PLAYLISTS = "playlists";
    static final String TABLE_PLAYLIST_ENTRIES = "playlist_entries";
    static final String TABLE_PLAYBACK_CONTROLLER_ENTRIES = "playback_controller_entries";
    private static volatile RoomDB instance;

    static RoomDB getDB(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, RoomDB.class, DB_NAME).build();
        }
        return instance;
    }

    abstract MetaDao metaModel();
    abstract RoomCacheDao cacheModel();
    abstract RoomPlaylistDao playlistModel();
    abstract RoomPlaylistEntryDao playlistEntryModel();
    abstract RoomPlaybackControllerEntryDao playbackControllerEntryModel();
}