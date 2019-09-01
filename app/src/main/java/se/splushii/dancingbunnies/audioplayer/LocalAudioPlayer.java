package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.core.util.Pair;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(LocalAudioPlayer.class);
    private Callback callback;
    private final PlaybackControllerStorage storage;

    private MediaPlayerInstance player;
    private boolean playWhenReady = false;
    private final Context context;
    private final LinkedList<MediaPlayerInstance> preloadPlayers;
    private final LinkedList<PlaybackEntry> historyPlaybackEntries;

    LocalAudioPlayer(Callback audioPlayerCallback,
                     Context context,
                     PlaybackControllerStorage storage,
                     boolean initFromStorage) {
        this.callback = audioPlayerCallback;
        this.context = context;
        this.storage = storage;
        preloadPlayers = new LinkedList<>();
        historyPlaybackEntries = new LinkedList<>();
        if (initFromStorage) {
            Pair<PlaybackEntry, Long> currentEntryInfo = storage.getLocalAudioPlayerCurrentEntry();
            if (currentEntryInfo != null) {
                PlaybackEntry playbackEntry = currentEntryInfo.first;
                long lastPos = currentEntryInfo.second == null ? 0L : currentEntryInfo.second;
                player = new MediaPlayerInstance(playbackEntry, mediaPlayerCallback);
                player.seekTo(lastPos);
                callback.onCurrentEntryChanged(playbackEntry);
            }
            storage.getLocalAudioPlayerQueueEntries()
                    .thenApply(entries -> {
                        addEntries(entries, preloadPlayers.size());
                        return null;
                    })
                    .thenCompose(aVoid -> storage.getLocalAudioPlayerHistoryEntries())
                    .thenApply(entries -> {
                        historyPlaybackEntries.addAll(entries);
                        return null;
                    })
                    .join();
        } else {
            clearState().join();
        }
    }

    @Override
    public CompletableFuture<Void> initialize() {
        Log.d(LC, "initialize");
        Log.d(LC, "initialized");
        return Util.futureResult(null);
    }

    @Override
    public CompletableFuture<Void> destroy() {
        Log.d(LC, "destroy");
        callback = AudioPlayer.dummyCallback;
        return stop()
                .thenCompose(v -> clearState())
                .thenRun(() -> Log.d(LC, "destroyed"));
    }

    private CompletableFuture<Void> clearState() {
        storage.removeLocalAudioPlayerCurrent();
        return storage.removeAll(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PRELOAD)
                .thenCompose(aVoid -> storage.removeAll(
                        PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY
                ));
    }

    @Override
    public int getMaxToPreload() {
        return 3;
    }

    @Override
    public CompletableFuture<Void> preload(List<PlaybackEntry> entries, int offset) {
        if (entries.isEmpty() || offset < 0 || offset > preloadPlayers.size()) {
            return Util.futureResult(null);
        }
        addEntries(entries, offset);
        if (player == null) {
            return next();
        }
        return persistState()
                .thenRun(() -> callback.onPreloadChanged());
    }

    private void addEntries(List<PlaybackEntry> entries, int offset) {
        List<MediaPlayerInstance> playersToQueue = new LinkedList<>();
        for (PlaybackEntry entry: entries) {
            entry.setPreloaded(true);
            MediaPlayerInstance playerInstance = new MediaPlayerInstance(
                    entry,
                    mediaPlayerCallback
            );
            MusicLibraryService.downloadAudioData(context, playerInstance.playbackEntry.entryID);
            playersToQueue.add(playerInstance);
        }
        preloadPlayers.addAll(offset, playersToQueue);
        setNextPlayer();
    }

    public int getNumPreloaded() {
        return preloadPlayers.size();
    }

    @Override
    public CompletableFuture<Void> play() {
        playWhenReady = true;
        if (player == null) {
            return next();
        }
        player.play();
        updatePlaybackState();
        return Util.futureResult(null);
    }

    @Override
    public CompletableFuture<Void> pause() {
        playWhenReady = false;
        if (player == null) {
            updatePlaybackState();
            return Util.futureResult(null);
        }
        player.pause();
        updatePlaybackState();
        return Util.futureResult(null);
    }

    @Override
    public CompletableFuture<Void> stop() {
        playWhenReady = false;
        if (player == null) {
            return Util.futureResult("Player is null");
        }
        player.stop();
        // TODO: Also cancel any download
        preloadPlayers.forEach(MediaPlayerInstance::stop);
        updatePlaybackState();
        return Util.futureResult(null);
    }

    @Override
    public CompletableFuture<Void> next() {
        MediaPlayerInstance previousPlayer = player;
        MediaPlayerInstance nextPlayer = preloadPlayers.poll();
        setCurrentPlayer(nextPlayer);
        setNextPlayer();
        if (previousPlayer != null) {
            previousPlayer.pause();
            previousPlayer.seekTo(0);
            // TODO: Also cancel the download
            previousPlayer.release();
            historyPlaybackEntries.addFirst(previousPlayer.playbackEntry);
        }
        CompletableFuture<Void> ret = persistState();
        if (player != null && playWhenReady) {
            ret = ret.thenCompose(aVoid -> play());
        }
        return ret.thenRun(() -> {
            updatePlaybackState();
            callback.onPreloadChanged();
        });
    }

    @Override
    public AudioPlayerState getLastState() {
        long lastPos = player == null ? 0 : player.getCurrentPosition();
        PlaybackEntry currentEntry = player == null ? null : player.playbackEntry;
        List<PlaybackEntry> entries = preloadPlayers.stream()
                .map(p -> p.playbackEntry)
                .collect(Collectors.toList());
        return new AudioPlayerState(currentEntry, historyPlaybackEntries, entries, lastPos);
    }

    private CompletableFuture<Void> persistState() {
        return storage.replaceWith(
                PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PRELOAD,
                preloadPlayers.stream()
                        .map(p -> p.playbackEntry)
                        .collect(Collectors.toList())
        ).thenCompose(aVoid -> storage.replaceWith(
                PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY,
                historyPlaybackEntries
        )).thenRun(() -> {
            if (player == null) {
                storage.removeLocalAudioPlayerCurrent();
            } else {
                storage.setLocalAudioPlayerCurrent(
                        player.playbackEntry,
                        player.getCurrentPosition()
                );
            }
        });
    }

    @Override
    public CompletableFuture<Void> seekTo(long pos) {
        if (player == null) {
            return Util.futureResult("Player is null");
        }
        player.seekTo(pos);
        updatePlaybackState();
        return Util.futureResult(null);
    }

    @Override
    public long getSeekPosition() {
        if (player == null) {
            return 0;
        }
        return player.getCurrentPosition();
    }

    @Override
    public PlaybackEntry getCurrentEntry() {
        return player == null ? null : player.playbackEntry;
    }

    @Override
    public PlaybackEntry getPreloadEntry(int position) {
        if (position < 0 || position > preloadPlayers.size()) {
            return null;
        }
        return preloadPlayers.get(position).playbackEntry;
    }

    @Override
    public List<PlaybackEntry> getPreloadEntries() {
        return preloadPlayers.stream()
                .map(m -> m.playbackEntry)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlaybackEntry> getHistory() {
        return new ArrayList<>(historyPlaybackEntries);
    }

    @Override
    public CompletableFuture<Void> dePreload(List<PlaybackEntry> playbackEntries) {
        if (playbackEntries.isEmpty()) {
            return Util.futureResult(null);
        }
        HashSet<PlaybackEntry> entries = new HashSet<>(playbackEntries);
        List<MediaPlayerInstance> mediaPlayersToRemove = new ArrayList<>();
        for (MediaPlayerInstance mp: preloadPlayers) {
            if (entries.contains(mp.playbackEntry)) {
                mediaPlayersToRemove.add(mp);
            }
        }
        for (MediaPlayerInstance mp: mediaPlayersToRemove) {
            preloadPlayers.remove(mp);
        }
        setNextPlayer();
        List<PlaybackEntry> playbackEntriesToRemove = new ArrayList<>();
        for (PlaybackEntry p: historyPlaybackEntries) {
            if (entries.contains(p)) {
                playbackEntriesToRemove.add(p);
            }
        }
        for (PlaybackEntry p: playbackEntriesToRemove) {
            historyPlaybackEntries.remove(p);
        }
        return persistState()
                .thenRun(() -> callback.onPreloadChanged());
    }

    private void updatePlaybackState() {
        if (player == null) {
            callback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
            return;
        }
        callback.onStateChanged(player.getPlaybackState());
    }

    private void setCurrentPlayer(MediaPlayerInstance mediaPlayerInstance) {
        if (player == mediaPlayerInstance) {
            return;
        }
        player = mediaPlayerInstance;
        Log.d(LC, "setCurrentPlayer: " + (player == null ? "null" : player.title()));
        if (player == null) {
            callback.onCurrentEntryChanged(null);
        } else {
            player.getReady();
            callback.onCurrentEntryChanged(player.playbackEntry);
        }
    }

    private void setNextPlayer() {
        if (player == null) {
            return;
        }
        MediaPlayerInstance nextPlayer = preloadPlayers.peek();
        if (nextPlayer == null) {
            player.setNext(null);
            return;
        }
        nextPlayer.getReady();
        player.setNext(nextPlayer);
    }

    private boolean isCurrentPlayer(MediaPlayerInstance mediaPlayerInstance) {
        return mediaPlayerInstance.equals(player);
    }

    private boolean isNextPlayer(MediaPlayerInstance mediaPlayerInstance) {
        MediaPlayerInstance nextPlayer = preloadPlayers.peek();
        return mediaPlayerInstance.equals(nextPlayer);
    }

    private MediaPlayerCallback mediaPlayerCallback = new MediaPlayerCallback() {
        @Override
        public void onBuffering(MediaPlayerInstance instance) {
            if (isCurrentPlayer(instance)) {
                callback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
            }
        }

        @Override
        public void onPrepared(MediaPlayerInstance instance) {
            if (isCurrentPlayer(instance)) {
                Log.d(LC, "onPrepared: " + instance.title());
                if (playWhenReady) {
                    play();
                } else {
                    updatePlaybackState();
                }
            }
            if (isNextPlayer(instance)) {
                if (player != null) {
                    Log.d(LC, "Setting " + instance.playbackEntry.entryID + ")"
                            + " after current " + player.playbackEntry.entryID);
                    player.setNext(instance);
                }
            }
        }

        @Override
        public void onPlaybackCompleted(MediaPlayerInstance instance) {
            if (isCurrentPlayer(instance)) {
                callback.onSongEnded();
                next();
            } else {
                Log.e(LC, "This should never happen...");
            }
        }

        @Override
        public void getAudioData(EntryID entryID,
                                 AudioDataDownloadHandler audioDataDownloadHandler) {
            MusicLibraryService.getAudioData(context, entryID, audioDataDownloadHandler);
        }
    };

    interface MediaPlayerCallback {
        void onBuffering(MediaPlayerInstance instance);
        void onPrepared(MediaPlayerInstance instance);
        void onPlaybackCompleted(MediaPlayerInstance instance);
        void getAudioData(EntryID entryID, AudioDataDownloadHandler audioDataDownloadHandler);
    }
}
