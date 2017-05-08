package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Album extends LibraryEntry {
    public Album(String src, String id, String name, Artist artist) {
        super(src, id, EntryType.ALBUM, name);
        setParent(artist);
    }

    public ArrayList<Song> getSongs() {
        ArrayList<Song> songs = new ArrayList<>();
        for (LibraryEntry e: getRefs(EntryType.SONG)) {
            songs.add((Song) e);
        }
        return songs;
    }

    public Artist getArtist() {
        return (Artist) getParent();
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return getSongs();
    }
}
