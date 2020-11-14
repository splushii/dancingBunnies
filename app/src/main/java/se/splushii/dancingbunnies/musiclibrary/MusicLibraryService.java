package se.splushii.dancingbunnies.musiclibrary;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.APIClientRequestHandler;
import se.splushii.dancingbunnies.backend.AudioDataHandler;
import se.splushii.dancingbunnies.backend.DummyAPIClient;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.search.Indexer;
import se.splushii.dancingbunnies.search.Searcher;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryService extends Service {
    private static final String LC = Util.getLogContext(MusicLibraryService.class);

    public static final String API_SRC_ID_REGEX = "[a-z0-9]+";
    // Supported API:s
    // TODO: Maybe put API ID:s in string XML?
    public static final String API_SRC_ID_DUMMY = "dummy";
    public static final String API_SRC_ID_DANCINGBUNNIES = "dancingbunnies";
    public static final String API_SRC_ID_SUBSONIC = "subsonic";
    public static final String API_SRC_ID_GIT = "git";
    public static final String API_SRC_NAME_DANCINGBUNNIES = "dancing Bunnies";
    public static final String API_SRC_NAME_SUBSONIC = "Subsonic";
    public static final String API_SRC_NAME_GIT = "Git";

    private static final String API_SRC_DELIMITER = "@";
    private static final String API_SRC_PATTERN_GROUP_ID = "id";
    private static final String API_SRC_PATTERN_GROUP_INSTANCE = "instance";
    public static final String API_SRC_REGEX =
            "^(?<" + API_SRC_PATTERN_GROUP_ID + ">" + API_SRC_ID_REGEX + ")"
                    + API_SRC_DELIMITER
                    + "(?<" + API_SRC_PATTERN_GROUP_INSTANCE + ">.*)$";
    private static final Pattern apiSourcePattern = Pattern.compile(API_SRC_REGEX);
    // Local API source
    public static final String API_SRC_DANCINGBUNNIES_LOCAL =
            getAPISource(API_SRC_ID_DANCINGBUNNIES, "local");

    private final IBinder binder = new MusicLibraryBinder();

    private MetaStorage metaStorage;

    public static String getAPISource(String apiID, String apiInstanceID) {
        return apiID + API_SRC_DELIMITER + apiInstanceID;
    }

    public static String getAPIFromSource(String src) {
        return getAPIPartFromSource(src, 0);
    }

    public static String getAPIInstanceIDFromSource(String src) {
        return getAPIPartFromSource(src, 1);
    }

    public static boolean matchAPISourceSyntax(String src) {
        return getAPISourceMatcher(src).matches();
    }

    private static Matcher getAPISourceMatcher(String src) {
        if (src == null) {
            return null;
        }
        return apiSourcePattern.matcher(src);
    }

    private static String getAPIPartFromSource(String src, int part) {
        Matcher matcher = getAPISourceMatcher(src);
        if (matcher == null || !matcher.matches()) {
            return null;
        }
        if (part == 0) {
            return matcher.group(API_SRC_PATTERN_GROUP_ID);
        }
        if (part == 1) {
            return matcher.group(API_SRC_PATTERN_GROUP_INSTANCE);
        }
        return null;
    }

    // API icons
    public static int getAPIIconResourceFromAPI(String api) {
        if (api == null) {
            return 0;
        }
        switch (api) {
            case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
                return R.drawable.api_db_icon;
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
                return R.drawable.api_sub_icon;
            case MusicLibraryService.API_SRC_ID_GIT:
                return R.drawable.api_git_icon;
            default:
                return 0;
        }
    }

    public static int getAPIIconResourceFromSource(String src) {
        if (src == null) {
            return 0;
        }
        String api = getAPIFromSource(src);
        return getAPIIconResourceFromAPI(api);
    }

    public static LiveData<Boolean> isSmartPlaylist(Context context, EntryID playlistID) {
        return Transformations.map(
                MusicLibraryService.getSmartPlaylistQuery(
                        context,
                        playlistID
                ),
                Objects::nonNull
        );
    }

    private static LiveData<QueryNode> getSmartPlaylistQuery(Context context,
                                                             EntryID playlistID) {
        return Transformations.map(
                MetaStorage.getInstance(context).getPlaylistMeta(playlistID),
                meta -> {
                    List<String> queries = meta.getStrings(Meta.FIELD_QUERY);
                    if (queries == null || queries.isEmpty()) {
                        return null;
                    }
                    // TODO: Support multiple queries?
                    return QueryNode.fromJSON(queries.get(0));
                }
        );
    }

    private static LiveData<List<PlaylistEntry>> getSmartPlaylistEntries(
            Context context,
            EntryID playlistID,
            QueryNode query
    ) {
        return Transformations.map(
                MetaStorage.getInstance(context).getTracks(query),
                entryIDs -> {
                    // Generate mock ID:s for smart playlist entries
                    return PlaylistEntry.generatePlaylistEntries(
                            playlistID,
                            entryIDs.toArray(new EntryID[0])
                    );
                }
        );
    }

    private static LiveData<Integer> getNumSmartPlaylistEntries(Context context,
                                                                QueryNode query) {
        return Transformations.map(
                MetaStorage.getInstance(context).getTracks(query),
                List::size
        );
    }

    public static LiveData<List<PlaylistEntry>> getPlaylistEntries(Context context,
                                                                   EntryID playlistID) {
        if (playlistID == null) {
            MutableLiveData<List<PlaylistEntry>> entries = new MutableLiveData<>();
            entries.setValue(Collections.emptyList());
            return entries;
        }
        return Transformations.switchMap(
                getSmartPlaylistQuery(context, playlistID),
                query -> {
                    if (query == null) {
                        // static playlist
                        return PlaylistStorage.getInstance(context).getPlaylistEntries(playlistID);
                    }
                    // smart playlist
                    return getSmartPlaylistEntries(context, playlistID, query);
                });
    }

    public static LiveData<Integer> getNumPlaylistEntries(Context context, EntryID playlistID) {
        if (playlistID == null) {
            MutableLiveData<Integer> entries = new MutableLiveData<>();
            entries.setValue(0);
            return entries;
        }
        return Transformations.switchMap(
                getSmartPlaylistQuery(context, playlistID),
                query -> {
                    if (query == null) {
                        // static playlist
                        return PlaylistStorage.getInstance(context).getNumPlaylistEntries(playlistID);
                    }
                    // smart playlist
                    return getNumSmartPlaylistEntries(context, query);
                });
    }

    public class MusicLibraryBinder extends Binder {
        public MusicLibraryService getService() {
            return MusicLibraryService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LC, "onCreate");
        metaStorage = MetaStorage.getInstance(this);
    }

    @Override
    public void onDestroy() {
        Log.d(LC, "onDestroy");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public static boolean clearIndex(Context context, String src, long writeLockTimeout) {
        Indexer indexer = Indexer.getInstance(context);
        if (!indexer.initialize(writeLockTimeout)) {
            Log.e(LC, "clearIndex: Could not initialize indexer");
            return false;
        }
        indexer.removeSongs(src);
        indexer.close();
        return true;
    }

    private static boolean reIndex(Context context,
                                List<Meta> metas,
                                long writeLockTimeout,
                                Consumer<String> progressHandler) {
        Log.d(LC, "Indexing library...");
        Indexer indexer = Indexer.getInstance(context);
        if (!indexer.initialize(writeLockTimeout)) {
            Log.e(LC, "clearIndex: Could not initialize indexer");
            return false;
        }
        long startTime = System.currentTimeMillis();
        int numDocs;
        int size = metas.size();
        Log.d(LC, "Entries to index: " + size);
        numDocs = indexer.indexSongs(metas, progress -> {
            if (progressHandler != null && (progress % 100 == 0 || progress == size)) {
                progressHandler.accept("Indexed " + progress + "/" + size + " entries...");
            }
        });
        indexer.close();
        long time = System.currentTimeMillis() - startTime;
        Log.d(LC, "Library indexed (" + numDocs + " docs)! Took " + time + "ms");
        return true;
    }

    private static AudioDataSource getAudioDataSource(Context context, EntryID entryID) {
        APIClient apiClient = APIClient.getAPIClient(context, entryID.src);
        return apiClient.getAudioData(entryID);
    }

    public static String getAudioURL(Context context, EntryID entryID) {
        AudioDataSource audioDataSource = getAudioDataSource(context, entryID);
        return audioDataSource == null ? null : audioDataSource.getURL();
    }

    public static CompletableFuture<Void> downloadAudioData(
            Context context,
            List<QueryNode> queryNodes,
            int priority
    ) {
        return getSongEntriesOnce(context, queryNodes)
                .thenAccept(songEntryIDs -> songEntryIDs.forEach(songEntryID ->
                        downloadAudioData(context, songEntryID, priority)
                ));
    }

    public static void downloadAudioData(Context context, EntryID entryID, int priority) {
        getAudioData(context, entryID, priority, null);
    }

    public static synchronized void getAudioData(Context context,
                                                 EntryID entryID,
                                                 int priority,
                                                 AudioDataHandler handler) {
        // TODO: JobSchedule this with AudioDataDownloadJob
        AudioStorage.getInstance(context).fetch(context, entryID, priority, handler);
    }

    public static synchronized CompletableFuture<Void> deleteAudioData(
            Context context,
            ArrayList<QueryNode> queryNodes
    ) {
        return getSongEntriesOnce(context, queryNodes)
                .thenAccept(songEntryIDs -> songEntryIDs.forEach(songEntryID ->
                        deleteAudioData(context, songEntryID)
                ));
    }

    public static synchronized CompletableFuture<Void> deleteAudioData(Context context,
                                                                       EntryID entryID) {
        return AudioStorage.getInstance(context).deleteAudioData(context, entryID);
    }

    public static LiveData<List<QueryEntry>> getSubscriptionEntries(Context context,
                                                                    String showField,
                                                                    List<String> sortFields,
                                                                    boolean sortOrderAscending,
                                                                    QueryNode queryNode) {
        return MetaStorage.getInstance(context).getQueryEntries(
                EntryID.TYPE_TRACK,
                showField,
                sortFields,
                sortOrderAscending,
                queryNode,
                false
        );
    }

    public LiveData<List<QueryEntry>> getSubscriptionEntries(String showField,
                                                             List<String> sortFields,
                                                             boolean sortOrderAscending,
                                                             QueryNode queryNode) {
        return metaStorage.getQueryEntries(
                EntryID.TYPE_TRACK,
                showField,
                sortFields,
                sortOrderAscending,
                queryNode,
                false
        );
    }

    public static CompletableFuture<List<EntryID>> getSongEntriesOnce(
            Context context,
            List<EntryID> entryIDs,
            QueryNode queryNode
    ) {
        return MetaStorage.getInstance(context).getTracksOnce(entryIDs, queryNode);
    }

    public static CompletableFuture<List<EntryID>> getSongEntriesOnce(
            Context context,
            List<QueryNode> queryNodes
    ) {
        return MetaStorage.getInstance(context).getTracksOnce(queryNodes);
    }

    public static List<EntryID> getSearchEntries(Context context, String query) {
        ArrayList<EntryID> entries = new ArrayList<>();
        Searcher searcher = Searcher.getInstance();
        if (!searcher.initialize(context)) {
            Toast.makeText(context, "Search is not initialized", Toast.LENGTH_SHORT).show();
            return entries;
        }
        TopDocs topDocs = searcher.search(query);
        if (topDocs == null) {
            Log.w(LC, "Error in getSearchEntries. Searcher may not be properly initialized.");
            return entries;
        }
        for (ScoreDoc scoreDoc: topDocs.scoreDocs) {
            Document doc = searcher.getDocument(scoreDoc);
            entries.add(EntryID.from(doc));
        }
        return entries;
    }

    public static LiveData<HashSet<EntryID>> getCachedEntries(Context context) {
        Query query = new Query();
        query.setShowField(Meta.FIELD_SPECIAL_ENTRY_ID_TRACK);
        query.setSortByFields(new ArrayList<>(Collections.singletonList(Meta.FIELD_TITLE)));
        query.and(Meta.FIELD_LOCAL_CACHED, Meta.FIELD_LOCAL_CACHED_VALUE_YES);
        return Transformations.map(
                MetaStorage.getInstance(context).getQueryEntries(
                        EntryID.TYPE_TRACK,
                        query.getShowField(),
                        query.getSortByFields(),
                        query.isSortOrderAscending(),
                        query.getQueryTree(),
                        false
                ),
                queryEntries -> queryEntries.stream()
                        .map(EntryID::from)
                        .collect(Collectors.toCollection(HashSet::new))
        );
    }

    public static CompletableFuture<Void> fetchLibrary(Context context,
                                                       final String src,
                                                       final MusicLibraryRequestHandler handler) {
        handler.onStart();
        MetaStorage metaStorage = MetaStorage.getInstance(context);
        String api = getAPIFromSource(src);
        APIClient client = APIClient.getAPIClient(context, src);
        if (client instanceof DummyAPIClient) {
            String msg = "Can not fetch library from " + src + ". API " + api + " not found.";
            return Util.futureResult(msg);
        }
        if (!client.hasLibrary()) {
            String msg = "Can not fetch library from " + src + ". "
                    + "API " + api + " does not support library.";
            handler.onProgress(msg);
            return Util.futureResult();
        }
        return client.getLibrary(new APIClientRequestHandler() {
            @Override
            public void onProgress(String s) {
                handler.onProgress(s);
            }
        }).thenCompose(opt -> {
            if (opt.isPresent()) {
                final List<Meta> data = opt.get();
                Log.d(LC, "Fetched library from " + src + ": " + data.size() + " entries.");
                handler.onProgress("Saving entries to local meta storage...");
                Log.d(LC, "saveLibraryToStorage start");
                return CompletableFuture.supplyAsync(() -> {
                    metaStorage.replaceAllTracksAndMetasFromSource(
                            src,
                            data,
                            false,
                            handler::onProgress
                    );
                    Log.d(LC, "saveLibraryToStorage finish");
                    return data;
                });
            } else {
                return Util.futureResult("Could not fetch library from " + src + ".");
            }
        }).thenAccept(data ->
                handler.onProgress("Successfully fetched "
                        + data.size() + " library entries from " + src + ".")
        );
    }

    public static CompletableFuture<Void> indexLibrary(Context context,
                                                       final String src,
                                                       final MusicLibraryRequestHandler handler) {
        handler.onStart();
        MetaStorage metaStorage = MetaStorage.getInstance(context);
        String api = getAPIFromSource(src);
        APIClient client = APIClient.getAPIClient(context, src);
        if (client instanceof DummyAPIClient) {
            String msg = "Can not index library from " + src + ". API " + api + " not found.";
            return Util.futureResult(msg);
        }
        if (!client.hasLibrary()) {
            String msg = "Can not index library from " + src + ". "
                    + "API " + api + " does not support library.";
            handler.onProgress(msg);
            return Util.futureResult();
        }
        return metaStorage.getTracksOnce(new QueryLeaf(
                Meta.FIELD_SPECIAL_ENTRY_SRC,
                QueryLeaf.Op.EQUALS,
                src,
                false
        )).thenComposeAsync(entryIDs -> {
            int entryIDCount = entryIDs.size();
            AtomicInteger metaCount = new AtomicInteger(0);
            List<CompletableFuture<Meta>> futures = entryIDs.stream()
                    .map(metaStorage::getTrackMetaOnce)
                    .peek(future -> future.thenAccept(meta -> {
                        int count = metaCount.incrementAndGet();
                        if ((count + 1) % 100 == 0 || count == entryIDCount - 1) {
                            handler.onProgress("Got meta for "
                                    + (count + 1) + "/" + entryIDCount
                                    + " entries ..."
                            );
                        }
                    }))
                    .collect(Collectors.toList());
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(aVoid -> futures.stream()
                            .map(future -> {
                                try {
                                    return future.get();
                                } catch (ExecutionException | InterruptedException e) {
                                    e.printStackTrace();
                                    return null;
                                }
                            })
                            .collect(Collectors.toList())
                    );
        }).thenCompose(data -> {
            Log.d(LC, "Saved library to local meta storage.");
            return CompletableFuture.supplyAsync(() -> {
                handler.onProgress("Clearing old search index...");
                if (!clearIndex(context, src, 10000)) {
                    throw new Util.FutureException("Failed to clear search index");
                }
                handler.onProgress("Indexing " + data.size() + " entries...");
                if (!reIndex(context, data, 10000, handler::onProgress)) {
                    throw new Util.FutureException("Failed to index");
                        }
                return data;
            });
        }).thenAccept(data ->
                handler.onProgress("Successfully indexed "
                        + data.size() + " library entries from " + src + ".")
        );
    }

    public static CompletableFuture<Void> fetchPlayLists(Context context,
                                                         final String src,
                                                         final MusicLibraryRequestHandler handler) {
        handler.onStart();
        String api = getAPIFromSource(src);
        APIClient client = APIClient.getAPIClient(context, src);
        PlaylistStorage playlistStorage = PlaylistStorage.getInstance(context);
        if (client instanceof DummyAPIClient) {
            String msg = "Can not fetch playlists from " + src + ". API " + api + " not found.";
            return Util.futureResult(msg);
        }
        if (!client.hasPlaylists()) {
            String msg = "Can not fetch playlists from " + src + "."
                    + " API " + api + " does not support playlists.";
            handler.onProgress(msg);
            return Util.futureResult();
        }
        return client.getPlaylists(context, new APIClientRequestHandler() {
            @Override
            public void onProgress(String s) {
                handler.onProgress(s);
            }
        }).thenCompose(opt -> {
            if (opt.isPresent()) {
                final List<Playlist> data = opt.get();
                Log.d(LC, "Fetched playlists from " + src + ": " + data.size() + " entries.");
                handler.onProgress("Saving playlists to local playlist storage...");
                Log.d(LC, "savePlaylistsToStorage start");
                return CompletableFuture.supplyAsync(() -> {
                    playlistStorage.replaceAllPlaylistsFromSource(
                            src,
                            data,
                            false,
                            handler::onProgress
                    );
                    Log.d(LC, "savePlaylistsToStorage finish");
                    return data;
                });
            } else {
                return Util.futureResult("Could not fetch playlists from " + src + ".");
            }
        }).thenAccept(data ->
                handler.onProgress("Successfully processed "
                        + data.size() + " playlist entries from " + src + ".")
        );
    }
}