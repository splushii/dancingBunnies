package se.splushii.dancingbunnies.ui;

import android.util.Pair;

import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;

public class MusicLibraryUserState {
    public final MusicLibraryQuery query;
    public final int pos;
    public final int pad;
    public MusicLibraryUserState(MusicLibraryQuery query, int pos, int pad) {
        this.query = query;
        this.pos = pos;
        this.pad = pad;
    }

    public MusicLibraryUserState(MusicLibraryQuery query, Pair<Integer, Integer> currentPosition) {
        this(query, currentPosition.first, currentPosition.second);
    }
}
