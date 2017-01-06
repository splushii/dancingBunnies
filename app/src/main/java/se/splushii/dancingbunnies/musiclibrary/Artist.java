package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Artist extends LibraryEntry {
    int albumCount;
    ArrayList<Album> albums;
    public Artist(String id, String name, int albumCount) {
        super(id, name);
        this.albumCount = albumCount;
        albums = new ArrayList<>(albumCount);
    }

    public ArrayList<Album> getAlbums() {
        return albums;
    }

    protected void setAlbums(ArrayList<Album> albums) {
        this.albums = albums;
        albumCount = albums.size();
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return albums;
    }
}
