package se.splushii.dancingbunnies.musiclibrary;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.widget.Toast;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryService extends Service {
    private static String LC = Util.getLogContext(MusicLibraryService.class);

    public static final String API_ID_DANCINGBUNNIES = "dancingbunnies";
    public static final String API_ID_SUBSONIC = "subsonic";
    private static final String LUCENE_INDEX_PATH = "lucene_index";

    private File indexDirectoryPath;
    private HashMap<String, APIClient> apis;
    private Searcher searcher;
    private MetaStorage metaStorage;
    private AudioStorage audioStorage;

    private final IBinder binder = new MusicLibraryBinder();

    public List<PlaybackEntry> getNextPlaylistEntries(String playlistId, int max_entries) {
        // TODO: Implement
        return new LinkedList<>();
    }

    public List<PlaybackEntry> getPreviousPlaylistEntries(String playlistId, int max_entries) {
        // TODO: Implement
        return new LinkedList<>();
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
        loadSettings();
        metaStorage = new MetaStorage(this);
        metaStorage.open();
        audioStorage = new AudioStorage();
        indexDirectoryPath = new File(getFilesDir(), LUCENE_INDEX_PATH);
        if (!indexDirectoryPath.isDirectory()) {
            if (!indexDirectoryPath.mkdirs()) {
                Log.w(LC, "Could not create lucene index dir " + indexDirectoryPath.toPath());
            }
        }
        notifyLibraryChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LC, "onDestroy");
        metaStorage.close();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void reIndex() {
        Log.d(LC, "Indexing library...");
        Indexer indexer = new Indexer(indexDirectoryPath);
        long startTime = System.currentTimeMillis();
        int numDocs = 0;
        // TODO: Do this one item at a time when fetching from backend API instead...
        List<MediaMetadataCompat> songs = metaStorage.getMetadataEntries(null);
        Log.d(LC, "Songs: " + songs.size());
        for (MediaMetadataCompat meta: songs) {
            numDocs = indexer.indexSong(meta);
        }
        indexer.close();
        long time = System.currentTimeMillis() - startTime;
        Log.d(LC, "Library indexed (" + numDocs + " docs)! Took " + time + "ms");
    }

    private void notifyLibraryChanged() {
        // TODO: Notify about ALL changed parents/children?
    }

    public void getAudioData(EntryID entryID, AudioDataDownloadHandler handler) {
        AudioDataSource audioDataSource = audioStorage.get(entryID);
        if (audioDataSource == null) {
            if (apis.containsKey(entryID.src)) {
                audioDataSource = apis.get(entryID.src).getAudioData(entryID);
                audioStorage.put(entryID, audioDataSource);
            } else {
                handler.onFailure("Could not find api for song with src: " + entryID.src
                        + ", id: " + entryID.id);
                return;
            }
        }
        if (audioDataSource.isFinished()) {
            handler.onStart();
            handler.onSuccess(audioDataSource);
            return;
        }
        // TODO: JobSchedule this with AudioDataDownloadJob
        audioStorage.download(entryID, handler);
    }

    public MediaMetadataCompat getSongMetaData(EntryID entryID) {
        if (EntryID.UnknownEntryID.equals(entryID)) {
            return Meta.UNKNOWN_ENTRY;
        }
        return metaStorage.getMetadataEntry(entryID);
    }

    public List<LibraryEntry> getSubscriptionEntries(MusicLibraryQuery q) {
        return metaStorage.getEntries(q.subQuery());
    }

    public List<LibraryEntry> getSearchEntries(String query) {
        ArrayList<LibraryEntry> entries = new ArrayList<>();
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
            entries.add(LibraryEntry.from(doc));
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
        if (!settings.getBoolean("pref_subsonic", false)) {
            apis.remove(API_ID_SUBSONIC);
        } else if (!apis.containsKey(API_ID_SUBSONIC)) {
            apis.put(API_ID_SUBSONIC, new SubsonicAPIClient());
        }
        for (String key: apis.keySet()) {
            apis.get(key).loadSettings(this);
        }
    }

    private void saveLibraryToStorage(ArrayList<MediaMetadataCompat> data) {
        long start = System.currentTimeMillis();
        Log.d(LC, "saveLibraryToStorage start");
        metaStorage.insertSongs(data);
        Log.d(LC, "saveLibraryToStorage finish " + (System.currentTimeMillis() - start));
    }

    private void clearStorageEntries(final String src) {
        long start = System.currentTimeMillis();
        Log.d(LC, "clearStorageEntries start");
        metaStorage.clearAll(src);
        Log.d(LC, "clearStorageEntries finish " + (System.currentTimeMillis() - start));
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
        CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> req =
                apis.get(api).getLibrary(new APIClientRequestHandler() {
                    @Override
                    public void onProgress(String s) {
                        handler.onProgress(s);
                    }
                });
        req.thenAccept(opt -> {
            if (opt.isPresent()) {
                final ArrayList<MediaMetadataCompat> data = opt.get();
                Log.d(LC, "Fetched library from " + api + ": " + data.size() + " entries.");
                handler.onProgress("Saving entries to metaStorage...");
                // TODO: Possible to perform a smart merge instead?
                clearStorageEntries(api);
                saveLibraryToStorage(data);
                Log.d(LC, "Saved library to metaStorage.");
                handler.onProgress("Indexing entries...");
                reIndex();
                notifyLibraryChanged();
                handler.onSuccess("Successfully fetched "
                        + data.size() + " entries.");
            } else {
                handler.onFailure("Could not fetch library.");
            }
        });
    }

    public void fetchPlayLists(String api, MusicLibraryRequestHandler handler) {
        // TODO: implement
    }
}
