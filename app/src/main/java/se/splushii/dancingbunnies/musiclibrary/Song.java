package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Song extends LibraryEntry {
    public Song(String src, String id, String name, Album album) {
        super(src, id, EntryType.SONG, name);
        setParent(album);
    }

    public Album getAlbum() {
        return (Album) getParent();
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return new ArrayList<>();
    }
}
