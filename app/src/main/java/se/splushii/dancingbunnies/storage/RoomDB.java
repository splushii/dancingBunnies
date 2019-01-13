package se.splushii.dancingbunnies.storage;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {RoomMetaSong.class, RoomCacheEntry.class},
        version = 1
)
abstract class RoomDB extends RoomDatabase {
    static final String TABLE_SONGS = "songs";
    static final String TABLE_CACHE = "cache";
    private static volatile RoomDB instance;

    static RoomDB getDB(Context context) {
        if (instance == null) {
            // TODO: Rename DB to something else than test when done.
            instance = Room.databaseBuilder(context, RoomDB.class, "test").build();
        }
        return instance;
    }

    abstract RoomMetaDao metaModel();
    abstract RoomCacheDao cacheModel();
}