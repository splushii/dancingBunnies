package se.splushii.dancingbunnies.events;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class SongRequestFailEvent {
    public final String api;
    public final String id;
    public final Album album;
    public final String status;
    public final CompletableFuture<Optional<ArrayList<Song>>> req;
    public SongRequestFailEvent(String api, String id, Album album,
                                CompletableFuture<Optional<ArrayList<Song>>> req,
                                String status) {
        this.api = api;
        this.id = id;
        this.album = album;
        this.req = req;
        this.status = status;
    }
}
