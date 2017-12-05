package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Artist extends LibraryEntry {
    public Artist(String name) {
        super("dancingbunnies", name, EntryType.ARTIST, name);
    }

    public ArrayList<Album> getAlbums() {
        ArrayList<Album> albums = new ArrayList<>();
        for (LibraryEntry e: getRefs(EntryType.ALBUM)) {
            albums.add((Album) e);
        }
        return albums;
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return getAlbums();
    }
}
