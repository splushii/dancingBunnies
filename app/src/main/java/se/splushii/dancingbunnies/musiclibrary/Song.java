package se.splushii.dancingbunnies.musiclibrary;

import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;

public class Song extends LibraryEntry {
    private MediaMetadataCompat meta;

    private Song(EntryID entryID, String name, MediaMetadataCompat meta) {
        super(entryID, name);
        this.meta = meta;
    }

    Song(String src, String id, String name, MediaMetadataCompat meta) {
        this(new EntryID(src, id, EntryType.SONG), name, meta);
    }

    public MediaMetadataCompat meta() {
        return meta;
    }

    public ArrayList<? extends LibraryEntry> getEntries() {
        return new ArrayList<>();
    }
}
