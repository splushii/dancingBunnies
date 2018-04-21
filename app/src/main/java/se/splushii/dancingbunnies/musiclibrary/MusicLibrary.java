package se.splushii.dancingbunnies.musiclibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.APIClientRequestHandler;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;
import se.splushii.dancingbunnies.events.LibraryChangedEvent;
import se.splushii.dancingbunnies.events.SettingsChangedEvent;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.search.Indexer;
import se.splushii.dancingbunnies.search.Searcher;
import se.splushii.dancingbunnies.storage.Storage;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibrary {
    private static String LC = Util.getLogContext(MusicLibrary.class);

    public static final String API_ID_SUBSONIC = "subsonic";
    private static final String LUCENE_INDEX_PATH = "lucene_index";

    private final AudioPlayerService context;
    private HashMap<String, APIClient> apis;
    private Storage storage;
    private ArrayList<Artist> artists = new ArrayList<>();
    private ArrayList<Album> albums = new ArrayList<>();
    private ArrayList<Song> songs = new ArrayList<>();
    private HashMap<String, Artist> artistMap = new HashMap<>();
    private HashMap<String, Album> albumMap= new HashMap<>();
    private HashMap<String, Song> songMap = new HashMap<>();
    private Indexer indexer;
    private Searcher searcher;

    public MusicLibrary(final AudioPlayerService context) {
        this.context = context;
        apis = new HashMap<>();
        loadSettings(context);
        EventBus.getDefault().register(this);
        storage = new Storage(context);
        File indexDirectoryPath = new File(context.getFilesDir(), LUCENE_INDEX_PATH);
        if (!indexDirectoryPath.mkdirs()) {
            Log.w(LC, "Could not create lucene index dir " + indexDirectoryPath.toPath());
        }
        indexer = new Indexer(indexDirectoryPath);
        searcher = new Searcher(indexDirectoryPath);
        Date storageStart = new Date();
        fetchLibraryFromStorage().thenRun(() -> {
            long time = new Date().getTime() - storageStart.getTime();
            Log.d(LC, "Library fetched from storage! Took " + time + "ms");
            Date indexStart = new Date();
            int numDocs = 0;
            Log.d(LC, "Songs: " + songs.size());
            for (Song s: songs) {
                numDocs = indexer.indexSong(s);
            }
            indexer.close();
            time = new Date().getTime() - indexStart.getTime();
            Log.d(LC, "Library indexed (" + numDocs + " docs)! Took " + time + "ms");
            notifyLibraryChanged();
        });
    }

    private void notifyLibraryChanged() {
        // TODO: Notify about ALL changed parents/children?
        EventBus.getDefault().post(new LibraryChangedEvent());
        context.notifyChildrenChanged(AudioPlayerService.MEDIA_ID_ROOT);
    }

    private CompletableFuture<Void> fetchLibraryFromStorage() {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        new Thread(() -> {
            storage.open();
            addToLibrary(storage.getAll(), null);
            storage.close();
            ret.complete(null);
        }).start();
        return ret;
    }

    public void onDestroy() {
        // TODO: saveLibrary();
        EventBus.getDefault().unregister(this);
    }

    public AudioDataSource getAudioData(EntryID entryID) {
        if (apis.containsKey(entryID.src)) {
            return apis.get(entryID.src).getAudioData(entryID);
        } else {
            Log.d(LC, "Could not find api for song with src: " + entryID.src
                    + ", id: " + entryID.id);
            return new AudioDataSource(null, entryID);
        }
    }

    public MediaMetadataCompat getSongMetaData(EntryID entryID) {
        if (entryID.type == LibraryEntry.EntryType.SONG)
            return songMap.get(entryID.key()).meta();
        return null;
    }

    public ArrayList<Artist> artists() {
        return new ArrayList<>(artists);
    }

    public ArrayList<Album> albums() {
        return new ArrayList<>(albums);
    }

    public ArrayList<Song> songs() {
        return new ArrayList<>(songs);
    }

    public ArrayList<? extends LibraryEntry> getSubscriptionEntries(MusicLibraryQuery q) {
        ArrayList<? extends LibraryEntry> entries;
        EntryID entryID = q.entryID();
        switch (entryID.type) {
            case ARTIST:
                entries = artists();
                break;
            case ALBUM:
                entries = albums();
                break;
            case SONG:
                entries = songs();
                break;
            default:
                Log.w(LC, "Unhandled LibraryEntry type: " + entryID.type.name());
                entries = new ArrayList<>();
                break;
        }
        for (LibraryEntry e: entries) {
            if (Objects.equals(entryID.src, e.src()) && Objects.equals(entryID.id, e.id())) {
                return e.getEntries();
            }
        }
        return new ArrayList<>();
    }

    public ArrayList<? extends LibraryEntry> getSearchEntries(String query) {
        ArrayList<Song> entries = new ArrayList<>();
        TopDocs topDocs = searcher.search(query);
        for (ScoreDoc scoreDoc: topDocs.scoreDocs) {
            Document doc = searcher.getDocument(scoreDoc);
            Song song = songMap.get(EntryID.from(doc).key());
            entries.add(song);
        }
        return entries;
    }

    @Subscribe
    public void onMessageEvent(SettingsChangedEvent sce) {
        loadSettings(this.context);
    }

    private void loadSettings(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!settings.getBoolean("pref_subsonic", false)) {
            apis.remove(API_ID_SUBSONIC);
        } else if (!apis.containsKey(API_ID_SUBSONIC)) {
            apis.put(API_ID_SUBSONIC, new SubsonicAPIClient());
        }
        for (String key: apis.keySet()) {
            apis.get(key).loadSettings(context);
        }
    }

    private void addToLibrary(final ArrayList<MediaMetadataCompat> data,
                              final MusicLibraryRequestHandler handler) {
        long start = System.currentTimeMillis();
        Log.d(LC, "addToLibrary start");
        int count = 0;
        int size = data.size();
        for (MediaMetadataCompat meta: data) {
            if (handler != null) {
                handler.onProgress("Adding songs to library: " + count++ + "/" + size + " added...");
            }
            // Check required attributes
            if (!meta.containsKey(Meta.METADATA_KEY_API)) {
                Log.e(LC, "Entry does not contain api. Skipping.");
                continue;
            }
            String src = meta.getString(Meta.METADATA_KEY_API);
            if (!meta.containsKey(Meta.METADATA_KEY_MEDIA_ROOT)) {
                Log.e(LC, "Entry does not contain media root. Skipping.");
                continue;
            }
            String root = meta.getString(Meta.METADATA_KEY_MEDIA_ROOT);
            if (!meta.containsKey(Meta.METADATA_KEY_MEDIA_ID)) {
                Log.e(LC, "Entry does not contain media id. Skipping.");
                continue;
            }
            String id = meta.getString(Meta.METADATA_KEY_MEDIA_ID);
            if (!meta.containsKey(Meta.METADATA_KEY_TITLE)) {
                Log.e(LC, "Entry does not contain title. Skipping.");
                continue;
            }
            String title = meta.getString(Meta.METADATA_KEY_TITLE);

            Song song = new Song(src, id, title, meta);
            String albumArtist = null;
            if (meta.containsKey(Meta.METADATA_KEY_ALBUM_ARTIST)) {
                albumArtist = meta.getString(Meta.METADATA_KEY_ALBUM_ARTIST);
            }

            // Contains artist:
            //   Create artist and/or add to artist
            // Else
            //   Put under <unknown artist>
            String artistName;
            if (meta.containsKey(Meta.METADATA_KEY_ARTIST)) {
                artistName = meta.getString(Meta.METADATA_KEY_ARTIST);
            } else if (albumArtist != null) {
                artistName = albumArtist;
            } else {
                artistName = Meta.METADATA_VALUE_UNKNOWN_ARTIST;
            }
            Artist artist = addToArtist(song, artistName);

            // Contains album:
            //   Contains album artist
            //     Create album and/or add to album and use album artist to build id
            //   Contains artist
            //     Create album and/or add to album and use artist to build id
            //   Else
            //     Create album and/or add to album and use <unknown_artist> to build id
            // Else
            //   Put under <unknown album>
            String albumName;
            if (meta.containsKey(Meta.METADATA_KEY_ALBUM)) {
                albumName = meta.getString(Meta.METADATA_KEY_ALBUM);
            } else {
                albumName = Meta.METADATA_VALUE_UNKNOWN_ALBUM;
            }
            if (albumArtist == null) {
                if (artistName != null) {
                    albumArtist = artistName;
                } else {
                    albumArtist = Meta.METADATA_VALUE_UNKNOWN_ARTIST;
                }
            }
            addToAlbum(song, artist, albumArtist, albumName);

            // Always
            //   Add to songs
            addToSongs(song);
        }
        Collections.sort(artists);
        Collections.sort(albums);
        Collections.sort(songs);
        Log.d(LC, "addToLibrary finish " + (System.currentTimeMillis() - start));
    }

    private CompletableFuture<Void> saveLibraryToStorage() {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        new Thread(() -> {
            long start = System.currentTimeMillis();
            Log.d(LC, "saveLibraryToStorage start");
            storage.open();
            // TODO: Do not remove all entries, only the needed
            storage.clearAll();
            storage.insertSongs(songs());
            storage.close();
            Log.d(LC, "saveLibraryToStorage finish " + (System.currentTimeMillis() - start));
            ret.complete(null);
        }).start();
        return ret;
    }

    private CompletableFuture<Void> clearStorageEntries(final String src) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        new Thread(() -> {
            long start = System.currentTimeMillis();
            Log.d(LC, "clearStorageEntries start");
            storage.open();
            storage.clearAll(src);
            storage.close();
            Log.d(LC, "clearStorageEntries finish " + (System.currentTimeMillis() - start));
            ret.complete(null);
        }).start();
        return ret;
    }

    private synchronized void addToSongs(Song song) {
        if (songMap.containsKey(song.key())) {
            return;
        }
        songs.add(song);
        songMap.put(song.key(), song);
    }

    private synchronized void addToAlbum(Song song, Artist artist, String albumArtist, String albumName) {
        Album album;
        if ((album = albumMap.get(albumName)) == null) {
            album = new Album(albumName, albumArtist);
            albums.add(album);
            albumMap.put(albumName, album);
        }
        album.addRef(song);
        album.addRef(artist);
    }

    private synchronized Artist addToArtist(Song song, String artistName) {
        Artist artist;
        if ((artist = artistMap.get(artistName)) == null) {
            artist = new Artist(artistName);
            artists.add(artist);
            artistMap.put(artistName, artist);
        }
        artist.addRef(song);
        return artist;
    }

    public void fetchAPILibrary(final String api, final MusicLibraryRequestHandler handler) {
        handler.onStart();
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
                addToLibrary(data, handler);
                notifyLibraryChanged();
                handler.onProgress("Saving entries to storage...");
                saveLibraryToStorage().thenRun(() -> handler.onSuccess("Successfully fetched "
                        + data.size() + " entries."));
            } else {
                handler.onFailure("Could not fetch library.");
            }
        });
    }

    public void fetchPlayLists(String api, MusicLibraryRequestHandler handler) {
        // TODO: implement
    }
}
