package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;


// TODO: Break out controller logic into PlaybackController?
abstract class AudioPlayer {
    private static final String LC = Util.getLogContext(AudioPlayer.class);

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
    abstract int getMaxToPreload();
    abstract CompletableFuture<Void> checkPreload();
    abstract AudioPlayerState getLastState();
    abstract long getSeekPosition();
    abstract List<PlaybackEntry> getPreloadedQueueEntries(int maxNum);
    abstract List<PlaybackEntry> getPreloadedPlaylistEntries(int maxNum);
    protected abstract PlaybackEntry getQueueEntry(int queuePosition);
    protected abstract int getNumQueueEntries();
    protected abstract int getNumPlaylistEntries();
    protected abstract CompletableFuture<Void> dePreload(int numQueueEntriesToDepreload,
                                                         int queueOffset,
                                                         int numPlaylistEntriesToDepreload,
                                                         int playlistOffset);
    protected abstract CompletableFuture<Void> playerQueue(List<PlaybackEntry> newEntriesToQueue,
                                                           int newEntriesToQueueOffset);
    abstract CompletableFuture<Void> dequeue(long[] positions);
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
        void onQueueChanged();
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

    int getNumToDepreload(int queueSize,
                          int playlistSize,
                          int numNewEntries,
                          int numToPreload) {
        return queueSize + playlistSize + numNewEntries - numToPreload;
    }

    int getNumQueueEntriesToQueue(int toPosition,
                                  int numToDepreload,
                                  List<PlaybackEntry> newEntries) {
        if (toPosition >= getMaxToPreload() - 1
                || newEntries.isEmpty()
                || numToDepreload >= newEntries.size() ) {
            return 0;
        }
        return numToDepreload <= 0 ? newEntries.size() : newEntries.size() - numToDepreload;
    }

    int getNumPlaylistEntriesToDepreload(int numToDepreload, int numPlaylistEntries) {
        if (numToDepreload <= 0 || numPlaylistEntries <= 0) {
            return 0;
        }
        return Integer.min(numToDepreload, numPlaylistEntries);
    }

    int getNumQueueEntriesToDepreload(int numToDepreload,
                                      int toPosition,
                                      int numQueueEntries) {
        if (numToDepreload <= 0
                || numQueueEntries <= 0
                || toPosition >= getMaxToPreload()
                || toPosition == AudioPlayerService.QUEUE_LAST) {
            return 0;
        }
        int numQueueEntriesToDepreload = Integer.min(getMaxToPreload() - toPosition, numQueueEntries);
        return Integer.min(numQueueEntriesToDepreload, numToDepreload);
    }

    List<PlaybackEntry> getQueueEntriesToFill(int toPosition, int numQueueEntries) {
        List<PlaybackEntry> entries = new LinkedList<>();
        int maxToFill = toPosition == AudioPlayerService.QUEUE_LAST ?
                getMaxToPreload() - 1 : toPosition;
        for (int i = 0; numQueueEntries + i < maxToFill; i++) {
            PlaybackEntry entry = controller.consumeQueueEntry(0);
            if (entry == null) {
                Log.w(LC, "Could not get enough items from controller.");
                break;
            }
            entries.add(entry);
        }
        return entries;
    }

    CompletableFuture<Void> moveQueueItems(long[] positions, int toPosition) {
        if (positions.length <= 0 || toPosition < 0) {
            return actionResult(null);
        }
        Arrays.sort(positions);
        int numPlayerQueueEntries = getNumQueueEntries();
        List<PlaybackEntry> moveEntries = new LinkedList<>();
        for (long queuePosition: positions) {
            if (queuePosition < 0) {
                Log.e(LC, "Can not dequeue negative index: " + queuePosition);
                continue;
            }
            PlaybackEntry entry;
            if (queuePosition < numPlayerQueueEntries) {
                entry = getQueueEntry((int) queuePosition);
            } else {
                entry = controller.getQueueEntry((int) queuePosition - numPlayerQueueEntries);
            }
            moveEntries.add(entry);
        }
        Log.d(LC, "moveQueueItems(" + Arrays.toString(positions) + ", " + toPosition + ")"
                + "\nmoveEntries: " + moveEntries.toString());
        return dequeue(positions)
                .thenCompose(v -> queue(moveEntries, toPosition))
                .thenRun(controller::onQueueChanged);
    }

    CompletableFuture<Void> queue(List<PlaybackEntry> entries, int toPosition) {
        if (entries == null || entries.isEmpty()) {
            return actionResult(null);
        }
        List<PlaybackEntry> playlistEntries = getPreloadedPlaylistEntries(Integer.MAX_VALUE);
        List<PlaybackEntry> queueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE);
        int numPlaylistEntries = playlistEntries.size();
        int numQueueEntries = queueEntries.size();
        int numNewEntries = entries.size();
        int maxToPreload = getMaxToPreload();

        int numToDepreload = getNumToDepreload(
                numQueueEntries,
                numPlaylistEntries,
                numNewEntries,
                maxToPreload
        );

