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
import se.splushii.dancingbunnies.backend.AudioDataHandler;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(LocalAudioPlayer.class);
    private Callback callback;
    private final PlaybackControllerStorage storage;

    private MediaPlayerInstance player;
    private boolean playWhenReady;
    private final Context context;
    // TODO: Make preloadPlayers thread safe (to avoid ConcurrentModificationException)
    private final LinkedList<MediaPlayerInstance> preloadPlayers;
    private final LinkedList<PlaybackEntry> historyPlaybackEntries;

    LocalAudioPlayer(Callback audioPlayerCallback,
                     Context context,
                     PlaybackControllerStorage storage,
                     boolean initFromStorage,
                     boolean playWhenReady) {
        this.callback = audioPlayerCallback;
        this.context = context;
        this.storage = storage;
        this.playWhenReady = playWhenReady;
        preloadPlayers = new LinkedList<>();
        historyPlaybackEntries = new LinkedList<>();
        if (initFromStorage) {
            Pair<PlaybackEntry, Long> currentEntryInfo = storage.getLocalAudioPlayerCurrentEntry();
            if (currentEntryInfo != null) {
                PlaybackEntry playbackEntry = currentEntryInfo.first;
                long lastPos = currentEntryInfo.second == null ? 0L : currentEntryInfo.second;
                player = new MediaPlayerInstance(playbackEntry, mediaPlayerCallback);
                player.seekTo(lastPos);
                MusicLibraryService.downloadAudioData(
                        context,
                        player.playbackEntry.entryID,
                        AudioStorage.DOWNLOAD_PRIO_TOP
                );
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
        AudioStorage.getInstance(context).addDeleteListener(this::onDeleteAudioData);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        Log.d(LC, "initialize");
        Log.d(LC, "initialized");
        return Util.futureResult();
    }

    @Override
    public CompletableFuture<Void> destroy(boolean clearState) {
        Log.d(LC, "destroy");
        AudioStorage.getInstance(context).removeDeleteListener(this::onDeleteAudioData);
        callback = AudioPlayer.dummyCallback;
        return stop()
                .thenCompose(v -> clearState ? clearState() : Util.futureResult())
                .thenRun(() -> Log.d(LC, "destroyed"));
    }

    private void onDeleteAudioData(EntryID entryID) {
        if (player != null && player.playbackEntry.entryID.equals(entryID)) {
            player.stop();
            player.release();
            updatePlaybackState();
        }
        for (MediaPlayerInstance preloadPlayer: preloadPlayers) {
            if (preloadPlayer.playbackEntry.entryID.equals(entryID)) {
                preloadPlayer.release();
            }
        }
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
            return Util.futureResult();
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
            playersToQueue.add(playerInstance);
        }
        preloadPlayers.addAll(offset, playersToQueue);
        for (int i = 0; i < preloadPlayers.size(); i++) {
            MusicLibraryService.downloadAudioData(
                    context,
                    preloadPlayers.get(i).playbackEntry.entryID,
                    AudioStorage.DOWNLOAD_PRIO_MEDIUM + i
            );
        }
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
        return Util.futureResult();
    }

    @Override
    public CompletableFuture<Void> pause() {
        playWhenReady = false;
        if (player == null) {
            updatePlaybackState();
            return Util.futureResult();
        }
        player.pause();
        updatePlaybackState();
        return Util.futureResult();
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
        return Util.futureResult();
    }

    @Override
    public CompletableFuture<Void> next() {
        MediaPlayerInstance previousPlayer = player;
        MediaPlayerInstance nextPlayer = preloadPlayers.poll();
        setCurrentPlayer(nextPlayer);
        setNextPlayer();
        if (previousPlayer != null) {
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
        return Util.futureResult();
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
            return Util.futureResult();
        }
        HashSet<PlaybackEntry> entries = new HashSet<>(playbackEntries);
        List<MediaPlayerInstance> preloadPlayersToRemove = new ArrayList<>();
        for (MediaPlayerInstance mp: preloadPlayers) {
            if (entries.contains(mp.playbackEntry)) {
                preloadPlayersToRemove.add(mp);
            }
        }
        boolean removeCurrentEntry = entries.contains(getCurrentEntry());
        List<PlaybackEntry> historyEntriesToRemove = new ArrayList<>();
        for (PlaybackEntry p: historyPlaybackEntries) {
            if (entries.contains(p)) {
                historyEntriesToRemove.add(p);
            }
        }
        Log.d(LC, "dePreload()"
                + "\nfrom preload: "
                + preloadPlayersToRemove.size()
                + ": " + preloadPlayersToRemove.stream()
                .map(m -> m.playbackEntry.toString())
                .collect(Collectors.joining(", "))
                + "\ncurrent: " + (removeCurrentEntry ? getCurrentEntry() : "no")
                + "\nfrom history: "
                + historyEntriesToRemove.size()
                + ": " + historyEntriesToRemove.stream()
                .map(PlaybackEntry::toString)
                .collect(Collectors.joining(", ")));
        for (MediaPlayerInstance mp: preloadPlayersToRemove) {
            preloadPlayers.remove(mp);
        }
        if (removeCurrentEntry) {
            setCurrentPlayer(preloadPlayers.poll());
        }
        if (removeCurrentEntry || !preloadPlayersToRemove.isEmpty()) {
            setNextPlayer();
        }
        for (PlaybackEntry p: historyEntriesToRemove) {
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
        if (player != null) {
            player.pause();
            player.seekTo(0);
            // TODO: Also cancel the download
            player.release();
        }
        player = mediaPlayerInstance;
        Log.d(LC, "setCurrentPlayer: " + (player == null ? "null" : player.title()));
        if (player == null) {
            callback.onCurrentEntryChanged(null);
        } else {
            MusicLibraryService.downloadAudioData(
                    context,
                    player.playbackEntry.entryID,
                    AudioStorage.DOWNLOAD_PRIO_TOP);
            player.getReady();
            callback.onCurrentEntryChanged(player.playbackEntry);
        }
    }

    private void setNextPlayer() {
        if (player == null) {
            return;
        }
        MediaPlayerInstance nextPlayer = preloadPlayers.peek();
        if (nextPlayer != null) {
            nextPlayer.getReady();
        }
        player.setNext(nextPlayer);
    }

    private boolean isCurrentPlayer(MediaPlayerInstance mediaPlayerInstance) {
        return mediaPlayerInstance.equals(player);
    }

    private boolean isNextPlayer(MediaPlayerInstance mediaPlayerInstance) {
        MediaPlayerInstance nextPlayer = preloadPlayers.peek();
        return mediaPlayerInstance.equals(nextPlayer);
    }

    private int getQueueIndex(MediaPlayerInstance instance) {
        return preloadPlayers.indexOf(instance);
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
        public void getAudioData(MediaPlayerInstance instance,
                                 EntryID entryID,
                                 AudioDataHandler audioDataHandler) {
            MusicLibraryService.getAudioData(
                    context,
                    entryID,
                    isCurrentPlayer(instance) ?
                            AudioStorage.DOWNLOAD_PRIO_TOP :
                            AudioStorage.DOWNLOAD_PRIO_MEDIUM + getQueueIndex(instance),
                    audioDataHandler
            );
        }
    };

    interface MediaPlayerCallback {
        void onBuffering(MediaPlayerInstance instance);
        void onPrepared(MediaPlayerInstance instance);
        void onPlaybackCompleted(MediaPlayerInstance instance);
        void getAudioData(MediaPlayerInstance instance,
                          EntryID entryID,
                          AudioDataHandler audioDataHandler);
    }
}
