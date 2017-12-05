package se.splushii.dancingbunnies.events;

import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;

public class LibraryEvent {
    public final String api;
    public enum LibraryAction {
        FETCH_LIBRARY,
        FETCH_PLAYLISTS
    }
    public final LibraryAction action;
    public final MusicLibraryRequestHandler handler;
    public LibraryEvent(String api, LibraryAction action, MusicLibraryRequestHandler handler) {
        this.api = api;
        this.action = action;
        this.handler = handler;
    }
}
