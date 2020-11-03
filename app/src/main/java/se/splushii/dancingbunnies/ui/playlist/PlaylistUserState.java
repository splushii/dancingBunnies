package se.splushii.dancingbunnies.ui.playlist;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

class PlaylistUserState {
    final boolean showPlaylists;
    final boolean showPlaybackEntries;
    final EntryID browsedPlaylistID;
    final int playlistPos;
    final int playlistPad;
    final int playlistEntriesPos;
    final int playlistEntriesPad;
    final int playlistPlaybackEntriesPos;
    final int playlistPlaybackEntriesPad;
    final boolean scrollPlaylistPlaybackToPlaylistPos;

    private PlaylistUserState(boolean showPlaylists,
                              boolean showPlaybackEntries,
                              EntryID browsedPlaylistID,
                              int playlistPos,
                              int playlistPad,
                              int playlistEntriesPos,
                              int playlistEntriesPad,
                              int playlistPlaybackEntriesPos,
                              int playlistPlaybackEntriesPad,
                              boolean scrollPlaylistPlaybackToPlaylistPos) {
        this.showPlaylists = showPlaylists;
        this.showPlaybackEntries = showPlaybackEntries;
        this.browsedPlaylistID = browsedPlaylistID;
        this.playlistPos = playlistPos;
        this.playlistPad = playlistPad;
        this.playlistEntriesPos = playlistEntriesPos;
        this.playlistEntriesPad = playlistEntriesPad;
        this.playlistPlaybackEntriesPos = playlistPlaybackEntriesPos;
        this.playlistPlaybackEntriesPad = playlistPlaybackEntriesPad;
        this.scrollPlaylistPlaybackToPlaylistPos = scrollPlaylistPlaybackToPlaylistPos;
    }

    boolean isBrowsedCurrent(EntryID currentPlaylistID) {
        if (browsedPlaylistID == null || currentPlaylistID == null) {
            return false;
        }
        return browsedPlaylistID.equals(currentPlaylistID);
    }

    static class Builder {
        private boolean showPlaylists = true;
        private boolean showPlaybackEntries = false;
        private EntryID browsedPlaylistID = null;
        private int playlistPos;
        private int playlistPad;
        private int playlistEntriesPos;
        private int playlistEntriesPad;
        private int playlistPlaybackEntriesPos;
        private int playlistPlaybackEntriesPad;
        private boolean scrollPlaylistPlaybackToPlaylistPos;

        Builder() {}

        Builder fromState(PlaylistUserState state) {
            showPlaylists = state.showPlaylists;
            showPlaybackEntries = state.showPlaybackEntries;
            browsedPlaylistID = state.browsedPlaylistID;
            playlistPos = state.playlistPos;
            playlistPad = state.playlistPad;
            playlistEntriesPos = state.playlistEntriesPos;
            playlistEntriesPad = state.playlistEntriesPad;
            playlistPlaybackEntriesPos = state.playlistPlaybackEntriesPos;
            playlistPlaybackEntriesPad = state.playlistPlaybackEntriesPad;
            scrollPlaylistPlaybackToPlaylistPos = state.scrollPlaylistPlaybackToPlaylistPos;
            return this;
        }

        Builder setShowPlaylists(boolean showPlaylists) {
            this.showPlaylists = showPlaylists;
            return this;
        }

        Builder setBrowsedPlaylist(EntryID playlistID) {
            browsedPlaylistID = playlistID;
            showPlaylists = false;
            return this;
        }

        Builder setShowPlaylistPlaybackEntries(boolean showPlaybackEntries) {
            this.showPlaybackEntries = showPlaybackEntries;
            return this;
        }

        Builder setPlaylistScroll(int pos, int pad) {
            this.playlistPos = pos;
            this.playlistPad = pad;
            return this;
        }

        Builder setPlaylistEntriesScroll(int pos, int pad) {
            this.playlistEntriesPos = pos;
            this.playlistEntriesPad = pad;
            return this;
        }

        Builder setPlaylistPlaybackEntriesScroll(int pos, int pad) {
            this.playlistPlaybackEntriesPos = pos;
            this.playlistPlaybackEntriesPad = pad;
            return this;
        }

        Builder setScrollPlaylistPlaybackToPlaylistPos(boolean scrollPlaylistPlaybackToPlaylistPos) {
            this.scrollPlaylistPlaybackToPlaylistPos = scrollPlaylistPlaybackToPlaylistPos;
            return this;
        }

        PlaylistUserState build() {
            return new PlaylistUserState(
                    showPlaylists,
                    showPlaybackEntries,
                    browsedPlaylistID,
                    playlistPos,
                    playlistPad,
                    playlistEntriesPos,
                    playlistEntriesPad,
                    playlistPlaybackEntriesPos,
                    playlistPlaybackEntriesPad,
                    scrollPlaylistPlaybackToPlaylistPos
            );
        }
    }
}
