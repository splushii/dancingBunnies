package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class DB extends SQLiteOpenHelper {
    private static final String LC = Util.getLogContext(DB.class);

    // Databases
    private static final String DB_NAME = "dancingbunnies";
    private static final int DB_VERSION = 1;

    // Tables
    static final String SONG_TABLE_NAME = "songs";

    DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSongsTable(db);
    }

    private void createSongsTable(SQLiteDatabase db) {
        StringBuilder query = new StringBuilder("CREATE TABLE " + SONG_TABLE_NAME + "(");
        for (String metaKey: Meta.db_keys) {
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
                .append(DB.Keyify(Meta.METADATA_KEY_API))
                .append(", ")
                .append(DB.Keyify(Meta.METADATA_KEY_MEDIA_ID))
                .append("));");
        Log.i(LC, "Creating 'songs' table: " + query.toString());
        db.execSQL(query.toString());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(LC, "DB upgrade! Dropping 'songs' table! Current/new DB version: "
                + oldVersion + "/" + newVersion + ".");
        db.execSQL("DROP TABLE songs");
        onCreate(db);
    }

    static String Keyify(String key) {
        return key.replace(".","_");
    }
}
