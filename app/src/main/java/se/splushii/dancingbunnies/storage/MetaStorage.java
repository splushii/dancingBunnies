package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.sqlite.db.SimpleSQLiteQuery;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.MetaBoolean;
import se.splushii.dancingbunnies.storage.db.MetaDao;
import se.splushii.dancingbunnies.storage.db.MetaDouble;
import se.splushii.dancingbunnies.storage.db.MetaLong;
import se.splushii.dancingbunnies.storage.db.MetaString;
import se.splushii.dancingbunnies.util.Util;

public class MetaStorage {
    private static final String LC = Util.getLogContext(MetaStorage.class);

    private final MetaDao metaModel;

    public MetaStorage(Context context) {
        metaModel = DB.getDB(context).metaModel();
    }

    public void insertSongs(List<Meta> metaList) {
        long start = System.currentTimeMillis();
        Log.d(LC, "insertSongs start");
        metaModel.insert(metaList);
        Log.d(LC, "insertSongs finish. " + (System.currentTimeMillis() - start) + "ms");
    }

    // TODO: Support sort-by argument in bundleQuery
    // TODO: rework bundleQuery and getEntries to support nested queries
    public LiveData<List<LibraryEntry>> getEntries(Bundle bundleQuery) {
        // TODO: Remove getTableAlias. Generate aliases from uniqueQueryKeys. (meta_1, meta_2, etc.)
        // TODO: For debug, print generated alias together with key (when printing query).
        String showTypeKey = bundleQuery.getString(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_MEDIA_ID);
        String showTypeTable = MetaDao.getTable(showTypeKey);
        String showTypeTableAlias = MetaDao.getTableAlias(showTypeKey);
        if (showTypeTable == null || showTypeTableAlias == null) {
            MutableLiveData<List<LibraryEntry>> entries = new MutableLiveData<>();
            entries.setValue(Collections.emptyList());
            return entries;
        }
        boolean showMeta = !Meta.METADATA_KEY_MEDIA_ID.equals(showTypeKey);
        // Other types (keys) which needs to be joined for the query
        HashSet<String> uniqueQueryKeys = new HashSet<>();
        for (String key: bundleQuery.keySet()) {
            if (Meta.METADATA_KEY_TYPE.equals(key)) {
                continue;
            }
            uniqueQueryKeys.add(key);
        }
        if (!showMeta) { // Add title to order by
            uniqueQueryKeys.add(Meta.METADATA_KEY_TITLE);
        }
        List<Object> queryArgs = new ArrayList<>();
        StringBuilder query = new StringBuilder("select");
        if (showMeta) {
            query.append(" distinct");
        }
        if (showMeta) {
            query.append(String.format(" %s.%s", showTypeTableAlias, DB.COLUMN_VALUE));
        } else {
            query.append(String.format(" %s.%s", showTypeTableAlias, DB.COLUMN_API));
            query.append(String.format(", %s.%s", showTypeTableAlias, DB.COLUMN_ID));
        }
        query.append(" from ").append(showTypeTable).append(" as ").append(showTypeTableAlias);
        for (String key: uniqueQueryKeys) {
            if (showTypeKey.equals(key)) {
                // Already added above in select
                continue;
            }
            String typeTable = MetaDao.getTable(key);
            String typeTableAlias = MetaDao.getTableAlias(key);
            if (typeTable == null || typeTableAlias == null) {
                Log.e(LC, "There is no type table"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            query.append(" left join " + typeTable + " as " + typeTableAlias
                    + " on ( " + typeTableAlias + "." + DB.COLUMN_KEY + " = ?"
                    + " and " + typeTableAlias + "." + DB.COLUMN_API
                    + " = " + showTypeTableAlias + "." + DB.COLUMN_API
                    + " and " + typeTableAlias + "." + DB.COLUMN_ID
                    + " = " + showTypeTableAlias + "." + DB.COLUMN_ID + " )");
            queryArgs.add(key);
        }
        // Add showType filter
        if (showMeta) {
            query.append(" where " + showTypeTableAlias + "." + DB.COLUMN_KEY + " = ?");
            queryArgs.add(showTypeKey);
        }
        // Add user query
        boolean whereClauseEmpty = true;
        for (String key: bundleQuery.keySet()) {
            if (Meta.METADATA_KEY_TYPE.equals(key)) {
                continue;
            }
            String typeTableAlias = MetaDao.getTableAlias(key);
            if (typeTableAlias == null) {
                Log.e(LC, "There is no type table"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            switch (Meta.getType(key)) {
                case LONG:
                    String value = bundleQuery.getString(key);
                    if (value == null) {
                        Log.e(LC, "LONG value in bundle is null for key: " + key);
                        break;
                    }
                    queryArgs.add(Long.parseLong(value));
                    break;
                case STRING:
                    queryArgs.add(bundleQuery.getString(key));
                    break;
                case BITMAP:
                    Log.e(LC, "Unsupported bundleQuery type: " + Meta.getType(key).name());
                    continue;
            }
            if (whereClauseEmpty) {
                if (showMeta) {
                    query.append(" AND ( ");
                } else {
                    query.append(" WHERE (");
                }
                whereClauseEmpty = false;
            } else {
                query.append(" AND ");
            }
            query.append(typeTableAlias).append(".").append(DB.COLUMN_VALUE).append(" = ?");
        }
        if (!whereClauseEmpty) {
            query.append(" )");
        }
        // Sort
        if (showMeta) {
            query.append(" order by " + showTypeTableAlias + "." + DB.COLUMN_VALUE);
        } else {
            query.append(" order by " + MetaDao.getTableAlias(Meta.METADATA_KEY_TITLE)
                    + "." + DB.COLUMN_VALUE);
        }
        SimpleSQLiteQuery sqlQuery = new SimpleSQLiteQuery(query.toString(), queryArgs.toArray());
        Log.d(LC, "query: " + sqlQuery.getSql());
        return Transformations.map(metaModel.getEntries(sqlQuery), values ->
                values.stream().map(value -> {
                            EntryID entryID = showTypeKey.equals(Meta.METADATA_KEY_MEDIA_ID) ?
                                    new EntryID(
                                            value.api,
                                            value.id,
                                            showTypeKey
                                    )
                                    :
                                    new EntryID(
                                            MusicLibraryService.API_ID_DANCINGBUNNIES,
                                            value.value,
                                            showTypeKey
                                    );
                            return new LibraryEntry(entryID, value.value);
                        }
                ).collect(Collectors.toList())
        );
    }

    public CompletableFuture<List<EntryID>> getEntries(EntryID entryID) {
        CompletableFuture<List<EntryID>> ret = new CompletableFuture<>();
        Bundle bundleQuery = new Bundle();
        bundleQuery.putString(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_MEDIA_ID);
        bundleQuery.putString(entryID.type, entryID.id);
        LiveData<List<LibraryEntry>> liveData = getEntries(bundleQuery);
        liveData.observeForever(new Observer<List<LibraryEntry>>() {
            @Override
            public void onChanged(List<LibraryEntry> libraryEntries) {
                ret.complete(libraryEntries.stream()
                        .map(libraryEntry -> libraryEntry.entryID)
                        .collect(Collectors.toList()));
                liveData.removeObserver(this);
            }
        });
        return ret;
    }

    public void clearAll(String src) {
        metaModel.deleteWhereSourceIs(src);
    }

    public CompletableFuture<Meta> getMeta(EntryID entryID) {
        CompletableFuture<List<MetaString>> stringFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getStringMeta(entryID.src, entryID.id)
        );
        CompletableFuture<List<MetaLong>> longFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getLongMeta(entryID.src, entryID.id)
        );
        CompletableFuture<List<MetaBoolean>> boolFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getBoolMeta(entryID.src, entryID.id)
        );
        CompletableFuture<List<MetaDouble>> doubleFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getDoubleMeta(entryID.src, entryID.id)
        );
        CompletableFuture[] futures = new CompletableFuture[] {
                stringFuture,
                longFuture,
                boolFuture,
                doubleFuture
        };
        CompletableFuture<Meta> ret = new CompletableFuture<>();
        CompletableFuture.allOf(futures).thenRunAsync(() -> {
            Meta meta = new Meta(entryID);
            // TODO: Add support in Meta for multiple values for same key
            stringFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.setString(v.key, v.value)
            );
            longFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.setLong(v.key, v.value)
            );
            boolFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.setBoolean(v.key, v.value)
            );
            doubleFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.setDouble(v.key, v.value)
            );
            ret.complete(meta);
        });
        return ret;
    }
}