package se.splushii.dancingbunnies.events;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;

public class AlbumRequestFailEvent {
    public final String api;
    public final String id;
    public final Artist artist;
    public final String status;
    public final CompletableFuture<Optional<ArrayList<Album>>> req;
    public AlbumRequestFailEvent(String api, String id, Artist artist,
                                 CompletableFuture<Optional<ArrayList<Album>>> req,
                                 String status) {
        this.api = api;
        this.id = id;
        this.artist = artist;
        this.req = req;
        this.status = status;
    }
}
