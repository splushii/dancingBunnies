package se.splushii.dancingbunnies.events;

import se.splushii.dancingbunnies.musiclibrary.Song;

public class PlaySongEvent {
    public final Song song;
    public PlaySongEvent(Song s) {
        this.song = s;
    }
}
