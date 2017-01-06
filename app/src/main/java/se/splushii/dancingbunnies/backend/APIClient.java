package se.splushii.dancingbunnies.backend;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.musiclibrary.Song;

public interface APIClient {
    void setCredentials(String usr, String pwd);
    CompletableFuture<Optional<ArrayList<Artist>>> getArtists(String musicFolderId);
    CompletableFuture<Optional<ArrayList<Album>>> getAlbums(Artist artist);
    CompletableFuture<Optional<ArrayList<Song>>> getSongs(Album album);
}