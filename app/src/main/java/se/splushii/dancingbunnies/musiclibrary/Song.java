package se.splushii.dancingbunnies.musiclibrary;

import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;

public class Song extends LibraryEntry {
    private MediaMetadataCompat meta;

    public Song(String src, String id, String name, MediaMetadataCompat meta) {
        super(new EntryID(src, id, EntryType.SONG), name);
        this.meta = meta;
    }

    public MediaMetadataCompat meta() {
        return meta;
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return new ArrayList<>();
    }
}
