package se.splushii.dancingbunnies.events;

import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class PlaySongEvent {
    public final MusicLibrary lib;
    public final Song song;
    public PlaySongEvent(MusicLibrary lib, Song s) {
        this.lib = lib;
        this.song = s;
    }
}
