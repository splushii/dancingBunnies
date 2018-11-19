package se.splushii.dancingbunnies.audioplayer;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
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
    abstract CompletableFuture<Optional<String>> checkPreload();
    abstract AudioPlayerState getLastState();
    abstract long getSeekPosition();
    abstract List<PlaybackEntry> getPreloadedQueueEntries(int maxNum);
    abstract List<PlaybackEntry> getPreloadedPlaylistEntries(int maxNum);
    abstract CompletableFuture<Optional<String>> queue(List<PlaybackEntry> playbackEntry, int toPosition);
    abstract CompletableFuture<Optional<String>> dequeue(long[] positions);
    abstract CompletableFuture<Optional<String>> moveQueueItems(long[] positions, int toPosition);
    abstract CompletableFuture<Optional<String>> play();
    abstract CompletableFuture<Optional<String>> pause();
    abstract CompletableFuture<Optional<String>> stop();
    abstract CompletableFuture<Optional<String>> seekTo(long pos);
    abstract CompletableFuture<Optional<String>> next();
    abstract CompletableFuture<Optional<String>> skipItems(int offset);
    abstract CompletableFuture<Optional<String>> previous();

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

    CompletableFuture<Optional<String>> actionResult(String error) {
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        if (error != null) {
            result.complete(Optional.of(error));
            return result;
        }
        result.complete(Optional.empty());
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
