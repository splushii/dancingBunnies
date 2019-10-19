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
import java.util.concurrent.ExecutionException;
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
                                                   boolean sortOrderAscending,
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
        if (!showMeta) {
            uniqueQueryKeys.add(Meta.FIELD_TITLE);
        }
        String sortKey = sortField == null ? showTypeKey : sortField;
        uniqueQueryKeys.add(sortKey);

        // Create table aliases
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
            String typeTableAlias = "meta_" + tableAliasIndex++;
            keyToTableAliasMap.put(key, typeTableAlias);
        }

        List<Object> queryArgs = new ArrayList<>();
        StringBuilder query = new StringBuilder("SELECT");
        if (showMeta) {
            query.append(" DISTINCT");
        }
        switch (showTypeKey) {
            case Meta.FIELD_SPECIAL_MEDIA_SRC:
                query.append(String.format(
                        " %s.%s AS %s",
                        showTypeTableAlias,
                        DB.COLUMN_API,
                        DB.COLUMN_API
                ));
                break;
            case Meta.FIELD_SPECIAL_MEDIA_ID:
                query.append(String.format(
                        " %s.%s AS %s",
                        showTypeTableAlias,
                        DB.COLUMN_API,
                        DB.COLUMN_API
                ));
                query.append(String.format(
                        ", %s.%s AS %s",
                        showTypeTableAlias,
                        DB.COLUMN_ID,
                        DB.COLUMN_ID
                ));
                query.append(String.format(
                        ", %s.%s AS %s",
                        keyToTableAliasMap.get(Meta.FIELD_TITLE),
                        DB.COLUMN_VALUE,
                        DB.COLUMN_VALUE
                ));
                break;
            default:
                query.append(String.format(
                        " %s.%s AS %s",
                        showTypeTableAlias,
                        DB.COLUMN_VALUE,
                        DB.COLUMN_VALUE
                ));
                break;
        }
        String sortKeyColumn;
        switch (sortKey) {
            case Meta.FIELD_SPECIAL_MEDIA_SRC:
                sortKeyColumn = DB.COLUMN_API;
                break;
            case Meta.FIELD_SPECIAL_MEDIA_ID:
                sortKeyColumn = DB.COLUMN_ID;
                break;
            default:
                sortKeyColumn = DB.COLUMN_VALUE;
                break;
        }
        query.append(String.format(
                ", %s.%s AS %s",
                keyToTableAliasMap.get(sortKey),
                sortKeyColumn,
                "sort1"
        ));
        query.append(" FROM ").append(showTypeTable).append(" AS ").append(showTypeTableAlias);
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
            String typeTableAlias = keyToTableAliasMap.get(key);
            if (typeTable == null) {
                Log.e(LC, "There is no type table"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            if (typeTableAlias == null) {
                Log.e(LC, "There is no type table alias"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            query.append("\nLEFT JOIN " + typeTable + " AS " + typeTableAlias
                    + " ON ( " + typeTableAlias + "." + DB.COLUMN_API
                    + " = " + showTypeTableAlias + "." + DB.COLUMN_API
                    + " AND " + typeTableAlias + "." + DB.COLUMN_ID
                    + " = " + showTypeTableAlias + "." + DB.COLUMN_ID
                    + " AND " + typeTableAlias + "." + DB.COLUMN_KEY + " = ? )");
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
        addSortToQuery(
                query,
                showTypeTableAlias,
                sortKey,
                keyToTableAliasMap.get(sortKey),
                sortOrderAscending
        );
        query.append(",\n");
        addSortToQuery(
                query,
                showTypeTableAlias,
                showTypeKey,
                keyToTableAliasMap.get(showTypeKey),
                sortOrderAscending
        );
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
        return Transformations.map(
                metaModel.getEntries(sqlQuery),
                values -> values.stream().map(value -> {
                    EntryID entryID;
                    String name;
                    switch (showTypeKey) {
                        case Meta.FIELD_SPECIAL_MEDIA_SRC:
                            entryID = new EntryID(
                                    MusicLibraryService.API_ID_DANCINGBUNNIES,
                                    value.api,
                                    showTypeKey
                            );
                            name = value.api;
                            break;
                        case Meta.FIELD_SPECIAL_MEDIA_ID:
                            entryID = new EntryID(
                                    value.api,
                                    value.id,
                                    showTypeKey
                            );
                            name = value.value;
                            break;
                        default:
                            entryID = new EntryID(
                                    MusicLibraryService.API_ID_DANCINGBUNNIES,
                                    value.value,
                                    showTypeKey
                            );
                            name = value.value;
                            break;
                    }
                    return new LibraryEntry(entryID, name, value.sort1);
                }).collect(Collectors.toList())
        );
    }

    private void addSortToQuery(StringBuilder query,
                                String showTypeTableAlias,
                                String key,
                                String keyTable,
                                boolean sortOrderAscending) {
        switch (key) {
            case Meta.FIELD_SPECIAL_MEDIA_ID:
                query.append(showTypeTableAlias).append(".").append(DB.COLUMN_ID);
                break;
            case Meta.FIELD_SPECIAL_MEDIA_SRC:
                query.append(showTypeTableAlias).append(".").append(DB.COLUMN_API);
                break;
            default:
                query.append(keyTable).append(".").append(DB.COLUMN_VALUE);
                break;
        }
        if (Meta.getType(key).equals(Meta.Type.STRING)) {
            query.append(" COLLATE NOCASE");
        }
        query.append(sortOrderAscending ? " ASC" : " DESC");
    }

    public LiveData<Integer> getNumSongEntries(List<EntryID> entryIDs, Bundle query) {
        if (query == null) {
            query = new Bundle();
        }
        MediatorLiveData<Integer> totalEntriesMediator = new MediatorLiveData<>();
        List<LiveData<Integer>> numSongEntryLiveDataList = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            numSongEntryLiveDataList.add(getNumSongEntries(entryID, query));
        }
        for (LiveData<Integer> numSongEntryLiveData: numSongEntryLiveDataList) {
            totalEntriesMediator.addSource(
                    numSongEntryLiveData,
                    e -> totalEntriesMediator.setValue(combineNumSongEntries(numSongEntryLiveDataList))
            );
        }
        return totalEntriesMediator;
    }

    private Integer combineNumSongEntries(List<LiveData<Integer>> numSongEntryLiveDataList) {
        int total = 0;
        for (LiveData<Integer> numSongEntryLiveData: numSongEntryLiveDataList) {
            if (numSongEntryLiveData != null && numSongEntryLiveData.getValue() != null) {
                total += numSongEntryLiveData.getValue();
            }
        }
        return total;
    }

    private LiveData<Integer> getNumSongEntries(EntryID entryID, Bundle bundleQuery) {
        String baseTableKey;
        String baseTableValue;
        String baseTableAlias = "base_table";
        String baseQuery = "";
        if (entryID == null
                || entryID.isUnknown()
                || Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)
                || Meta.FIELD_SPECIAL_MEDIA_SRC.equals(entryID.type)) {
            baseTableKey = Meta.FIELD_SPECIAL_MEDIA_ID;
            baseTableValue = "";
        } else {
            baseTableKey = entryID.type;
            baseTableValue = entryID.id;
            baseQuery = " " + baseTableAlias + "." + DB.COLUMN_KEY + " = ?"
                    + " AND " + baseTableAlias + "." + DB.COLUMN_VALUE + " = ?";
        }
        String baseTable = MetaDao.getTable(baseTableKey);
        HashMap<String, String> keyToTableAliasMap = new HashMap<>();
        keyToTableAliasMap.put(baseTableKey, baseTableAlias);
        // Other types (keys) which needs to be joined for the query
        HashSet<String> uniqueQueryKeys = new HashSet<>(bundleQuery.keySet());
        List<Object> queryArgs = new ArrayList<>();
        StringBuilder query = new StringBuilder("SELECT");
        query.append(" COUNT(*)");
        query.append(" FROM ").append(baseTable).append(" AS ").append(baseTableAlias);
        int tableAliasIndex = 1;
        for (String key : uniqueQueryKeys) {
            if (baseTableKey.equals(key)) {
                // Already handled
                continue;
            }
            if (Meta.FIELD_SPECIAL_MEDIA_SRC.equals(key)
                    || Meta.FIELD_SPECIAL_MEDIA_ID.equals(key)) {
                // These keys are present in every meta table
                keyToTableAliasMap.put(key, baseTableAlias);
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
                    + " ON ( " + typeTableAlias + "." + DB.COLUMN_API
                    + " = " + baseTableAlias + "." + DB.COLUMN_API
                    + " AND " + typeTableAlias + "." + DB.COLUMN_ID
                    + " = " + baseTableAlias + "." + DB.COLUMN_ID
                    + " AND " + typeTableAlias + "." + DB.COLUMN_KEY + " = ? )");
            queryArgs.add(key);
        }
        boolean whereClauseEmpty = true;
        // Add query for base table meta
        if (!baseQuery.isEmpty()) {
            query.append("\nWHERE (");
            query.append(baseQuery);
            queryArgs.add(baseTableKey);
            queryArgs.add(baseTableValue);
            whereClauseEmpty = false;
        }
        // Add user query
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
                query.append("\nWHERE (");
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
        SimpleSQLiteQuery sqlQuery = new SimpleSQLiteQuery(query.toString(), queryArgs.toArray());
//        Log.d(LC, "getNumSongEntries:"
//                + "\nquery: " + sqlQuery.getSql()
//                + "\nargs: "
//                + String.join(", ", queryArgs.stream()
//                .map(Object::toString)
//                .collect(Collectors.toList()))
//                + "\naliases: "
//                + String.join(", ", keyToTableAliasMap.entrySet().stream()
//                .map(e -> e.getKey() + ": " + e.getValue())
//                .collect(Collectors.toList()))
//        );
        return metaModel.getNumEntries(sqlQuery);
    }

    public LiveData<List<EntryID>> getSongEntries(List<EntryID> entryIDs, Bundle query) {
        MediatorLiveData<List<EntryID>> allEntriesMediator = new MediatorLiveData<>();
        List<LiveData<List<EntryID>>> songEntryLiveDataList = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            songEntryLiveDataList.add(getSongEntries(entryID, query));
        }
        for (LiveData<List<EntryID>> songEntryLiveData: songEntryLiveDataList) {
            allEntriesMediator.addSource(
                    songEntryLiveData,
                    e -> allEntriesMediator.setValue(combineSongEntries(songEntryLiveDataList))
            );
        }
        return allEntriesMediator;
    }

    private List<EntryID> combineSongEntries(List<LiveData<List<EntryID>>> songEntryLiveDataList) {
        List<EntryID> allEntries = new ArrayList<>();
        for (LiveData<List<EntryID>> songEntryLiveData: songEntryLiveDataList) {
            if (songEntryLiveData != null && songEntryLiveData.getValue() != null) {
                allEntries.addAll(songEntryLiveData.getValue());
            }
        }
        return allEntries;
    }

    private LiveData<List<EntryID>> getSongEntries(EntryID entryID, Bundle query) {
        Bundle b = new Bundle();
        b.putAll(query);
        if (!entryID.isUnknown()) {
            b.putString(entryID.type, entryID.id);
        }
        return Transformations.map(
                getEntries(Meta.FIELD_SPECIAL_MEDIA_ID, Meta.FIELD_TITLE, true, b),
                libraryEntries -> libraryEntries.stream()
                        .map(libraryEntry -> libraryEntry.entryID)
                        .collect(Collectors.toList())
        );
    }

    public CompletableFuture<List<EntryID>> getSongEntriesOnce(List<EntryID> entryIDs, Bundle query) {
        List<CompletableFuture<List<EntryID>>> futureEntryLists = new ArrayList<>();
//        Log.d(LC, "getSongEntriesOnce for " + entryIDs.size() + " entryIDs, query: " + query);
        for (EntryID entryID : entryIDs) {
            CompletableFuture<List<EntryID>> songEntries;
            songEntries = getSongEntriesOnce(entryID, query);
            futureEntryLists.add(songEntries);
        }
        return CompletableFuture.allOf(futureEntryLists.toArray(new CompletableFuture[0]))
                .thenApply(aVoid -> futureEntryLists.stream()
                        .map(futureEntryList -> {
                            try {
                                return futureEntryList.get();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            return Collections.<EntryID>emptyList();
                        })
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<List<EntryID>> getSongEntriesOnce(EntryID entryID, Bundle query) {
        CompletableFuture<List<EntryID>> ret = new CompletableFuture<>();
        Bundle b = new Bundle();
        if (query != null) {
            b.putAll(query);
        }
        if (!entryID.isUnknown()) {
            b.putString(entryID.type, entryID.id);
        }
        LiveData<List<LibraryEntry>> liveData = getEntries(
                Meta.FIELD_SPECIAL_MEDIA_ID,
                Meta.FIELD_TITLE,
                true,
                b
        );
        liveData.observeForever(new Observer<List<LibraryEntry>>() {
            @Override
            public void onChanged(List<LibraryEntry> libraryEntries) {
                liveData.removeObserver(this);
                ret.complete(libraryEntries.stream()
                        .map(libraryEntry -> libraryEntry.entryID)
                        .collect(Collectors.toList()));
            }
        });
        return ret;
    }

    public void clearAll(String src) {
        metaModel.deleteWhereSourceIs(src);
    }

    public CompletableFuture<Meta> getMetaOnce(EntryID entryID) {
        CompletableFuture<List<MetaString>> stringFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getStringMetaSync(entryID.src, entryID.id)
        );
        CompletableFuture<List<MetaLong>> longFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getLongMetaSync(entryID.src, entryID.id)
        );
        CompletableFuture<List<MetaDouble>> doubleFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getDoubleMetaSync(entryID.src, entryID.id)
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

    public LiveData<Meta> getMeta(LiveData<EntryID> entryIDLiveData) {
        return Transformations.switchMap(entryIDLiveData, this::getMeta);
    }

    public LiveData<Meta> getMeta(EntryID entryID) {
        LiveData<List<MetaString>> metaStrings = metaModel.getStringMeta(entryID.src, entryID.id);
        LiveData<List<MetaLong>> metaLongs = metaModel.getLongMeta(entryID.src, entryID.id);
        LiveData<List<MetaDouble>> metaDoubles = metaModel.getDoubleMeta(entryID.src, entryID.id);
        MediatorLiveData<Meta> meta = new MediatorLiveData<>();
        meta.addSource(metaStrings, strings -> combineMetaValues(entryID, meta, metaStrings, metaLongs, metaDoubles));
        meta.addSource(metaLongs, longs -> combineMetaValues(entryID, meta, metaStrings, metaLongs, metaDoubles));
        meta.addSource(metaDoubles, doubles -> combineMetaValues(entryID, meta, metaStrings, metaLongs, metaDoubles));
        return meta;
    }

    public LiveData<List<String>> getMetaString(EntryID entryID, String key) {
        return Transformations.map(
                metaModel.getStringMeta(entryID.src, entryID.id, key),
                metaStrings -> metaStrings.stream()
                        .map(metaString -> metaString.value)
                        .collect(Collectors.toList())
        );
    }

    private void combineMetaValues(EntryID entryID,
                                   MediatorLiveData<Meta> metaMediator,
                                   LiveData<List<MetaString>> metaStringsLiveData,
                                   LiveData<List<MetaLong>> metaLongsLiveData,
                                   LiveData<List<MetaDouble>> metaDoublesLiveData) {
        List<MetaString> strings = metaStringsLiveData.getValue();
        List<MetaLong> longs = metaLongsLiveData.getValue();
        List<MetaDouble> doubles = metaDoublesLiveData.getValue();
        if (strings == null || longs == null || doubles == null) {
            return;
        }
        Meta meta = new Meta(entryID);
        for (MetaString m: strings) {
            meta.addString(m.key, m.value);
        }
        for (MetaLong m: longs) {
            meta.addLong(m.key, m.value);
        }
        for (MetaDouble m: doubles) {
            meta.addDouble(m.key, m.value);
        }
        metaMediator.setValue(meta);
    }

    public LiveData<List<String>> getMetaFields() {
        MediatorLiveData<List<String>> allMetaKeys = new MediatorLiveData<>();
        LiveData<List<String>> stringKeys = metaModel.getStringMetaKeys();
        LiveData<List<String>> longKeys = metaModel.getLongMetaKeys();
        LiveData<List<String>> doubleKeys = metaModel.getDoubleMetaKeys();
        List<LiveData<List<String>>> sources = Arrays.asList(stringKeys, longKeys, doubleKeys);
        for (LiveData<List<String>> source: sources) {
            allMetaKeys.addSource(source, keys ->
                    allMetaKeys.setValue(combineMetaKeys(sources))
            );
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

    public CompletableFuture<Void> insertLocalMeta(EntryID entryID, String field, String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.insertLocalMeta(entryID, field, value, false)
        );
    }

    public CompletableFuture<Void> deleteLocalMeta(EntryID entryID, String field, String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.deleteLocalMeta(entryID, field, value, false)
        );
    }

    public CompletableFuture<Void> insertLocalUserMeta(EntryID entryID, String field, String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.insertLocalMeta(entryID, field, value, true)
        );
    }

    public CompletableFuture<Void> replaceLocalUserMeta(EntryID entryID,
                                                        String field,
                                                        String oldValue,
                                                        String newValue) {
        return CompletableFuture.runAsync(() ->
                metaModel.replaceLocalMeta(entryID, field, oldValue, newValue, true)
        );
    }

    public CompletableFuture<Void> deleteLocalUserMeta(EntryID entryID, String field, String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.deleteLocalMeta(entryID, field, value, true)
        );
    }
}