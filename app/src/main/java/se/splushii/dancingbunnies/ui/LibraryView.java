package se.splushii.dancingbunnies.ui;

import android.util.Pair;

import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;

public class LibraryView {
    public final MusicLibraryQuery query;
    public final int pos;
    public final int pad;
    public LibraryView(MusicLibraryQuery query, int pos, int pad) {
        this.query = query;
        this.pos = pos;
        this.pad = pad;
    }

    public LibraryView(MusicLibraryQuery query, Pair<Integer, Integer> currentPosition) {
        this(query, currentPosition.first, currentPosition.second);
    }
}
