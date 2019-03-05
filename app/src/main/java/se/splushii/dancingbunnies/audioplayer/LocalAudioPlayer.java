package se.splushii.dancingbunnies.audioplayer;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(LocalAudioPlayer.class);
    private final Callback callback;
    private final PlaybackControllerStorage storage;

    private enum MediaPlayerState {
        NULL,
        IDLE,
        INITIALIZED,
        PREPARING,
        STARTED,
        PAUSED,
        STOPPED,
        PLAYBACK_COMPLETED,
        PREPARED
    }
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
                ));
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
                            MediaPlayerInstance instance = new MediaPlayerInstance(entry);
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
            preparePlayers();
            return persistState();
        });
    }

    private void addEntries(List<PlaybackEntry> entries) {
        for (PlaybackEntry entry: entries) {
            MediaPlayerInstance playerInstance = new MediaPlayerInstance(entry);
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
        preparePlayers();
        updatePlaybackState();
        return Util.futureResult(null);
    }

    private void preparePlayers() {
        int count = 0;
        for (MediaPlayerInstance mediaPlayerInstance: queuePlayers) {
            if (count >= getMaxToPreload()) {
                mediaPlayerInstance.release();
            } else {
                mediaPlayerInstance.getReady();
            }
            count++;
        }
        for (MediaPlayerInstance mediaPlayerInstance: playlistPlayers) {
            if (count >= getMaxToPreload()) {
                mediaPlayerInstance.release();
            } else {
                mediaPlayerInstance.getReady();
            }
            count++;
        }
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
            previousPlayer.release();
            historyPlayers.add(previousPlayer);
        }
        updatePlaybackState();
        callback.onPreloadChanged();
        preparePlayers();
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
            m.release();
        }
        for (int i = 0; i < numQueueEntriesToDepreload; i++) {
            MediaPlayerInstance m = queuePlayers.pollLast();
            m.release();
        }
        return persistState();
    }

    @Override
    public CompletableFuture<Void> queue(List<PlaybackEntry> entries, int offset) {
        if (!entries.isEmpty()) {
            List<MediaPlayerInstance> playersToQueue = new LinkedList<>();
            for (PlaybackEntry entry: entries) {
                MediaPlayerInstance instance = new MediaPlayerInstance(entry);
                instance.preload();
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

    private class MediaPlayerInstance {
        private final PlaybackEntry playbackEntry;
        private final String title;
        private MediaPlayer mediaPlayer;
        private MediaPlayerState state;
        private boolean buffering = false;
        private long lastSeek = -1;

        MediaPlayerInstance(PlaybackEntry playbackEntry) {
            reconstruct();
            this.playbackEntry = playbackEntry;
            this.title = musicLibraryService.getSongMetaData(playbackEntry.entryID)
                    .getString(Meta.METADATA_KEY_TITLE);
        }

        private void reconstruct() {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            mediaPlayer.setOnErrorListener(mediaPlayerErrorListener);
            mediaPlayer.setOnPreparedListener(mp -> {
                buffering = false;
                state = MediaPlayerState.PREPARED;
                Log.d(LC, "MediaPlayer(" + title() + ") prepared");
                if (this.equals(player)) {
                    Log.d(LC, "onReady: " + title());
                    updatePlaybackState();
                    if (lastSeek != -1) {
                        seekTo(lastSeek);
                        lastSeek = -1;
                    }
                    if (playWhenReady) {
                        play();
                        updatePlaybackState();
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(LC, "MediaPlayer(" + title() + ") completed");
                state = MediaPlayerState.PLAYBACK_COMPLETED;
                if (this.equals(player)) {
                    callback.onSongEnded();
                    next();
                } else {
                    Log.e(LC, "This should never happen...");
                }
            });
            state = MediaPlayerState.IDLE;
        }

        void preload() {
            switch (state) {
                case IDLE:
                    break;
                case NULL:
                    reconstruct();
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") preload in wrong state: " + state);
                    return;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") preload");
            buffering = true;
            if (this.equals(player)) {
                callback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
            }
            musicLibraryService.getAudioData(playbackEntry.entryID, new AudioDataDownloadHandler() {
                @Override
                public void onDownloading() {
                    Log.d(LC, "MediaPlayer(" + title() + ") downloading audio data");
                }

                @Override
                public void onSuccess(AudioDataSource audioDataSource) {
                    Log.d(LC, "MediaPlayer(" + title() + ") successfully got audio data");
                    initialize(audioDataSource);
                }

                @Override
                public void onFailure(String status) {
                    Log.e(LC, "MediaPlayer(" + title() + ") could not get audio data: "
                            + status +".\nRetrying...");
                    // TODO: Restrict number of attempts and show user error when maxed out.
                    preload();
                }
            });
        }

        private void initialize(AudioDataSource audioDataSource) {
            switch (state) {
                case IDLE:
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") "
                            + "initialize in wrong state: " + state);
                    return;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") initializing");
            mediaPlayer.setDataSource(audioDataSource);
            state = MediaPlayerState.INITIALIZED;
            prepare();
        }

        private void prepare() {
            switch (state) {
                case INITIALIZED:
                case STOPPED:
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") "
                            + "prepare in wrong state: " + state);
                    return;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") preparing");
            mediaPlayer.prepareAsync();
            state = MediaPlayerState.PREPARING;
        }

        boolean play() {
            switch (state) {
                case PREPARED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                    break;
                case STARTED:
                    Log.d(LC, "MediaPlayer(" + title() + ") play in STARTED");
                    return false;
                case IDLE:
                    preload();
                    return false;
                case STOPPED:
                    prepare();
                    return false;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") play in wrong state: " + state);
                    return false;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") starting");
            mediaPlayer.start();
            state = MediaPlayerState.STARTED;
            return true;
        }

        boolean pause() {
            switch (state) {
                case PAUSED:
                case STARTED:
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") pause in wrong state: " + state);
                    return false;
            }
            if (mediaPlayer == null) {
                return false;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") pausing");
            mediaPlayer.pause();
            state = MediaPlayerState.PAUSED;
            return true;
        }

        boolean stop() {
            switch (state) {
                case PREPARED:
                case STARTED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                    break;
                case STOPPED:
                case NULL:
                    return true;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") stop in wrong state: " + state);
                    return false;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") stopping");
            mediaPlayer.stop();
            state = MediaPlayerState.STOPPED;
            return true;
        }

        void release() {
            if (mediaPlayer != null) {
                Log.d(LC, "MediaPlayer(" + title() + ") releasing");
                mediaPlayer.release();
                mediaPlayer = null;
            }
            state = MediaPlayerState.NULL;
        }

        boolean seekTo(long pos) {
            switch (state) {
                case IDLE:
                case INITIALIZED:
                case PREPARING:
                case STOPPED:
                case NULL:
                    lastSeek = pos;
                    Log.d(LC, "MediaPlayer(" + title() + ") "
                            + "setting initial seek to: " + lastSeek);
                    return true;
                case PREPARED:
                case STARTED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                    lastSeek = pos;
                    Log.d(LC, "MediaPlayer(" + title() + ") seeking to " + pos);
                    mediaPlayer.seekTo((int) pos);
                    return true;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") seekTo in wrong state: " + state);
                    return false;
            }
        }

        long getCurrentPosition() {
            long pos = 0L;
            switch (state) {
                case STOPPED:
                    break;
                case PREPARED:
                    pos = lastSeek;
                    break;
                case STARTED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                    pos = mediaPlayer.getCurrentPosition();
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") "
                            + "getPlayerSeekPosition in wrong state: " + state);
                    return 0L;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") getPlayerSeekPosition: " + pos);
            return pos;
        }

        boolean isStopped() {
            return MediaPlayerState.STOPPED.equals(state);
        }

        boolean isIdle() {
            return MediaPlayerState.IDLE.equals(state) && !buffering;
        }

        private int getPlaybackState() {
            switch (state) {
                case STARTED:
                    return PlaybackStateCompat.STATE_PLAYING;
                case PLAYBACK_COMPLETED:
                case PAUSED:
                case PREPARED:
                    return PlaybackStateCompat.STATE_PAUSED;
                case IDLE:
                    if (buffering) {
                        return PlaybackStateCompat.STATE_BUFFERING;
                    }
                case NULL:
                case STOPPED:
                case INITIALIZED:
                    return PlaybackStateCompat.STATE_STOPPED;
                case PREPARING:
                    return PlaybackStateCompat.STATE_BUFFERING;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") unknown state");
                    return PlaybackStateCompat.STATE_ERROR;
            }
        }

        private final MediaPlayer.OnErrorListener mediaPlayerErrorListener = (mp, what, extra) -> {
            String msg;
            switch (what) {
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    switch (extra) {
                        case MediaPlayer.MEDIA_ERROR_IO:
                            msg = "MEDIA_ERROR_IO";
                            break;
                        case MediaPlayer.MEDIA_ERROR_MALFORMED:
                            msg = "MEDIA_ERROR_MALFORMED";
                            break;
                        case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                            msg = "MEDIA_ERROR_UNSUPPORTED";
                            break;
                        case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                            msg = "MEDIA_ERROR_TIMED_OUT";
                            break;
                        case 0x80000000: // MediaPlayer.MEDIA_ERROR_SYSTEM
                            msg = "MEDIA_ERROR_SYSTEM (low level weird error)";
                            break;
                        default:
                            msg = "Unhandled MEDIA_ERROR_UNKNOWN error extra: " + extra;
                            break;
                    }
                    break;
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    msg = "MEDIA_ERROR_SERVER_DIED";
                    break;
                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                    msg = "MEDIA_ERROR_UNSUPPORTED";
                    break;
                default:
                    msg = "Unhandled error: " + what;
                    break;
            }
            Log.e(LC, "MediaPlayer(" + title() + ") error: " + msg);
            return false;
        };

        private String title() {
            return title;
        }

        void getReady() {
            if (isIdle()) {
                preload();
            } else if (isStopped()) {
                prepare();
            }
        }
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
            player.preload();
            callback.onMetaChanged(player.playbackEntry.entryID);
        }
    }
}
