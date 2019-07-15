package se.splushii.dancingbunnies.ui.nowplaying;

import java.util.ArrayList;
import java.util.List;

import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

class NowPlayingState {
    final List<PlaybackEntry> queue;
    final PlaylistID currentPlaylistID;
    final long currentPlaylistPos;

    private NowPlayingState(List<PlaybackEntry> queue,
                            PlaylistID currentPlaylistID,
                            long currentPlaylistPos) {
        this.queue = queue;
        this.currentPlaylistID = currentPlaylistID;
        this.currentPlaylistPos = currentPlaylistPos;
    }

    static class Builder {
        private List<PlaybackEntry> queue = new ArrayList<>();
        private PlaylistID currentPlaylistID;
        private long currentPlaylistPos;

        Builder() {}

        Builder fromState(NowPlayingState state) {
            queue = state.queue;
            currentPlaylistID = state.currentPlaylistID;
            currentPlaylistPos = state.currentPlaylistPos;
            return this;
        }

        public Builder setQueue(List<PlaybackEntry> queue) {
            this.queue = queue;
            return this;
        }

        Builder setCurrentPlaylist(PlaylistID playlistID) {
            this.currentPlaylistID = playlistID;
            return this;
        }

        Builder setCurrentPlaylistPos(long playlistPos) {
            this.currentPlaylistPos = playlistPos;
            return this;
        }

        NowPlayingState build() {
            return new NowPlayingState(
                    queue,
                    currentPlaylistID,
                    currentPlaylistPos
            );
        }
    }
}
