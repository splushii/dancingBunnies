package se.splushii.dancingbunnies.events;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.musiclibrary.Artist;

public class ArtistRequestFailEvent {
    public final String api;
    public final String id;
    public final String status;
    public final CompletableFuture<Optional<ArrayList<Artist>>> req;
    public ArtistRequestFailEvent(String api, String id,
                                  CompletableFuture<Optional<ArrayList<Artist>>> req,
                                  String status) {
        this.api = api;
        this.id = id;
        this.req = req;
        this.status = status;
    }
}
