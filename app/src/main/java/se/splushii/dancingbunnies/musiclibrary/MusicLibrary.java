package se.splushii.dancingbunnies.musiclibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;

import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;
import se.splushii.dancingbunnies.events.AlbumRequestFailEvent;
import se.splushii.dancingbunnies.events.ArtistRequestFailEvent;
import se.splushii.dancingbunnies.events.SongRequestFailEvent;

public class MusicLibrary {
    public static String API_ID_SUBSONIC = "subsonic";
    private static final Integer REQ_RETRY_COUNT = 3;
    private HashMap<String, APIClient> apis;
    private HashMap<String, Integer> retries;
    private ArrayList<Artist> artists = new ArrayList<>();
    private ArrayList<Album> albums = new ArrayList<>();
    private ArrayList<Song> songs = new ArrayList<>();

    public MusicLibrary(Context context) {
        apis = new HashMap<>();
        retries = new HashMap<>();
        loadSettings(context);
        EventBus.getDefault().register(this);
    }

    @Subscribe
    public void onMessageEvent(final SongRequestFailEvent e) {
        Integer retryCount;
        if ((retryCount = retries.get(e.id)) == null) {
            retryCount = 1;
        }
        final CompletableFuture<Optional<ArrayList<Song>>> pastReq = e.req;
        String status = "SongRequestFailEvent: Album " + e.album.name() + ".";
        if (retryCount > REQ_RETRY_COUNT) {
            Log.d("MusicLibrary",  status + " No more retries.");
            pastReq.complete(Optional.<ArrayList<Song>>empty());
            return;
        }
        retries.put(e.id, retryCount + 1);
        Log.d("MusicLibrary", status + " Retry " + retryCount + "/" + REQ_RETRY_COUNT);
        APIClient c = apis.get(e.api);
        if (c == null) {
            pastReq.complete(Optional.<ArrayList<Song>>empty());
            return;
        }
        CompletableFuture<Optional<ArrayList<Song>>> req = c.getSongs(e.album);
        req.thenAccept(new Consumer<Optional<ArrayList<Song>>>() {
            @Override
            public void accept(Optional<ArrayList<Song>> arrayListOptional) {
                pastReq.complete(arrayListOptional);
            }
        });
    }

    @Subscribe
    public void onMessageEvent(final AlbumRequestFailEvent e) {
        Integer retryCount;
        if ((retryCount = retries.get(e.id)) == null) {
            retryCount = 1;
        }
        final CompletableFuture<Optional<ArrayList<Album>>> pastReq = e.req;
        String status = "AlbumRequestFailEvent: Artist " + e.artist.name() + ".";
        if (retryCount > REQ_RETRY_COUNT) {
            Log.d("MusicLibrary",  status + " No more retries.");
            pastReq.complete(Optional.<ArrayList<Album>>empty());
            return;
        }
        retries.put(e.id, retryCount + 1);
        Log.d("MusicLibrary", status + " Retry " + retryCount + "/" + REQ_RETRY_COUNT);
        APIClient c = apis.get(e.api);
        if (c == null) {
            pastReq.complete(Optional.<ArrayList<Album>>empty());
            return;
        }
        CompletableFuture<Optional<ArrayList<Album>>> req = c.getAlbums(e.artist);
        req.thenAccept(new Consumer<Optional<ArrayList<Album>>>() {
            @Override
            public void accept(Optional<ArrayList<Album>> arrayListOptional) {
                pastReq.complete(arrayListOptional);
            }
        });
    }

