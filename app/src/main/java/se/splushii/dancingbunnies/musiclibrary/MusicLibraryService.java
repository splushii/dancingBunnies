package se.splushii.dancingbunnies.musiclibrary;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.APIClientRequestHandler;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;
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
    public static boolean checkAPISupport(String src, String action) {
        switch (src) {
            case API_ID_DANCINGBUNNIES:
                switch (action) {
                    case PLAYLIST_ENTRY_DELETE:
                        return true;
                    case PLAYLIST_ENTRY_MOVE:
                        return true;
                    case PLAYLIST_DELETE:
                        return true;
                    case PLAYLIST_ENTRY_ADD:
                        return true;
                }
                return false;
            case API_ID_SUBSONIC:
                switch (action) {
                    case PLAYLIST_ENTRY_DELETE:
                        return false;
                    case PLAYLIST_ENTRY_MOVE:
                        return false;
                    case PLAYLIST_DELETE:
                        return false;
                    case PLAYLIST_ENTRY_ADD:
                        return false;
                }
                return false;
            default:
                return false;
        }
    }

    private static final String LUCENE_INDEX_PATH = "lucene_index";

    private File indexDirectoryPath;
    private HashMap<String, APIClient> apis;
    private Searcher searcher;
    private MetaStorage metaStorage;
    private AudioStorage audioStorage;
    private PlaylistStorage playlistStorage;

    private final IBinder binder = new MusicLibraryBinder();
    private List<Runnable> metaChangedListeners;

    public static LiveData<List<PlaylistEntry>> getSmartPlaylistEntries(Context context,
                                                                        PlaylistID playlistID) {
        return Transformations.map(
                Transformations.switchMap(
                        PlaylistStorage.getInstance(context).getPlaylist(playlistID),
                        playlist -> MetaStorage.getInstance(context).getEntries(
                                Meta.FIELD_SPECIAL_MEDIA_ID,
                                Meta.FIELD_TITLE,
                                SmartPlaylist.jsonQueryToBundle(playlist.query)
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

    public int addMetaChangedListener(Runnable runnable) {
        int position = metaChangedListeners.size();
        metaChangedListeners.add(runnable);
        return position;
    }

    public void removeMetaChangedListener(int id) {
        metaChangedListeners.remove(id);
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
        apis = new HashMap<>();
        metaChangedListeners = new ArrayList<>();
        loadSettings();
        metaStorage = MetaStorage.getInstance(this);
        playlistStorage = PlaylistStorage.getInstance(this);
        audioStorage = AudioStorage.getInstance(this);
        prepareIndex();
        notifyLibraryChanged();
    }

    private void prepareIndex() {
        if (indexDirectoryPath != null) {
            return;
        }
        indexDirectoryPath = new File(getFilesDir(), LUCENE_INDEX_PATH);
        if (!indexDirectoryPath.isDirectory()) {
            if (!indexDirectoryPath.mkdirs()) {
                Log.w(LC, "Could not create lucene index dir " + indexDirectoryPath.toPath());
            }
        }
        Log.e(LC, "index path: " + indexDirectoryPath);
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

    private void reIndex(List<Meta> metas) {
        Log.d(LC, "Indexing library...");
        prepareIndex();
        Indexer indexer = new Indexer(indexDirectoryPath);
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

    private void notifyLibraryChanged() {
        Log.d(LC, "notifyLibraryChange");
        for (Runnable r: metaChangedListeners) {
            r.run();
        }

    }

    private AudioDataSource getAudioDataSource(EntryID entryID) {
        APIClient apiClient = apis.get(entryID.src);
        return apiClient == null ? null : apiClient.getAudioData(entryID);
    }

    public String getAudioURL(EntryID entryID) {
        AudioDataSource audioDataSource = getAudioDataSource(entryID);
        return audioDataSource == null ? null : audioDataSource.getURL();
    }

    public static void downloadAudioData(Context context, EntryID entryID) {
        getAudioData(context, entryID, new AudioDataDownloadHandler() {
            @Override
            public void onDownloading() {
                Log.d(LC, "Downloading: " + entryID);
            }

            @Override
            public void onSuccess(AudioDataSource audioDataSource) {
                Log.d(LC, "Downloaded: " + entryID + " size: " + audioDataSource.getSize());
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC, "Failed to download: " + entryID);
            }
        });
    }

    public static synchronized void getAudioData(Context context,
                                                 EntryID entryID,
                                                 AudioDataDownloadHandler handler) {
        AudioStorage storage = AudioStorage.getInstance(context);
        AudioDataSource audioDataSource = storage.get(entryID);
        if (audioDataSource == null) {
            HashMap<String, APIClient> apis = new HashMap<>();
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            if (settings.getBoolean(context.getResources().getString(R.string.pref_key_subsonic), false)) {
                apis.put(API_ID_SUBSONIC, new SubsonicAPIClient(context));
            }
            for (String key: apis.keySet()) {
                apis.get(key).loadSettings(context);
            }
            APIClient apiClient = apis.get(entryID.src);
            audioDataSource = apiClient == null ? null : apiClient.getAudioData(entryID);
            if (audioDataSource == null) {
                handler.onFailure("Could not get AudioDataSource for song with src: "
                        + entryID.src + ", id: " + entryID.id);
                return;
            }
            storage.put(entryID, audioDataSource);
        }
        if (audioDataSource.isDataReady()) {
            handler.onSuccess(audioDataSource);
            return;
        }
        // TODO: JobSchedule this with AudioDataDownloadJob
        storage.fetch(entryID, handler);
    }

    public CompletableFuture<Meta> getSongMeta(EntryID entryID) {
        return metaStorage.getMetaOnce(entryID);
    }

    public CompletableFuture<List<Meta>> getSongMetas(List<EntryID> entryIDs) {
        CompletableFuture<List<Meta>> ret = new CompletableFuture<>();
        Meta[] metas = new Meta[entryIDs.size()];
        List<CompletableFuture> futureList = new ArrayList<>();
        for (int i = 0; i < metas.length; i++) {
            int index = i;
            EntryID entryID = entryIDs.get(index);
            futureList.add(getSongMeta(entryID).thenAccept(meta -> metas[index] = meta));
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .thenRun(() -> ret.complete(Arrays.asList(metas)));
        return ret;
    }

    public LiveData<List<LibraryEntry>> getSubscriptionEntries(String showField,
                                                               String sortField,
                                                               Bundle query) {
        return metaStorage.getEntries(showField, sortField, query);
    }

    public CompletableFuture<List<EntryID>> getSongEntriesOnce(List<EntryID> entryIDs, Bundle query) {
        return metaStorage.getSongEntriesOnce(entryIDs, query);
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

    private void loadSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.getBoolean(getResources().getString(R.string.pref_key_subsonic), false)) {
            apis.remove(API_ID_SUBSONIC);
        } else if (!apis.containsKey(API_ID_SUBSONIC)) {
            apis.put(API_ID_SUBSONIC, new SubsonicAPIClient(this));
        }
        for (String key: apis.keySet()) {
            apis.get(key).loadSettings(this);
        }
    }

    private void saveLibraryToStorage(List<Meta> data) {
        long start = System.currentTimeMillis();
        Log.d(LC, "saveLibraryToStorage start");
        metaStorage.insertSongs(data);
        Log.d(LC, "saveLibraryToStorage finish " + (System.currentTimeMillis() - start));
    }

    private void clearLibraryStorageEntries(final String src) {
        long start = System.currentTimeMillis();
        Log.d(LC, "clearLibraryStorageEntries start");
        metaStorage.clearAll(src);
        Log.d(LC, "clearLibraryStorageEntries finish " + (System.currentTimeMillis() - start));
    }

    private void savePlaylistsToStorage(List<Playlist> data) {
        long start = System.currentTimeMillis();
        Log.d(LC, "saveLibraryToStorage start");
        playlistStorage.insertPlaylists(0, data);
        Log.d(LC, "saveLibraryToStorage finish " + (System.currentTimeMillis() - start));
    }

    private void clearPlaylistStorageEntries(String src) {
        long start = System.currentTimeMillis();
        Log.d(LC, "clearPlaylistStorageEntries start");
        playlistStorage.clearAll(src);
        Log.d(LC, "clearPlaylistStorageEntries finish " + (System.currentTimeMillis() - start));
    }

    public void fetchAPILibrary(final String api, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        loadSettings();
        APIClient client = apis.get(api);
        if (client == null) {
            handler.onFailure("Can not fetch library. API " + api + " not found.");
            return;
        }
        if (!client.hasLibrary()) {
            handler.onFailure("Can not fetch library. API " + api + " does not support library.");
            return;
        }
        client.getLibrary(new APIClientRequestHandler() {
            @Override
            public void onProgress(String s) {
                handler.onProgress(s);
            }
        }).thenAccept(opt -> {
            if (opt.isPresent()) {
                final List<Meta> data = opt.get();
                Log.d(LC, "Fetched library from " + api + ": " + data.size() + " entries.");
                handler.onProgress("Saving entries to local meta storage...");
                // TODO: Possible to perform a smart merge instead?
                clearLibraryStorageEntries(api);
                saveLibraryToStorage(data);
                Log.d(LC, "Saved library to local meta storage.");
                notifyLibraryChanged();
                handler.onProgress("Indexing...");
                reIndex(data);
                handler.onProgress("Indexing done.");
                handler.onSuccess("Successfully processed "
                        + data.size() + " library entries from " + api + ".");
            } else {
                handler.onFailure("Could not fetch library from " + api + ".");
            }
        });
    }

    public void fetchPlayLists(final String api, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        loadSettings();
        APIClient client = apis.get(api);
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
        }).thenAccept(opt -> {
            if (opt.isPresent()) {
                final List<Playlist> data = opt.get();
                Log.d(LC, "Fetched playlists from " + api + ": " + data.size() + " entries.");
                handler.onProgress("Saving playlists to local playlist storage...");
                // TODO: Before clearing, check if there are any unsynced changes in 'api' playlists
                clearPlaylistStorageEntries(api);
                savePlaylistsToStorage(data);
                Log.d(LC, "Saved playlists to local playlist storage.");
                notifyLibraryChanged();
                handler.onSuccess("Successfully processed "
                        + data.size() + " playlist entries from " + api + ".");
            } else {
                handler.onFailure("Could not fetch playlists from " + api + ".");
            }
        });
    }
}
