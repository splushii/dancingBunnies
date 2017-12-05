package se.splushii.dancingbunnies.events;

public class PlaySongEvent {
    public final String src;
    public final String id;
    public PlaySongEvent(String src, String id) {
        this.src = src;
        this.id = id;
    }
}
