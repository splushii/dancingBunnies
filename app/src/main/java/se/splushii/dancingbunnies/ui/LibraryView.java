package se.splushii.dancingbunnies.ui;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class LibraryView {
    public final EntryID entryID;
    public final int pos;
    public final int pad;
    public LibraryView(EntryID entryID,
                       int pos, int pad) {
        this.entryID = entryID;
        this.pos = pos;
        this.pad = pad;
    }
}
