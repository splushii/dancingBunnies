package se.splushii.dancingbunnies.audioplayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

interface AudioPlayer {
    enum Type {
        LOCAL,
        CAST
    }

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
    CompletableFuture<Void> deQueue(List<Integer> positions);
    CompletableFuture<Void> preload(List<PlaybackEntry> entries);
    CompletableFuture<Void> dePreload(int numQueueEntriesToDepreload,
                                      int queueOffset,
                                      int numPlaylistEntriesToDepreload,
                                      int playlistOffset);
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
