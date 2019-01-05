package se.splushii.dancingbunnies.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

public class DB extends SQLiteOpenHelper {
    private static final String LC = Util.getLogContext(DB.class);

    // Databases
    private static final String DB_NAME = "dancingbunnies";
    // When bumping the DB_VERSION, update onUpgrade()
    private static final int DB_VERSION = 1;

    // Tables
    static final String TABLE_SONGS = "songs";
    static final String COLUMN_SONGS_SRC = DB.Keyify(Meta.METADATA_KEY_API);
    static final String COLUMN_SONGS_ID = DB.Keyify(Meta.METADATA_KEY_MEDIA_ID);

    static final String TABLE_PLAYLISTS = "playlists";
    static final String COLUMN_PLAYLISTS_TABLE_ID = "_playlists_id";
    static final String COLUMN_PLAYLISTS_PLAYLIST_SRC = "src";
    static final String COLUMN_PLAYLISTS_PLAYLIST_ID = "id";
    static final String COLUMN_PLAYLISTS_PLAYLIST_TYPE = "type";
    static final String COLUMN_PLAYLISTS_PLAYLIST_NAME = "name";
    static final String COLUMN_PLAYLISTS_SMART_QUERY = "smart_query";

    static final String TABLE_PLAYLIST_ENTRIES = "playlist_entries";
    static final String COLUMN_PLAYLIST_ENTRIES_TABLE_ID = "_playlist_entries_id";
    static final String COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID = COLUMN_PLAYLISTS_TABLE_ID;
    static final String COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC = "src";
    static final String COLUMN_PLAYLIST_ENTRIES_ENTRY_ID = "id";
    static final String COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION = "pos";

    private static DB instance = null;
    private static int connectedClients = 0;

    private DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public static synchronized DB getInstance(Context context) {
        if (connectedClients == 0 && instance == null) {
            instance = new DB(context);
        }
        connectedClients++;
        Log.d(LC, "open(): connected clients: " + connectedClients);
        return instance;
    }

    public synchronized void closeDB() {
        if (connectedClients == 1) {
            instance.close();
            instance = null;
        }
        if (connectedClients > 0) {
            connectedClients--;
        }
        Log.d(LC, "close(): connected clients: " + connectedClients);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSongsTable(db);
        createPlaylistsTable(db);
        createPlaylistSongsTable(db);
    }

    private void createSongsTable(SQLiteDatabase db) {
        StringBuilder query = new StringBuilder("CREATE TABLE " + TABLE_SONGS + "(");
        for (String metaKey: RoomMetaSong.db_keys) {
            Meta.Type type = Meta.getType(metaKey);
            switch (type) {
                case STRING:
                    query.append(DB.Keyify(metaKey)).append(" TEXT, ");
                    break;
                case LONG:
                    query.append(DB.Keyify(metaKey)).append(" INTEGER, ");
                    break;
                case BITMAP:
                    query.append(DB.Keyify(metaKey)).append(" BLOB, ");
                    break;
                case RATING:
                    query.append(DB.Keyify(metaKey)).append(" REAL, ");
                    break;
                default:
                    Log.w(LC, type + " not supported in db.");
                    break;
            }
        }
        query.append("CONSTRAINT primary_key_constraint PRIMARY KEY (")
                .append(COLUMN_SONGS_SRC)
                .append(", ")
                .append(COLUMN_SONGS_ID)
                .append("));");
        Log.i(LC, "Creating '"+ TABLE_SONGS + "' table: " + query.toString());
        db.execSQL(query.toString());
    }

    private void createPlaylistsTable(SQLiteDatabase db) {
        StringBuilder query = new StringBuilder("CREATE TABLE " + TABLE_PLAYLISTS + "(");
        query.append(COLUMN_PLAYLISTS_TABLE_ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ");
        query.append(COLUMN_PLAYLISTS_PLAYLIST_SRC).append(" TEXT NOT NULL, ");
        query.append(COLUMN_PLAYLISTS_PLAYLIST_ID).append(" TEXT NOT NULL, ");
        query.append(COLUMN_PLAYLISTS_PLAYLIST_TYPE).append(" TEXT NOT NULL, ");
        query.append(COLUMN_PLAYLISTS_PLAYLIST_NAME).append(" TEXT NOT NULL, ");
        query.append(COLUMN_PLAYLISTS_SMART_QUERY).append(" TEXT, ");
        query.append("CONSTRAINT uniqueness_constraint UNIQUE("
                + COLUMN_PLAYLISTS_PLAYLIST_SRC + ", " + COLUMN_PLAYLISTS_PLAYLIST_ID
                + ")");
        query.append(");");
        Log.i(LC, "Creating '" + TABLE_PLAYLISTS + "' table: " + query.toString());
        db.execSQL(query.toString());
        ContentValues c = new ContentValues();
        c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_SRC, PlaylistID.defaultPlaylistID.src);
        c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_ID, PlaylistID.defaultPlaylistID.id);
        c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_TYPE, PlaylistID.defaultPlaylistID.type);
        c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_NAME, PlaylistID.defaultPlaylistName);
        db.insert(DB.TABLE_PLAYLISTS, null, c);
    }

    private void createPlaylistSongsTable(SQLiteDatabase db) {
        StringBuilder query = new StringBuilder("CREATE TABLE " + TABLE_PLAYLIST_ENTRIES + "(");
        query.append(COLUMN_PLAYLIST_ENTRIES_TABLE_ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT, ");
        query.append(COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID).append(" INTEGER NOT NULL, ");
        query.append(COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC).append(" TEXT NOT NULL, ");
        query.append(COLUMN_PLAYLIST_ENTRIES_ENTRY_ID).append(" TEXT NOT NULL, ");
        query.append(COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION).append(" INTEGER NOT NULL, ");
        query.append("FOREIGN KEY(" + COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID + ") "
                + "REFERENCES " + TABLE_PLAYLISTS + "(" + COLUMN_PLAYLISTS_TABLE_ID + ")");
        query.append("FOREIGN KEY("
                + COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC + ", " + COLUMN_PLAYLIST_ENTRIES_ENTRY_ID + ") "
                + "REFERENCES " + TABLE_SONGS + "(").append(COLUMN_SONGS_SRC).append(", ")
                .append(COLUMN_SONGS_ID).append(")");
        query.append(");");
        Log.i(LC, "Creating '" + TABLE_PLAYLIST_ENTRIES + "' table: " + query.toString());
        db.execSQL(query.toString());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(LC, "DB upgrade! Dropping 'songs', 'playlists', 'playlist_songs' table! Current/new DB version: "
                + oldVersion + "/" + newVersion + ".");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLISTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST_ENTRIES);
        onCreate(db);
    }

    static String Keyify(String key) {
        return key.replace(".","_");
    }
}
