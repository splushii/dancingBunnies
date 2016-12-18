package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Artist {
    String id;
    String name;
    int albumCount;
    ArrayList<Album> albums;
    public Artist(String id, String name, int albumCount) {
        this.id = id;
        this.name = name;
        this.albumCount = albumCount;
        albums = new ArrayList<>(albumCount);
    }

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }

    public int albumCount() {
        return albumCount;
    }

    public ArrayList<Album> getAlbums() {
        return albums;
    }

    protected void setAlbums(ArrayList<Album> albums) {
        this.albums = albums;
        albumCount = albums.size();
    }
}
