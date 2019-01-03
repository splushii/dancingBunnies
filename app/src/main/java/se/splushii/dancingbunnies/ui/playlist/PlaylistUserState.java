package se.splushii.dancingbunnies.ui.playlist;

import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

class PlaylistUserState {
    public final int pos;
    final int pad;
    final boolean playlistMode;
    public final PlaylistID playlistID;

    PlaylistUserState(int pos, int pad) {
        this.pos = pos;
        this.pad = pad;
        this.playlistMode = true;
        this.playlistID = null;
    }

    PlaylistUserState(PlaylistID playlistID, int pos, int pad) {
        this.pos = pos;
        this.pad = pad;
        this.playlistMode = false;
        this.playlistID = playlistID;
    }

    PlaylistUserState(PlaylistUserState userState, int pos, int pad) {
        this.pos = pos;
        this.pad = pad;
        this.playlistMode = userState.playlistMode;
        this.playlistID = userState.playlistID;
    }
}
