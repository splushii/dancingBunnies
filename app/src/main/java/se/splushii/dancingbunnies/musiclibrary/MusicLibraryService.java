package se.splushii.dancingbunnies.musiclibrary;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
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

import androidx.annotation.Nullable;
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

    private final IBinder binder = new MusicLibraryBinder();

    public List<PlaybackEntry> playlistGetNext(PlaylistID playlistID, long index, int maxEntries) {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        Playlist playlist = playlistStorage.getPlaylist(playlistID);
        if (playlist == null) {
            return playbackEntries;
        }
        List<EntryID> entryIDS = new LinkedList<>();
        switch (playlist.id.type) {
            case PlaylistID.TYPE_STUPID:
                entryIDS.addAll(((StupidPlaylist) playlist).getEntries());
                break;
            case PlaylistID.TYPE_SMART:
                Log.e(LC, "playlistGetNext not implemented for smart playlists");
                break;
        }
        if (entryIDS.isEmpty()) {
            return playbackEntries;
        }
        for (int count = 0; count < maxEntries && index < entryIDS.size(); count++, index++) {
            EntryID entryID = entryIDS.get((int)index);
            Meta meta = getSongMetaData(entryID);
            playbackEntries.add(new PlaybackEntry(meta, PlaybackEntry.USER_TYPE_PLAYLIST));
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
        Playlist playlist = playlistStorage.getPlaylist(playlistID);
        if (playlist == null) {
            return 0;
        }
        switch (playlist.id.type) {
            case PlaylistID.TYPE_STUPID:
                int playlistSize = ((StupidPlaylist) playlist).getEntries().size();
                playlistPosition += offset;
                return playlistPosition > playlistSize ? playlistSize : playlistPosition;
            case PlaylistID.TYPE_SMART:
                Log.e(LC, "playlistGetNext not implemented for smart playlists");
                break;
        }
        return 0;
    }

    public List<PlaylistItem> getPlaylists() {
        return playlistStorage.getPlaylists();
    }

    public List<LibraryEntry> getPlaylistEntries(PlaylistID playlistID) {
        Playlist playlist = playlistStorage.getPlaylist(playlistID);
        switch (playlist.id.type) {
            case PlaylistID.TYPE_STUPID:
                List<LibraryEntry> entries = new LinkedList<>();
                for (EntryID entryID: ((StupidPlaylist) playlist).getEntries()) {
                    Meta meta = getSongMetaData(entryID);
                    String title = meta.getString(Meta.METADATA_KEY_TITLE);
                    entries.add(new LibraryEntry(entryID, title));
                }
                return entries;
            case PlaylistID.TYPE_SMART:
                Log.e(LC, "getPlaylistEntries not implemented for smart playlists");
                break;
        }
        return new LinkedList<>();
    }

    public void playlistAddEntries(PlaylistID playlistID, List<EntryID> entryIDs) {
        playlistStorage.addToPlaylist(playlistID, entryIDs);
    }

    public void playlistRemoveEntry(PlaylistID playlistID, int position) {
        playlistStorage.removeFromPlaylist(playlistID, position);
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
        playlistStorage = new PlaylistStorage(this);
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
        playlistStorage.close();
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
        List<Meta> songs = metaStorage.getMetadataEntries(null);
        Log.d(LC, "Songs: " + songs.size());
        for (Meta meta: songs) {
            numDocs = indexer.indexSong(meta);
        }
        indexer.close();
        long time = System.currentTimeMillis() - startTime;
        Log.d(LC, "Library indexed (" + numDocs + " docs)! Took " + time + "ms");
    }

    private void notifyLibraryChanged() {
        // TODO: Notify about ALL changed parents/children?
        Log.e(LC, "notifyLibraryChange not implemented");
    }

    public String getAudioURL(EntryID entryID) {
        if (!apis.containsKey(entryID.src)) {
            return null;
        }
        AudioDataSource audioDataSource = apis.get(entryID.src).getAudioData(entryID);
        return audioDataSource.getURL();
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

    public Meta getSongMetaData(EntryID entryID) {
        if (EntryID.UnknownEntryID.equals(entryID)) {
            return Meta.UNKNOWN_ENTRY;
        }
        // TODO: Add cache here?
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
                handler.onProgress("Indexing entries...");
                reIndex();
                notifyLibraryChanged();
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
