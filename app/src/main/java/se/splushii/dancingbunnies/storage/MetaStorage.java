package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.sqlite.db.SimpleSQLiteQuery;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.MetaDao;
import se.splushii.dancingbunnies.storage.db.MetaDouble;
import se.splushii.dancingbunnies.storage.db.MetaLong;
import se.splushii.dancingbunnies.storage.db.MetaString;
import se.splushii.dancingbunnies.util.Util;

public class MetaStorage {
    private static final String LC = Util.getLogContext(MetaStorage.class);
    private static MetaStorage instance;

    private final MetaDao metaModel;

    public static synchronized MetaStorage getInstance(Context context) {
        if (instance == null) {
            instance = new MetaStorage(context);
        }
        return instance;
    }

    private MetaStorage(Context context) {
        metaModel = DB.getDB(context).metaModel();
    }

    public void insertSongs(List<Meta> metaList) {
        long start = System.currentTimeMillis();
        Log.d(LC, "insertSongs start");
        metaModel.insert(metaList);
        Log.d(LC, "insertSongs finish. " + (System.currentTimeMillis() - start) + "ms");
    }

    // TODO: rework bundleQuery and getEntries to support nested queries
    public LiveData<List<LibraryEntry>> getEntries(String showField,
                                                   String sortField,
                                                   Bundle bundleQuery) {
        HashMap<String, String> keyToTableAliasMap = new HashMap<>();
        String showTypeKey = showField == null ? Meta.FIELD_SPECIAL_MEDIA_ID : showField;
        String showTypeTable = MetaDao.getTable(showTypeKey);
        String showTypeTableAlias = "meta_show";
        keyToTableAliasMap.put(showTypeKey, showTypeTableAlias);
        if (showTypeTable == null) {
            MutableLiveData<List<LibraryEntry>> entries = new MutableLiveData<>();
            entries.setValue(Collections.emptyList());
            return entries;
        }
        boolean showMeta = !Meta.FIELD_SPECIAL_MEDIA_ID.equals(showTypeKey);
        // Other types (keys) which needs to be joined for the query
        HashSet<String> uniqueQueryKeys = new HashSet<>(bundleQuery.keySet());
        String sortKey = sortField == null ? showTypeKey : sortField;
        uniqueQueryKeys.add(sortKey);
        List<Object> queryArgs = new ArrayList<>();
        StringBuilder query = new StringBuilder("SELECT");
        if (showMeta) {
            query.append(" DISTINCT");
        }
        switch (showTypeKey) {
            case Meta.FIELD_SPECIAL_MEDIA_SRC:
                query.append(String.format(" %s.%s", showTypeTableAlias, DB.COLUMN_API));
                break;
            case Meta.FIELD_SPECIAL_MEDIA_ID:
                query.append(String.format(" %s.%s", showTypeTableAlias, DB.COLUMN_API));
                query.append(String.format(", %s.%s", showTypeTableAlias, DB.COLUMN_ID));
                break;
            default:
                query.append(String.format(" %s.%s", showTypeTableAlias, DB.COLUMN_VALUE));
                break;
        }
        query.append(" FROM ").append(showTypeTable).append(" AS ").append(showTypeTableAlias);
        int tableAliasIndex = 1;
        for (String key: uniqueQueryKeys) {
            if (showTypeKey.equals(key)) {
                // Already handled
                continue;
            }
            if (Meta.FIELD_SPECIAL_MEDIA_SRC.equals(key)
                    || Meta.FIELD_SPECIAL_MEDIA_ID.equals(key)) {
                // These keys are present in every meta table
                keyToTableAliasMap.put(key, showTypeTableAlias);
                continue;
            }
            String typeTable = MetaDao.getTable(key);
            String typeTableAlias = "meta_" + tableAliasIndex++;
            keyToTableAliasMap.put(key, typeTableAlias);
            if (typeTable == null) {
                Log.e(LC, "There is no type table"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            query.append("\nLEFT JOIN " + typeTable + " AS " + typeTableAlias
                    + " ON ( " + typeTableAlias + "." + DB.COLUMN_KEY + " = ?"
                    + " AND " + typeTableAlias + "." + DB.COLUMN_API
                    + " = " + showTypeTableAlias + "." + DB.COLUMN_API
                    + " AND " + typeTableAlias + "." + DB.COLUMN_ID
                    + " = " + showTypeTableAlias + "." + DB.COLUMN_ID + " )");
            queryArgs.add(key);
        }
        // Add showType filter
        if (!showTypeKey.equals(Meta.FIELD_SPECIAL_MEDIA_ID)
                && !showTypeKey.equals(Meta.FIELD_SPECIAL_MEDIA_SRC)) {
            query.append("\nWHERE " + showTypeTableAlias + "." + DB.COLUMN_KEY + " = ?");
            queryArgs.add(showTypeKey);
        }
        // Add user query
        boolean whereClauseEmpty = true;
        for (String key: bundleQuery.keySet()) {
            String typeTableAlias = keyToTableAliasMap.get(key);
            if (typeTableAlias == null) {
                Log.e(LC, "There is no type table"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            switch (Meta.getType(key)) {
                case LONG:
                    String longValue = bundleQuery.getString(key);
                    if (longValue == null) {
                        Log.e(LC, "LONG value in bundle is null for key: " + key);
                        break;
                    }
                    queryArgs.add(Long.parseLong(longValue));
                    break;
                case STRING:
                    queryArgs.add(bundleQuery.getString(key));
                    break;
                case DOUBLE:
                    String doubleValue = bundleQuery.getString(key);
                    if (doubleValue == null) {
                        Log.e(LC, "DOUBLE value in bundle is null for key: " + key);
                        break;
                    }
                    queryArgs.add(Double.parseDouble(doubleValue));
                    break;
                default:
                    Log.e(LC, "Unsupported bundleQuery type: " + Meta.getType(key).name());
                    continue;
            }
            if (whereClauseEmpty) {
                if (showMeta) {
                    query.append(" AND ( ");
                } else {
                    query.append("\nWHERE (");
                }
                whereClauseEmpty = false;
            } else {
                query.append(" AND ");
            }
            query.append(typeTableAlias).append(".");
            if (Meta.FIELD_SPECIAL_MEDIA_SRC.equals(key)) {
                query.append(DB.COLUMN_API);
            } else if (Meta.FIELD_SPECIAL_MEDIA_ID.equals(key)) {
                query.append(DB.COLUMN_ID);
            } else {
                query.append(DB.COLUMN_VALUE);
            }
            query.append(" = ?");
        }
        if (!whereClauseEmpty) {
            query.append(" )");
        }
        // Sort
        query.append("\nORDER BY ");
        switch (sortKey) {
            case Meta.FIELD_SPECIAL_MEDIA_ID:
                query.append(showTypeTableAlias + "." + DB.COLUMN_ID);
                break;
            case Meta.FIELD_SPECIAL_MEDIA_SRC:
                query.append(showTypeTableAlias + "." + DB.COLUMN_API);
                break;
            default:
                query.append(keyToTableAliasMap.get(sortKey) + "." + DB.COLUMN_VALUE);
                break;
        }
        SimpleSQLiteQuery sqlQuery = new SimpleSQLiteQuery(query.toString(), queryArgs.toArray());
        Log.d(LC, "getEntries:"
                + "\nquery: " + sqlQuery.getSql()
                + "\nargs: "
                + String.join(", ", queryArgs.stream()
                .map(Object::toString)
                .collect(Collectors.toList()))
                + "\naliases: "
                + String.join(", ", keyToTableAliasMap.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.toList()))
        );
        return Transformations.map(metaModel.getEntries(sqlQuery), values ->
                values.stream().map(value -> {
                    switch (showTypeKey) {
                        case Meta.FIELD_SPECIAL_MEDIA_SRC:
                            return new LibraryEntry(new EntryID(
                                    MusicLibraryService.API_ID_DANCINGBUNNIES,
                                    value.api,
                                    showTypeKey
                            ), value.api);
                        case Meta.FIELD_SPECIAL_MEDIA_ID:
                            return new LibraryEntry(new EntryID(
                                    value.api,
                                    value.id,
                                    showTypeKey
                            ), value.id);
                        default:
                            return new LibraryEntry(new EntryID(
                                    MusicLibraryService.API_ID_DANCINGBUNNIES,
                                    value.value,
                                    showTypeKey
                            ), value.value);
                    }
                }).collect(Collectors.toList())
        );
    }

    public CompletableFuture<List<EntryID>> getEntries(EntryID entryID) {
        CompletableFuture<List<EntryID>> ret = new CompletableFuture<>();
        Bundle bundleQuery = new Bundle();
        bundleQuery.putString(entryID.type, entryID.id);
        LiveData<List<LibraryEntry>> liveData = getEntries(
                Meta.FIELD_SPECIAL_MEDIA_ID,
                Meta.FIELD_TITLE,
                bundleQuery
        );
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
        CompletableFuture<List<MetaDouble>> doubleFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getDoubleMeta(entryID.src, entryID.id)
        );
        CompletableFuture[] futures = new CompletableFuture[] {
                stringFuture,
                longFuture,
                doubleFuture
        };
        CompletableFuture<Meta> ret = new CompletableFuture<>();
        CompletableFuture.allOf(futures).thenRunAsync(() -> {
            Meta meta = new Meta(entryID);
            stringFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.addString(v.key, v.value)
            );
            longFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.addLong(v.key, v.value)
            );
            doubleFuture.getNow(Collections.emptyList()).forEach(v ->
                    meta.addDouble(v.key, v.value)
            );
            ret.complete(meta);
        });
        return ret;
    }

    public LiveData<List<String>> getMetaFields() {
        MediatorLiveData<List<String>> allMetaKeys = new MediatorLiveData<>();
        LiveData<List<String>> stringKeys = metaModel.getStringMetaKeys();
        LiveData<List<String>> longKeys = metaModel.getLongMetaKeys();
        LiveData<List<String>> doubleKeys = metaModel.getDoubleMetaKeys();
        List<LiveData<List<String>>> sources = Arrays.asList(stringKeys, longKeys, doubleKeys);
        for (LiveData<List<String>> source: sources) {
            allMetaKeys.addSource(source, keys -> {
                allMetaKeys.setValue(combineMetaKeys(sources));
            });
        }
        return allMetaKeys;
    }

    private List<String> combineMetaKeys(List<LiveData<List<String>>> keySources) {
        List<String> allKeys = new ArrayList<>();
        for (LiveData<List<String>> keySource: keySources) {
            if (keySource != null && keySource.getValue() != null) {
                allKeys.addAll(keySource.getValue());
            }
        }
        return allKeys;
    }
}