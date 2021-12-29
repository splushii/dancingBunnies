package se.splushii.dancingbunnies.storage;

import android.content.Context;
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

import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.sqlite.db.SimpleSQLiteQuery;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.QueryEntry;
import se.splushii.dancingbunnies.musiclibrary.QueryLeaf;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.musiclibrary.QueryTree;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.MetaDao;
import se.splushii.dancingbunnies.storage.db.MetaDouble;
import se.splushii.dancingbunnies.storage.db.MetaLong;
import se.splushii.dancingbunnies.storage.db.MetaString;
import se.splushii.dancingbunnies.storage.db.MetaValueEntry;
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

    public CompletableFuture<Void> insertTracksAndMetas(List<Meta> metaList,
                                                        boolean allowLocalKeys,
                                                        Consumer<String> progressHandler) {
        return insertEntriesAndMetas(EntryID.TYPE_TRACK, metaList, allowLocalKeys, progressHandler);
    }

    public CompletableFuture<Void> addPlaylists(List<EntryID> playlistIDs,
                                                List<String> names,
                                                List<String> queries) {
        List<Meta> metas = new ArrayList<>();
        for (int i = 0; i < playlistIDs.size() && i < names.size() && i < queries.size(); i++) {
            Meta meta = new Meta(playlistIDs.get(i));
            meta.addString(Meta.FIELD_TITLE, names.get(i));
            String query = queries.get(i);
            if (query != null) {
                meta.addString(Meta.FIELD_QUERY, query);
            }
            metas.add(meta);
        }
        return insertPlaylistsAndMetas(metas, false, null);
    }

    public CompletableFuture<Void> addPlaylist(EntryID playlistID,
                                               String name,
                                               String query) {
        Meta meta = new Meta(playlistID);
        meta.addString(Meta.FIELD_TITLE, name);
        if (query != null) {
            meta.addString(Meta.FIELD_QUERY, query);
        }
        return insertPlaylistsAndMetas(
                Collections.singletonList(meta),
                false,
                null
        );
    }

    public CompletableFuture<Void> insertPlaylistsAndMetas(List<Meta> metaList,
                                                           boolean allowLocalKeys,
                                                           Consumer<String> progressHandler) {
        return insertEntriesAndMetas(
                EntryID.TYPE_PLAYLIST,
                metaList,
                allowLocalKeys,
                progressHandler
        );
    }

    private CompletableFuture<Void> insertEntriesAndMetas(String entryType,
                                                          List<Meta> metaList,
                                                          boolean allowLocalKeys,
                                                          Consumer<String> progressHandler) {
        return CompletableFuture.runAsync(() ->
                metaModel.insertEntriesAndMetas(
                        entryType,
                        metaList,
                        allowLocalKeys,
                        progressHandler
                )
        );
    }
    
    public void replaceAllTracksAndMetasFromSource(String src,
                                                   List<Meta> metaList,
                                                   boolean allowLocalKeys,
                                                   Consumer<String> progressHandler) {
        replaceAllEntriesAndMetasFromSource(
                EntryID.TYPE_TRACK,
                src,
                metaList,
                allowLocalKeys,
                progressHandler
        );
    }

    public void replaceAllPlaylistsAndMetasFromSource(String src,
                                                      List<Meta> metaList,
                                                      boolean allowLocalKeys,
                                                      Consumer<String> progressHandler) {
        replaceAllEntriesAndMetasFromSource(
                EntryID.TYPE_PLAYLIST,
                src,
                metaList,
                allowLocalKeys,
                progressHandler
        );
    }

    private void replaceAllEntriesAndMetasFromSource(String entryType,
                                                     String src,
                                                     List<Meta> metaList,
                                                     boolean allowLocalKeys,
                                                     Consumer<String> progressHandler) {
        long start = System.currentTimeMillis();
        Log.d(LC, "replaceEntryMetasWith (type: " + entryType + ") start");
        metaModel.replaceAllEntriesAndMetasFromSource(
                entryType,
                src,
                metaList,
                allowLocalKeys,
                progressHandler
        );
        Log.d(LC, "replacePlaylistMetasWith (type: " + entryType + ")"
                + " finish. " + (System.currentTimeMillis() - start) + "ms");
    }

    public CompletableFuture<List<QueryEntry>> getQueryEntriesOnce(String entryType,
                                                                   String primaryField,
                                                                   List<String> sortFields,
                                                                   boolean sortOrderAscending,
                                                                   QueryNode queryNode,
                                                                   boolean debug) {
        SimpleSQLiteQuery sqlQuery = getQueryEntriesSQLQuery(
                entryType,
                primaryField,
                sortFields,
                sortOrderAscending,
                queryNode,
                debug
        );
        if (sqlQuery == null) {
            return Util.futureResult(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> metaModel.getEntriesOnce(entryType, sqlQuery))
                .thenApply(v -> getQueryEntriesMetaValueEntriesToMeta(entryType, primaryField, v));
    }

    public LiveData<List<QueryEntry>> getQueryEntries(String entryType,
                                                      String primaryField,
                                                      List<String> sortFields,
                                                      boolean sortOrderAscending,
                                                      QueryNode queryNode,
                                                      boolean debug) {
        SimpleSQLiteQuery sqlQuery = getQueryEntriesSQLQuery(
                entryType,
                primaryField,
                sortFields,
                sortOrderAscending,
                queryNode,
                debug
        );
        if (sqlQuery == null) {
            MutableLiveData<List<QueryEntry>> entries = new MutableLiveData<>();
            entries.setValue(Collections.emptyList());
            return entries;
        }
        return Transformations.map(
                metaModel.getEntries(entryType, sqlQuery),
                v -> getQueryEntriesMetaValueEntriesToMeta(entryType, primaryField, v)
        );
    }

    private String getQueryEntriesSQLQueryPrimaryTypeKey(String entryType, String primaryField) {
        if (primaryField != null) {
            return primaryField;
        }
        switch (entryType) {
            default:
            case EntryID.TYPE_TRACK:
                return Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
            case EntryID.TYPE_PLAYLIST:
                return Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST;
        }
    }

    private SimpleSQLiteQuery getQueryEntriesSQLQuery(String entryType,
                                                      String primaryField,
                                                      List<String> sortFields,
                                                      boolean sortOrderAscending,
                                                      QueryNode queryNode,
                                                      boolean debug) {
        if (queryNode == null) {
            queryNode = new QueryTree(QueryTree.Op.AND, false);
        }
        HashMap<String, String> keyToTableAliasMap = new HashMap<>();
        HashSet<String> uniqueQueryKeys = queryNode.getKeys();
        String primaryTypeKey = getQueryEntriesSQLQueryPrimaryTypeKey(entryType, primaryField);
        String primaryTypeTable = MetaDao.getTable(entryType, primaryTypeKey);
        String primaryTypeTableAlias = "meta_primary";
        keyToTableAliasMap.put(primaryTypeKey, primaryTypeTableAlias);
        if (primaryTypeTable == null) {
            return null;
        }
        boolean showMeta = !Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(primaryTypeKey)
                && !Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(primaryTypeKey);
        // Other types (keys) which needs to be joined for the query
        if (!showMeta) {
            uniqueQueryKeys.add(Meta.FIELD_TITLE);
        }
        List<String> sortKeys = new ArrayList<>();
        if (sortFields != null && !sortFields.isEmpty()) {
            for (String sortField: sortFields) {
                if (sortField != null) {
                    if (!sortKeys.contains(sortField)) {
                        sortKeys.add(sortField);
                    }
                    uniqueQueryKeys.add(sortField);
                }
            }

        }
        if (sortKeys.isEmpty() || !sortKeys.contains(primaryTypeKey)) {
            sortKeys.add(primaryTypeKey);
        }

        // Create table aliases
        int tableAliasIndex = 1;
        for (String key: uniqueQueryKeys) {
            if (primaryTypeKey.equals(key)) {
                // Already handled
                continue;
            }
            if (Meta.FIELD_SPECIAL_ENTRY_SRC.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(key)) {
                // These keys are present in every meta table
                keyToTableAliasMap.put(key, primaryTypeTableAlias);
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
        switch (primaryTypeKey) {
            case Meta.FIELD_SPECIAL_ENTRY_SRC:
                query.append(String.format(
                        " %s.%s AS %s",
                        primaryTypeTableAlias,
                        DB.COLUMN_SRC,
                        DB.COLUMN_SRC
                ));
                break;
            case Meta.FIELD_SPECIAL_ENTRY_ID_TRACK:
            case Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST:
                query.append(String.format(
                        " %s.%s AS %s",
                        primaryTypeTableAlias,
                        DB.COLUMN_SRC,
                        DB.COLUMN_SRC
                ));
                query.append(String.format(
                        ", %s.%s AS %s",
                        primaryTypeTableAlias,
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
                        primaryTypeTableAlias,
                        DB.COLUMN_VALUE,
                        DB.COLUMN_VALUE
                ));
                break;
        }
        // Fetch columns with extra values ("extra1", "extra2", ...) as defined in MetaValueEntry
        for (int i = 0; i < sortKeys.size() && i < MetaValueEntry.NUM_MAX_EXTRA_VALUES; i++) {
            String sortKey = sortKeys.get(i);
            String sortKeyColumn;
            switch (sortKey) {
                case Meta.FIELD_SPECIAL_ENTRY_SRC:
                    sortKeyColumn = DB.COLUMN_SRC;
                    break;
                case Meta.FIELD_SPECIAL_ENTRY_ID_TRACK:
                case Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST:
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
                    "extra" + (i + 1)
            ));
        }
        query.append(" FROM ").append(primaryTypeTable).append(" AS ").append(primaryTypeTableAlias);
        for (String key: uniqueQueryKeys) {
            if (primaryTypeKey.equals(key)) {
                // Already handled
                continue;
            }
            if (Meta.FIELD_SPECIAL_ENTRY_SRC.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(key)) {
                // These keys are present in every meta table
                keyToTableAliasMap.put(key, primaryTypeTableAlias);
                continue;
            }
            String typeTable = MetaDao.getTable(entryType, key);
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
                    + " ON ( " + typeTableAlias + "." + DB.COLUMN_SRC
                    + " = " + primaryTypeTableAlias + "." + DB.COLUMN_SRC
                    + " AND " + typeTableAlias + "." + DB.COLUMN_ID
                    + " = " + primaryTypeTableAlias + "." + DB.COLUMN_ID
                    + " AND " + typeTableAlias + "." + DB.COLUMN_KEY + " = ? )");
            queryArgs.add(key);
        }
        // Add showType filter
        if (!Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(primaryTypeKey)
                && !Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(primaryTypeKey)
                && !Meta.FIELD_SPECIAL_ENTRY_SRC.equals(primaryTypeKey)) {
            query.append("\nWHERE " + primaryTypeTableAlias + "." + DB.COLUMN_KEY + " = ?");
            queryArgs.add(primaryTypeKey);
        }
        // Add user query
        addQueryNodeToQuery(
                query,
                queryArgs,
                keyToTableAliasMap,
                showMeta ? " AND" : "\nWHERE",
                queryNode
        );
        // Sort
        for (int i = 0; i < sortKeys.size(); i++) {
            if (i == 0) {
                query.append("\nORDER BY ");
            } else {
                query.append(",\n");
            }
            String sortKey = sortKeys.get(i);
            addSortToQuery(
                    query,
                    primaryTypeTableAlias,
                    sortKey,
                    keyToTableAliasMap.get(sortKey),
                    sortOrderAscending
            );
        }
        SimpleSQLiteQuery sqlQuery = new SimpleSQLiteQuery(query.toString(), queryArgs.toArray());
        if (debug) {
            Log.d(LC, "getEntries:"
                    + "\nquery: " + sqlQuery.getSql()
                    + "\nargs: "
                    + queryArgs.stream()
                    .map(v -> v == null ? null : v.toString())
                    .collect(Collectors.joining(", "))
                    + "\naliases: "
                    + keyToTableAliasMap.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "))
            );
        }
        return sqlQuery;
    }

    private List<QueryEntry> getQueryEntriesMetaValueEntriesToMeta(
            String entryType,
            String primaryField,
            List<MetaValueEntry> metaValueEntries
    ) {
        String primaryTypeKey = getQueryEntriesSQLQueryPrimaryTypeKey(entryType, primaryField);
        return metaValueEntries.stream().map(value -> {
            EntryID entryID;
            String name;
            switch (primaryTypeKey) {
                case Meta.FIELD_SPECIAL_ENTRY_SRC:
                    entryID = new EntryID(
                            MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                            value.src,
                            primaryTypeKey
                    );
                    name = value.src;
                    break;
                case Meta.FIELD_SPECIAL_ENTRY_ID_TRACK:
                case Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST:
                    entryID = new EntryID(
                            value.src,
                            value.id,
                            primaryTypeKey
                    );
                    name = value.value;
                    break;
                default:
                    entryID = new EntryID(
                            MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                            value.value,
                            primaryTypeKey
                    );
                    name = value.value;
                    break;
            }
            return new QueryEntry(
                    entryID,
                    name,
                    new ArrayList<>(Arrays.asList(
                            value.extra1,
                            value.extra2,
                            value.extra3,
                            value.extra4,
                            value.extra5
                    ))
            );
        }).collect(Collectors.toList());
    }

    private boolean addQueryNodeToQuery(StringBuilder query,
                                        List<Object> queryArgs,
                                        HashMap<String, String> keyToTableAliasMap,
                                        String prefix,
                                        QueryNode queryNode) {
        StringBuilder tmpQuery = new StringBuilder(prefix);
        List<Object> tmpQueryArgs = new ArrayList<>();
        boolean whereClauseEmpty = true;
        if (queryNode instanceof QueryLeaf) {
            QueryLeaf leaf = (QueryLeaf) queryNode;
            addQueryLeafToQuery(tmpQuery, tmpQueryArgs, keyToTableAliasMap, " (", leaf);
            whereClauseEmpty = false;
        } else if (queryNode instanceof QueryTree) {
            QueryTree tree = (QueryTree) queryNode;
            for (QueryNode node: tree) {
                if (node == null) {
                    continue;
                }
                String operatorString;
                switch (tree.getOperator()) {
                    default:
                    case AND:
                        operatorString = "AND";
                        break;
                    case OR:
                        operatorString = "OR";
                        break;
                }
                String negateString = tree.isNegated() ? " NOT" : "";
                if (node instanceof QueryTree) {
                    String treeQueryPrefix;
                    if (whereClauseEmpty) {
                        treeQueryPrefix = negateString + " (";
                    } else {
                        treeQueryPrefix = " " + operatorString;
                    }
                    if (addQueryNodeToQuery(
                            tmpQuery,
                            tmpQueryArgs,
                            keyToTableAliasMap,
                            treeQueryPrefix,
                            node
                    )) {
                        whereClauseEmpty = false;
                    }
                    continue;
                }
                QueryLeaf leaf = (QueryLeaf) node;
                String leafQueryPrefix;
                if (whereClauseEmpty) {
                    leafQueryPrefix = negateString + " (";
                } else {
                    leafQueryPrefix = " " + operatorString;
                }
                if (addQueryLeafToQuery(
                        tmpQuery,
                        tmpQueryArgs,
                        keyToTableAliasMap,
                        leafQueryPrefix,
                        leaf
                )) {
                    whereClauseEmpty = false;
                }
            }
        }
        if (!whereClauseEmpty) {
            tmpQuery.append(" )");
            query.append(tmpQuery);
            queryArgs.addAll(tmpQueryArgs);
        }
        return !whereClauseEmpty;
    }

    private boolean addQueryLeafToQuery(
            StringBuilder query,
            List<Object> queryArgs,
            HashMap<String, String> keyToTableAliasMap,
            String prefix,
            QueryLeaf leaf
    ) {
        String key = leaf.getKey();
        String value = leaf.getValue();
        String sqlOp = leaf.getSQLOp();
        boolean negated = leaf.isNegated();
        String typeTableAlias = keyToTableAliasMap.get(key);
        if (typeTableAlias == null) {
            Log.e(LC, "There is no type table"
                    + " for bundleQuery key \"" + key + "\""
                    + " with type " + Meta.getType(key));
            return false;
        }
        if (sqlOp == null) {
            Log.e(LC, "op is null for key: " + key);
            return false;
        }
        switch (Meta.getType(key)) {
            case LONG:
                queryArgs.add(Long.parseLong(value));
                break;
            case STRING:
                queryArgs.add(value);
                break;
            case DOUBLE:
                queryArgs.add(Double.parseDouble(value));
                break;
            default:
                Log.e(LC, "Unsupported bundleQuery type: " + Meta.getType(key).name());
                return false;
        }
        query.append(prefix);
        if (negated) {
            query.append(" NOT");
        }
        query.append(" ").append(typeTableAlias).append(".");
        if (Meta.FIELD_SPECIAL_ENTRY_SRC.equals(key)) {
            query.append(DB.COLUMN_SRC);
        } else if (Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(key)
                || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(key)) {
            query.append(DB.COLUMN_ID);
        } else {
            query.append(DB.COLUMN_VALUE);
        }
        query.append(" ").append(sqlOp).append(" ?");
        return true;
    }

    private void addSortToQuery(StringBuilder query,
                                String showTypeTableAlias,
                                String key,
                                String keyTable,
                                boolean sortOrderAscending) {
        switch (key) {
            case Meta.FIELD_SPECIAL_ENTRY_ID_TRACK:
            case Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST:
                query.append(showTypeTableAlias).append(".").append(DB.COLUMN_ID);
                break;
            case Meta.FIELD_SPECIAL_ENTRY_SRC:
                query.append(showTypeTableAlias).append(".").append(DB.COLUMN_SRC);
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

    public LiveData<Integer> getNumTracksSum(List<EntryID> entryIDs, QueryNode queryNode) {
        MediatorLiveData<Integer> totalEntriesMediator = new MediatorLiveData<>();
        List<LiveData<Integer>> numSongEntryLiveDataList = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            numSongEntryLiveDataList.add(getNumEntries(
                    EntryID.TYPE_TRACK,
                    entryID,
                    queryNode,
                    false
            ));
        }
        for (LiveData<Integer> numSongEntryLiveData: numSongEntryLiveDataList) {
            totalEntriesMediator.addSource(
                    numSongEntryLiveData,
                    e -> totalEntriesMediator.setValue(combineNumTracks(numSongEntryLiveDataList))
            );
        }
        return totalEntriesMediator;
    }

    private Integer combineNumTracks(List<LiveData<Integer>> numSongEntryLiveDataList) {
        int total = 0;
        for (LiveData<Integer> numSongEntryLiveData: numSongEntryLiveDataList) {
            if (numSongEntryLiveData != null && numSongEntryLiveData.getValue() != null) {
                total += numSongEntryLiveData.getValue();
            }
        }
        return total;
    }

    public LiveData<Integer> getNumTracks() {
        return metaModel.getNumEntries(EntryID.TYPE_TRACK);
    }

    public LiveData<Integer> getNumTracks(String src) {
        return metaModel.getNumEntries(EntryID.TYPE_TRACK, src);
    }

    public LiveData<Integer> getNumTracks(EntryID entryID,
                                          QueryNode queryNode,
                                          boolean debug) {
        return getNumEntries(EntryID.TYPE_TRACK, entryID, queryNode, debug);
    }

    public LiveData<Integer> getNumPlaylists() {
        return metaModel.getNumEntries(EntryID.TYPE_PLAYLIST);
    }

    public LiveData<Integer> getNumPlaylists(String src) {
        return metaModel.getNumEntries(EntryID.TYPE_PLAYLIST, src);
    }

    public LiveData<Integer> getNumPlaylists(EntryID entryID,
                                             QueryNode queryNode,
                                             boolean debug) {
        return getNumEntries(EntryID.TYPE_PLAYLIST, entryID, queryNode, debug);
    }

    private LiveData<Integer> getNumEntries(String entryType,
                                            EntryID entryID,
                                            QueryNode queryNode,
                                            boolean debug) {
        String baseTableKey;
        String baseTableAlias = "base_table";
        String baseWhereQuery = "";
        List<Object> baseWhereQueryArgs = new ArrayList<>();
        if (entryID == null || entryID.isUnknown()) {
            switch (entryType) {
                default:
                case EntryID.TYPE_TRACK:
                    baseTableKey = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
                    break;
                case EntryID.TYPE_PLAYLIST:
                    baseTableKey = Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST;
                    break;
            }
        } else if (Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(entryID.type)
                || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(entryID.type)) {
            baseTableKey = entryID.type;
        } else if (Meta.FIELD_SPECIAL_ENTRY_SRC.equals(entryID.type)) {
            switch (entryType) {
                default:
                case EntryID.TYPE_TRACK:
                    baseTableKey = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
                    break;
                case EntryID.TYPE_PLAYLIST:
                    baseTableKey = Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST;
                    break;
            }
            baseWhereQuery = " " + baseTableAlias + "." + DB.COLUMN_SRC + " = ?";
            baseWhereQueryArgs.add(entryID.id); // src
        } else {
            baseTableKey = entryID.type;
            baseWhereQuery = " " + baseTableAlias + "." + DB.COLUMN_KEY + " = ?"
                    + " AND " + baseTableAlias + "." + DB.COLUMN_VALUE + " = ?";
            baseWhereQueryArgs.add(baseTableKey);
            baseWhereQueryArgs.add(entryID.id); // value
        }
        String baseTable = MetaDao.getTable(entryType, baseTableKey);
        HashMap<String, String> keyToTableAliasMap = new HashMap<>();
        keyToTableAliasMap.put(baseTableKey, baseTableAlias);
        // Other types (keys) which needs to be joined for the query
        HashSet<String> uniqueQueryKeys = queryNode.getKeys();
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
            if (Meta.FIELD_SPECIAL_ENTRY_SRC.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(key)) {
                // These keys are present in every meta table
                keyToTableAliasMap.put(key, baseTableAlias);
                continue;
            }
            String typeTable = MetaDao.getTable(entryType, key);
            String typeTableAlias = "meta_" + tableAliasIndex++;
            keyToTableAliasMap.put(key, typeTableAlias);
            if (typeTable == null) {
                Log.e(LC, "There is no type table"
                        + " for bundleQuery key \"" + key + "\""
                        + " with type " + Meta.getType(key));
                continue;
            }
            query.append("\nLEFT JOIN " + typeTable + " AS " + typeTableAlias
                    + " ON ( " + typeTableAlias + "." + DB.COLUMN_SRC
                    + " = " + baseTableAlias + "." + DB.COLUMN_SRC
                    + " AND " + typeTableAlias + "." + DB.COLUMN_ID
                    + " = " + baseTableAlias + "." + DB.COLUMN_ID
                    + " AND " + typeTableAlias + "." + DB.COLUMN_KEY + " = ? )");
            queryArgs.add(key);
        }
        boolean whereClauseEmpty = true;
        if (!baseWhereQuery.isEmpty()) {
            query.append("\nWHERE (");
            query.append(baseWhereQuery);
            queryArgs.addAll(baseWhereQueryArgs);
            whereClauseEmpty = false;
        }
        // Add user query
        addQueryNodeToQuery(
                query,
                queryArgs,
                keyToTableAliasMap,
                whereClauseEmpty ? "\nWHERE" : " AND",
                queryNode
        );
        if (!baseWhereQuery.isEmpty()) {
            query.append(" )");
        }
        SimpleSQLiteQuery sqlQuery = new SimpleSQLiteQuery(query.toString(), queryArgs.toArray());
        if (debug) {
            Log.d(LC, "getNumSongEntries:"
                    + "\nquery: " + sqlQuery.getSql()
                    + "\nargs: "
                    + queryArgs.stream()
                    .map(v -> v == null ? null : v.toString())
                    .collect(Collectors.joining(", "))
                    + "\naliases: "
                    + keyToTableAliasMap.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "))
            );
        }
        return metaModel.getNumEntries(entryType, sqlQuery);
    }

    public LiveData<List<EntryID>> getTracksSum(List<EntryID> entryIDs,
                                                QueryNode queryNode) {
        MediatorLiveData<List<EntryID>> allEntriesMediator = new MediatorLiveData<>();
        List<LiveData<List<EntryID>>> tracksLiveDataList = new ArrayList<>();
        for (EntryID entryID: entryIDs) {
            tracksLiveDataList.add(getTracks(entryID, queryNode));
        }
        for (LiveData<List<EntryID>> tracksLiveData: tracksLiveDataList) {
            allEntriesMediator.addSource(
                    tracksLiveData,
                    e -> allEntriesMediator.setValue(combineEntries(tracksLiveDataList))
            );
        }
        return allEntriesMediator;
    }

    private List<EntryID> combineEntries(List<LiveData<List<EntryID>>> songEntryLiveDataList) {
        List<EntryID> allEntries = new ArrayList<>();
        for (LiveData<List<EntryID>> songEntryLiveData: songEntryLiveDataList) {
            if (songEntryLiveData != null && songEntryLiveData.getValue() != null) {
                allEntries.addAll(songEntryLiveData.getValue());
            }
        }
        return allEntries;
    }

    public LiveData<List<EntryID>> getPlaylists() {
        return getEntries(EntryID.TYPE_PLAYLIST, Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST);
    }

    public LiveData<List<EntryID>> getPlaylists(EntryID entryID,
                                                QueryNode queryNode) {
        return getPlaylists(queryNode.withEntryID(entryID));
    }

    public LiveData<List<EntryID>> getPlaylists(QueryNode queryNode) {
        return getEntries(EntryID.TYPE_PLAYLIST, queryNode);
    }

    public LiveData<List<EntryID>> getTracks() {
        return getEntries(EntryID.TYPE_TRACK, Meta.FIELD_SPECIAL_ENTRY_ID_TRACK);
    }

    public LiveData<List<EntryID>> getTracks(EntryID entryID,
                                             QueryNode queryNode) {
        return getTracks(queryNode.withEntryID(entryID));
    }

    public LiveData<List<EntryID>> getTracks(QueryNode queryNode) {
        return getEntries(EntryID.TYPE_TRACK, queryNode);
    }

    public LiveData<List<EntryID>> getEntries(String entryType, String metaType) {
        return Transformations.map(metaModel.getEntries(entryType),
                entries -> entries.stream()
                        .map(e -> EntryID.from(e, metaType))
                        .collect(Collectors.toList())
        );
    }

    public LiveData<List<EntryID>> getEntries(String entryType, QueryNode queryNode) {
        String showType;
        switch (entryType) {
            default:
            case EntryID.TYPE_TRACK:
                showType = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
                break;
            case EntryID.TYPE_PLAYLIST:
                showType = Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST;
                break;
        }
        return Transformations.map(
                getQueryEntries(
                        entryType,
                        showType,
                        Collections.singletonList(Meta.FIELD_TITLE),
                        true,
                        queryNode,
                        false
                ),
                queryEntries -> queryEntries.stream()
                        .map(queryEntry -> queryEntry.entryID)
                        .collect(Collectors.toList())
        );
    }

    public CompletableFuture<List<EntryID>> getTracksOnce(QueryNode queryNodes) {
        return getEntriesOnce(EntryID.TYPE_TRACK, Collections.singletonList(queryNodes));
    }

    public CompletableFuture<List<EntryID>> getTracksOnce(List<EntryID> entryIDs,
                                                          QueryNode queryNode) {
        return getEntriesOnce(EntryID.TYPE_TRACK, queryNode.withEntryIDs(entryIDs));
    }

    public CompletableFuture<List<EntryID>> getTracksOnce(List<QueryNode> queryNodes) {
        return getEntriesOnce(EntryID.TYPE_TRACK, queryNodes);
    }

    public CompletableFuture<List<EntryID>> getPlaylistsOnce(QueryNode queryNodes) {
        return getEntriesOnce(EntryID.TYPE_PLAYLIST, Collections.singletonList(queryNodes));
    }

    public CompletableFuture<List<EntryID>> getPlaylistsOnce(List<EntryID> entryIDs,
                                                             QueryNode queryNode) {
        return getEntriesOnce(EntryID.TYPE_PLAYLIST, queryNode.withEntryIDs(entryIDs));
    }

    public CompletableFuture<List<EntryID>> getPlaylistsOnce(List<QueryNode> queryNodes) {
        return getEntriesOnce(EntryID.TYPE_PLAYLIST, queryNodes);
    }

    private CompletableFuture<List<EntryID>> getEntriesOnce(
            String entryType,
            List<QueryNode> queryNodes
    ) {
        List<CompletableFuture<List<EntryID>>> futureEntryLists = new ArrayList<>();
        for (QueryNode queryNode: queryNodes) {
            CompletableFuture<List<EntryID>> songEntries;
            songEntries = getEntriesOnce(entryType, queryNode);
            futureEntryLists.add(songEntries);
        }
        return CompletableFuture.allOf(futureEntryLists.toArray(new CompletableFuture[0]))
                .thenApply(aVoid -> futureEntryLists.stream()
                        .map(futureEntryList -> {
                            try {
                                return futureEntryList.get();
                            } catch (ExecutionException | InterruptedException e) {
                                e.printStackTrace();
                            }
                            return Collections.<EntryID>emptyList();
                        })
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<List<EntryID>> getEntriesOnce(String entryType,
                                                            QueryNode queryNode) {
        String showType;
        switch (entryType) {
            default:
            case EntryID.TYPE_TRACK:
                showType = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
                break;
            case EntryID.TYPE_PLAYLIST:
                showType = Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST;
                break;
        }
        return getQueryEntriesOnce(
                entryType,
                showType,
                Collections.singletonList(Meta.FIELD_TITLE),
                true,
                queryNode,
                false
        ).thenApply(queryEntries ->
                queryEntries.stream()
                        .map(queryEntry -> queryEntry.entryID)
                        .collect(Collectors.toList())
        );
    }

    public CompletableFuture<Void> deleteTracks(List<EntryID> entryIDs) {
        return CompletableFuture.runAsync(() ->
                metaModel.deleteEntries(EntryID.TYPE_TRACK, entryIDs)
        );
    }

    // Delete cascades to playlistEntries
    public CompletableFuture<Void> deletePlaylists(List<EntryID> entryIDs) {
        return CompletableFuture.runAsync(() ->
                metaModel.deleteEntries(EntryID.TYPE_PLAYLIST, entryIDs)
        );
    }

    public void clearAllTracks(String src) {
        metaModel.deleteEntriesWhereSourceIs(EntryID.TYPE_TRACK, src);
    }

    public void clearAllPlaylists(String src) {
        metaModel.deleteEntriesWhereSourceIs(EntryID.TYPE_PLAYLIST, src);
    }

    public CompletableFuture<Meta> getTrackMetaOnce(EntryID entryID) {
        return getEntryMetaOnce(EntryID.TYPE_TRACK, entryID);
    }

    public CompletableFuture<Meta> getPlaylistMetaOnce(EntryID entryID) {
        return getEntryMetaOnce(EntryID.TYPE_PLAYLIST, entryID);
    }

    public CompletableFuture<List<Meta>> getTrackMetasOnce(List<EntryID> entryIDs) {
        return getEntryMetasOnce(EntryID.TYPE_TRACK, entryIDs);
    }

    public CompletableFuture<List<Meta>> getPlaylistMetasOnce(List<EntryID> entryIDs) {
        return getEntryMetasOnce(EntryID.TYPE_PLAYLIST, entryIDs);
    }

    private CompletableFuture<List<Meta>> getEntryMetasOnce(String entryType,
                                                            List<EntryID> entryIDs) {
        CompletableFuture<List<Meta>> ret = new CompletableFuture<>();
        Meta[] metas = new Meta[entryIDs.size()];
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        for (int i = 0; i < metas.length; i++) {
            int index = i;
            EntryID entryID = entryIDs.get(index);
            futureList.add(
                    getEntryMetaOnce(entryType, entryID).thenAccept(meta -> metas[index] = meta)
            );
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .thenRun(() -> ret.complete(Arrays.asList(metas)));
        return ret;
    }

    private CompletableFuture<Meta> getEntryMetaOnce(String entryType, EntryID entryID) {
        CompletableFuture<List<MetaString>> stringFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getStringMetaSync(entryType, entryID)
        );
        CompletableFuture<List<MetaLong>> longFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getLongMetaSync(entryType, entryID)
        );
        CompletableFuture<List<MetaDouble>> doubleFuture = CompletableFuture.supplyAsync(() ->
                metaModel.getDoubleMetaSync(entryType, entryID)
        );
        CompletableFuture<Meta> ret = new CompletableFuture<>();
        CompletableFuture.allOf(new CompletableFuture[] {
                stringFuture,
                longFuture,
                doubleFuture
        }).thenRunAsync(() -> {
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

    public LiveData<Meta> getPlaylistMeta(EntryID entryID) {
        return getMeta(EntryID.TYPE_PLAYLIST, entryID);
    }

    public LiveData<Meta> getTrackMeta(EntryID entryID) {
        return getMeta(EntryID.TYPE_TRACK, entryID);
    }

    public LiveData<Meta> getTrackMeta(LiveData<EntryID> entryIDLiveData) {
        return getMeta(EntryID.TYPE_TRACK, entryIDLiveData);
    }

    public LiveData<Meta> getPlaylistMeta(LiveData<EntryID> entryIDLiveData) {
        return getMeta(EntryID.TYPE_PLAYLIST, entryIDLiveData);
    }

    private LiveData<Meta> getMeta(String entryType, LiveData<EntryID> entryIDLiveData) {
        return Transformations.switchMap(entryIDLiveData, entryID -> getMeta(entryType, entryID));
    }

    private LiveData<Meta> getMeta(String entryType, EntryID entryID) {
        if (entryID == null) {
            return new MutableLiveData<>(null);
        }
        LiveData<List<MetaString>> metaStrings = metaModel.getStringMeta(entryType, entryID);
        LiveData<List<MetaLong>> metaLongs = metaModel.getLongMeta(entryType, entryID);
        LiveData<List<MetaDouble>> metaDoubles = metaModel.getDoubleMeta(entryType, entryID);
        MediatorLiveData<Meta> meta = new MediatorLiveData<>();
        meta.addSource(metaStrings, strings -> combineMetaValues(entryID, meta, metaStrings, metaLongs, metaDoubles));
        meta.addSource(metaLongs, longs -> combineMetaValues(entryID, meta, metaStrings, metaLongs, metaDoubles));
        meta.addSource(metaDoubles, doubles -> combineMetaValues(entryID, meta, metaStrings, metaLongs, metaDoubles));
        return meta;
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

    public LiveData<List<String>> getTrackMetaStringValues(String key) {
        return metaModel.getStringMetaValues(EntryID.TYPE_TRACK, key);
    }

    public LiveData<List<String>> getPlaylistMetaStringValues(String key) {
        return metaModel.getStringMetaValues(EntryID.TYPE_PLAYLIST, key);
    }

    public LiveData<List<Long>> getTrackMetaLongValues(String key) {
        return metaModel.getLongMetaValues(EntryID.TYPE_TRACK, key);
    }

    public LiveData<List<Long>> getPlaylistMetaLongValues(String key) {
        return metaModel.getLongMetaValues(EntryID.TYPE_PLAYLIST, key);
    }

    public LiveData<List<Double>> getTrackMetaDoubleValues(String key) {
        return metaModel.getDoubleMetaValues(EntryID.TYPE_TRACK, key);
    }

    public LiveData<List<Double>> getPlaylistMetaDoubleValues(String key) {
        return metaModel.getDoubleMetaValues(EntryID.TYPE_PLAYLIST, key);
    }

    public LiveData<List<String>> getTrackMetaValuesAsStrings(String key) {
        return getMetaValuesAsStrings(EntryID.TYPE_TRACK, key);
    }

    public LiveData<List<String>> getPlaylistMetaValuesAsStrings(String key) {
        return getMetaValuesAsStrings(EntryID.TYPE_PLAYLIST, key);
    }

    private LiveData<List<String>> getMetaValuesAsStrings(String entryType, String key) {
        switch (Meta.getType(key)) {
            default:
            case STRING:
                return metaModel.getStringMetaValues(entryType, key);
            case LONG:
                return Transformations.map(
                        metaModel.getLongMetaValues(entryType, key),
                        longValues -> longValues.stream()
                                .map(String::valueOf)
                                .collect(Collectors.toList())
                );
            case DOUBLE:
                return Transformations.map(
                        metaModel.getDoubleMetaValues(entryType, key),
                        doubleValues -> doubleValues.stream()
                                .map(String::valueOf)
                                .collect(Collectors.toList())
                );
        }
    }

    public LiveData<List<String>> getTrackSources() {
        return metaModel.getSources(EntryID.TYPE_TRACK);
    }

    public LiveData<List<String>> getPlaylistSources() {
        return metaModel.getSources(EntryID.TYPE_PLAYLIST);
    }

    public LiveData<List<String>> getTrackMetaKeys() {
        return getMetaKeys(EntryID.TYPE_TRACK);
    }

    public LiveData<List<String>> getPlaylistMetaKeys() {
        return getMetaKeys(EntryID.TYPE_PLAYLIST);
    }

    private LiveData<List<String>> getMetaKeys(String entryType) {
        MediatorLiveData<List<String>> allMetaKeys = new MediatorLiveData<>();
        LiveData<List<String>> stringKeys = metaModel.getStringMetaKeys(entryType);
        LiveData<List<String>> longKeys = metaModel.getLongMetaKeys(entryType);
        LiveData<List<String>> doubleKeys = metaModel.getDoubleMetaKeys(entryType);
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

    public CompletableFuture<Void> insertTrackMeta(EntryID entryID,
                                                   String key,
                                                   String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.insertMeta(EntryID.TYPE_TRACK, entryID, key, value)
        );
    }

    public CompletableFuture<Void> deleteTrackMeta(EntryID entryID,
                                                   String key,
                                                   String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.deleteMeta(EntryID.TYPE_TRACK, entryID, key, value)
        );
    }

    public CompletableFuture<Void> replaceMeta(EntryID entryID,
                                               String key,
                                               String oldValue,
                                               String newValue) {
        return CompletableFuture.runAsync(() ->
                metaModel.replaceMeta(EntryID.TYPE_TRACK, entryID, key, oldValue, newValue)
        );
    }

    public CompletableFuture<Void> insertPlaylistMeta(EntryID playlistID,
                                                      String key,
                                                      String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.insertMeta(EntryID.TYPE_PLAYLIST, playlistID, key, value)
        );
    }

    public CompletableFuture<Void> deletePlaylistMeta(EntryID playlistID,
                                                      String key,
                                                      String value) {
        return CompletableFuture.runAsync(() ->
                metaModel.deleteMeta(EntryID.TYPE_PLAYLIST, playlistID, key, value)
        );
    }

    public CompletableFuture<Void> replacePlaylistMeta(EntryID playlistID,
                                                       String key,
                                                       String oldValue,
                                                       String newValue) {
        return CompletableFuture.runAsync(() ->
                metaModel.replaceMeta(EntryID.TYPE_PLAYLIST, playlistID, key, oldValue, newValue)
        );
    }
}