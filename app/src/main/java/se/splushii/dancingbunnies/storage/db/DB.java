package se.splushii.dancingbunnies.storage.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                Entry.class,
                MetaString.class,
                MetaLong.class,
                MetaDouble.class,
                MetaLocalString.class,
                MetaLocalLong.class,
                MetaLocalDouble.class,
                WaveformEntry.class,
                Playlist.class,
                PlaylistEntry.class,
                PlaybackControllerEntry.class
        },
        version = 1
)
public abstract class DB extends RoomDatabase {
    private static final String DB_NAME = "dB";
    static final String TABLE_ENTRY_ID = "entry_id";
    static final String TABLE_META_STRING = "meta_string";
    static final String TABLE_META_LONG = "meta_long";
    static final String TABLE_META_DOUBLE = "meta_double";
    static final String TABLE_META_LOCAL_STRING = "meta_local_string";
    static final String TABLE_META_LOCAL_LONG = "meta_local_long";
    static final String TABLE_META_LOCAL_DOUBLE = "meta_local_double";
    public static final String COLUMN_API = "api";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_VALUE = "value";
    static final String TABLE_WAVEFORM = "waveform";
    static final String TABLE_PLAYLISTS = "playlists";
    static final String TABLE_PLAYLIST_ENTRIES = "playlist_entries";
    static final String TABLE_PLAYBACK_CONTROLLER_ENTRIES = "playback_controller_entries";
    private static volatile DB instance;

    public static DB getDB(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, DB.class, DB_NAME).build();
        }
        return instance;
    }

    public abstract MetaDao metaModel();
    public abstract WaveformDao waveformModel();
    public abstract PlaylistDao playlistModel();
    public abstract PlaylistEntryDao playlistEntryModel();
    public abstract PlaybackControllerEntryDao playbackControllerEntryModel();
}