package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.Song;

public abstract class APIClient {
    public abstract boolean hasLibrary();
    public CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> getLibrary() {
        CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> ret = new CompletableFuture<>();
        ret.complete(Optional.<ArrayList<MediaMetadataCompat>>empty());
        return ret;
    }
    public abstract boolean hasPlaylists();
    public CompletableFuture<Optional<Playlist>> getPlaylists() {
        CompletableFuture<Optional<Playlist>> ret = new CompletableFuture<>();
        ret.complete(Optional.<Playlist>empty());
        return ret;
    }
    public abstract AudioDataSource getAudioData(Song song);
    public abstract void loadSettings(Context context);

    // TODO: Deprecate
    public abstract CompletableFuture<Optional<ArrayList<Artist>>> getArtists();
    public abstract CompletableFuture<Optional<ArrayList<Album>>> getAlbums(Artist artist);
    public abstract CompletableFuture<Optional<ArrayList<Song>>> getSongs(Album album);

}