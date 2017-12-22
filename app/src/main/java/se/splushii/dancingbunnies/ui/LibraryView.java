package se.splushii.dancingbunnies.ui;

import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;

public class LibraryView {
    public final String src;
    public final String parentId;
    public final LibraryEntry.EntryType type;
    public final int pos;
    public final int pad;
    public LibraryView(String src, String parentId, LibraryEntry.EntryType type,
                       int pos, int pad) {
        this.src = src;
        this.parentId = parentId;
        this.type = type;
        this.pos = pos;
        this.pad = pad;
    }
}
