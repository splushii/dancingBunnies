package se.splushii.dancingbunnies.storage.db;

import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.sqlite.db.SupportSQLiteQuery;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class MetaDao {
    private static final String LC = Util.getLogContext(MetaDao.class);

    // TODO: Reenable some keys
    public static final String[] db_keys = {
            // DancingBunnies keys
//        METADATA_KEY_ALBUM_ID,
            Meta.METADATA_KEY_API,
//        METADATA_KEY_ARTIST_ID,
//        METADATA_KEY_AVERAGE_RATING,
            Meta.METADATA_KEY_BITRATE,
//        METADATA_KEY_BOOKMARK_POSITION,
            Meta.METADATA_KEY_CONTENT_TYPE,
//        METADATA_KEY_DATE_ADDED,
//        METADATA_KEY_DATE_STARRED,
//        METADATA_KEY_FILE_SIZE,
//        METADATA_KEY_FILE_SUFFIX,
//        METADATA_KEY_HEART_RATING,
            Meta.METADATA_KEY_MEDIA_ROOT,
//        METADATA_KEY_PARENT_ID,
//        METADATA_KEY_TYPE,
//        METADATA_KEY_TRANSCODED_SUFFIX,
//        METADATA_KEY_TRANSCODED_TYPE,
            // Android MediaMetadataCompat keys
//        METADATA_KEY_ADVERTISEMENT,
            Meta.METADATA_KEY_ALBUM,
//        METADATA_KEY_ALBUM_ART,
            Meta.METADATA_KEY_ALBUM_ARTIST,
//        METADATA_KEY_ALBUM_ART_URI,
//        METADATA_KEY_ART,
            Meta.METADATA_KEY_ARTIST,
//        METADATA_KEY_ART_URI,
//        METADATA_KEY_AUTHOR,
//        METADATA_KEY_BT_FOLDER_TYPE,
//        METADATA_KEY_COMPILATION,
//        METADATA_KEY_COMPOSER,
//        METADATA_KEY_DATE,
            Meta.METADATA_KEY_DISC_NUMBER,
//        METADATA_KEY_DISPLAY_DESCRIPTION,
//        METADATA_KEY_DISPLAY_ICON,
//        METADATA_KEY_DISPLAY_ICON_URI,
//        METADATA_KEY_DISPLAY_SUBTITLE,
//        METADATA_KEY_DISPLAY_TITLE,
//        METADATA_KEY_DOWNLOAD_STATUS,
            Meta.METADATA_KEY_DURATION,
            Meta.METADATA_KEY_GENRE,
            Meta.METADATA_KEY_MEDIA_ID,
//        METADATA_KEY_MEDIA_URI,
//        METADATA_KEY_NUM_TRACKS,
//        METADATA_KEY_RATING,
            Meta.METADATA_KEY_TITLE,
            Meta.METADATA_KEY_TRACK_NUMBER,
//        METADATA_KEY_USER_RATING,
//        METADATA_KEY_WRITER,
            Meta.METADATA_KEY_YEAR
    };
    public static final HashSet DBKeysSet = new HashSet<>(Arrays.asList(db_keys));

    public static String getTable(String key) {
        if (Meta.METADATA_KEY_MEDIA_ID.equals(key)) {
            return DB.TABLE_ENTRY_ID;
        }
        switch (Meta.getType(key)) {
            case STRING:
                return DB.TABLE_META_STRING;
            case LONG:
                return DB.TABLE_META_LONG;
            case BOOLEAN:
                return DB.TABLE_META_BOOLEAN;
            case DOUBLE:
                return DB.TABLE_META_DOUBLE;
            default:
                return null;
        }
    }

    public static String getTableAlias(String key) {
        switch (key) {
            case Meta.METADATA_KEY_MEDIA_ID:
                return "meta_id";
            case Meta.METADATA_KEY_ARTIST:
                return "meta_artist";
            case Meta.METADATA_KEY_ALBUM:
                return "meta_album";
            case Meta.METADATA_KEY_TITLE:
                return "meta_title";
            default:
                return null;
        }
    }

    @Insert(onConflict = REPLACE)
    abstract void insert(Entry... entries);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaString... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLong... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaDouble... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaBoolean... values);
    @Query("SELECT * FROM " + DB.TABLE_ENTRY_ID)
    public abstract LiveData<List<Entry>> getEntries();
    @RawQuery(observedEntities = {
            Entry.class,
            MetaString.class,
            MetaLong.class,
            MetaBoolean.class,
            MetaDouble.class
    })
    public abstract LiveData<List<MetaValueEntry>> getEntries(SupportSQLiteQuery query);
    @Transaction
    public void insert(List<Meta> metaList) {
        for (Meta meta: metaList) {
            insert(meta);
        }
    }
    public void insert(Meta meta) {
        String api = meta.getString(Meta.METADATA_KEY_API);
        String id = meta.getString(Meta.METADATA_KEY_MEDIA_ID);
        insert(Entry.from(api, id));
        for (String key: meta.keySet()) {
            switch (Meta.getType(key)) {
                case STRING:
                    insert(MetaString.from(api, id, key, meta.getString(key)));
                    break;
                case LONG:
                    insert(MetaLong.from(api, id, key, meta.getLong(key)));
                    break;
                case DOUBLE:
                    insert(MetaDouble.from(api, id, key, meta.getDouble(key)));
                    break;
                case BOOLEAN:
                    insert(MetaBoolean.from(api, id, key, meta.getBoolean(key)));
                    break;
                case BITMAP:
                default:
                    Log.e(LC, "Unhandled key: " + key + " type: " + Meta.getType(key));
            }
        }
    }
    @Query("DELETE FROM " + DB.TABLE_ENTRY_ID + " WHERE " + DB.COLUMN_API + " = :src")
    public abstract void deleteWhereSourceIs(String src);

    @Query("SELECT * FROM " + DB.TABLE_META_STRING
            + " WHERE " + DB.COLUMN_API + " = :src "
            + " AND " + DB.COLUMN_ID + " = :id")
    public abstract List<MetaString> getStringMeta(String src, String id);
    @Query("SELECT * FROM " + DB.TABLE_META_LONG
            + " WHERE " + DB.COLUMN_API + " = :src "
            + " AND " + DB.COLUMN_ID + " = :id")
    public abstract List<MetaLong> getLongMeta(String src, String id);
    @Query("SELECT * FROM " + DB.TABLE_META_BOOLEAN
            + " WHERE " + DB.COLUMN_API + " = :src "
            + " AND " + DB.COLUMN_ID + " = :id")
    public abstract List<MetaBoolean> getBoolMeta(String src, String id);
    @Query("SELECT * FROM " + DB.TABLE_META_DOUBLE
            + " WHERE " + DB.COLUMN_API + " = :src "
            + " AND " + DB.COLUMN_ID + " = :id")
    public abstract List<MetaDouble> getDoubleMeta(String src, String id);
}