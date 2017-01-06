package se.splushii.dancingbunnies.musiclibrary;

import android.support.v4.app.Fragment;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;

import java8.util.function.Consumer;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;

public class MusicLibrary {
    private APIClient api;
    private ArrayList<Artist> artists = new ArrayList<>();
    private ArrayList<Album> albums = new ArrayList<>();
    private ArrayList<Song> songs = new ArrayList<>();

    public MusicLibrary(Fragment fragment) {
        api = new SubsonicAPIClient(fragment);
        api.setCredentials("testor", "testodude");
    }

    public CompletableFuture<Optional<ArrayList<Artist>>> getAllArtists(String musicFolderId, boolean refresh) {
        final CompletableFuture<Optional<ArrayList<Artist>>> ret = new CompletableFuture<>();
        if (refresh || artists.size() == 0) {
            CompletableFuture<Optional<ArrayList<Artist>>> req = api.getArtists(musicFolderId);
            req.thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
        @Override
        public void accept(Optional<ArrayList<Artist>> a) {
                if (a.isPresent()) {
                    setArtists(a.get());
                    ret.complete(Optional.of(artists));
                } else {
                    ret.complete(Optional.<ArrayList<Artist>>empty());
                }
                }
            });
        } else {
            ret.complete(Optional.of(artists));
        }
        return ret;
    }

    public CompletableFuture<String> getAlbums(final Artist artist, boolean refresh) {
        final CompletableFuture<String> ret = new CompletableFuture<>();
        if (refresh || albums.size() == 0) {
            this.albums.clear();
            CompletableFuture<Optional<ArrayList<Album>>> req = api.getAlbums(artist);
            req.thenAccept(new Consumer<Optional<ArrayList<Album>>>() {
                @Override
                public void accept(Optional<ArrayList<Album>> a) {
                    if (a.isPresent()) {
                        setAlbums(artist, a.get());
                        ret.complete("");
                    } else {
                        ret.complete("Could not get albums for " + artist.name());
                    }
                }
            });
        } else {
            ret.complete("");
        }
        return ret;
    }

    public CompletableFuture<String> getSongs(final Album album, boolean refresh) {
        final CompletableFuture<String> ret = new CompletableFuture<>();
        if (refresh || songs.size() == 0) {
            this.songs.clear();
            CompletableFuture<Optional<ArrayList<Song>>> req = api.getSongs(album);
            req.thenAccept(new Consumer<Optional<ArrayList<Song>>>() {
                @Override
                public void accept(Optional<ArrayList<Song>> a) {
                    if (a.isPresent()) {
                        setSongs(album, a.get());
                        ret.complete("");
                    } else {
                        ret.complete("Could not get songs for " + album.name());
                    }
                }
            });
        } else {
            ret.complete("");
        }
        return ret;
    }

    public void setArtists(ArrayList<Artist> artists) {
        this.artists = artists;
    }

    private void setAlbums(Artist artist, ArrayList<Album> albums) {
        artist.setAlbums(albums);
        this.albums.addAll(albums);
    }

    private void setSongs(Album album, ArrayList<Song> songs) {
        album.setSongs(songs);
        this.songs.addAll(songs);
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
}
