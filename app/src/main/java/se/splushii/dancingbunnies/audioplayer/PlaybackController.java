package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

// PlaybackController should have audio players, an internal queue, an internal playlist and a
// reference to the current playlist.
//
// It is responsible for giving entries to preload to the audio players.
// The data to preload is supplied by the following sources in order until depleted:
// internal queue, internal playlist, current playlist by reference.
//
// It is responsible for exposing the UI queue items and playlist items.
// The UI queue is taken from: current audio player, internal queue.
// The UI playlist items is taken from: current audio player, internal playlist items, current
// playlist by reference.
//
// It is responsible for shifting state between audio players.

// TODO: Sequentialize control calls to AudioPlayers here, to avoid doing it in every AudioPlayer.
class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);

    private final MusicLibraryService musicLibraryService;
    private final Callback callback;
    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListenerImpl();
    private final SessionManager sessionManager;
    private final PlaybackControllerStorage storage;

    // Audio players
    private AudioPlayer audioPlayer;
    private final AudioPlayer.Callback audioPlayerCallback = new AudioPlayerCallback();

    // Internal queue items
    private final PlaybackQueue queue;

    // Internal playlist items
    private final PlaybackQueue playlistItems;

    // Current playlist reference
    private final PlaylistItem currentPlaylist = PlaylistItem.defaultPlaylist;
    private long playlistPosition = 0;

    // History of played items
    private final PlaybackQueue history;

    // Internal state
    private boolean isPlaying;

    PlaybackController(Context context,
                       PlaybackControllerStorage playbackControllerStorage,
                       MusicLibraryService musicLibraryService,
                       Callback callback) {
        this.storage = playbackControllerStorage;
        this.musicLibraryService = musicLibraryService;
        this.callback = callback;

        LiveData<List<PlaybackEntry>> queueEntriesLiveData = storage.getQueueEntries();
        queue = new PlaybackQueue(
                PlaybackControllerStorage.QUEUE_ID_QUEUE,
                storage,
                queueEntriesLiveData,
                this::onQueueChanged
        );
        LiveData<List<PlaybackEntry>> playlistEntriesLiveData = storage.getPlaylistEntries();
        playlistItems = new PlaybackQueue(
                PlaybackControllerStorage.QUEUE_ID_PLAYLIST,
                storage,
                playlistEntriesLiveData,
                this::onQueueChanged
        );
        LiveData<List<PlaybackEntry>> historyEntriesLiveData = storage.getHistoryEntries();
        history = new PlaybackQueue(
                PlaybackControllerStorage.QUEUE_ID_HISTORY,
                storage,
                historyEntriesLiveData,
                () -> {}
        );

        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                storage,
                true
        );

        CastContext castContext = CastContext.getSharedInstance(context);
        sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener);
    }

    void initialize() {
        isPlaying = false;
        checkPreload();
    }

    void onDestroy() {
        Log.d(LC, "onDestroy");
        sessionManager.removeSessionManagerListener(sessionManagerListener);
        audioPlayer.stop();
        queue.onDestroy();
        playlistItems.onDestroy();
        history.onDestroy();
    }

    long getPlayerSeekPosition() {
        return audioPlayer.getSeekPosition();
    }

    PlaylistItem getCurrentPlaylist() {
        return currentPlaylist;
    }

    CompletableFuture<Void> play() {
        return audioPlayer.play();
    }

    CompletableFuture<Void> pause() {
        return audioPlayer.pause();
    }

    CompletableFuture<Void> playPause() {
        return isPlaying ? pause() : play();
    }

    CompletableFuture<Void> stop() {
        return audioPlayer.stop();
    }

    CompletableFuture<Void> skipToNext() {
        return checkPreload()
                .thenCompose(v -> audioPlayer.next())
                .thenCompose(v -> checkPreload());
    }

    CompletableFuture<Void> skipToPrevious() {
        // TODO: implement
        Log.e(LC, "skipToPrevious not implemented");
        return audioPlayer.previous();
    }

    private int getNumQueueEntries() {
        return queue.size();
    }

    private CompletableFuture<List<PlaybackEntry>> consumePlaylistEntries(int max) {
        CompletableFuture<List<PlaybackEntry>> ret = CompletableFuture.completedFuture(new ArrayList<>());
        if (!playlistItems.isEmpty()) {
            ret = ret.thenCompose(entries ->
                    playlistItems.poll(max).thenApply(e -> {
                        entries.addAll(e);
                        return entries;
                    })
            );
        }
        ret = ret.thenApply(entries -> {
            int numWanted = max - entries.size();
            if (numWanted <= 0) {
                return entries;
            }
            List<PlaybackEntry> playbackEntries = musicLibraryService.playlistGetNext(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    numWanted
            );
            long oldPosition = playlistPosition;
            playlistPosition = musicLibraryService.playlistPosition(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    playbackEntries.size()
            );
            Log.d(LC, "Playlist position +" + playbackEntries.size()
                    + " from " + oldPosition + " to " + playlistPosition);
            callback.onPlaylistPositionChanged();
            entries.addAll(playbackEntries);
            return entries;
        });
        return ret;
    }

    private PlaybackEntry getQueueEntry(int offset) {
        return queue.get(offset);
    }

    private int getNumNewEntriesToPreload(int numNewEntries,
                                          int toPosition,
                                          int numPlayerQueue,
                                          int maxToPreload) {
        if (toPosition >= maxToPreload || toPosition > numPlayerQueue) {
            return 0;
        }
        return Integer.max(0, Integer.min(maxToPreload - toPosition, numNewEntries));
    }

    private int getNumPlaylistEntriesToDepreload(int numToDepreload, int numPlaylistEntries) {
        if (numToDepreload <= 0 || numPlaylistEntries <= 0) {
            return 0;
        }
        return Integer.min(numToDepreload, numPlaylistEntries);
    }

    private int getNumQueueEntriesToDepreload(int numToDepreload,
                                              int numQueueEntries) {
        if (numToDepreload <= 0 || numQueueEntries <= 0) {
            return 0;
        }
        return Integer.min(numToDepreload, numQueueEntries);
    }

    private List<PlaybackEntry> getNewEntriesToQueue(List<PlaybackEntry> newEntries, int num) {
        return num <= 0 ? Collections.emptyList() : newEntries.subList(0, num);
    }

    private List<PlaybackEntry> getNewEntriesToDepreload(int numToQueue,
                                                         List<PlaybackEntry> newEntries) {
        if (numToQueue < 0 || numToQueue >= newEntries.size()) {
            return Collections.emptyList();
        }
        return newEntries.subList(numToQueue, newEntries.size());
    }

    private int getDepreloadOffset(int toPosition,
                                   int numPlayerQueue,
                                   int numPlayerQueueDePreload,
                                   int numNewEntriesToPreload) {
        int targetPlayerQueueSize = numPlayerQueue
                - numPlayerQueueDePreload
                + numNewEntriesToPreload;
        return toPosition < targetPlayerQueueSize? 0 : toPosition - targetPlayerQueueSize;
    }

    private int numPreloadNeeded() {
        int numPreloaded = audioPlayer.getNumPreloaded();
        int maxToPreload = audioPlayer.getMaxToPreload();
        return maxToPreload - numPreloaded;
    }

    private CompletableFuture<Void> checkPreload() {
        Log.d(LC, "checkPreload()");
        int numToPreload = numPreloadNeeded();
        if (numToPreload > 0) {
            return pollNextPreloadItems(numToPreload)
                    .thenCompose(entries -> audioPlayer.preload(entries));
        }
        return Util.futureResult(null);
    }

    CompletableFuture<Void> skip(int offset) {
        Log.d(LC, "skip(" + offset + ")");
        if (offset == 0) {
            return CompletableFuture.completedFuture(null);
        }
        if (offset == 1) {
            return skipToNext();
        }
        int numPlayerQueueEntries = audioPlayer.getNumQueueEntries();
        int numControllerQueueEntries = getNumQueueEntries();
        int totalQueueEntries = numPlayerQueueEntries + numControllerQueueEntries;
        PlaybackEntry nextEntry;
        if (offset > 0) {
            // Skip forward
            if (offset <= totalQueueEntries) {
                // Play queue item at offset now
                int queueOffset = offset - 1;
                CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
                if (queueOffset < numPlayerQueueEntries) {
                    // Get the queue entry from player
                    Log.d(LC, "skip short queue offset");
                    nextEntry = audioPlayer.getQueueEntry(queueOffset);
                    result = result.thenCompose(v ->
                            audioPlayer.dePreload(
                                    1,
                                    queueOffset,
                                    0,
                                    0
                            )
                    );
                } else {
                    // Get the queue entry from controller
                    Log.d(LC, "skip long queue offset");
                    nextEntry = getQueueEntry(queueOffset);
                    result = result.thenCompose(v ->
                            consumeQueueEntries(
                                    Collections.singletonList(queueOffset - numPlayerQueueEntries)
                            )
                    ).thenApply(entries -> null);
                }
                // Queue after current and play
                return result
                        .thenCompose(v -> audioPlayer.queue(Collections.singletonList(nextEntry), 0))
                        .thenCompose(v -> skipToNext())
                        .thenCompose(v -> play());
            } else {
                // Skip all playlist items until offset
                int playlistOffset =  offset - numPlayerQueueEntries;
                int numPlayerPlaylistEntries = audioPlayer.getNumPlaylistEntries();
                if (playlistOffset <= numPlayerPlaylistEntries) {
                    // Remove all playlist items until offset, then queue and play offset
                    Log.d(LC, "skip short playlist offset");
                    nextEntry = audioPlayer.getPlaylistEntry(playlistOffset);
                    return audioPlayer.dePreload(0, 0, playlistOffset, 0)
                            .thenCompose(v -> audioPlayer.queue(Collections.singletonList(nextEntry), 0))
                            .thenCompose(v -> skipToNext())
                            .thenCompose(v -> play());
                } else {
                    // Dequeue all playlist items. Consume and throw away all playlist items up
                    // until offset. Insert and play offset.
                    Log.d(LC, "skip long playlist offset");
                    int consumeOffset = playlistOffset - numPlayerPlaylistEntries;
                    return audioPlayer.dePreload(0, 0, numPlayerPlaylistEntries, 0)
                            .thenCompose(v -> consumePlaylistEntries(consumeOffset)
                                    .thenCompose(entries -> {
                                        if (entries.isEmpty()) {
                                            return Util.futureResult(null);
                                        }
                                        return audioPlayer.queue(
                                                Collections.singletonList(
                                                        entries.get(entries.size() - 1)),
                                                0
                                        );
                                    }))
                            .thenCompose(v -> skipToNext())
                            .thenCompose(v -> play());
                }
            }
        } else {
            // Skip backward
            // TODO: implement
            return Util.futureResult("Not implemented: skip backward");
        }
    }

    CompletableFuture<Void> queue(List<PlaybackEntry> entries, int toPosition) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        Log.d(LC, "queue(): " + entries.toString());
        List<PlaybackEntry> playerPlaylist = audioPlayer.getPlaylistEntries(Integer.MAX_VALUE);
        List<PlaybackEntry> playerQueue = audioPlayer.getQueueEntries(Integer.MAX_VALUE);
        int numPlayerPlaylist = playerPlaylist.size();
        int numPlayerQueue = playerQueue.size();
        int numControllerQueue = getNumQueueEntries();
        int numNew = entries.size();
        int maxToPreload = audioPlayer.getMaxToPreload();
        if (toPosition == AudioPlayerService.QUEUE_LAST) {
            toPosition = numPlayerQueue + numControllerQueue;
        }

        int numNewEntriesToPreload = getNumNewEntriesToPreload(
                numNew,
                toPosition,
                numPlayerQueue,
                maxToPreload
        );

        int numToDepreload = numPlayerQueue
                + numNewEntriesToPreload
                + numPlayerPlaylist
                - maxToPreload;
        int leftToDepreload = numToDepreload;

        int numPlaylistEntriesToDepreload = getNumPlaylistEntriesToDepreload(
                leftToDepreload,
                numPlayerPlaylist
        );
        int playlistEntriesToDepreloadOffset = numPlayerPlaylist - numPlaylistEntriesToDepreload;
        leftToDepreload -= numPlaylistEntriesToDepreload;

        int numQueueEntriesToDepreload = getNumQueueEntriesToDepreload(
                leftToDepreload,
                numPlayerQueue
        );
        int queueEntriesToDepreloadOffset = numPlayerQueue - numQueueEntriesToDepreload;
        leftToDepreload -= numQueueEntriesToDepreload;
        int numToFill = leftToDepreload < 0 ? -leftToDepreload : 0;

        int newEntriesToPreloadOffset = Integer.min(numPlayerQueue, toPosition);

        int numNewEntriesToController = numNew - numNewEntriesToPreload;
        int newEntriesToControllerOffset = getDepreloadOffset(
                toPosition,
                numPlayerQueue,
                numQueueEntriesToDepreload,
                numNewEntriesToPreload
        );

        Log.d(LC, "queue()"
                + "\nDe-preload: " + numToDepreload
                + "\nDe-preload queue: " + numQueueEntriesToDepreload
                + "\nDe-preload playlist: " + numPlaylistEntriesToDepreload
                + "\nPreload new: " + numNewEntriesToPreload
                + "\nPlayer queue[" + newEntriesToControllerOffset + "]"
                + "\nController new: " + numNewEntriesToController
                + "\nController queue[" + newEntriesToControllerOffset + "]"
                + "\nNum to fill: " + numToFill);

        // Get player queue entries to de-preload
        List<PlaybackEntry> queueEntriesToDePreload = playerQueue.subList(
                queueEntriesToDepreloadOffset,
                queueEntriesToDepreloadOffset + numQueueEntriesToDepreload
        );

        // Get player playlist entries to de-preload
        List<PlaybackEntry> playlistEntriesToDePreload = playerPlaylist.subList(
                playlistEntriesToDepreloadOffset,
                numPlayerPlaylist
        );

        // Get new entries to preload
        List<PlaybackEntry> newEntriesToPreload = getNewEntriesToQueue(
                entries,
                numNewEntriesToPreload
        );

        // Get new entries to controller
        List<PlaybackEntry> newEntriesToController = getNewEntriesToDepreload(
                numNewEntriesToPreload,
                entries
        );

        return CompletableFuture.completedFuture(null).thenCompose(v -> {
            if (numQueueEntriesToDepreload > 0 || numPlaylistEntriesToDepreload > 0) {
                return audioPlayer.dePreload(
                        numQueueEntriesToDepreload,
                        queueEntriesToDepreloadOffset,
                        numPlaylistEntriesToDepreload,
                        playlistEntriesToDepreloadOffset
                ).thenCompose(aVoid -> {
                    if (!playlistEntriesToDePreload.isEmpty()) {
                        return dePreloadPlaylistEntries(playlistEntriesToDePreload);
                    }
                    return Util.futureResult(null);
                }).thenCompose(aVoid -> {
                    if (!queueEntriesToDePreload.isEmpty()) {
                        return dePreloadQueueEntries(queueEntriesToDePreload, 0);
                    }
                    return Util.futureResult(null);
                });
            } else {
                return Util.futureResult(null);
            }
        }).thenCompose(v -> newEntriesToPreload.isEmpty() ? CompletableFuture.completedFuture(null) :
                audioPlayer.queue(
                        newEntriesToPreload,
                        newEntriesToPreloadOffset
                )
        ).thenCompose(aVoid -> {
            if (newEntriesToController.size() > 0) {
                return dePreloadQueueEntries(
                        newEntriesToController,
                        newEntriesToControllerOffset
                );
            }
            return Util.futureResult(null);
        }).thenCompose(v -> checkPreload());
    }

    private void onQueueChanged() {
        callback.onQueueChanged(getQueue());
    }

    private CompletableFuture<Void> dePreloadQueueEntries(List<PlaybackEntry> queueEntries,
                                                          int toPosition) {
        return queue.add(toPosition, queueEntries);
    }

    private CompletableFuture<Void> dePreloadPlaylistEntries(List<PlaybackEntry> playlistEntries) {
        return playlistItems.add(0, playlistEntries);
    }

    CompletableFuture<Void> deQueue(long[] positions) {
        Arrays.sort(positions);
        int numPlayerQueueEntries = audioPlayer.getNumQueueEntries();
        List<Integer> queuePositionsToRemoveFromPlayer = new LinkedList<>();
        List<Integer> queuePositionsToRemoveFromController = new LinkedList<>();
        for (long queuePosition: positions) {
            if (queuePosition < 0) {
                Log.e(LC, "Can not deQueue negative index: " + queuePosition);
                continue;
            }
            if (queuePosition < numPlayerQueueEntries) {
                queuePositionsToRemoveFromPlayer.add((int) queuePosition);
            } else {
                queuePositionsToRemoveFromController.add((int) queuePosition - numPlayerQueueEntries);
            }
        }
        Log.d(LC, "deQueue()"
                + "\nfrom player: "
                + queuePositionsToRemoveFromPlayer.size()
                + ": " + queuePositionsToRemoveFromPlayer
                + "\nfrom controller: "
                + queuePositionsToRemoveFromController.size()
                + ": " + queuePositionsToRemoveFromController);
        CompletableFuture<Void> result = Util.futureResult(null);
        if (queuePositionsToRemoveFromPlayer.size() > 0) {
            result = result.thenCompose(v ->
                    audioPlayer.deQueue(queuePositionsToRemoveFromPlayer)
            );
        }
        if (!queuePositionsToRemoveFromController.isEmpty()) {
            result = result.thenCompose(aVoid ->
                    consumeQueueEntries(queuePositionsToRemoveFromController)
            ).thenApply(entries -> null);
        }
        return result
                .thenCompose(v -> checkPreload())
                .thenRun(() -> callback.onQueueChanged(getQueue())
        );
    }

    private CompletionStage<List<PlaybackEntry>> consumeQueueEntries(List<Integer> queuePositions) {
        return queue.remove(queuePositions);
    }

    CompletableFuture<Void> moveQueueItems(long[] positions, int toPosition) {
        if (positions.length <= 0 || toPosition < 0) {
            return Util.futureResult(null);
        }
        Arrays.sort(positions);
        int numPlayerQueueEntries = audioPlayer.getNumQueueEntries();
        List<PlaybackEntry> moveEntries = new LinkedList<>();
        for (long queuePosition: positions) {
            if (queuePosition < 0) {
                Log.e(LC, "Can not deQueue negative index: " + queuePosition);
                continue;
            }
            PlaybackEntry entry;
            if (queuePosition < numPlayerQueueEntries) {
                entry = audioPlayer.getQueueEntry((int) queuePosition);
            } else {
                entry = getQueueEntry((int) queuePosition - numPlayerQueueEntries);
            }
            if (entry == null) {
                return Util.futureResult("moveQueueItems: Internal error: Got null entry.");
            }
            moveEntries.add(entry);
        }
        Log.d(LC, "moveQueueItems(" + Arrays.toString(positions) + ", " + toPosition + ")"
                + "\nmoveEntries: " + moveEntries.toString());
        return deQueue(positions)
                .thenCompose(v -> queue(moveEntries, toPosition))
                .thenRun(this::onQueueChanged);
    }

    CompletableFuture<Void> seekTo(long pos) {
        return audioPlayer.seekTo(pos)
                .thenRun(() -> callback.onPlayerSeekPositionChanged(pos));
    }

    CompletableFuture<Void> playNow(PlaybackEntry playbackEntry) {
        return queue(Collections.singletonList(playbackEntry), 0)
                .thenCompose(r -> skipToNext())
                .thenCompose(r -> play());
    }

    private List<MediaSessionCompat.QueueItem> getQueue() {
        List<MediaSessionCompat.QueueItem> queueItems = new LinkedList<>();
        List<PlaybackEntry> entries = new LinkedList<>();
        for (PlaybackEntry entry: audioPlayer.getPlaylistEntries(Integer.MAX_VALUE)) {
            entry.setPreloaded(true);
        }
        entries.addAll(audioPlayer.getQueueEntries(Integer.MAX_VALUE));
        entries.addAll(queue.getEntries());
        for (PlaybackEntry playbackEntry: entries) {
            Meta meta = musicLibraryService.getSongMetaData(playbackEntry.entryID);
            if (playbackEntry.isPreloaded()) {
                    meta.setBoolean(Meta.METADATA_KEY_PLAYBACK_PRELOADSTATUS, true);
            }
            MediaDescriptionCompat description = meta.toMediaDescriptionCompat();
            MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(
                    description,
                    playbackEntry.entryID.hashCode()
            );
            queueItems.add(queueItem);
        }
        return queueItems;
    }

    List<PlaybackEntry> getPlaylistEntries(int maxNum) {
        List<PlaybackEntry> entries = audioPlayer.getPlaylistEntries(maxNum);
        if (entries.size() < maxNum) {
            entries.addAll(playlistItems.getEntries(maxNum - entries.size()));
        }
        if (entries.size() < maxNum) {
            entries.addAll(musicLibraryService.playlistGetNext(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    maxNum
            ));
        }
        return entries;
    }

    void updateQueue() {
        callback.onQueueChanged(getQueue());
    }

    void updateCurrent() {
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        EntryID entryID = currentEntry == null ?
                EntryID.from(Meta.UNKNOWN_ENTRY) : currentEntry.entryID;
        callback.onMetaChanged(entryID);
    }

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onQueueChanged(List<MediaSessionCompat.QueueItem> queue);
        void onPlaylistPositionChanged();
        void onPlayerSeekPositionChanged(long pos);
    }

    private class AudioPlayerCallback implements AudioPlayer.Callback {
        @Override
        public void onStateChanged(int newPlaybackState) {
            isPlaying = newPlaybackState  == PlaybackStateCompat.STATE_PLAYING;
            callback.onStateChanged(newPlaybackState);
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            Log.d(LC, "onMetaChanged: " + entryID.toString());
            callback.onMetaChanged(entryID);
        }

        @Override
        public void onPreloadChanged() {
            callback.onQueueChanged(getQueue());
            callback.onPlaylistPositionChanged();
        }

        @Override
        public void onSongEnded() {
            int numToPreload = numPreloadNeeded() + 1;
            if (numToPreload > 0) {
                pollNextPreloadItems(numToPreload)
                        .thenCompose(entries -> audioPlayer.preload(entries));
            }
        }
    }

    private CompletableFuture<List<PlaybackEntry>> pollNextPreloadItems(int num) {
        Log.d(LC, "pollNextPreloadItems");
        return queue.poll(num)
                .thenApplyAsync(
                        e -> {
                            callback.onQueueChanged(getQueue());
                            return e;
                        },
                        Util.getMainThreadExecutor())
                .thenCompose(
                        entries -> {
                            int numWanted = entries.size() - num;
                            if (numWanted <= 0) {
                                return CompletableFuture.completedFuture(entries);
                            }
                            // Get playlist items
                            return consumePlaylistEntries(numWanted);
                        });
    }

    private CompletableFuture<Void> transferAudioPlayerState(AudioPlayer.AudioPlayerState state) {
        storage.insert(
                PlaybackControllerStorage.QUEUE_ID_HISTORY,
                0,
                state.history.stream()
                        .map(p -> p.entryID)
                        .collect(Collectors.toList())
        );
        CompletableFuture<Void> ret = CompletableFuture.completedFuture(null);
        if (state.currentEntry != null) {
            ret = ret.thenCompose(v ->
                    audioPlayer.preload(Collections.singletonList(state.currentEntry))
            );
        }
        return ret.thenCompose(v -> audioPlayer.preload(state.entries))
                .thenCompose(v -> seekTo(state.lastPos))
                .thenCompose(v -> checkPreload());
    }

    private void onCastConnect(CastSession session) {
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastConnect", lastState);
        AudioPlayer oldPlayer = audioPlayer;
        oldPlayer.stop()
                .thenCompose(v -> oldPlayer.destroy());
        audioPlayer = new CastAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                session
        );
        transferAudioPlayerState(lastState);
        callback.onPlayerChanged(AudioPlayer.Type.CAST);
    }

    private void onCastDisconnect() {
        if (!(audioPlayer instanceof CastAudioPlayer)) {
            return;
        }
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastDisconnect", lastState);
        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                storage,
                false
        );
        transferAudioPlayerState(lastState);
        callback.onPlayerChanged(AudioPlayer.Type.LOCAL);
    }

    private void printState(String caption, AudioPlayer.AudioPlayerState lastState) {
        Log.d(LC, caption);
        Log.d(LC, "current: " + lastState.currentEntry);
        Log.d(LC, "history:");
        for (PlaybackEntry entry: lastState.history) {
            Log.d(LC, entry.toString());
        }
        Log.d(LC, "entries:");
        for (PlaybackEntry entry: lastState.entries) {
            Log.d(LC, entry.toString());
        }
        Log.d(LC, "lastPos: " + lastState.lastPos);
    }

    private class SessionManagerListenerImpl implements SessionManagerListener<Session> {
        @Override
        public void onSessionStarting(Session session) {
            Log.d(LC, "CastSession starting");
        }

        @Override
        public void onSessionStarted(Session session, String s) {
            Log.d(LC, "CastSession started");
            onCastConnect((CastSession) session);
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {
            Log.d(LC, "CastSession start failed");
            onCastDisconnect();
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(LC, "CastSession ending");
            onCastDisconnect();
        }

        @Override
        public void onSessionEnded(Session session, int i) {
            Log.d(LC, "CastSession ended");
            onCastDisconnect();
        }

        @Override
        public void onSessionResuming(Session session, String s) {
            Log.d(LC, "CastSession resuming");
        }

        @Override
        public void onSessionResumed(Session session, boolean b) {
            Log.d(LC, "CastSession resumed");
            onCastConnect((CastSession) session);
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {
            Log.d(LC, "CastSession resume failed");
            onCastDisconnect();
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
            Log.d(LC, "CastSession suspended");
        }
    }
}
