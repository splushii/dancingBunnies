package se.splushii.dancingbunnies.musiclibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.APIClientRequestHandler;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;
import se.splushii.dancingbunnies.events.SettingsChangedEvent;
import se.splushii.dancingbunnies.storage.Storage;
import se.splushii.dancingbunnies.util.Util;

// TODO: BIG TIME: Do not hog the main thread. Do heavy stuff in separate threads.
public class MusicLibrary {
    private static String LC = Util.getLogContext(MusicLibrary.class);

    public static final String API_ID_ANY = "any";
    public static final String API_ID_SUBSONIC = "subsonic";

    private final Context context;
    private HashMap<String, APIClient> apis;
    private Storage storage;
    private ArrayList<Artist> artists = new ArrayList<>();
    private ArrayList<Album> albums = new ArrayList<>();
    private ArrayList<Song> songs = new ArrayList<>();
    private HashMap<String, Artist> artistMap = new HashMap<>();
    private HashMap<String, Album> albumMap= new HashMap<>();
    private HashMap<String, Song> songMap = new HashMap<>();

    public MusicLibrary(Context context) {
        this.context = context;
        apis = new HashMap<>();
        loadSettings(context);
        EventBus.getDefault().register(this);
        storage = new Storage(context);
        fetchLibraryFromStorage();
    }

    private void fetchLibraryFromStorage() {
        storage.open();
        addToLibrary(storage.getAll(), new MusicLibraryRequestHandler() {
            @Override
            public void onStart() {
                Log.i(LC, "Fetching library from storage...");
            }

            @Override
            public void onSuccess(String status) {
                Log.i(LC, "Successfully fetched library from storage: " + status);
                storage.close();
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC, "Could not fetch library from storage.");
                storage.close();
            }
        });
    }

    public void onDestroy() {
        // TODO: saveLibrary();
        EventBus.getDefault().unregister(this);
    }

    public AudioDataSource getAudioData(String src, String id) {
        if (apis.containsKey(src)) {
            return apis.get(src).getAudioData(id);
        } else {
            Log.d(LC, "Could not find api for song with src: " + src + ", id: " + id);
            return new AudioDataSource(null);
        }
    }

    public ArrayList<Artist> artists() {
        return artists;
    }

    public ArrayList<Album> albums() {
        return albums;
    }

    public ArrayList<Song> songs() {
        return songs;
    }

    public ArrayList<? extends LibraryEntry> getEntries(MusicLibraryQuery q) {
        ArrayList<? extends LibraryEntry> entries;
        switch (q.type) {
            case ARTIST:
                entries = artists;
                break;
            case ALBUM:
                entries = albums;
                break;
            case SONG:
                entries = songs;
                break;
            default:
                Log.w(LC, "Unhandled LibraryEntry type: " + q.type.name());
                entries = new ArrayList<>();
                break;
        }
        for (LibraryEntry e: entries) {
            if (Objects.equals(q.src, e.src()) && Objects.equals(q.id, e.id())) {
                return e.getEntries();
            }
        }
        return new ArrayList<>();
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
        int count = 0;
        int size = data.size();
        for (MediaMetadataCompat meta: data) {
            handler.onProgress("Adding songs to library: " + count++ + "/" + size + " added...");
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
                artistName = Meta.METADATA_VALUE_UNKOWN_ARTIST;
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
                    albumArtist = Meta.METADATA_VALUE_UNKOWN_ARTIST;
                }
            }
            addToAlbum(song, artist, albumArtist, albumName);

            // Always
            //   Add to songs
            addToSongs(song);
        }
    }

    private void saveLibraryToStorage() {
        storage.open();
        for (Song song: songs) {
            storage.insertSong(song);
        }
        storage.close();
    }

    private synchronized void addToSongs(Song song) {
        if (songMap.containsKey(song.key())) {
            return;
        }
        songs.add(song);
        Collections.sort(songs);
        songMap.put(song.key(), song);
    }

    private synchronized void addToAlbum(Song song, Artist artist, String albumArtist, String albumName) {
        Album album = null;
        if ((album = albumMap.get(albumName)) == null) {
            album = new Album(albumName, albumArtist);
            albums.add(album);
            Collections.sort(albums);
            albumMap.put(albumName, album);
        }
        album.addRef(song);
        album.addRef(artist);
    }

    private synchronized Artist addToArtist(Song song, String artistName) {
        Artist artist = null;
        if ((artist = artistMap.get(artistName)) == null) {
            artist = new Artist(artistName);
            artists.add(artist);
            Collections.sort(artists);
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
        req.thenAccept(new Consumer<Optional<ArrayList<MediaMetadataCompat>>>() {
            @Override
            public void accept(Optional<ArrayList<MediaMetadataCompat>> opt) {
                if (opt.isPresent()) {
                    final ArrayList<MediaMetadataCompat> data = opt.get();
                    Log.d(LC, "Fetched library from " + api + ": " + data.size() + " entries.");
                    // TODO: Do not forget to remove old entries
                    addToLibrary(data, handler);
                    saveLibraryToStorage();
                    handler.onSuccess("Successfully fetched " + data.size() + " entries.");
                } else {
                    handler.onFailure("Could not fetch library.");
                }
            }
        });
    }

    public void fetchPlayLists(String api, MusicLibraryRequestHandler handler) {
        // TODO: implement
    }

}
