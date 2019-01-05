package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class MetaStorage {
    private static final String LC = Util.getLogContext(MetaStorage.class);

    private final RoomMetaDao metaModel;

    public MetaStorage(Context context) {
        metaModel = RoomDB.getDB(context).metaModel();
    }

    public void insertSongs(List<Meta> metaList) {
        long start = System.currentTimeMillis();
        Log.d(LC, "insertSongs start");
        RoomMetaSong[] songs = new RoomMetaSong[metaList.size()];
        for (int i = 0; i < songs.length; i++) {
            songs[i] = metaList.get(i).toRoomSong();
        }
        metaModel.insert(songs);
        Log.d(LC, "insertSongs finish. " + (System.currentTimeMillis() - start) + "ms");
    }

    private List<Meta> fromSongs(List<RoomMetaSong> songs) {
        List<Meta> metaList = new ArrayList<>();
        for (RoomMetaSong song: songs) {
            metaList.add(song.getMeta());
        }
        return metaList;
    }

    private LiveData<List<Meta>> getMetadataEntries(Bundle bundleQuery) {
        SupportSQLiteQuery query = getSongSelectQuery(bundleQuery);
        Log.d(LC, "getMetaDataEntry query: " + query.getSql());
        LiveData<List<RoomMetaSong>> songs = metaModel.getSongsViaQuery(query);
        return Transformations.map(songs, this::fromSongs);
    }

    private SupportSQLiteQuery getSongSelectQuery(Bundle bundleQuery) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT * FROM " + RoomDB.SONG_TABLE);
        if (bundleQuery == null || bundleQuery.isEmpty()) {
            return new SimpleSQLiteQuery(query.toString());
        }
        List<Object> args = constructSQLWhereClause(query, bundleQuery);
        return new SimpleSQLiteQuery(query.toString(), args.toArray());
    }

    public LiveData<List<LibraryEntry>> getEntries(Bundle bundleQuery) {
        LiveData<List<LibraryEntry>> libraryEntries;
        String type = bundleQuery.getString(Meta.METADATA_KEY_TYPE);
        String metaType = type == null ? Meta.METADATA_KEY_MEDIA_ID : type;
        boolean resultIsMeta = !Meta.METADATA_KEY_MEDIA_ID.equals(metaType);
        SupportSQLiteQuery query = resultIsMeta ?
                getEntriesSelectQuery(bundleQuery) : getSongSelectQuery(bundleQuery);
        Log.d(LC, "getEntries query: " + query.getSql());
        if (resultIsMeta) {
            LiveData<List<RoomMetaValue>> entries = metaModel.getMetaViaQuery(query);
            libraryEntries = Transformations.map(entries, _entries ->
                    _entries.stream().map(ent -> new LibraryEntry(
                            new EntryID(
                                    MusicLibraryService.API_ID_DANCINGBUNNIES,
                                    ent.value,
                                    metaType
                            ),
                            ent.value
                    )).collect(Collectors.toList())
            );
        } else {
            LiveData<List<RoomMetaSong>> songs = metaModel.getSongsViaQuery(query);
            libraryEntries = Transformations.map(songs, _songs ->
                    _songs.stream().map(s -> new LibraryEntry(
                            new EntryID(
                                    s.api,
                                    s.id,
                                    metaType
                            ),
                            s.title
                    )).collect(Collectors.toList())
            );
        }
        return libraryEntries;
    }

    private SupportSQLiteQuery getEntriesSelectQuery(Bundle bundleQuery) {
        if (bundleQuery == null || bundleQuery.isEmpty()) {
            return new SimpleSQLiteQuery("SELECT "
                    + RoomMetaSong.COLUMN_ID + " as " + RoomMetaValue.VALUE
                    + " FROM " + DB.TABLE_SONGS
            );
        }
        String type = bundleQuery.getString(Meta.METADATA_KEY_TYPE);
        String metaColumn = type == null ? RoomMetaSong.COLUMN_ID : RoomMetaSong.columnName(type);
        StringBuilder query = new StringBuilder();
        query.append("SELECT DISTINCT ")
                .append(metaColumn).append(" as ").append(RoomMetaValue.VALUE)
                .append(" FROM ").append(RoomDB.SONG_TABLE);
        List<Object> args = constructSQLWhereClause(query, bundleQuery);
        return new SimpleSQLiteQuery(query.toString(), args.toArray());
    }

    private List<Object> constructSQLWhereClause(StringBuilder query, Bundle bundleQuery) {
        List<Object> args = new ArrayList<>();
        for (String key: bundleQuery.keySet()) {
            String columnName = RoomMetaSong.columnName(key);
            if (columnName == null) {
                if (!Meta.METADATA_KEY_TYPE.equals(key)) {
                    Log.e(LC, "getSongSelectQuery:"
                            + " there is no column in \"" + RoomDB.SONG_TABLE + "\""
                            + " for bundleQuery key \"" + key + "\"");
                }
                continue;
            }
            switch (Meta.getType(key)) {
                case LONG:
                    args.add(bundleQuery.getLong(key));
                    break;
                case STRING:
                    args.add(bundleQuery.getString(key));
                    break;
                case BITMAP:
                case RATING:
                    Log.e(LC, "Unsupported bundleQuery type: " + Meta.getType(key).name());
                    continue;
            }
            if (args.size() < 1) {
                Log.wtf(LC, "getSongSelectQuery: internal error");
                continue;
            } else if (args.size() == 1) {
                query.append(" WHERE ");
            } else {
                query.append(" AND ");
            }
            query.append(columnName).append("=?");
        }
        return args;
    }

    public LiveData<List<Meta>> getAllSongMetaData() {
        return getMetadataEntries(null);
    }

    public void clearAll(String src) {
        metaModel.deleteWhereSourceIs(src);
    }
}