    @Subscribe
    public void onMessageEvent(final ArtistRequestFailEvent e) {
        Integer retryCount;
        if ((retryCount = retries.get(e.id)) == null) {
            retryCount = 1;
        }
        final CompletableFuture<Optional<ArrayList<Artist>>> pastReq = e.req;
        String status = "ArtistRequestFailEvent:";
        if (retryCount > REQ_RETRY_COUNT) {
            Log.d("MusicLibrary",  status + " No more retries.");
            pastReq.complete(Optional.<ArrayList<Artist>>empty());
            return;
        }
        retries.put(e.id, retryCount + 1);
        Log.d("MusicLibrary", status + " Retry " + retryCount + "/" + REQ_RETRY_COUNT);
        APIClient c = apis.get(e.api);
        if (c == null) {
            pastReq.complete(Optional.<ArrayList<Artist>>empty());
            return;
        }
        CompletableFuture<Optional<ArrayList<Artist>>> req = c.getArtists();
        req.thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
            @Override
            public void accept(Optional<ArrayList<Artist>> arrayListOptional) {
                pastReq.complete(arrayListOptional);
            }
        });
    }

    public CompletableFuture<String> fetchAllArtists(boolean force) {
        final CompletableFuture<String> ret = new CompletableFuture<>();
        if (force || artists.size() == 0) {
            final ArrayList<CompletableFuture<String>> reqs = new ArrayList<>();
            artists.clear();
            for (String key: apis.keySet()) {
                final String api = key;
                APIClient c = apis.get(api);
                final CompletableFuture<String> reqsret = new CompletableFuture<>();
                reqs.add(reqsret);
                CompletableFuture<Optional<ArrayList<Artist>>> req = c.getArtists();
                req.thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
                    @Override
                    public void accept(Optional<ArrayList<Artist>> a) {
                        if (a.isPresent()) {
                            artists.addAll(a.get());
                            reqsret.complete("");
                        } else {
                            reqsret.complete("Could not get artists from backend: " + api);
                        }
                    }
                });
            }
            CompletableFuture<Void> req = CompletableFuture.allOf(reqs.toArray(new CompletableFuture[reqs.size()]));
            req.thenAccept(new Consumer<Object>() {
               @Override
               public void accept(Object o) {
                   ret.complete("");
               }
               // TODO: how to report failure with allOf?
           });
        } else {
            ret.complete("");
        }
        return ret;
    }

    public CompletableFuture<String> fetchAlbums(final Artist artist, boolean force) {
        final CompletableFuture<String> ret = new CompletableFuture<>();
        if (force || albums.size() == 0) {
            final ArrayList<CompletableFuture<String>> reqs = new ArrayList<>();
            albums.clear();
            for (String key: apis.keySet()) {
                final String api = key;
                APIClient c = apis.get(api);
                final CompletableFuture<String> reqsret = new CompletableFuture<>();
                reqs.add(reqsret);
                CompletableFuture<Optional<ArrayList<Album>>> req = c.getAlbums(artist);
                req.thenAccept(new Consumer<Optional<ArrayList<Album>>>() {
                    @Override
                    public void accept(Optional<ArrayList<Album>> a) {
                        if (a.isPresent()) {
                            albums.addAll(a.get());
                            reqsret.complete("");
                        } else {
                            reqsret.complete("Could not get albums from " + artist.name() +
                                    " using backend: " + api);
                        }
                    }
                });
            }
            CompletableFuture<Void> req = CompletableFuture.allOf(reqs.toArray(new CompletableFuture[reqs.size()]));
            req.thenAccept(new Consumer<Object>() {
                @Override
                public void accept(Object o) {
                    ret.complete("");
                }
                // TODO: how to report failure with allOf?
            });
        } else {
            ret.complete("");
        }
        return ret;
    }

    public CompletableFuture<String> fetchSongs(final Album album, boolean force) {
        final CompletableFuture<String> ret = new CompletableFuture<>();
        if (force || songs.size() == 0) {
            final ArrayList<CompletableFuture<String>> reqs = new ArrayList<>();
            songs.clear();
            for (String key: apis.keySet()) {
                final String api = key;
                APIClient c = apis.get(api);
                final CompletableFuture<String> reqsret = new CompletableFuture<>();
                reqs.add(reqsret);
                CompletableFuture<Optional<ArrayList<Song>>> req = c.getSongs(album);
                req.thenAccept(new Consumer<Optional<ArrayList<Song>>>() {
                    @Override
                    public void accept(Optional<ArrayList<Song>> a) {
                        if (a.isPresent()) {
                            songs.addAll(a.get());
                            reqsret.complete("");
                        } else {
                            reqsret.complete("Could not get songs from " + album.name() +
                                    " using backend: " + api);
                        }
                    }
                });
            }
            CompletableFuture<Void> req = CompletableFuture.allOf(reqs.toArray(new CompletableFuture[reqs.size()]));
            req.thenAccept(new Consumer<Object>() {
                @Override
                public void accept(Object o) {
                    ret.complete("");
                }
                // TODO: how to report failure with allOf?
            });
        } else {
            ret.complete("");
        }
        return ret;
    }

    public AudioDataSource getAudioData(Song song) {
        if (apis.containsKey(song.src())) {
            return apis.get(song.src()).getAudioData(song);
        } else {
            System.out.println("Could not find api for song " + song.name());
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

    public void loadSettings(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!settings.getBoolean("pref_subsonic", false)) {
            apis.remove(API_ID_SUBSONIC);
        } else if (!apis.containsKey(API_ID_SUBSONIC)) {
            apis.put(API_ID_SUBSONIC, new SubsonicAPIClient(context));
        }
        for (String key: apis.keySet()) {
            apis.get(key).loadSettings(context);
        }
    }
}
