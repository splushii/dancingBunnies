package se.splushii.dancingbunnies.musiclibrary;

import android.app.VoiceInteractor;

import com.loopj.android.http.AsyncHttpResponseHandler;

import java.lang.reflect.Array;
import java.util.ArrayList;

import cz.msebera.android.httpclient.Header;
import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;

import java8.util.function.Consumer;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;

public class MusicLibrary {
    SubsonicAPIClient api;
    private ArrayList<Artist> artists = new ArrayList<>();

    public MusicLibrary() {
        api = new SubsonicAPIClient();
        api.setCredentials("testor", "testodude1");
    }

    public CompletableFuture<Optional<ArrayList<Artist>>> getAllArtists(String musicFolderId, boolean refresh) {
        final CompletableFuture<Optional<ArrayList<Artist>>> req = new CompletableFuture<>();
        if (refresh || artists.size() == 0) {
            api.getArtists(musicFolderId).thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
                @Override
                public void accept(Optional<ArrayList<Artist>> a) {
                    if (a.isPresent()) {
                        setArtists(a.get());
                        req.complete(Optional.of(artists));
                    } else {
                        req.complete(Optional.<ArrayList<Artist>>empty());
                    }
                }
            });
        } else {
            req.complete(Optional.of(artists));
        }
        return req;
    }

    public CompletableFuture<String> getAlbums(final Artist a) {
        CompletableFuture<Optional<ArrayList<Album>>> req = api.getArtist(a.id());
        final CompletableFuture<String> ret = new CompletableFuture<>();
        req.thenAccept(new Consumer<Optional<ArrayList<Album>>>() {
            @Override
            public void accept(Optional<ArrayList<Album>> alba) {
                if (alba.isPresent()) {
                    setAlbums(a, alba.get());
                    ret.complete("");
                } else {
                    ret.complete("Could not get albums for " + a.name());
                }
            }
        });
        return ret;
    }

    public void setArtists(ArrayList<Artist> artists) {
        this.artists = artists;
    }

    private void setAlbums(Artist artist, ArrayList<Album> albums) {
        artist.setAlbums(albums);
    }
}
