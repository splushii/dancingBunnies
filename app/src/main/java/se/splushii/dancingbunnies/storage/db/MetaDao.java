package se.splushii.dancingbunnies.storage.db;

import android.util.Log;

import java.util.Collections;
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

    private static final String isSource = DB.COLUMN_SRC + " = :src";
    private static final String isEntryID =  isSource + " AND " + DB.COLUMN_ID + " = :id";

    public static String getTable(String entryType, String key) {
        boolean isLocal = Meta.isLocal(key);
        boolean isID = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(key)
                || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(key)
                || Meta.FIELD_SPECIAL_ENTRY_SRC.equals(key);
        switch (entryType) {
            default:
            case EntryID.TYPE_TRACK:
                if (isID) {
                    return DB.TABLE_TRACK_ID;
                }
                switch (Meta.getType(key)) {
                    default:
                    case STRING:
                        return isLocal
                                ? DB.TABLE_META_LOCAL_STRING
                                : DB.TABLE_META_STRING;
                    case LONG:
                        return isLocal
                                ? DB.TABLE_META_LOCAL_LONG
                                : DB.TABLE_META_LONG;
                    case DOUBLE:
                        return isLocal
                                ? DB.TABLE_META_LOCAL_DOUBLE
                                : DB.TABLE_META_DOUBLE;
                }
            case EntryID.TYPE_PLAYLIST:
                if (isID) {
                    return DB.TABLE_PLAYLIST_ID;
                }
                switch (Meta.getType(key)) {
                    default:
                    case STRING:
                        return isLocal
                                ? DB.TABLE_PLAYLIST_META_LOCAL_STRING
                                : DB.TABLE_PLAYLIST_META_STRING;
                    case LONG:
                        return isLocal
                                ? DB.TABLE_PLAYLIST_META_LOCAL_LONG
                                : DB.TABLE_PLAYLIST_META_LONG;
                    case DOUBLE:
                        return isLocal
                                ? DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE
                                : DB.TABLE_PLAYLIST_META_DOUBLE;
                }
        }
    }

    @Insert(onConflict = REPLACE)
    abstract void insert(Track... entries);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaString... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLong... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaDouble... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLocalString... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLocalLong... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(MetaLocalDouble... values);

    @Insert(onConflict = REPLACE)
    abstract void insert(Playlist... entries);
    @Insert(onConflict = REPLACE)
    abstract void insert(PlaylistMetaString... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(PlaylistMetaLong... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(PlaylistMetaDouble... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(PlaylistMetaLocalString... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(PlaylistMetaLocalLong... values);
    @Insert(onConflict = REPLACE)
    abstract void insert(PlaylistMetaLocalDouble... values);

    public LiveData<List<Entry>> getEntries(String entryType) {
        return getEntries(entryType, "");
    }

    public LiveData<List<Entry>> getEntries(String entryType, String src) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                if (src == null || src.isEmpty()) {
                    return getTracks();
                }
                return getTracks(src);
            case EntryID.TYPE_PLAYLIST:
                if (src == null || src.isEmpty()) {
                    return getPlaylists();
                }
                return getPlaylists(src);
            default:
                Log.e(LC, "Unknown entry type: " + entryType);
                return null;
        }
    }
    @Query("SELECT * FROM " + DB.TABLE_TRACK_ID)
    abstract LiveData<List<Entry>> getTracks();
    @Query("SELECT * FROM " + DB.TABLE_TRACK_ID + " WHERE " + isSource)
    abstract LiveData<List<Entry>> getTracks(String src);
    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ID)
    abstract LiveData<List<Entry>> getPlaylists();
    @Query("SELECT * FROM " + DB.TABLE_PLAYLIST_ID + " WHERE " + isSource)
    abstract LiveData<List<Entry>> getPlaylists(String src);

    public LiveData<List<MetaValueEntry>> getEntries(String entryType, SupportSQLiteQuery query) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTracks(query);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylists(query);
            default:
                Log.e(LC, "Unknown entry type: " + entryType);
                return null;
        }
    }
    @RawQuery(observedEntities = {
            Track.class,
            MetaString.class,
            MetaLong.class,
            MetaDouble.class,
            MetaLocalString.class,
            MetaLocalLong.class,
            MetaLocalDouble.class
    })
    abstract LiveData<List<MetaValueEntry>> getTracks(SupportSQLiteQuery query);
    @RawQuery(observedEntities = {
            Playlist.class,
            PlaylistMetaString.class,
            PlaylistMetaLong.class,
            PlaylistMetaDouble.class,
            PlaylistMetaLocalString.class,
            PlaylistMetaLocalLong.class,
            PlaylistMetaLocalDouble.class
    })
    abstract LiveData<List<MetaValueEntry>> getPlaylists(SupportSQLiteQuery query);

    public List<MetaValueEntry> getEntriesOnce(String entryType, SupportSQLiteQuery query) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTracksOnce(query);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistsOnce(query);
            default:
                Log.e(LC, "Unknown entry type: " + entryType);
                return null;
        }
    }
    @RawQuery(observedEntities = {
            Track.class,
            MetaString.class,
            MetaLong.class,
            MetaDouble.class,
            MetaLocalString.class,
            MetaLocalLong.class,
            MetaLocalDouble.class
    })
    abstract List<MetaValueEntry> getTracksOnce(SupportSQLiteQuery query);
    @RawQuery(observedEntities = {
            Playlist.class,
            PlaylistMetaString.class,
            PlaylistMetaLong.class,
            PlaylistMetaDouble.class,
            PlaylistMetaLocalString.class,
            PlaylistMetaLocalLong.class,
            PlaylistMetaLocalDouble.class
    })
    abstract List<MetaValueEntry> getPlaylistsOnce(SupportSQLiteQuery query);

    public LiveData<Integer> getNumEntries(String entryType) {
        return getNumEntries(entryType, "");
    }

    public LiveData<Integer> getNumEntries(String entryType, String src) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                if (src == null || src.isEmpty()) {
                    return getNumTracks();
                }
                return getNumTracks(src);
            case EntryID.TYPE_PLAYLIST:
                if (src == null || src.isEmpty()) {
                    return getNumPlaylists();
                }
                return getNumPlaylists(src);
            default:
                Log.e(LC, "Unknown entry type: " + entryType);
                return null;
        }
    }
    @Query("SELECT COUNT(*) FROM " + DB.TABLE_TRACK_ID)
    abstract LiveData<Integer> getNumTracks();
    @Query("SELECT COUNT(*) FROM " + DB.TABLE_TRACK_ID + " WHERE " + isSource)
    abstract LiveData<Integer> getNumTracks(String src);
    @Query("SELECT COUNT(*) FROM " + DB.TABLE_PLAYLIST_ID)
    abstract LiveData<Integer> getNumPlaylists();
    @Query("SELECT COUNT(*) FROM " + DB.TABLE_PLAYLIST_ID + " WHERE " + isSource)
    abstract LiveData<Integer> getNumPlaylists(String src);

    public LiveData<Integer> getNumEntries(String entryType, SupportSQLiteQuery query) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getNumTracks(query);
            case EntryID.TYPE_PLAYLIST:
                return getNumPlaylists(query);
            default:
                Log.e(LC, "Unknown entry type: " + entryType);
                return null;
        }
    }
    @RawQuery(observedEntities = {
            Track.class,
            MetaString.class,
            MetaLong.class,
            MetaDouble.class,
            MetaLocalString.class,
            MetaLocalLong.class,
            MetaLocalDouble.class
    })
    public abstract LiveData<Integer> getNumTracks(SupportSQLiteQuery query);
    @RawQuery(observedEntities = {
            Playlist.class,
            PlaylistMetaString.class,
            PlaylistMetaLong.class,
            PlaylistMetaDouble.class,
            PlaylistMetaLocalString.class,
            PlaylistMetaLocalLong.class,
            PlaylistMetaLocalDouble.class
    })
    public abstract LiveData<Integer> getNumPlaylists(SupportSQLiteQuery query);

    @Transaction
    public void insertEntriesAndMetas(String entryType,
                                      List<Meta> metaList,
                                      boolean allowLocalKeys,
                                      Consumer<String> progressHandler) {
        int size = metaList.size();
        for (int i = 0; i < size; i++) {
            Meta meta = metaList.get(i);
            String error = insertEntry(entryType, meta.entryID);
            if (error != null) {
                Log.e(LC, "insertEntriesAndMetas skipping due to error inserting entry: "
                        + error);
                continue;
            }
            insertMeta(entryType, meta, allowLocalKeys);
            if (progressHandler != null
                    && ((i + 1) % 100 == 0 || i == size - 1)) {
                progressHandler.accept(
                         "Saved " + (i + 1) + "/" + size + " entries to local meta storage..."
                );
            }
        }
    }

    private String insertEntry(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                insert(Track.from(entryID.src, entryID.id));
                return null;
            case EntryID.TYPE_PLAYLIST:
                insert(Playlist.from(entryID.src, entryID.id));
                return null;
            default:
                return "Unknown entry type: " + entryType;
        }
    }

    private void insertMeta(String entryType, Meta meta, boolean allowLocalKeys) {
        for (String key : meta.keySet()) {
            if (!allowLocalKeys && Meta.isLocal(key)) {
                Log.e(LC, "Won't insert value with key reserved for local use: " + key);
                continue;
            }
            switch (Meta.getType(key)) {
                case STRING:
                    insertMetaStrings(entryType, meta.entryID, key, meta.getStrings(key));
                    break;
                case LONG:
                    insertMetaLongs(entryType, meta.entryID, key, meta.getLongs(key));
                    break;
                case DOUBLE:
                    insertMetaDoubles(entryType, meta.entryID, key, meta.getDoubles(key));
                    break;
                default:
                    Log.e(LC, "Unhandled key: " + key + " type: " + Meta.getType(key));
                    break;
            }
        }
    }

    private void insertMetaStrings(String entryType, EntryID entryID, String key, List<String> values) {
        String table = getTable(entryType, key);
        switch (table) {
            case DB.TABLE_META_STRING:
                values.forEach(v -> insert(MetaString.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_META_LOCAL_STRING:
                values.forEach(v -> insert(MetaLocalString.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_PLAYLIST_META_STRING:
                values.forEach(v -> insert(PlaylistMetaString.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_PLAYLIST_META_LOCAL_STRING:
                values.forEach(v -> insert(PlaylistMetaLocalString.from(entryID.src, entryID.id, key, v)));
                break;
            default:
                Log.e(LC, "Trying to insert strings into non-string table: " + table);
        }
    }

    private void insertMetaLongs(String entryType, EntryID entryID, String key, List<Long> values) {
        String table = getTable(entryType, key);
        switch (table) {
            case DB.TABLE_META_LONG:
                values.forEach(v -> insert(MetaLong.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_META_LOCAL_LONG:
                values.forEach(v -> insert(MetaLocalLong.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_PLAYLIST_META_LONG:
                values.forEach(v -> insert(PlaylistMetaLong.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_PLAYLIST_META_LOCAL_LONG:
                values.forEach(v -> insert(PlaylistMetaLocalLong.from(entryID.src, entryID.id, key, v)));
                break;
            default:
                Log.e(LC, "Trying to insert longs into non-long table: " + table);
        }
    }

    private void insertMetaDoubles(String entryType, EntryID entryID, String key, List<Double> values) {
        String table = getTable(entryType, key);
        switch (table) {
            case DB.TABLE_META_DOUBLE:
                values.forEach(v -> insert(MetaDouble.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_META_LOCAL_DOUBLE:
                values.forEach(v -> insert(MetaLocalDouble.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_PLAYLIST_META_DOUBLE:
                values.forEach(v -> insert(PlaylistMetaDouble.from(entryID.src, entryID.id, key, v)));
                break;
            case DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE:
                values.forEach(v -> insert(PlaylistMetaLocalDouble.from(entryID.src, entryID.id, key, v)));
                break;
            default:
                Log.e(LC, "Trying to insert doubles into non-double table: " + table);
        }
    }

    public void insertMeta(String entryType, EntryID entryID, String key, String value) {
        switch (Meta.getType(key)) {
            case STRING:
                insertMetaStrings(
                        entryType,
                        entryID,
                        key,
                        Collections.singletonList(value)
                );
                break;
            case LONG:
                insertMetaLongs(
                        entryType,
                        entryID,
                        key,
                        Collections.singletonList(Long.parseLong(value))
                );
                break;
            case DOUBLE:
                insertMetaDoubles(
                        entryType,
                        entryID,
                        key,
                        Collections.singletonList(Double.parseDouble(value))
                );
                break;
            default:
                Log.e(LC, "Unhandled key: " + key + " type: " + Meta.getType(key));
                break;
        }
    }

    public void deleteMeta(String entryType, EntryID entryID, String key, String value) {
        String src = entryID.src;
        String id = entryID.id;
        String table = getTable(entryType, key);
        switch (table) {
            case DB.TABLE_META_STRING:
            case DB.TABLE_META_LONG:
            case DB.TABLE_META_DOUBLE:
                switch (Meta.getType(key)) {
                    case STRING:
                        deleteString(src, id, key, value);
                        break;
                    case LONG:
                        deleteLong(src, id, key, Long.parseLong(value));
                        break;
                    case DOUBLE:
                        deleteDouble(src, id, key, Double.parseDouble(value));
                        break;
                }
                break;
            case DB.TABLE_META_LOCAL_STRING:
            case DB.TABLE_META_LOCAL_LONG:
            case DB.TABLE_META_LOCAL_DOUBLE:
                switch (Meta.getType(key)) {
                    case STRING:
                        deleteLocalString(src, id, key, value);
                        break;
                    case LONG:
                        deleteLocalLong(src, id, key, Long.parseLong(value));
                        break;
                    case DOUBLE:
                        deleteLocalDouble(src, id, key, Double.parseDouble(value));
                        break;
                }
                break;
            case DB.TABLE_PLAYLIST_META_STRING:
            case DB.TABLE_PLAYLIST_META_LONG:
            case DB.TABLE_PLAYLIST_META_DOUBLE:
                switch (Meta.getType(key)) {
                    case STRING:
                        deletePlaylistString(src, id, key, value);
                        break;
                    case LONG:
                        deletePlaylistLong(src, id, key, Long.parseLong(value));
                        break;
                    case DOUBLE:
                        deletePlaylistDouble(src, id, key, Double.parseDouble(value));
                        break;
                }
                break;
            case DB.TABLE_PLAYLIST_META_LOCAL_STRING:
            case DB.TABLE_PLAYLIST_META_LOCAL_LONG:
            case DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE:
                switch (Meta.getType(key)) {
                    case STRING:
                        deletePlaylistLocalString(src, id, key, value);
                        break;
                    case LONG:
                        deletePlaylistLocalLong(src, id, key, Long.parseLong(value));
                        break;
                    case DOUBLE:
                        deletePlaylistLocalDouble(src, id, key, Double.parseDouble(value));
                        break;
                }
                break;
            case DB.TABLE_TRACK_ID:
            case DB.TABLE_PLAYLIST_ID:
                // NOPE
            default:
                Log.e(LC, "Tried to delete meta from invalid table \"" + table + "\""
                        + ", key: " + key);
                break;
        }
    }

    private static final String isTag = "\"" + DB.COLUMN_KEY + "\" = :key"
        + " AND \"" + DB.COLUMN_VALUE + "\" = :value";

    @Query("DELETE FROM " + DB.TABLE_META_STRING + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteString(String src, String id, String key, String value);
    @Query("DELETE FROM " + DB.TABLE_META_LONG + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLong(String src, String id, String key, long value);
    @Query("DELETE FROM " + DB.TABLE_META_DOUBLE + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteDouble(String src, String id, String key, double value);
    @Query("DELETE FROM " + DB.TABLE_META_LOCAL_STRING + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLocalString(String src, String id, String key, String value);
    @Query("DELETE FROM " + DB.TABLE_META_LOCAL_LONG + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLocalLong(String src, String id, String key, long value);
    @Query("DELETE FROM " + DB.TABLE_META_LOCAL_DOUBLE + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deleteLocalDouble(String src, String id, String key, double value);

    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_META_STRING + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deletePlaylistString(String src, String id, String key, String value);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_META_LONG + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deletePlaylistLong(String src, String id, String key, long value);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_META_DOUBLE + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deletePlaylistDouble(String src, String id, String key, double value);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_META_LOCAL_STRING + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deletePlaylistLocalString(String src, String id, String key, String value);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_META_LOCAL_LONG + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deletePlaylistLocalLong(String src, String id, String key, long value);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE + " WHERE " + isEntryID + " AND " + isTag)
    abstract void deletePlaylistLocalDouble(String src, String id, String key, double value);

    @Transaction
    public void replaceMeta(String entryType,
                            EntryID entryID,
                            String key,
                            String oldValue,
                            String newValue) {
        deleteMeta(entryType, entryID, key, oldValue);
        insertMeta(entryType, entryID, key, newValue);
    }

    @Transaction
    public void deleteEntries(String entryType, List<EntryID> entryIDs) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                for (EntryID entryID: entryIDs) {
                    deleteTrack(entryID.src, entryID.id);
                }
                break;
            case EntryID.TYPE_PLAYLIST:
                for (EntryID entryID: entryIDs) {
                    // Delete cascades to playlistEntries
                    deletePlaylist(entryID.src, entryID.id);
                }
                break;
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
        }
    }
    @Query("DELETE FROM " + DB.TABLE_TRACK_ID + " WHERE " + isEntryID)
    abstract void deleteTrack(String src, String id);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_ID + " WHERE " + isEntryID)
    abstract void deletePlaylist(String src, String id); // Delete cascades to playlistEntries

    public void deleteEntriesWhereSourceIs(String entryType, String src) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                deleteTracksWhereSourceIs(src);
                break;
            case EntryID.TYPE_PLAYLIST:
                deletePlaylistsWhereSourceIs(src);
                break;
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
        }
    }
    @Query("DELETE FROM " + DB.TABLE_TRACK_ID + " WHERE " + isSource)
    abstract void deleteTracksWhereSourceIs(String src);
    @Query("DELETE FROM " + DB.TABLE_PLAYLIST_ID + " WHERE " + isSource)
    abstract void deletePlaylistsWhereSourceIs(String src); // Delete cascades to playlistEntries

    @Transaction
    public void replaceAllEntriesAndMetasFromSource(String entryType,
                                                    String src,
                                                    List<Meta> metaList,
                                                    boolean allowLocalKeys,
                                                    Consumer<String> progressHandler) {
        progressHandler.accept("Clearing old entries...");
        deleteEntriesWhereSourceIs(entryType, src);
        insertEntriesAndMetas(entryType, metaList, allowLocalKeys, progressHandler);
    }

    public List<MetaString> getStringMetaSync(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackStringMetaSync(entryID.src, entryID.id);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistStringMetaSync(entryID.src, entryID.id);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<MetaString>> getStringMeta(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackStringMeta(entryID.src, entryID.id);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistStringMeta(entryID.src, entryID.id);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    private static final String getTrackStringMeta
            = "SELECT * FROM " + DB.TABLE_META_STRING + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_STRING + " WHERE " + isEntryID;
    @Query(getTrackStringMeta)
    abstract List<MetaString> getTrackStringMetaSync(String src, String id);
    @Query(getTrackStringMeta)
    abstract LiveData<List<MetaString>> getTrackStringMeta(String src, String id);

    private static final String getPlaylistStringMeta
            = "SELECT * FROM " + DB.TABLE_PLAYLIST_META_STRING + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_PLAYLIST_META_LOCAL_STRING + " WHERE " + isEntryID;
    @Query(getPlaylistStringMeta)
    abstract List<MetaString> getPlaylistStringMetaSync(String src, String id);
    @Query(getPlaylistStringMeta)
    abstract LiveData<List<MetaString>> getPlaylistStringMeta(String src, String id);

    public List<MetaLong> getLongMetaSync(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackLongMetaSync(entryID.src, entryID.id);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistLongMetaSync(entryID.src, entryID.id);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<MetaLong>> getLongMeta(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackLongMeta(entryID.src, entryID.id);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistLongMeta(entryID.src, entryID.id);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    private static final String getTrackLongMeta
            = "SELECT * FROM " + DB.TABLE_META_LONG + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_LONG + " WHERE " + isEntryID;
    @Query(getTrackLongMeta)
    abstract List<MetaLong> getTrackLongMetaSync(String src, String id);
    @Query(getTrackLongMeta)
    abstract LiveData<List<MetaLong>> getTrackLongMeta(String src, String id);

    private static final String getPlaylistLongMeta
            = "SELECT * FROM " + DB.TABLE_PLAYLIST_META_LONG + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_PLAYLIST_META_LOCAL_LONG + " WHERE " + isEntryID;
    @Query(getPlaylistLongMeta)
    abstract List<MetaLong> getPlaylistLongMetaSync(String src, String id);
    @Query(getPlaylistLongMeta)
    abstract LiveData<List<MetaLong>> getPlaylistLongMeta(String src, String id);

    public List<MetaDouble> getDoubleMetaSync(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackDoubleMetaSync(entryID.src, entryID.id);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistDoubleMetaSync(entryID.src, entryID.id);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<MetaDouble>> getDoubleMeta(String entryType, EntryID entryID) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackDoubleMeta(entryID.src, entryID.id);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistDoubleMeta(entryID.src, entryID.id);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    private static final String getTrackDoubleMeta
            = "SELECT * FROM " + DB.TABLE_META_DOUBLE + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_META_LOCAL_DOUBLE + " WHERE " + isEntryID;
    @Query(getTrackDoubleMeta)
    abstract List<MetaDouble> getTrackDoubleMetaSync(String src, String id);
    @Query(getTrackDoubleMeta)
    abstract LiveData<List<MetaDouble>> getTrackDoubleMeta(String src, String id);

    private static final String getPlaylistDoubleMeta
            = "SELECT * FROM " + DB.TABLE_PLAYLIST_META_DOUBLE + " WHERE " + isEntryID
            + " UNION"
            + " SELECT * FROM " + DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE + " WHERE " + isEntryID;
    @Query(getPlaylistDoubleMeta)
    abstract List<MetaDouble> getPlaylistDoubleMetaSync(String src, String id);
    @Query(getPlaylistDoubleMeta)
    abstract LiveData<List<MetaDouble>> getPlaylistDoubleMeta(String src, String id);

    public LiveData<List<String>> getStringMetaKeys(String entryType) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackStringMetaKeys();
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistStringMetaKeys();
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<String>> getLongMetaKeys(String entryType) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackLongMetaKeys();
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistLongMetaKeys();
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<String>> getDoubleMetaKeys(String entryType) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackDoubleMetaKeys();
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistDoubleMetaKeys();
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_STRING
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LOCAL_STRING)
    abstract LiveData<List<String>> getTrackStringMetaKeys();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LONG
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LOCAL_LONG)
    abstract LiveData<List<String>> getTrackLongMetaKeys();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_DOUBLE
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_META_LOCAL_DOUBLE)
    abstract LiveData<List<String>> getTrackDoubleMetaKeys();

    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_PLAYLIST_META_STRING
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_PLAYLIST_META_LOCAL_STRING)
    abstract LiveData<List<String>> getPlaylistStringMetaKeys();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_PLAYLIST_META_LONG
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_PLAYLIST_META_LOCAL_LONG)
    abstract LiveData<List<String>> getPlaylistLongMetaKeys();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_PLAYLIST_META_DOUBLE
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_KEY + "\" FROM " + DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE)
    abstract LiveData<List<String>> getPlaylistDoubleMetaKeys();

    public LiveData<List<String>> getStringMetaValues(String entryType, String key) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackStringMetaValues(key);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistStringMetaValues(key);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<Long>> getLongMetaValues(String entryType, String key) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackLongMetaValues(key);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistLongMetaValues(key);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    public LiveData<List<Double>> getDoubleMetaValues(String entryType, String key) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackDoubleMetaValues(key);
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistDoubleMetaValues(key);
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }

    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_STRING
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LOCAL_STRING
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    abstract LiveData<List<String>> getTrackStringMetaValues(String key);
    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LONG
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LOCAL_LONG
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    abstract LiveData<List<Long>> getTrackLongMetaValues(String key);
    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_DOUBLE
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_META_LOCAL_DOUBLE
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    abstract LiveData<List<Double>> getTrackDoubleMetaValues(String key);

    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_PLAYLIST_META_STRING
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_PLAYLIST_META_LOCAL_STRING
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    abstract LiveData<List<String>> getPlaylistStringMetaValues(String key);
    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_PLAYLIST_META_LONG
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_PLAYLIST_META_LOCAL_LONG
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    abstract LiveData<List<Long>> getPlaylistLongMetaValues(String key);
    @Query("SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_PLAYLIST_META_DOUBLE
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key"
            + " UNION"
            + " SELECT DISTINCT \"" + DB.COLUMN_VALUE + "\" FROM " + DB.TABLE_PLAYLIST_META_LOCAL_DOUBLE
            + " WHERE \"" + DB.COLUMN_KEY + "\" = :key")
    abstract LiveData<List<Double>> getPlaylistDoubleMetaValues(String key);

    public LiveData<List<String>> getSources(String entryType) {
        switch (entryType) {
            case EntryID.TYPE_TRACK:
                return getTrackSources();
            case EntryID.TYPE_PLAYLIST:
                return getPlaylistSources();
            default:
                Log.e(LC, "Unhandled entry type: " + entryType);
                return null;
        }
    }
    @Query("SELECT DISTINCT \"" + DB.COLUMN_SRC + "\" FROM " + DB.TABLE_TRACK_ID)
    abstract LiveData<List<String>> getTrackSources();
    @Query("SELECT DISTINCT \"" + DB.COLUMN_SRC + "\" FROM " + DB.TABLE_PLAYLIST_ID)
    abstract LiveData<List<String>> getPlaylistSources();
}