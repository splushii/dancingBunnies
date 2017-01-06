package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Album extends LibraryEntry {
    int songCount;
    Artist artist;
    ArrayList<Song> songs;
    public Album(String id, String name, Artist artist, int songCount) {
        super(id, name);
        this.songCount = songCount;
        this.artist = artist;
        songs = new ArrayList<>(songCount);
    }

    protected void setSongs(ArrayList<Song> songs) {
        this.songs = songs;
        songCount = songs.size();
    }

    public ArrayList<Song> getSongs() {
        return songs;
    }

    public Artist getArtist() {
        return artist;
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return songs;
    }
}
