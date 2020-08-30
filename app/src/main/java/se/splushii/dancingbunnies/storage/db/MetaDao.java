package se.splushii.dancingbunnies.storage.db;

import android.util.Log;

import java.util.List;

import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.sqlite.db.SupportSQLiteQuery;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

import static androidx.room.OnConflictStrategy.REPLACE;

@Dao
public abstract class MetaDao {
    private static final String LC = Util.getLogContext(MetaDao.class);

    private static final String isEntryID = DB.COLUMN_SRC + " = :src " + " AND " + DB.COLUMN_ID + " = :id";

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
    public abstract List<MetaValueEntry> getEntriesOnce(SupportSQLiteQuery query);
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
    public void insert(List<Meta> metaList, Consumer<String> progressHandler) {
        int size = metaList.size();
        for (int i = 0; i < size; i++) {
             insert(metaList.get(i));
             if (progressHandler != null
                     && ((i + 1) % 100 == 0 || i == size - 1)) {
                 progressHandler.accept(
                         "Saved " + (i + 1) + "/" + size + " entries to local meta storage..."
                 );
             }
        }
    }
    public void insert(Meta meta) {
        String src = meta.entryID.src;
        String id = meta.entryID.id;
        insert(Entry.from(src, id));
        for (String key: meta.keySet()) {
            if (Meta.isLocal(key)) {
                Log.e(LC, "Won't insert value with key reserved for local use: " + key);
                continue;
            }
            switch (Meta.getType(key)) {
                case STRING:
                    for (String s: meta.getStrings(key)) {
                        insert(MetaString.from(src, id, key, s));
                    }
                    break;
                case LONG:
                    for (Long l: meta.getLongs(key)) {
                        insert(MetaLong.from(src, id, key, l));
                    }
                    break;
                case DOUBLE:
                    for (Double d: meta.getDoubles(key)) {
                        insert(MetaDouble.from(src, id, key, d));
                    }
                    break;
                default:
                    Log.e(LC, "Unhandled key: " + key + " type: " + Meta.getType(key));
            }
        }
    }

    private boolean isValidLocalMetaTag(String expectedTable,
                                        String expectedType,
                                        boolean userKey,
                                        String key) {
        if (!Meta.isLocal(key)) {
            Log.e(LC, "isValidLocalMetaTag. Key is not reserved for local use: + " + key);
            return false;
        }
        if (userKey && !Meta.isLocalUser(key)) {
            Log.e(LC, "isValidLocalMetaTag. Key is not reserved for local user use: " + key);
            return false;
        }
        String table = getTable(key);
        if (!expectedTable.equals(table)) {
            Log.e(LC, "isValidLocalMetaTag: Trying to insert tag"
                    + " with wrong value type for key: " + key
                    + " expected type: " + Meta.getType(key) + " actual type: " + expectedType);
            return false;
        }
        return true;
    }
    public void insertLocalMeta(EntryID entryID, String key, String value, boolean userKey) {
        if (!isValidLocalMetaTag(DB.TABLE_META_LOCAL_STRING, "string", userKey, key)) {
            return;
        }
        insert(MetaLocalString.from(entryID.src, entryID.id, key, value));
    }
    public void insertLocalMeta(EntryID entryID, String key, long value, boolean userKey) {
        if (!isValidLocalMetaTag(DB.TABLE_META_LOCAL_LONG, "long", userKey, key)) {
            return;
        }
        insert(MetaLocalLong.from(entryID.src, entryID.id, key, value));
    }
    public void insertLocalMeta(EntryID entryID, String key, double value, boolean userKey) {
        if (!isValidLocalMetaTag(DB.TABLE_META_LOCAL_DOUBLE, "double", userKey, key)) {
            return;
        }
        insert(MetaLocalDouble.from(entryID.src, entryID.id, key, value));
    }
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLocalString... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLocalLong... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLocalDouble... values);

    public void deleteLocalMeta(EntryID entryID, String key, String value, boolean userKey) {
        if (!isValidLocalMetaTag(DB.TABLE_META_LOCAL_STRING, "string", userKey, key)) {
            return;
        }
        deleteLocalString(entryID.src, entryID.id, key, value);
    }
    public void deleteLocalMeta(EntryID entryID, String key, long value, boolean userKey) {
        if (!isValidLocalMetaTag(DB.TABLE_META_LOCAL_LONG, "long", userKey, key)) {
            return;
        }
        deleteLocalLong(entryID.src, entryID.id, key, value);
    }
    public void deleteLocalMeta(EntryID entryID, String key, double value, boolean userKey) {
        if (!isValidLocalMetaTag(DB.TABLE_META_LOCAL_DOUBLE, "double", userKey, key)) {
            return;
        }
        deleteLocalDouble(entryID.src, entryID.id, key, value);
    }
    private static final String isTag = "\"" + DB.COLUMN_KEY + "\" = :key"
        + " AND \"" + DB.COLUMN_VALUE + "\" = :value";
    @Query("DELETE FROM " + DB.TABLE_META_LOCAL_STRING + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLocalString(String src, String id, String key, String value);
    @Query("DELETE FROM " + DB.TABLE_META_LOCAL_LONG + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLocalLong(String src, String id, String key, long value);
    @Query("DELETE FROM " + DB.TABLE_META_LOCAL_DOUBLE + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLocalDouble(String src, String id, String key, double value);
    @Transaction
    public void replaceLocalMeta(EntryID entryID, String key, String oldValue, String newValue, boolean userKey) {
        deleteLocalMeta(entryID, key, oldValue, userKey);
        insertLocalMeta(entryID, key, newValue, userKey);
    }
    @Transaction
    public void replaceLocalMeta(EntryID entryID, String key, long oldValue, long newValue, boolean userKey) {
        deleteLocalMeta(entryID, key, oldValue, userKey);
        insertLocalMeta(entryID, key, newValue, userKey);
    }
    @Transaction
    public void replaceLocalMeta(EntryID entryID, String key, double oldValue, double newValue, boolean userKey) {
        deleteLocalMeta(entryID, key, oldValue, userKey);
        insertLocalMeta(entryID, key, newValue, userKey);
    }

    @Query("DELETE FROM " + DB.TABLE_ENTRY_ID + " WHERE " + DB.COLUMN_SRC + " = :src")
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

    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_STRING
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LOCAL_STRING
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    public abstract LiveData<List<String>> getStringMetaValues(String key);
    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LONG
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LOCAL_LONG
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    public abstract LiveData<List<Long>> getLongMetaValues(String key);
    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_DOUBLE
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LOCAL_DOUBLE
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    public abstract LiveData<List<Double>> getDoubleMetaValues(String key);

    @Query("SELECT DISTINCT \"" + DB.COLUMN_SRC + "\" FROM " + DB.TABLE_ENTRY_ID)
    public abstract LiveData<List<String>> getSources();
}