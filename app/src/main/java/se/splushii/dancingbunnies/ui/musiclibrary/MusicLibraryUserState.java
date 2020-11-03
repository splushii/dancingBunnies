package se.splushii.dancingbunnies.ui.musiclibrary;

import androidx.core.util.Pair;
import se.splushii.dancingbunnies.musiclibrary.Query;

public class MusicLibraryUserState {
    public final Query query;
    public final int pos;
    final int pad;

    MusicLibraryUserState(Query query, int pos, int pad) {
        this.query = query;
        this.pos = pos;
        this.pad = pad;
    }

    MusicLibraryUserState(Query query, Pair<Integer, Integer> currentPosition) {
        this(query, currentPosition.first, currentPosition.second);
    }
}
