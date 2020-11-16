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

        @Override
        public void begToBeDisconnected() {
            Log.d(LC, "disconnect");
        }
    };

    enum Type {
        LOCAL,
        CAST
    }

    CompletableFuture<Void> initialize();
    AudioPlayerState getLastState();
    CompletableFuture<Void> destroy(boolean clearState);

    // History entries are chronologically behind the current entry
    List<PlaybackEntry> getHistory();

    // Current entry is current
    PlaybackEntry getCurrentEntry();
    long getSeekPosition();

    // Preload entries are chronologically in front of the current entry
    List<PlaybackEntry> getPreloadEntries();
    CompletableFuture<Void> preload(List<PlaybackEntry> entries, int offset);
    int getNumPreloaded();
    int getMaxToPreload();

    // Used to remove entries no matter if they're history, current or preload
    CompletableFuture<Void> remove(List<PlaybackEntry> playbackEntries);

    CompletableFuture<Void> play();
    CompletableFuture<Void> pause();
    CompletableFuture<Void> stop();
    CompletableFuture<Void> seekTo(long pos);
    CompletableFuture<Void> next();

    interface Callback {
        void onStateChanged(int playBackState);
        void onCurrentEntryChanged(PlaybackEntry entry);
        void onPreloadChanged();
        void onSongEnded();
        void begToBeDisconnected();
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
