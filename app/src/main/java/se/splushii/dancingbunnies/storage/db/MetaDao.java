package se.splushii.dancingbunnies.storage.db;

import android.util.Log;

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

    private static final String isEntryID = DB.COLUMN_API + " = :src " + " AND " + DB.COLUMN_ID + " = :id";

    public static String getTable(String key) {
        if (Meta.FIELD_SPECIAL_MEDIA_ID.equals(key) || Meta.FIELD_SPECIAL_MEDIA_SRC.equals(key)) {
            return DB.TABLE_ENTRY_ID;
        }
        boolean isLocal = Meta.isLocal(key);
        switch (Meta.getType(key)) {
            default:
            case STRING:
                return isLocal ? DB.TABLE_META_LOCAL_STRING : DB.TABLE_META_STRING;
            case LONG:
                return isLocal ? DB.TABLE_META_LOCAL_LONG : DB.TABLE_META_LONG;
            case DOUBLE:
                return isLocal ? DB.TABLE_META_LOCAL_DOUBLE : DB.TABLE_META_DOUBLE;
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
    public abstract void insert(MetaLocalString... values);
    @Query("SELECT * FROM " + DB.TABLE_ENTRY_ID)
    public abstract LiveData<List<Entry>> getEntries();
    @RawQuery(observedEntities = {
            Entry.class,
            MetaString.class,
            MetaLong.class,
            MetaDouble.class,
            MetaLocalString.class,
            MetaLocalLong.class,
            MetaLocalDouble.class

    })
    public abstract LiveData<List<MetaValueEntry>> getEntries(SupportSQLiteQuery query);
    @RawQuery(observedEntities = {
            Entry.class,
            MetaString.class,
            MetaLong.class,
            MetaDouble.class,
            MetaLocalString.class,
            MetaLocalLong.class,
            MetaLocalDouble.class
    })
    public abstract LiveData<Integer> getNumEntries(SupportSQLiteQuery query);
    @Transaction
    public void insert(List<Meta> metaList) {
        for (Meta meta: metaList) {
            insert(meta);
        }
    }
    public void insert(Meta meta) {
        String api = meta.entryID.src;
        String id = meta.entryID.id;
        insert(Entry.from(api, id));
        for (String key: meta.keySet()) {
            if (Meta.isLocal(key)) {
                Log.e(LC, "Won't insert value with key reserved for local use: " + key);
                continue;
            }
            switch (Meta.getType(key)) {
                case STRING:
                    for (String s: meta.getStrings(key)) {
                        insert(MetaString.from(api, id, key, s));
                    }
                    break;
                case LONG:
                    for (Long l: meta.getLongs(key)) {
                        insert(MetaLong.from(api, id, key, l));
                    }
                    break;
                case DOUBLE:
                    for (Double d: meta.getDoubles(key)) {
                        insert(MetaDouble.from(api, id, key, d));
                    }
                    break;
                default:
                    Log.e(LC, "Unhandled key: " + key + " type: " + Meta.getType(key));
            }
        }
    }
    @Query("DELETE FROM " + DB.TABLE_ENTRY_ID + " WHERE " + DB.COLUMN_API + " = :src")
    public abstract void deleteWhereSourceIs(String src);

    private static final String getStringMeta
            = "SELECT * FROM " + DB.TABLE_META_STRING + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_STRING + " WHERE " + isEntryID;
    @Query(getStringMeta)
    public abstract List<MetaString> getStringMetaSync(String src, String id);
    @Query(getStringMeta)
    public abstract LiveData<List<MetaString>> getStringMeta(String src, String id);
    @Query("SELECT * FROM " + DB.TABLE_META_STRING
            + " WHERE " + isEntryID + " AND \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_STRING
            + " WHERE " + isEntryID + " AND \"" + DB.COLUMN_KEY + "\" = :key")
    public abstract LiveData<List<MetaString>> getStringMeta(String src, String id, String key);

    private static final String getLongMeta
            = "SELECT * FROM " + DB.TABLE_META_LONG + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_LONG + " WHERE " + isEntryID;
    @Query(getLongMeta)
    public abstract List<MetaLong> getLongMetaSync(String src, String id);
    @Query(getLongMeta)
    public abstract LiveData<List<MetaLong>> getLongMeta(String src, String id);

    private static final String getDoubleMeta
            = "SELECT * FROM " + DB.TABLE_META_DOUBLE + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_DOUBLE + " WHERE " + isEntryID;
    @Query(getDoubleMeta)
    public abstract List<MetaDouble> getDoubleMetaSync(String src, String id);
    @Query(getDoubleMeta)
    public abstract LiveData<List<MetaDouble>> getDoubleMeta(String src, String id);

    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_STRING
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LOCAL_STRING)
    public abstract LiveData<List<String>> getStringMetaKeys();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LONG
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LOCAL_LONG)
    public abstract LiveData<List<String>> getLongMetaKeys();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_DOUBLE
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LOCAL_DOUBLE)
    public abstract LiveData<List<String>> getDoubleMetaKeys();
}