        // Get player playlist entries to de-preload
        int numPlaylistEntriesToDepreload = getNumPlaylistEntriesToDepreload(
                numToDepreload,
                numPlaylistEntries
        );
        int playlistEntriesToDepreloadOffset = numPlaylistEntries - numPlaylistEntriesToDepreload;
        List<PlaybackEntry> playlistEntriesToDePreload = playlistEntries.subList(
                playlistEntriesToDepreloadOffset,
                playlistEntriesToDepreloadOffset + numPlaylistEntriesToDepreload
        );
        numToDepreload -= numPlaylistEntriesToDepreload;

        // Get player queue entries to de-preload
        int numQueueEntriesToDepreload = getNumQueueEntriesToDepreload(
                numToDepreload,
                toPosition,
                numQueueEntries
        );
        int queueEntriesToDepreloadOffset = numQueueEntries - numQueueEntriesToDepreload;
        List<PlaybackEntry> queueEntriesToDePreload = queueEntries.subList(
                queueEntriesToDepreloadOffset,
                queueEntriesToDepreloadOffset + numQueueEntriesToDepreload
        );
        numToDepreload -= numQueueEntriesToDepreload;

        // Fill player with queue entries if needed
        List<PlaybackEntry> filledQueueEntries = getQueueEntriesToFill(
                toPosition,
                numQueueEntries
        );
        List<PlaybackEntry> newEntries = new LinkedList<>(filledQueueEntries);
        newEntries.addAll(entries);
        if (toPosition != AudioPlayerService.QUEUE_LAST) {
            toPosition = Integer.max(toPosition - filledQueueEntries.size(), 0);
        }
        numToDepreload += filledQueueEntries.size();

        // Get new entries to queue in the player
        List<PlaybackEntry> newEntriesToQueue = getNewEntriesToQueue(
                toPosition,
                numToDepreload,
                newEntries
        );
        int newEntriesToQueueOffset = toPosition == AudioPlayerService.QUEUE_LAST ?
                numQueueEntries : toPosition;

        // Get new entries to queue (de-preload) in the controller
        List<PlaybackEntry> newEntriesToDepreload = getNewEntriesToDepreload(
                newEntriesToQueue.size(),
                newEntries
        );
        int newEntriesToDepreloadOffset = getDepreloadOffset(
                toPosition,
                numQueueEntries,
                numQueueEntriesToDepreload,
                newEntriesToQueue.size(),
                filledQueueEntries.size()
        );

        Log.d(LC, "queue()"
                + "\nQueue entries to de-preload: "
                + numQueueEntriesToDepreload
                + "\nPlaylist entries to de-preload: "
                + numPlaylistEntriesToDepreload
                + "\nFilled from controller: " + filledQueueEntries.size()
                + "\nNew entries to queue: " + newEntriesToQueue.size()
                + " at " + newEntriesToQueueOffset
                + "\nNew entries to de-preload: " + newEntriesToDepreload.size()
                + " at " + newEntriesToDepreloadOffset);
        return dePreload(
                numQueueEntriesToDepreload,
                queueEntriesToDepreloadOffset,
                numPlaylistEntriesToDepreload,
                playlistEntriesToDepreloadOffset
        ).thenRun(() -> {
            if (!playlistEntriesToDePreload.isEmpty()) {
                controller.dePreloadPlaylistEntries(playlistEntriesToDePreload);
                controller.onQueueChanged();
            }
            if (!queueEntriesToDePreload.isEmpty()) {
                controller.dePreloadQueueEntries(queueEntriesToDePreload, 0);
                controller.onQueueChanged();
            }
        }).thenCompose(v -> newEntriesToQueue.isEmpty() ? CompletableFuture.completedFuture(null) :
                playerQueue(
                        newEntriesToQueue,
                        newEntriesToQueueOffset
                )
        ).thenRun(() -> {
            if (newEntriesToDepreload.size() > 0) {
                controller.dePreloadQueueEntries(
                        newEntriesToDepreload,
                        newEntriesToDepreloadOffset
                );
                controller.onQueueChanged();
            }
        });
    }

    List<PlaybackEntry> getNewEntriesToQueue(int toPosition,
                                             int numToDepreload,
                                             List<PlaybackEntry> newEntries) {
        int numToQueue = getNumQueueEntriesToQueue(toPosition, numToDepreload, newEntries);
        return numToQueue <= 0 ? Collections.emptyList() : newEntries.subList(0, numToQueue);
    }

    private List<PlaybackEntry> getNewEntriesToDepreload(int numToQueue,
                                                         List<PlaybackEntry> newEntries) {
        if (numToQueue < 0 || numToQueue >= newEntries.size()) {
            return Collections.emptyList();
        }
        return newEntries.subList(numToQueue, newEntries.size());
    }

    int getDepreloadOffset(int toPosition,
                           int numQueueEntries,
                           int numQueueEntriesToDepreload,
                           int numNewEntriesToQueue,
                           int numFilledFromController) {
        if (toPosition == AudioPlayerService.QUEUE_LAST) {
            return AudioPlayerService.QUEUE_LAST;
        }
        int targetQueueSize = numQueueEntries
                - numQueueEntriesToDepreload
                + numNewEntriesToQueue
                + numFilledFromController;
        return toPosition < targetQueueSize ? 0 : toPosition - targetQueueSize;
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
