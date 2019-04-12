package se.splushii.dancingbunnies.musiclibrary;

import android.app.Service;
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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
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

    public static final String API_ID_DANCINGBUNNIES = "dancingbunnies";
    public static final String API_ID_SUBSONIC = "subsonic";
    private static final String LUCENE_INDEX_PATH = "lucene_index";

    private File indexDirectoryPath;
    private HashMap<String, APIClient> apis;
    private Searcher searcher;
    private MetaStorage metaStorage;
    private AudioStorage audioStorage;
    private PlaylistStorage playlistStorage;
    private LiveData<List<PlaylistItem>> playlistLiveData;
    private LiveData<List<PlaylistEntry>> playlistEntriesLiveData;
    private List<PlaylistItem> playlists = new ArrayList<>();
    private HashMap<PlaylistID, List<EntryID>> playlistMap = new HashMap<>();

    private final IBinder binder = new MusicLibraryBinder();
    private List<Runnable> metaChangedListeners;

    public List<PlaybackEntry> playlistGetNext(PlaylistID playlistID, long index, int maxEntries) {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        List<EntryID> entryIDs = playlistMap.get(playlistID);
        if (entryIDs == null || entryIDs.isEmpty()) {
            return playbackEntries;
        }
        for (int count = 0; count < maxEntries && index < entryIDs.size(); count++, index++) {
            EntryID entryID = entryIDs.get((int)index);
            playbackEntries.add(new PlaybackEntry(entryID, PlaybackEntry.USER_TYPE_PLAYLIST));
        }
        return playbackEntries;
    }

    public List<PlaybackEntry> playlistGetPrevious(PlaylistID playlistID, long index, int maxEntries) {
        // TODO: Implement
        Log.e(LC, "playlistGetPrevious not implemented");
        return new LinkedList<>();
    }

    public long playlistPosition(PlaylistID playlistID, long playlistPosition, int offset) {
        if (offset == 0) {
            return playlistPosition;
        }
        List<EntryID> playlistEntries = playlistMap.getOrDefault(playlistID, Collections.emptyList());
        int playlistSize = playlistEntries.size();
        playlistPosition += offset;
        return playlistPosition > playlistSize ? playlistSize : playlistPosition;
    }

    public List<PlaylistItem> getPlaylists() {
        return playlists;
    }

    // TODO: Make this work similar to musiclibrary entries
    public List<LibraryEntry> getPlaylistEntries(PlaylistID playlistID) {
        return playlistMap.getOrDefault(playlistID, Collections.emptyList()).stream()
                .map(e -> new LibraryEntry(e, "<fixme>"))
                .collect(Collectors.toList());
    }

    public void playlistAddEntries(PlaylistID playlistID, List<EntryID> entryIDs) {
        playlistStorage.addToPlaylist(playlistID, entryIDs);
    }

    public void playlistRemoveEntry(PlaylistID playlistID, int position) {
        playlistStorage.removeFromPlaylist(playlistID, position);
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

    private final Observer<List<PlaylistItem>> playlistsObserver = entries -> {
        Log.d(LC, "playlistsObserver: " + entries.size() + " entries.");
        playlists = entries;
    };

    private final Observer<List<PlaylistEntry>> playlistEntriesObserver = entries -> {
        long start = System.currentTimeMillis();
        Log.d(LC, "playlistEntriesObserver: " + entries.size() + " entries."
                + " Building playlist map...");
        HashMap<PlaylistID, List<EntryID>> newPlaylistMap = new HashMap<>();
        for (PlaylistEntry entry: entries) {
            PlaylistID id = new PlaylistID(entry.playlist_api, entry.playlist_id, PlaylistID.TYPE_STUPID);
            List<EntryID> entryIDs = newPlaylistMap.getOrDefault(id, new ArrayList<>());
            entryIDs.add(new EntryID(entry.api, entry.id, Meta.FIELD_SPECIAL_MEDIA_ID));
            newPlaylistMap.put(id, entryIDs);
        }
        playlistMap = newPlaylistMap;
        Log.d(LC, "playlistEntriesObserver: Finished building playlist map. "
                + (System.currentTimeMillis() - start) + "ms.");
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LC, "onCreate");
        apis = new HashMap<>();
        metaChangedListeners = new ArrayList<>();
        loadSettings();
        metaStorage = MetaStorage.getInstance(this);
        playlistStorage = new PlaylistStorage(this);
        playlistLiveData = playlistStorage.getPlaylists();
        playlistLiveData.observeForever(playlistsObserver);
        playlistEntriesLiveData = playlistStorage.getPlaylistEntries();
        playlistEntriesLiveData.observeForever(playlistEntriesObserver);
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
        playlistEntriesLiveData.removeObserver(playlistEntriesObserver);
        playlistLiveData.removeObserver(playlistsObserver);
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
        if (!apis.containsKey(entryID.src)) {
            return null;
        }
        return apis.get(entryID.src).getAudioData(entryID);
    }

    public String getAudioURL(EntryID entryID) {
        AudioDataSource audioDataSource = getAudioDataSource(entryID);
        return audioDataSource == null ? null : audioDataSource.getURL();
    }

    public void downloadAudioData(EntryID entryID) {
        getAudioData(entryID, new AudioDataDownloadHandler() {
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

    public void getAudioData(EntryID entryID, AudioDataDownloadHandler handler) {
        AudioDataSource audioDataSource = audioStorage.get(entryID);
        if (audioDataSource == null) {
            audioDataSource = getAudioDataSource(entryID);
            if (audioDataSource == null) {
                handler.onFailure("Could not get AudioDataSource for song with src: "
                        + entryID.src + ", id: " + entryID.id);
                return;
            }
            audioStorage.put(entryID, audioDataSource);
        }
        if (audioDataSource.isFinished()) {
            handler.onSuccess(audioDataSource);
            return;
        }
        // TODO: JobSchedule this with AudioDataDownloadJob
        audioStorage.fetch(entryID, handler);
    }

    public CompletableFuture<Meta> getSongMeta(EntryID entryID) {
        return metaStorage.getMeta(entryID);
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

    public LiveData<List<LibraryEntry>> getSubscriptionEntries(String showType, Bundle query) {
        return metaStorage.getEntries(showType, query);
    }

    public CompletableFuture<List<EntryID>> getSongEntries(EntryID entryID) {
        return metaStorage.getEntries(entryID);
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
        playlistStorage.insertPlaylists(data);
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
        if (!apis.containsKey(api)) {
            handler.onFailure("Can not fetch library. API " + api + " not found.");
            return;
        }
        APIClient client = apis.get(api);
        if (!client.hasLibrary()) {
            handler.onFailure("Can not fetch library. API " + api + " does not support library.");
            return;
        }
        apis.get(api).getLibrary(new APIClientRequestHandler() {
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
        if (!apis.containsKey(api)) {
            handler.onFailure("Can not fetch playlists. API " + api + " not found.");
            return;
        }
        APIClient client = apis.get(api);
        if (!client.hasPlaylists()) {
            handler.onFailure("Can not fetch playlists. API " + api
                    + " does not support playlists.");
            return;
        }
        apis.get(api).getPlaylists(new APIClientRequestHandler() {
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
