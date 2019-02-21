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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
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

    // Internal state
    private boolean isPlaying;

    PlaybackController(Context context, MusicLibraryService musicLibraryService, Callback callback) {
        this.musicLibraryService = musicLibraryService;
        this.callback = callback;

        queue = new PlaybackQueue();
        playlistItems = new PlaybackQueue();

        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService
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
        sessionManager.removeSessionManagerListener(sessionManagerListener);
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

    private PlaybackEntry consumeQueueEntry(int offset) {
        return queue.remove(offset);
    }

    private PlaybackEntry consumePlaylistEntry() {
        if (!playlistItems.isEmpty()) {
            return playlistItems.next();
        }
        List<PlaybackEntry> playbackEntries = musicLibraryService.playlistGetNext(
                currentPlaylist.playlistID,
                playlistPosition,
                1
        );
        playlistPosition = musicLibraryService.playlistPosition(
                currentPlaylist.playlistID,
                playlistPosition,
                playbackEntries.size()
        );
        return playbackEntries.size() > 0 ? playbackEntries.get(0) : null;
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
            List<PlaybackEntry> entries = pollNextPreloadItems(numToPreload);
            return audioPlayer.preload(entries);
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
                    nextEntry = consumeQueueEntry(queueOffset - numPlayerQueueEntries);
                }
                // Queue after current and play
                return result.thenCompose(v -> audioPlayer.queue(Collections.singletonList(nextEntry), 0))
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
                            .thenCompose(v -> {
                                for (int i = 0; i < consumeOffset - 1; i++) {
                                    consumePlaylistEntry();
                                }
                                PlaybackEntry entry = consumePlaylistEntry();
                                return audioPlayer.queue(Collections.singletonList(entry), 0);
                            })
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
                ).thenRun(() -> {
                    if (!playlistEntriesToDePreload.isEmpty()) {
                        dePreloadPlaylistEntries(playlistEntriesToDePreload);
                        onQueueChanged();
                    }
                    if (!queueEntriesToDePreload.isEmpty()) {
                        dePreloadQueueEntries(queueEntriesToDePreload, 0);
                        onQueueChanged();
                    }
                });
            } else {
                return Util.futureResult(null);
            }
        }).thenCompose(v -> newEntriesToPreload.isEmpty() ? CompletableFuture.completedFuture(null) :
                audioPlayer.queue(
                        newEntriesToPreload,
                        newEntriesToPreloadOffset
                )
        ).thenRun(() -> {
            if (newEntriesToController.size() > 0) {
                dePreloadQueueEntries(
                        newEntriesToController,
                        newEntriesToControllerOffset
                );
                onQueueChanged();
            }
        }).thenCompose(v -> checkPreload());
    }

    private void onQueueChanged() {
        callback.onQueueChanged(getQueue());
    }

    private void dePreloadQueueEntries(List<PlaybackEntry> queueEntries, int toPosition) {
        queue.add(toPosition, queueEntries);
    }

    private void dePreloadPlaylistEntries(List<PlaybackEntry> playlistEntries) {
        playlistItems.addFirst(playlistEntries);
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
            result = result.thenRun(() -> {
                int len = queuePositionsToRemoveFromController.size();
                for (int i = 0; i < len; i++) {
                    consumeQueueEntry(queuePositionsToRemoveFromController.get(len - 1 - i));
                }
            });
        }
        return result
                .thenCompose(v -> checkPreload())
                .thenRun(() -> callback.onQueueChanged(getQueue())
        );
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
            entry.meta.getBundle().putString(
                    Meta.METADATA_KEY_PLAYBACK_PRELOADSTATUS,
                    PlaybackEntry.PRELOADSTATUS_PRELOADED
            );
        }
        entries.addAll(audioPlayer.getQueueEntries(Integer.MAX_VALUE));
        entries.addAll(queue.getEntries());
        for (PlaybackEntry playbackEntry: entries) {
            MediaDescriptionCompat description = playbackEntry.meta.toMediaDescriptionCompat();
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
                List<PlaybackEntry> entries = pollNextPreloadItems(numToPreload);
                audioPlayer.preload(entries);
            }
        }
    }

    private List<PlaybackEntry> pollNextPreloadItems(int num) {
        Log.d(LC, "pollNextPreloadItems");
        List<PlaybackEntry> entries = new LinkedList<>();
        // Get from internal queue
        if (!queue.isEmpty()) {
            entries.addAll(queue.poll(num));
            callback.onQueueChanged(getQueue());
        }
        // Get from internal playlist items
        if (entries.size() < num && !playlistItems.isEmpty()) {
            entries.addAll(playlistItems.poll(num - entries.size()));
            callback.onPlaylistPositionChanged();
        }
        // Get from current playlist reference
        if (entries.size() < num) {
            List<PlaybackEntry> playlistEntries = musicLibraryService.playlistGetNext(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    num - entries.size()
            );
            long oldPosition = playlistPosition;
            playlistPosition = musicLibraryService.playlistPosition(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    playlistEntries.size()
            );
            entries.addAll(playlistEntries);
            Log.d(LC, "Playlist position +" + (num - playlistEntries.size())
                    + " from " + oldPosition + " to " + playlistPosition);
            callback.onPlaylistPositionChanged();
        }
        return entries;
    }

    private void transferAudioPlayerState(AudioPlayer.AudioPlayerState state) {
        // TODO: handle state.history
        audioPlayer.preload(state.entries)
                .thenCompose(v -> seekTo(state.lastPos))
                .thenCompose(v -> checkPreload());
    }

    private void onCastConnect(CastSession session) {
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastConnect", lastState);
        audioPlayer.stop();
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
                musicLibraryService
        );
        transferAudioPlayerState(lastState);
        callback.onPlayerChanged(AudioPlayer.Type.LOCAL);
    }

    private void printState(String caption, AudioPlayer.AudioPlayerState lastState) {
        Log.d(LC, caption);
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
