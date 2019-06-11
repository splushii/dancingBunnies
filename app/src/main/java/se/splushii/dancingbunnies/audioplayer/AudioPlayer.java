package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

interface AudioPlayer {
    Callback dummyCallback = new Callback() {
        private static final String LC = "AudioPlayer.dummyCallback";

        @Override
        public void onStateChanged(int playBackState) {
            Log.d(LC, "onStateChanged");
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            Log.d(LC, "onMetaChanged");
        }

        @Override
        public void onPreloadChanged() {
            Log.d(LC, "onPreloadChanged");
        }

        @Override
        public void onSongEnded() {
            Log.d(LC, "onSongEnded");
        }
    };

    enum Type {
        LOCAL,
        CAST
    }

    // TODO: Get rid of Queue/Playlist. Simply return PlaybackEntries.
    // TODO: Make PlaybackController take care of that distinction.
    AudioPlayerState getLastState();
    long getSeekPosition();
    int getMaxToPreload();
    int getNumPreloaded();
    int getNumQueueEntries();
    int getNumPlaylistEntries();
    PlaybackEntry getCurrentEntry();
    PlaybackEntry getQueueEntry(int queuePosition);
    PlaybackEntry getPlaylistEntry(int playlistPosition);
    List<PlaybackEntry> getQueueEntries(int maxNum);
    List<PlaybackEntry> getPlaylistEntries(int maxNum);
    CompletableFuture<Void> play();
    CompletableFuture<Void> pause();
    CompletableFuture<Void> stop();
    CompletableFuture<Void> seekTo(long pos);
    CompletableFuture<Void> next();
    CompletableFuture<Void> previous();
    CompletableFuture<Void> queue(List<PlaybackEntry> entries, int offset);
    CompletableFuture<Void> preload(List<PlaybackEntry> entries);
    CompletableFuture<Void> dePreload(List<PlaybackEntry> playbackEntries);
    CompletableFuture<Void> dePreload(int numQueueEntriesToDepreload,
                                      int queueOffset,
                                      int numPlaylistEntriesToDepreload,
                                      int playlistOffset);
    CompletableFuture<Void> initialize();
    CompletableFuture<Void> destroy();

    interface Callback {
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onPreloadChanged();
        void onSongEnded();
    }

    class AudioPlayerState {
        final PlaybackEntry currentEntry;
        final List<PlaybackEntry> history;
        final List<PlaybackEntry> entries;
        final long lastPos;

        AudioPlayerState(PlaybackEntry currentEntry,
                         List<PlaybackEntry> history,
                         List<PlaybackEntry> entries,
                         long lastPos) {
            this.currentEntry = currentEntry;
            this.history = history;
            this.entries = entries;
            this.lastPos = lastPos;
        }
    }
}
