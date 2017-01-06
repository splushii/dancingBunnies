package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Song extends LibraryEntry {
    private Album album;
    public Song(String id, String name, Album album) {
        super(id, name);
        this.album = album;
    }

    public Album getAlbum() {
        return album;
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return new ArrayList<>();
    }
}
