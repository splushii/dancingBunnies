package se.splushii.dancingbunnies.events;

import android.support.v4.media.MediaMetadataCompat;

import java.util.concurrent.ConcurrentLinkedQueue;

import java8.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.APIClientRequestHandler;
import se.splushii.dancingbunnies.backend.SubsonicAPIClient;

public class SubsonicRequestFailEvent {
    public final SubsonicAPIClient.RequestType type;
    public final CompletableFuture<Void> req;
    public final String query;
    public final String status;
    public final ConcurrentLinkedQueue<MediaMetadataCompat> metaList;
    public final APIClientRequestHandler handler;
    public final String musicFolder;

    public SubsonicRequestFailEvent(SubsonicAPIClient.RequestType type,
                                    CompletableFuture<Void> req,
                                    String query,
                                    String musicFolder,
                                    String status,
                                    ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                                    APIClientRequestHandler handler) {
        this.type = type;
        this.req = req;
        this.query = query;
        this.musicFolder = musicFolder;
        this.status = status;
        this.metaList = metaList;
        this.handler = handler;
    }
}
