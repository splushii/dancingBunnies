package se.splushii.dancingbunnies.events;

public class PlaybackEvent {
    public enum PlaybackAction {
        PLAY,
        PAUSE,
        STOP,
        NEXT,
        PREVIOUS,
        SEEK
    }
    public final PlaybackAction action;
    public PlaybackEvent(PlaybackAction action) {
        this.action = action;
    }
}
