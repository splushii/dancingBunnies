package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public class Album extends LibraryEntry {
    public Album(String name, String albumArtist) {
        super(new EntryID("dancingbunnies", name, EntryType.ALBUM), name);
    }

    public ArrayList<Song> getSongs() {
        ArrayList<Song> songs = new ArrayList<>();
        for (LibraryEntry e: getRefs(EntryType.SONG)) {
            songs.add((Song) e);
        }
        return songs;
    }

    public ArrayList<Artist> getArtists() {
        ArrayList<Artist> artists = new ArrayList<>();
        for (LibraryEntry e: getRefs(EntryType.ARTIST)) {
            artists.add((Artist) e);
        }
        return artists;
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return getSongs();
    }
}
