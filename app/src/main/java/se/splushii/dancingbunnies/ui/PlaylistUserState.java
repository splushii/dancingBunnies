package se.splushii.dancingbunnies.ui;

import androidx.core.util.Pair;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

public class PlaylistUserState {
    public final int pos;
    public final int pad;
    public final boolean playlistMode;
    public final PlaylistID playlistID;

    public PlaylistUserState(int pos, int pad) {
        this.pos = pos;
        this.pad = pad;
        this.playlistMode = true;
        this.playlistID = null;
    }

    public PlaylistUserState(PlaylistID playlistID, int pos, int pad) {
        this.pos = pos;
        this.pad = pad;
        this.playlistMode = false;
        this.playlistID = playlistID;
    }

    public PlaylistUserState(PlaylistUserState userState, Pair<Integer, Integer> currentPosition) {
        pos = currentPosition.first;
        pad = currentPosition.second;
        playlistMode = userState.playlistMode;
        playlistID = userState.playlistID;
    }
}
