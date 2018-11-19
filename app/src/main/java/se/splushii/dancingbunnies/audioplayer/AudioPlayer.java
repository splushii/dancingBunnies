package se.splushii.dancingbunnies.audioplayer;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

abstract class AudioPlayer {
    static AudioPlayerState EmptyState = new AudioPlayer.AudioPlayerState(
            new LinkedList<>(),
            new LinkedList<>(),
            0
    );

    enum Type {
        LOCAL,
        CAST
    }

    final Callback controller;

    AudioPlayer(Callback controller) {
        this.controller = controller;
    }
    abstract CompletableFuture<Void> checkPreload();
    abstract AudioPlayerState getLastState();
    abstract long getSeekPosition();
    abstract List<PlaybackEntry> getPreloadedQueueEntries(int maxNum);
    abstract List<PlaybackEntry> getPreloadedPlaylistEntries(int maxNum);
    abstract CompletableFuture<Void> queue(List<PlaybackEntry> playbackEntry, int toPosition);
    abstract CompletableFuture<Void> dequeue(long[] positions);
    abstract CompletableFuture<Void> moveQueueItems(long[] positions, int toPosition);
    abstract CompletableFuture<Void> play();
    abstract CompletableFuture<Void> pause();
    abstract CompletableFuture<Void> stop();
    abstract CompletableFuture<Void> seekTo(long pos);
    abstract CompletableFuture<Void> next();
    abstract CompletableFuture<Void> skipItems(int offset);
    abstract CompletableFuture<Void> previous();

    interface Callback {
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onPreloadChanged();
        void dePreloadQueueEntries(List<PlaybackEntry> queueEntries, int offset);
        void dePreloadPlaylistEntries(List<PlaybackEntry> playlistEntries);
        int getNumQueueEntries();
        List<PlaybackEntry> requestPreload(int num);
        PlaybackEntry getQueueEntry(int offset);
        PlaybackEntry consumeQueueEntry(int offset);
        PlaybackEntry consumePlaylistEntry();
    }

    public class AudioPlayerException extends Throwable {
        String msg;
        AudioPlayerException(String msg) {
            super(msg);
            this.msg = msg;
        }

        @Override
        public String toString() {
            return msg;
        }
    }

    CompletableFuture<Void> actionResult(String error) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (error != null) {
            result.completeExceptionally(new AudioPlayerException(error));
            return result;
        }
        result.complete(null);
        return result;
    }

    static class AudioPlayerState {
        final List<PlaybackEntry> history;
        final List<PlaybackEntry> entries;
        final long lastPos;

        AudioPlayerState(List<PlaybackEntry> history,
                         List<PlaybackEntry> entries,
                         long lastPos) {
            this.history = history;
            this.entries = entries;
            this.lastPos = lastPos;
        }
    }
}
