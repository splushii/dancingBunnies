package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(LocalAudioPlayer.class);
    private final Callback callback;
    private final PlaybackControllerStorage storage;

    private MediaPlayerInstance player;
    private boolean playWhenReady = false;
    private final MusicLibraryService musicLibraryService;
    private final LinkedList<MediaPlayerInstance> queuePlayers;
    private final LinkedList<MediaPlayerInstance> playlistPlayers;
    private final LinkedList<MediaPlayerInstance> historyPlayers;

    LocalAudioPlayer(Callback audioPlayerCallback,
                     MusicLibraryService musicLibraryService,
                     PlaybackControllerStorage storage,
                     boolean initFromStorage) {
        this.callback = audioPlayerCallback;
        this.musicLibraryService = musicLibraryService;
        this.storage = storage;
        queuePlayers = new LinkedList<>();
        playlistPlayers = new LinkedList<>();
        historyPlayers = new LinkedList<>();
        if (initFromStorage) {
            EntryID entryID = storage.getLocalAudioPlayerCurrentEntry();
            long lastPos = storage.getLocalAudioPlayerCurrentLastPos();
            String title = musicLibraryService.getSongMetaData(entryID)
                    .getString(Meta.METADATA_KEY_TITLE);
            if (entryID != null) {
                player = new MediaPlayerInstance(new PlaybackEntry(
                        entryID,
                        PlaybackEntry.USER_TYPE_QUEUE,
                        title
                ), mediaPlayerCallback, musicLibraryService);
                player.seekTo(lastPos);
            }
            storage.getLocalAudioPlayerQueueEntries()
                    .thenApply(entries -> {
                        addEntries(entries);
                        return null;
                    })
                    .thenCompose(aVoid -> storage.getLocalAudioPlayerPlaylistEntries())
                    .thenApply(entries -> {
                        addEntries(entries);
                        return null;
                    })
                    .thenCompose(aVoid -> storage.getLocalAudioPlayerHistoryEntries())
                    .thenApply(entries -> {
                        historyPlayers.addAll(entries.stream().map(entry -> {
                            MediaPlayerInstance instance = new MediaPlayerInstance(
                                    entry,
                                    mediaPlayerCallback,
                                    musicLibraryService
                            );
                            instance.release();
                            return instance;
                        }).collect(Collectors.toList()));
                        return null;
                    })
                    .join();
        } else {
            clearState().join();
        }
    }

    @Override
    public CompletableFuture<Void> destroy() {
        return stop()
                .thenCompose(v -> clearState());
    }

    private CompletableFuture<Void> clearState() {
        storage.removeLocalAudioPlayerCurrent();
        return storage.removeAll(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_QUEUE)
                .thenCompose(aVoid -> storage.removeAll(
                        PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PLAYLIST
                ))
                .thenCompose(aVoid -> storage.removeAll(
                        PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY
                ));
    }

    @Override
    public int getMaxToPreload() {
        return 3;
    }

    @Override
    public CompletableFuture<Void> preload(List<PlaybackEntry> entries) {
        addEntries(entries);
        CompletableFuture<Void> ret = CompletableFuture.completedFuture(null);
        if (player == null) {
            ret = ret.thenCompose(v -> next());
        }
        return ret.thenCompose(v -> {
            callback.onPreloadChanged();
            return persistState();
        });
    }

    private void addEntries(List<PlaybackEntry> entries) {
        for (PlaybackEntry entry: entries) {
            MediaPlayerInstance playerInstance = new MediaPlayerInstance(
                    entry,
                    mediaPlayerCallback,
                    musicLibraryService
            );
            musicLibraryService.downloadAudioData(playerInstance.playbackEntry.entryID);
            if (entry.playbackType.equals(PlaybackEntry.USER_TYPE_QUEUE)) {
                queuePlayers.add(playerInstance);
            } else if (entry.playbackType.equals(PlaybackEntry.USER_TYPE_PLAYLIST)){
                playlistPlayers.add(playerInstance);
            } else {
                Log.e(LC, "Unknown playback entry type (" + entry.playbackType + "): "
                        + entry.toString());
            }
        }
    }

    public int getNumPreloaded() {
        return queuePlayers.size() + playlistPlayers.size();
    }

    @Override
    public CompletableFuture<Void> play() {
        playWhenReady = true;
        if (player == null) {
            return Util.futureResult("Player is null");
        }
        player.play();
        updatePlaybackState();
        return Util.futureResult(null);
    }

    @Override
    public CompletableFuture<Void> pause() {
        playWhenReady = false;
        if (player == null) {
            return Util.futureResult("Player is null");
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
        for (MediaPlayerInstance mediaPlayerInstance: queuePlayers) {
            mediaPlayerInstance.stop();
        }
        for (MediaPlayerInstance mediaPlayerInstance: playlistPlayers) {
            mediaPlayerInstance.stop();
        }
        updatePlaybackState();
        return Util.futureResult(null);
    }

    @Override
    public CompletableFuture<Void> next() {
        MediaPlayerInstance previousPlayer = player;
        MediaPlayerInstance nextPlayer = queuePlayers.poll();
        if (nextPlayer == null) {
            nextPlayer = playlistPlayers.poll();
        }
        setCurrentPlayer(nextPlayer);
        if (previousPlayer != null) {
            previousPlayer.pause();
            previousPlayer.seekTo(0);
            // TODO: Also cancel the download
            previousPlayer.release();
            historyPlayers.add(previousPlayer);
        }
        updatePlaybackState();
        callback.onPreloadChanged();
        CompletableFuture<Void> persist = persistState();
        return playWhenReady ? persist.thenCompose(aVoid -> play()) : persist;
    }

    @Override
    public AudioPlayerState getLastState() {
        long lastPos = player == null ? 0 : player.getCurrentPosition();
        List<PlaybackEntry> history = historyPlayers.stream()
                .map(p -> p.playbackEntry).collect(Collectors.toCollection(LinkedList::new));
        List<PlaybackEntry> entries = new LinkedList<>();
        PlaybackEntry currentEntry = player == null ? null : player.playbackEntry;
        entries.addAll(queuePlayers.stream().map(p -> p.playbackEntry).collect(Collectors.toList()));
        entries.addAll(playlistPlayers.stream().map(p -> p.playbackEntry).collect(Collectors.toList()));
        return new AudioPlayerState(currentEntry, history, entries, lastPos);
    }

    private CompletableFuture<Void> persistState() {
        return clearState()
                .thenCompose(aVoid -> storage.insert(
                        PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_QUEUE,
                        0,
                        queuePlayers.stream()
                                .map(p -> p.playbackEntry.entryID)
                                .collect(Collectors.toList())
                ))
                .thenCompose(aVoid -> storage.insert(
                        PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PLAYLIST,
                        0,
                        playlistPlayers.stream()
                                .map(p -> p.playbackEntry.entryID)
                                .collect(Collectors.toList())
                ))
                .thenCompose(aVoid -> storage.insert(
                        PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY,
                        0,
                        historyPlayers.stream()
                                .map(p -> p.playbackEntry.entryID)
                                .collect(Collectors.toList())
                ))
                .thenRun(() -> {
                    if (player == null) {
                        storage.removeLocalAudioPlayerCurrent();
                    } else {
                        storage.setLocalAudioPlayerCurrent(
                                player.playbackEntry.entryID,
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

    private List<PlaybackEntry> getPreloadedEntries(int maxNum, List<MediaPlayerInstance> players) {
        List<PlaybackEntry> entries = new LinkedList<>();
        for (MediaPlayerInstance p: players) {
            if (entries.size() >= maxNum) {
                return entries;
            }
            p.playbackEntry.setPreloaded(true);
            entries.add(p.playbackEntry);
        }
        return entries;
    }

    @Override
    public List<PlaybackEntry> getQueueEntries(int maxNum) {
        return getPreloadedEntries(maxNum, queuePlayers);
    }

    @Override
    public List<PlaybackEntry> getPlaylistEntries(int maxNum) {
        return getPreloadedEntries(maxNum, playlistPlayers);
    }

    @Override
    public PlaybackEntry getCurrentEntry() {
        return player == null ? null : player.playbackEntry;
    }

    @Override
    public PlaybackEntry getQueueEntry(int queuePosition) {
        return queuePlayers.isEmpty() || queuePosition >= queuePlayers.size() ? null :
                queuePlayers.get(queuePosition).playbackEntry;
    }

    @Override
    public PlaybackEntry getPlaylistEntry(int playlistPosition) {
        return playlistPlayers.isEmpty() || playlistPosition >= playlistPlayers.size() ? null :
                playlistPlayers.get(playlistPosition).playbackEntry;
    }

    @Override
    public int getNumQueueEntries() {
        return queuePlayers.size();
    }

    @Override
    public int getNumPlaylistEntries() {
        return playlistPlayers.size();
    }

    @Override
    public CompletableFuture<Void> dePreload(int numQueueEntriesToDepreload,
                                             int queueOffset,
                                             int numPlaylistEntriesToDepreload,
                                             int playlistOffset) {
        for (int i = 0; i < numPlaylistEntriesToDepreload; i++) {
            MediaPlayerInstance m = playlistPlayers.pollLast();
            // TODO: Also cancel the download
            m.release();
        }
        for (int i = 0; i < numQueueEntriesToDepreload; i++) {
            MediaPlayerInstance m = queuePlayers.pollLast();
            // TODO: Also cancel the download
            m.release();
        }
        return persistState();
    }

    @Override
    public CompletableFuture<Void> queue(List<PlaybackEntry> entries, int offset) {
        if (!entries.isEmpty()) {
            List<MediaPlayerInstance> playersToQueue = new LinkedList<>();
            for (PlaybackEntry entry: entries) {
                MediaPlayerInstance instance = new MediaPlayerInstance(
                        entry,
                        mediaPlayerCallback,
                        musicLibraryService
                );
                musicLibraryService.downloadAudioData(instance.playbackEntry.entryID);
                playersToQueue.add(instance);
            }
            queuePlayers.addAll(offset, playersToQueue);
        }
        if (player == null) {
            return next();
        }
        return persistState();
    }

    @Override
    public CompletableFuture<Void> deQueue(List<Integer> positions) {
        // Start from largest index because queues are modified
        for (int i = 0; i < positions.size(); i++) {
            int queuePosition = positions.get(positions.size() - 1 - i);
            // TODO: Also cancel the download
            queuePlayers.remove(queuePosition).release();
        }
        return persistState();
    }

    @Override
    public CompletableFuture<Void> previous() {
        // TODO: implement
        Log.e(LC, "previous not implemented");
        return Util.futureResult("Not implemented");
    }

    private void updatePlaybackState() {
        if (player == null) {
            callback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
            return;
        }
        callback.onStateChanged(player.getPlaybackState());
    }

    private void setCurrentPlayer(MediaPlayerInstance mediaPlayerInstance) {
        player = mediaPlayerInstance;
        Log.d(LC, "setCurrentPlayer: " + (player == null ? "null" : player.title()));
        if (player == null) {
            callback.onMetaChanged(EntryID.from(Meta.UNKNOWN_ENTRY));
        } else {
            player.getReady();
            callback.onMetaChanged(player.playbackEntry.entryID);
        }
    }

    private boolean isCurrentPlayer(MediaPlayerInstance mediaPlayerInstance) {
        return mediaPlayerInstance.equals(player);
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
                updatePlaybackState();
                if (playWhenReady) {
                    play();
                    updatePlaybackState();
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
            musicLibraryService.getAudioData(entryID, audioDataDownloadHandler);
        }
    };

    interface MediaPlayerCallback {
        void onBuffering(MediaPlayerInstance instance);
        void onPrepared(MediaPlayerInstance instance);
        void onPlaybackCompleted(MediaPlayerInstance instance);
        void getAudioData(EntryID entryID,
                          AudioDataDownloadHandler audioDataDownloadHandler);
    }
}
