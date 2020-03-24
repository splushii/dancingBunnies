package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AudioPlayer {
    Callback dummyCallback = new Callback() {
        private static final String LC = "AudioPlayer.dummyCallback";

        @Override
        public void onStateChanged(int playBackState) {
            Log.d(LC, "onStateChanged");
        }

        @Override
        public void onCurrentEntryChanged(PlaybackEntry entry) {
            Log.d(LC, "onCurrentEntryChanged");
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

    AudioPlayerState getLastState();
    long getSeekPosition();
    int getMaxToPreload();
    int getNumPreloaded();
    PlaybackEntry getCurrentEntry();
    PlaybackEntry getPreloadEntry(int position);
    List<PlaybackEntry> getPreloadEntries();
    List<PlaybackEntry> getHistory();
    CompletableFuture<Void> play();
    CompletableFuture<Void> pause();
    CompletableFuture<Void> stop();
    CompletableFuture<Void> seekTo(long pos);
    CompletableFuture<Void> next();
    CompletableFuture<Void> preload(List<PlaybackEntry> entries, int offset);
    CompletableFuture<Void> dePreload(List<PlaybackEntry> playbackEntries);
    CompletableFuture<Void> initialize();
    CompletableFuture<Void> destroy();

    interface Callback {
        void onStateChanged(int playBackState);
        void onCurrentEntryChanged(PlaybackEntry entry);
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
