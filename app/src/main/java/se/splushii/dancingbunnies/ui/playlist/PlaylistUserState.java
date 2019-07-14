package se.splushii.dancingbunnies.ui.playlist;

import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

class PlaylistUserState {
    public final int pos;
    final int pad;
    final boolean showPlaylists;
    final boolean showPlaybackEntries;
    final PlaylistID browsedPlaylistID;

    private PlaylistUserState(int pos,
                              int pad,
                              boolean showPlaylists,
                              boolean showPlaybackEntries,
                              PlaylistID browsedPlaylistID) {
        this.pos = pos;
        this.pad = pad;
        this.showPlaylists = showPlaylists;
        this.showPlaybackEntries = showPlaybackEntries;
        this.browsedPlaylistID = browsedPlaylistID;
    }

    boolean isBrowsedCurrent(PlaylistID currentPlaylistID) {
        if (browsedPlaylistID == null || currentPlaylistID == null) {
            return false;
        }
        return browsedPlaylistID.equals(currentPlaylistID);
    }

    boolean isInitial() {
        return showPlaylists;
    }

    static class Builder {
        private int pos = 0;
        private int pad = 0;
        private boolean showPlaylists = true;
        private boolean showPlaybackEntries = false;
        private PlaylistID browsedPlaylistID = null;

        Builder() {}

        Builder fromState(PlaylistUserState state) {
            pos = state.pos;
            pad = state.pad;
            showPlaylists = state.showPlaylists;
            showPlaybackEntries = state.showPlaybackEntries;
            browsedPlaylistID = state.browsedPlaylistID;
            return this;
        }

        Builder setPos(int pos, int pad) {
            this.pos = pos;
            this.pad = pad;
            return this;
        }

        Builder setBrowsedPlaylist(PlaylistID playlistID) {
            browsedPlaylistID = playlistID;
            showPlaylists = false;
            return this;
        }

        Builder setShowPlaybackEntries(boolean showPlaybackEntries) {
            this.showPlaybackEntries = showPlaybackEntries;
            return this;
        }

        PlaylistUserState build() {
            return new PlaylistUserState(
                    pos,
                    pad,
                    showPlaylists,
                    showPlaybackEntries,
                    browsedPlaylistID
            );
        }
    }
}
