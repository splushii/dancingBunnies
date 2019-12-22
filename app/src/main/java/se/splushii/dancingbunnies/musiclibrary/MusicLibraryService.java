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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.APIClientRequestHandler;
import se.splushii.dancingbunnies.backend.AudioDataHandler;
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

    // Supported API:s
    public static final String API_ID_DANCINGBUNNIES = "dancingbunnies";
    public static final String API_ID_SUBSONIC = "subsonic";

    // API icons
    public static int getAPIIconResource(String src) {
        if (src == null) {
            return 0;
        }
        switch (src) {
            case MusicLibraryService.API_ID_DANCINGBUNNIES:
                return R.mipmap.dancingbunnies_icon;
            case MusicLibraryService.API_ID_SUBSONIC:
                return R.drawable.sub_icon;
            default:
                return 0;
        }
    }

    // API actions
    public static final String PLAYLIST_DELETE = "playlist_delete";
    public static final String PLAYLIST_ENTRY_DELETE = "playlist_entry_delete";
    public static final String PLAYLIST_ENTRY_MOVE = "playlist_entry_move";
    public static final String PLAYLIST_ENTRY_ADD = "playlist_entry_add";
    public static final String META_EDIT = "meta_edit";
    public static final String META_ADD = "meta_add";
    public static final String META_DELETE = "meta_delete";
    public static boolean checkAPISupport(String src, String action) {
        switch (src) {
            case API_ID_DANCINGBUNNIES:
                switch (action) {
                    case PLAYLIST_ENTRY_DELETE:
                    case PLAYLIST_ENTRY_MOVE:
                    case PLAYLIST_DELETE:
                    case PLAYLIST_ENTRY_ADD:
                        return true;
                    case META_EDIT:
                    case META_ADD:
                        return false;
                }
                return false;
            case API_ID_SUBSONIC:
                switch (action) {
                    case PLAYLIST_ENTRY_DELETE:
                    case PLAYLIST_ENTRY_MOVE:
                    case PLAYLIST_DELETE:
                    case PLAYLIST_ENTRY_ADD:
                    case META_EDIT:
                    case META_ADD:
                        return false;
                }
                return false;
            default:
                return false;
        }
    }

    private static final String LUCENE_INDEX_PATH = "lucene_index";

    private File indexDirectoryPath;
    private Searcher searcher;
    private MetaStorage metaStorage;

    private final IBinder binder = new MusicLibraryBinder();

    public static LiveData<List<PlaylistEntry>> getSmartPlaylistEntries(Context context,
                                                                        PlaylistID playlistID) {
        return Transformations.map(
                Transformations.switchMap(
                        PlaylistStorage.getInstance(context).getPlaylist(playlistID),
                        playlist -> playlist == null ?
                                new MutableLiveData<>(Collections.emptyList())
                                :
                                MetaStorage.getInstance(context).getEntries(
                                        Meta.FIELD_SPECIAL_MEDIA_ID,
                                        Collections.singletonList(Meta.FIELD_TITLE),
                                        true,
                                        MusicLibraryQueryNode.fromJSON(playlist.query)
                                )
                ),
                libraryEntries -> {
                    List<PlaylistEntry> playlistEntries = new ArrayList<>();
                    for (int i = 0; i < libraryEntries.size(); i++) {
                        LibraryEntry libraryEntry = libraryEntries.get(i);
                        PlaylistEntry playlistEntry = PlaylistEntry.from(
                                playlistID,
                                libraryEntry.entryID,
                                i
                        );
                        playlistEntry.rowId = i;
                        playlistEntries.add(playlistEntry);
                    }
                    return playlistEntries;
                }
        );
    }

    public static LiveData<List<PlaylistEntry>> getPlaylistEntries(Context context,
                                                                   PlaylistID playlistID) {
        if (playlistID == null) {
            MutableLiveData<List<PlaylistEntry>> entries = new MutableLiveData<>();
            entries.setValue(Collections.emptyList());
            return entries;
        }
        if (playlistID.type == PlaylistID.TYPE_STUPID) {
            return PlaylistStorage.getInstance(context).getPlaylistEntries(playlistID);
        } else if (playlistID.type == PlaylistID.TYPE_SMART){
            return MusicLibraryService.getSmartPlaylistEntries(context, playlistID);
        }
        MutableLiveData<List<PlaylistEntry>> ret = new MutableLiveData<>();
        ret.setValue(Collections.emptyList());
        return ret;
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
        indexDirectoryPath = prepareIndex(getFilesDir(), indexDirectoryPath);
    }

    private static File prepareIndex(File filesDir, File currentIndexPath) {
        if (currentIndexPath != null) {
            return currentIndexPath;
        }
        currentIndexPath = new File(filesDir, LUCENE_INDEX_PATH);
        if (!currentIndexPath.isDirectory()) {
            if (!currentIndexPath.mkdirs()) {
                Log.w(LC, "Could not create lucene index dir " + currentIndexPath.toPath());
            }
        }
        Log.e(LC, "index path: " + currentIndexPath);
        return currentIndexPath;
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

    private static void reIndex(Context context, List<Meta> metas, File indexPath) {
        Log.d(LC, "Indexing library...");
        indexPath = prepareIndex(context.getFilesDir(), indexPath);
        Indexer indexer = new Indexer(indexPath);
        long startTime = System.currentTimeMillis();
        int numDocs = 0;
        Log.d(LC, "Songs: " + metas.size());
        for (Meta meta: metas) {
            numDocs = indexer.indexSong(meta);
        }
        indexer.close();
        long time = System.currentTimeMillis() - startTime;
        Log.d(LC, "Library indexed (" + numDocs + " docs)! Took " + time + "ms");
    }

    private static AudioDataSource getAudioDataSource(Context context, EntryID entryID) {
        APIClient apiClient = APIClient.getAPIClient(context, entryID.src);
        return apiClient == null ? null : apiClient.getAudioData(entryID);
    }

    public static String getAudioURL(Context context, EntryID entryID) {
        AudioDataSource audioDataSource = getAudioDataSource(context, entryID);
        return audioDataSource == null ? null : audioDataSource.getURL();
    }

    public static CompletableFuture<Void> downloadAudioData(
            Context context,
            List<MusicLibraryQueryNode> queryNodes,
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
            ArrayList<MusicLibraryQueryNode> queryNodes
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

    public static CompletableFuture<Meta> getSongMeta(Context context, EntryID entryID) {
        return MetaStorage.getInstance(context).getMetaOnce(entryID);
    }

    public static CompletableFuture<List<Meta>> getSongMetas(Context context,
                                                             List<EntryID> entryIDs) {
        CompletableFuture<List<Meta>> ret = new CompletableFuture<>();
        Meta[] metas = new Meta[entryIDs.size()];
        List<CompletableFuture> futureList = new ArrayList<>();
        for (int i = 0; i < metas.length; i++) {
            int index = i;
            EntryID entryID = entryIDs.get(index);
            futureList.add(getSongMeta(context, entryID).thenAccept(meta -> metas[index] = meta));
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .thenRun(() -> ret.complete(Arrays.asList(metas)));
        return ret;
    }

    public LiveData<List<LibraryEntry>> getSubscriptionEntries(String showField,
                                                               List<String> sortFields,
                                                               boolean sortOrderAscending,
                                                               MusicLibraryQueryNode queryNode) {
        return metaStorage.getEntries(
                showField,
                sortFields,
                sortOrderAscending,
                queryNode
        );
    }

    public static CompletableFuture<List<EntryID>> getSongEntriesOnce(
            Context context,
            List<EntryID> entryIDs,
            MusicLibraryQueryNode queryNode
    ) {
        return MetaStorage.getInstance(context).getSongEntriesOnce(entryIDs, queryNode);
    }

    public static CompletableFuture<List<EntryID>> getSongEntriesOnce(
            Context context,
            List<MusicLibraryQueryNode> queryNodes
    ) {
        return MetaStorage.getInstance(context).getSongEntriesOnce(queryNodes);
    }

    public List<EntryID> getSearchEntries(String query) {
        ArrayList<EntryID> entries = new ArrayList<>();
        if (!initializeSearcher()) {
            Toast.makeText(this, "Search is not initialized", Toast.LENGTH_SHORT).show();
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
        MusicLibraryQuery query = new MusicLibraryQuery();
        query.setShowField(Meta.FIELD_SPECIAL_MEDIA_ID);
        query.setSortByFields(new ArrayList<>(Collections.singletonList(Meta.FIELD_TITLE)));
        query.and(Meta.FIELD_LOCAL_CACHED, Meta.FIELD_LOCAL_CACHED_VALUE_YES);
        return Transformations.map(
                MetaStorage.getInstance(context).getEntries(
                        query.getShowField(),
                        query.getSortByFields(),
                        query.isSortOrderAscending(),
                        query.getQueryTree()
                ),
                libraryEntries -> libraryEntries.stream()
                        .map(EntryID::from)
                        .collect(Collectors.toCollection(HashSet::new))
        );
    }

    private boolean initializeSearcher() {
        if (searcher != null) {
            return true;
        }
        searcher = new Searcher(indexDirectoryPath);
        if (!searcher.initialize()) {
            searcher = null;
            return false;
        }
        return true;
    }

    public static CompletableFuture<Void> fetchAPILibrary(Context context, final String api, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        MetaStorage metaStorage = MetaStorage.getInstance(context);
        APIClient client = APIClient.getAPIClient(context, api);
        if (client == null) {
            handler.onFailure("Can not fetch library. API " + api + " not found.");
            return Util.futureResult("Can not fetch library. API " + api + " not found.");
        }
        if (!client.hasLibrary()) {
            handler.onFailure("Can not fetch library. API " + api + " does not support library.");
            return Util.futureResult("Can not fetch library. API " + api + " does not support library.");
        }
        return client.getLibrary(new APIClientRequestHandler() {
            @Override
            public void onProgress(String s) {
                handler.onProgress(s);
            }
        }).thenCompose(opt -> {
            if (opt.isPresent()) {
                final List<Meta> data = opt.get();
                Log.d(LC, "Fetched library from " + api + ": " + data.size() + " entries.");
                handler.onProgress("Saving entries to local meta storage...");
                // TODO: Possible to perform a smart merge instead?
                Log.d(LC, "clearLibraryStorageEntries start");
                return CompletableFuture.supplyAsync(() -> {
                    metaStorage.clearAll(api);
                    Log.d(LC, "clearLibraryStorageEntries finish");
                    return data;
                });
            } else {
                handler.onFailure("Could not fetch library from " + api + ".");
                return Util.futureResult("Could not fetch library from " + api + ".");
            }
        }).thenCompose(data -> {
            Log.d(LC, "saveLibraryToStorage start");
            return CompletableFuture.supplyAsync(() -> {
                metaStorage.insertSongs(data);
                Log.d(LC, "saveLibraryToStorage finish");
                return data;
            });
        }).thenCompose(data -> {
            Log.d(LC, "Saved library to local meta storage.");
            handler.onProgress("Indexing...");
            return CompletableFuture.supplyAsync(() -> {
                reIndex(context, data, null);
                handler.onProgress("Indexing done.");
                return data;
            });
        }).thenAccept(data -> {
            handler.onSuccess("Successfully processed "
                    + data.size() + " library entries from " + api + ".");
        }).exceptionally(throwable -> {
            if (throwable != null) {
                throwable.printStackTrace();
                Log.e(LC, "msg: " + throwable.getMessage());
            }
            handler.onFailure("Could not fetch library from " + api + ".");
            return null;
        });
    }

    public static void fetchPlayLists(Context context, final String api, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        APIClient client = APIClient.getAPIClient(context, api);
        PlaylistStorage playlistStorage = PlaylistStorage.getInstance(context);
        if (client == null) {
            handler.onFailure("Can not fetch playlists. API " + api + " not found.");
            return;
        }
        if (!client.hasPlaylists()) {
            handler.onFailure("Can not fetch playlists. API " + api
                    + " does not support playlists.");
            return;
        }
        client.getPlaylists(new APIClientRequestHandler() {
            @Override
            public void onProgress(String s) {
                handler.onProgress(s);
            }
        }).thenCompose(opt -> {
            if (opt.isPresent()) {
                final List<Playlist> data = opt.get();
                Log.d(LC, "Fetched playlists from " + api + ": " + data.size() + " entries.");
                handler.onProgress("Saving playlists to local playlist storage...");
                // TODO: Before clearing, check if there are any unsynced changes in 'api' playlists
                Log.d(LC, "clearPlaylistStorageEntries start");
                return CompletableFuture.supplyAsync(() -> {
                    playlistStorage.clearAll(api);
                    Log.d(LC, "clearPlaylistStorageEntries finish");
                    return data;
                });
            } else {
                handler.onFailure("Could not fetch playlists from " + api + ".");
                return Util.futureResult("Could not fetch playlists from " + api + ".");
            }
        }).thenCompose(data -> {
            Log.d(LC, "saveLibraryToStorage start");
            return CompletableFuture.supplyAsync(() -> {
                playlistStorage.insertPlaylists(0, data);
                Log.d(LC, "saveLibraryToStorage finish");
                Log.d(LC, "Saved playlists to local playlist storage.");
                return data;
            });
        }).thenAccept(data -> {
            handler.onSuccess("Successfully processed "
                    + data.size() + " playlist entries from " + api + ".");
        }).exceptionally(throwable -> {
            if (throwable != null) {
                throwable.printStackTrace();
                Log.e(LC, "msg: " + throwable.getMessage());
            }
            handler.onFailure("Could not fetch playlists from " + api + ".");
            return null;
        });
    }
}