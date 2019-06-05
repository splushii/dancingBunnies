package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.LongSparseArray;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

// PlaybackController should have audio players, an internal queue, an internal playlist,
// a history of played items and a reference to the current playlist.
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
    private static final int MAX_PLAYLIST_ENTRIES_TO_PREFETCH = 3;

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
    private PlaylistID currentPlaylistID;
    private long currentPlaylistPosition;
    private long nextPlaylistFetchPosition;
    private LongSparseArray<Long> playlistIDToPosMap;

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

        currentPlaylistID = storage.getCurrentPlaylist();
        currentPlaylistPosition = storage.getPlaylistPosition();
        nextPlaylistFetchPosition = storage.getPlaylistFetchPosition();
        playlistIDToPosMap = new LongSparseArray<>();

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

    void setCurrentPlaylist(PlaylistID playlistID) {
        Log.d(LC, "setCurrentPlaylist: " + playlistID);
        currentPlaylistID = playlistID;
        currentPlaylistPosition = 0;
        storage.setCurrentPlaylist(currentPlaylistID);
        storage.setPlaylistPosition(currentPlaylistPosition);
        callback.onPlaylistSelectionChanged(currentPlaylistID, currentPlaylistPosition);
        removePlaylistEntries()
                .thenCompose(aVoid -> checkPreload());
    }

    void setCurrentPlaylistPosition(long pos) {
        Log.d(LC, "setCurrentPlaylistPosition: " + pos);
        currentPlaylistPosition = pos;
        storage.setPlaylistPosition(currentPlaylistPosition);
        callback.onPlaylistPositionChanged(currentPlaylistPosition);
        removePlaylistEntries()
                .thenCompose(aVoid -> checkPreload());
    }

    private CompletableFuture<Void> removePlaylistEntries() {
        return audioPlayer.deQueueEntries(audioPlayer.getPlaylistEntries(Integer.MAX_VALUE))
                .thenComposeAsync(aVoid -> playlistItems.clear());
//        int numPlayerQueue = audioPlayer.getNumQueueEntries();
//        int numPlayerPlaylist = audioPlayer.getNumPlaylistEntries();
//        List<Integer> rangeToRemove = IntStream.range(
//                numPlayerQueue,
//                numPlayerQueue + numPlayerPlaylist
//        ).boxed().collect(Collectors.toList());
//        return audioPlayer.deQueue(rangeToRemove);
    }

    PlaylistID getCurrentPlaylistID() {
        return currentPlaylistID;
    }

    CompletableFuture<Void> play() {
        Log.d(LC, "play");
        if (audioPlayer.getCurrentEntry() == null) {
            return checkPreload()
                    .thenComposeAsync(aVoid -> audioPlayer.play(), Util.getMainThreadExecutor());
        }
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
        Log.d(LC, "skipToNext");
        if (audioPlayer.getCurrentEntry() == null) {
            return checkPreload();
        }
        CompletableFuture<Void> ret = CompletableFuture.completedFuture(null);
        if (audioPlayer.getNumPreloaded() <= 0) {
            ret = ret.thenCompose(aVoid -> checkPreload());
        }
        return ret.thenCompose(aVoid -> audioPlayer.next())
                .thenCompose(aVoid -> checkPreload());
// TODO: May need some type of forceful preload. Try with simple checkPreload first
//                .thenCompose(aVoid -> requirePreload(1));
    }

    CompletableFuture<Void> skipToPrevious() {
        // TODO: implement
        Log.e(LC, "skipToPrevious not implemented");
        return audioPlayer.previous();
    }

    private int getNumQueueEntries() {
        return queue.size();
    }

    private CompletableFuture<List<PlaybackEntry>> fetchPlaylistEntries(int num) {
        Log.d(LC, "fetchPlaylistEntries");
        if (currentPlaylistID == null) {
            return Util.futureResult(null);
        }
        return musicLibraryService.playlistGetNext(
                currentPlaylistID,
                nextPlaylistFetchPosition,
                num,
                getShuffleSeed()
        ).thenCompose(playlistEntries -> {
            List<PlaybackEntry> playbackEntries = new ArrayList<>();
            for (PlaylistEntry playlistEntry: playlistEntries) {
                PlaybackEntry playbackEntry = new PlaybackEntry(
                        EntryID.from(playlistEntry),
                        generatePlaybackID(),
                        PlaybackEntry.USER_TYPE_PLAYLIST
                );
                playlistIDToPosMap.put(playlistEntry.pos, playbackEntry.playbackID);
                playbackEntries.add(playbackEntry);
            }
            return musicLibraryService.playlistPosition(
                    currentPlaylistID,
                    nextPlaylistFetchPosition,
                    playlistEntries.size(),
                    getShuffleSeed()
            ).thenApply(newPos -> {
                nextPlaylistFetchPosition = newPos;
                return playbackEntries;
            });
        });
    }

//    private CompletableFuture<PlaybackEntry> consumePlaylistEntry() {
//        Log.d(LC, "consumePlaylistEntry");
//        if (currentPlaylistID == null) {
//            return CompletableFuture.completedFuture(null);
//        }
//        return musicLibraryService.playlistGetNext(
//                currentPlaylistID,
//                currentPlaylistPosition,
//                1,
//                getShuffleSeed()
//        ).thenCompose(playlistEntries -> {
//            if (playlistEntries.size() != 1) {
//                return null;
//            }
//            return musicLibraryService.playlistPosition(
//                    currentPlaylistID,
//                    currentPlaylistPosition,
//                    1,
//                    getShuffleSeed()
//            ).thenApply(newPos -> {
//                currentPlaylistPosition = newPos;
//                callback.onPlaylistPositionChanged(currentPlaylistPosition);
//                PlaylistEntry playlistEntry = playlistEntries.get(0);
//                Log.e(LC, "consumePlaylistEntry: " + playlistEntry);
//                return new PlaybackEntry(
//                        EntryID.from(playlistEntry),
//                        generatePlaybackID(),
//                        PlaybackEntry.USER_TYPE_PLAYLIST
//                );
//            });
//        });
//    }

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
        int numPlaylistEntriesToFetch = MAX_PLAYLIST_ENTRIES_TO_PREFETCH
                - audioPlayer.getNumPlaylistEntries() + playlistItems.size();
        // Get queue entries
        return queue.poll(numToPreload)
                // Get playlist entries from controller
                .thenCompose(entries -> {
                    if (numToPreload > entries.size()) {
                        return playlistItems.poll(1)
                                .thenApply(playbackEntries -> {
                                    if (playbackEntries != null) {
                                        entries.addAll(playbackEntries);
                                    }
                                    return entries;
                                });
                    }
                    return Util.futureResult(null, entries);
                })
                // Then make sure there is a at least a single playlist entry
                .thenComposeAsync(entries -> {
                    // Fetch more playlist entries if needed
                    if (false && numPlaylistEntriesToFetch > 0) {
                        Log.e(LC, "checkPreload() fetching " + numPlaylistEntriesToFetch
                                + " playlist entries");
                        return fetchPlaylistEntries(numPlaylistEntriesToFetch)
                                .thenApply(playlistEntries -> {
                                    entries.addAll(playlistEntries);
                                    return entries;
                                });
                    }
                    return Util.futureResult(null, entries);

//                    if (audioPlayer.getNumPlaylistEntries() + playlistItems.size() <= 0) {
//                        Log.e(LC, "Gonna get some new playlist entries");
//                        return consumePlaylistEntry()
//                                .thenApply(playbackEntry -> {
//                                    if (playbackEntry != null) {
//                                        entries.add(playbackEntry);
//                                    }
//                                    return entries;
//                                });
//                    }
//                    return Util.futureResult(null, entries);
                }, Util.getMainThreadExecutor())
                .thenComposeAsync(entries -> entries.isEmpty() ?
                                Util.futureResult(null) : audioPlayer.preload(entries),
                        Util.getMainThreadExecutor()
                );

//        return CompletableFuture.supplyAsync(this::numPreloadNeeded, Util.getMainThreadExecutor())
//                .thenCompose(numToPreload -> {
//                    if (numToPreload > 0) {
//                        return pollNextPreloadItems(numToPreload, false);
//                    }
//                    return Util.futureResult(null, Collections.emptyList());
//                }).thenCompose(entries -> entries.isEmpty() ?
//                        Util.futureResult(null) : audioPlayer.preload(entries)
//                );
    }

//    private CompletableFuture<Void> requirePreload(int num) {
//        return pollNextPreloadItems(num, true)
//                .thenCompose(entries -> entries.isEmpty() ?
//                        Util.futureResult(null) : audioPlayer.preload(entries)
//                );
//    }

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
                    int controllerQueueOffset = queueOffset - numPlayerQueueEntries;
                    Log.d(LC, "skip long queue offset");
                    nextEntry = getQueueEntry(controllerQueueOffset);
                    result = result.thenCompose(v ->
                            consumeQueueEntries(
                                    Collections.singletonList(controllerQueueOffset)
                            )
                    ).thenApply(entries -> null);
                }
                // Queue after current and play
                return result
                        .thenComposeAsync(
                                v -> audioPlayer.queue(Collections.singletonList(nextEntry), 0),
                                Util.getMainThreadExecutor())
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
                    throw new RuntimeException("Not implemented");
                    // TODO: Rethink this
//                    int consumeOffset = playlistOffset - numPlayerPlaylistEntries;
//                    return audioPlayer.dePreload(0, 0, numPlayerPlaylistEntries, 0)
//                            .thenCompose(v -> consumePlaylistEntries(consumeOffset)
//                                    .thenCompose(entries -> {
//                                        if (entries.isEmpty()) {
//                                            return Util.futureResult(null);
//                                        }
//                                        return audioPlayer.queue(
//                                                Collections.singletonList(
//                                                        entries.get(entries.size() - 1)),
//                                                0
//                                        );
//                                    }))
//                            .thenCompose(v -> skipToNext())
//                            .thenCompose(v -> play());
                }
            }
        } else {
            // Skip backward
            // TODO: implement
            return Util.futureResult("Not implemented: skip backward");
        }
    }

    CompletableFuture<Void> queue(List<EntryID> entries, int toPosition) {
        Log.d(LC, "queue(" + entries.size() + ") to " + toPosition);
        List<PlaybackEntry> newPlaybackEntries = new ArrayList<>();
        for (EntryID entryID: entries) {
            newPlaybackEntries.add(new PlaybackEntry(
                    entryID,
                    generatePlaybackID(),
                    PlaybackEntry.USER_TYPE_QUEUE
            ));
        }
        return queuePlaybackEntries(newPlaybackEntries, toPosition);
    }

    private long generatePlaybackID() {
        long id = storage.getNextPlaybackID();
        Log.e(LC, "new ID: " + id);
        return id;
    }

    private CompletableFuture<Void> queuePlaybackEntries(List<PlaybackEntry> entries,
                                                         int toPosition) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        Log.d(LC, "queuePlaybackEntries() to " + toPosition + " : " + entries.toString());
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

        Log.d(LC, "queuePlaybackEntries()"
                + "\nDe-preload: " + numToDepreload
                + "\nDe-preload queue: " + numQueueEntriesToDepreload
                + "\nDe-preload playlist: " + numPlaylistEntriesToDepreload
                + "\nPreload new: " + numNewEntriesToPreload
                + "\nPlayer queue[" + newEntriesToPreloadOffset + "]"
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
        }).thenComposeAsync(v -> newEntriesToPreload.isEmpty() ? CompletableFuture.completedFuture(null) :
                audioPlayer.queue(
                        newEntriesToPreload,
                        newEntriesToPreloadOffset
                ),
                Util.getMainThreadExecutor()
        ).thenCompose(aVoid -> {
            if (newEntriesToController.size() > 0) {
                return dePreloadQueueEntries(
                        newEntriesToController,
                        newEntriesToControllerOffset
                );
            }
            return Util.futureResult(null);
        }).thenComposeAsync(v -> checkPreload(), Util.getMainThreadExecutor());
    }

    void onQueueChanged() {
        List<PlaybackEntry> entries = getAllQueueEntries();
        List<PlaybackEntry> playlistEntries = getAllPlaylistEntries();
        if (playlistEntries.isEmpty()) {
            currentPlaylistPosition = nextPlaylistFetchPosition;
            playlistIDToPosMap.clear();
        } else {
            // TODO: THIS CRASHES ALL
//            currentPlaylistPosition = playlistIDToPosMap.get(playlistEntries.get(0).playbackID);
        }
        storage.setPlaylistPosition(currentPlaylistPosition);
        entries.addAll(playlistEntries);
        callback.onQueueChanged(getAllEntries());
    }

    private CompletableFuture<Void> dePreloadQueueEntries(List<PlaybackEntry> queueEntries,
                                                          int toPosition) {
        return queue.add(toPosition, queueEntries);
    }

    private CompletableFuture<Void> dePreloadPlaylistEntries(List<PlaybackEntry> playlistEntries) {
        return playlistItems.add(0, playlistEntries);
    }

    CompletableFuture<Void> deQueueByID(List<PlaybackEntry> playbackEntries, boolean thenCheckPreload) {
        Log.d(LC, "deQueue");
        CompletableFuture<Void> result = queue.removeEntries(playbackEntries)
//        CompletableFuture<Void> result = Util.futureResult(null)
                .thenComposeAsync(
                        v -> audioPlayer.deQueueEntries(playbackEntries),
                        Util.getMainThreadExecutor()
                );
        return thenCheckPreload ?
                result.thenComposeAsync(v -> checkPreload(), Util.getMainThreadExecutor())
                : result;
    }

//    private CompletableFuture<Void> deQueue(long[] positions, boolean thenCheckPreload) {
//        Arrays.sort(positions);
//        int numPlayerQueueEntries = audioPlayer.getNumQueueEntries();
//        List<Integer> queuePositionsToRemoveFromPlayer = new LinkedList<>();
//        List<Integer> queuePositionsToRemoveFromController = new LinkedList<>();
//        for (long queuePosition: positions) {
//            if (queuePosition < 0) {
//                Log.e(LC, "Can not deQueue negative index: " + queuePosition);
//                continue;
//            }
//            if (queuePosition < numPlayerQueueEntries) {
//                queuePositionsToRemoveFromPlayer.add((int) queuePosition);
//            } else {
//                queuePositionsToRemoveFromController.add((int) queuePosition - numPlayerQueueEntries);
//            }
//        }
//        Log.d(LC, "deQueue()"
//                + "\nfrom player: "
//                + queuePositionsToRemoveFromPlayer.size()
//                + ": " + queuePositionsToRemoveFromPlayer
//                + "\nfrom controller: "
//                + queuePositionsToRemoveFromController.size()
//                + ": " + queuePositionsToRemoveFromController);
//        CompletableFuture<Void> result = Util.futureResult(null);
//        if (!queuePositionsToRemoveFromController.isEmpty()) {
//            result = result.thenCompose(aVoid ->
//                    consumeQueueEntries(queuePositionsToRemoveFromController)
//            ).thenApply(entries -> null);
//        }
//        if (queuePositionsToRemoveFromPlayer.size() > 0) {
//            result = result.thenComposeAsync(
//                    v -> audioPlayer.deQueue(queuePositionsToRemoveFromPlayer),
//                    Util.getMainThreadExecutor()
//            );
//        }
//        return thenCheckPreload ?
//                result.thenComposeAsync(v -> checkPreload(), Util.getMainThreadExecutor())
//                : result;
////        return result;
////                .thenCompose(v -> checkPreload())
////                .thenRun(this::onQueueChanged);
//    }

//    CompletableFuture<Void> deQueue(long[] positions) {
//        return deQueue(positions, true);
//    }

    private CompletionStage<List<PlaybackEntry>> consumeQueueEntries(List<Integer> queuePositions) {
        return queue.remove(queuePositions);
    }

    CompletableFuture<Void> moveQueueItemsByID(List<PlaybackEntry> playbackEntries, int toPosition) {
        if (playbackEntries.size() <= 0 || toPosition < 0) {
            return Util.futureResult(null);
        }
        return musicLibraryService.getSongMetas(playbackEntries.stream().map(p -> p.entryID).collect(Collectors.toList()))
                .thenAccept(metas -> {
                    StringBuilder sb = new StringBuilder("meta to move");
                    for (Meta m: metas) {
                        sb.append("\n").append(m);
                    }
                    Log.e(LC, sb.toString());
                }).thenComposeAsync(aVoid -> {
                    Log.d(LC, "moveQueueItems(" + playbackEntries + ", " + toPosition + ")");
                    return deQueueByID(playbackEntries, false)
                            .thenComposeAsync(
                                    v -> queuePlaybackEntries(playbackEntries, toPosition),
                                    Util.getMainThreadExecutor()
                            );
                }, Util.getMainThreadExecutor());
    }

//    CompletableFuture<Void> moveQueueItems(long[] positions, int toPosition) {
//        if (positions.length <= 0 || toPosition < 0) {
//            return Util.futureResult(null);
//        }
//        Arrays.sort(positions);
//        int numPlayerQueueEntries = audioPlayer.getNumQueueEntries();
//        List<PlaybackEntry> moveEntries = new LinkedList<>();
//        for (long queuePosition: positions) {
//            if (queuePosition < 0) {
//                Log.e(LC, "Can not deQueue negative index: " + queuePosition);
//                continue;
//            }
//            PlaybackEntry entry;
//            if (queuePosition < numPlayerQueueEntries) {
//                entry = audioPlayer.getQueueEntry((int) queuePosition);
//            } else {
//                entry = getQueueEntry((int) queuePosition - numPlayerQueueEntries);
//            }
//            if (entry == null) {
//                return Util.futureResult("moveQueueItems: Internal error: Got null entry.");
//            }
//            moveEntries.add(entry);
//        }
//        Log.d(LC, "moveQueueItems(" + Arrays.toString(positions) + ", " + toPosition + ")"
//                + "\nmoveEntries: " + moveEntries.toString());
//        return deQueue(positions, false)
//                .thenComposeAsync(
//                        v -> queuePlaybackEntries(moveEntries, toPosition),
//                        Util.getMainThreadExecutor()
//                );
////                .thenRun(this::onQueueChanged);
//    }

    CompletableFuture<Void> seekTo(long pos) {
        return audioPlayer.seekTo(pos)
                .thenRun(() -> callback.onPlayerSeekPositionChanged(pos));
    }

    CompletableFuture<Void> playNow(EntryID entryID) {
        return queue(Collections.singletonList(entryID), 0)
                .thenCompose(r -> getNumQueueEntries() > 1 ? skipToNext() : Util.futureResult(null))
                .thenCompose(r -> play());
    }

    void updateCurrent() {
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        EntryID entryID = currentEntry == null ? EntryID.UNKOWN : currentEntry.entryID;
        callback.onMetaChanged(entryID);
    }

    private List<PlaybackEntry> getAllQueueEntries() {
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        playbackEntries.addAll(audioPlayer.getQueueEntries(Integer.MAX_VALUE));
        playbackEntries.addAll(queue.getEntries());
        return playbackEntries;
    }

    private List<PlaybackEntry> getAllPlaylistEntries() {
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        playbackEntries.addAll(audioPlayer.getPlaylistEntries(Integer.MAX_VALUE));
        playbackEntries.addAll(playlistItems.getEntries());
        return playbackEntries;
    }

    private List<PlaybackEntry> getAllEntries() {
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        playbackEntries.addAll(getAllQueueEntries());
        playbackEntries.addAll(getAllPlaylistEntries());
        return playbackEntries;
    }

    long getCurrentPlaylistPosition() {
        return currentPlaylistPosition;
    }

    int getShuffleSeed() {
        // TODO: Smart this up when implementing shuffle
        return 0;
    }

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onQueueChanged(List<PlaybackEntry> queue);
        void onPlaylistSelectionChanged(PlaylistID playlistID, long pos);
        void onPlaylistPositionChanged(long pos);
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
            Log.d(LC, "onPreloadChanged");
            onQueueChanged();
        }

        @Override
        public void onSongEnded() {
            Log.d(LC, "onSongEnded");
            checkPreload();
            // TODO: May need some type of forceful preload. Try with simple checkPreload first
//            int numToPreload = numPreloadNeeded() + 1;
//            if (numToPreload > 0) {
//                pollNextPreloadItems(numToPreload, true)
//                        .thenCompose(entries -> audioPlayer.preload(entries));
//            }
        }
    }

    private CompletableFuture<List<PlaybackEntry>> pollNextPreloadItems(int num, boolean require) {
        Log.d(LC, "pollNextPreloadItems");
        return queue.poll(num)
                .thenComposeAsync(entries -> {
                    int numWanted = num - entries.size();
                    Log.e(LC, "THINKING ABOUT GETTING SOME MOAR PRELOAD: " + entries.size() + " " + num + " " + numWanted);
                    int numPlayerCurrent = audioPlayer.getCurrentEntry() == null ? 0 : 1;
                    int numPlayerQueue = audioPlayer.getNumQueueEntries();
                    int numControllerQueue = getNumQueueEntries();
                    int totalEntries = numPlayerCurrent + numPlayerQueue + numControllerQueue;
                    Log.e(LC,  "pc: " + numPlayerCurrent + " pq: " + numPlayerQueue + " cq: " + numControllerQueue + " tq: " + totalEntries);
                    // TODO: Fix addition of playlistEntries
//                    if ((numWanted > 0 && totalEntries <= 1) // Get if there is no queue
//                            || (entries.isEmpty() && totalEntries <= 2 && require) //
//                    ) {
//                        Log.e(LC, "SEEMS NICE YO");
//                        // Get playlist items
//                        return consumePlaylistEntry().thenApply(entry -> {
//                            if (entry != null) {
//                                entries.add(entry);
//                            }
//                            return entries;
//                        });
//                    }
                    return CompletableFuture.completedFuture(entries);
                }, Util.getMainThreadExecutor());
    }

    private CompletableFuture<Void> transferAudioPlayerState(AudioPlayer.AudioPlayerState state) {
        List<PlaybackEntry> entriesToQueue = state.entries;
        if (state.currentEntry != null) {
            entriesToQueue.add(0, state.currentEntry);
        }
        return queue.add(0, entriesToQueue)
                .thenComposeAsync(
                        aVoid -> history.add(0, state.history),
                        Util.getMainThreadExecutor()
                );
    }

    private void onCastConnect(CastSession session) {
        if (audioPlayer instanceof CastAudioPlayer) {
            // TODO: What if sessions differ? Transfer state?
            Log.w(LC, "onCastConnect: Replacing session for CastAudioPlayer");
            ((CastAudioPlayer)audioPlayer).setCastSession(session);
            return;
        }
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
        // TODO: Fix preload on transfer (without breaking during app restart)
        //
        // TODO: An idea: Add an isInitialized() to AudioPlayer.
        // TODO: checkPreload() (and others?) will schedule a later run if isInitialized() is false.
//                .thenComposeAsync(aVoid -> checkPreload(), Util.getMainThreadExecutor());
        callback.onPlayerChanged(AudioPlayer.Type.CAST);
        updateCurrent();
    }

    private void onCastDisconnect() {
        if (!(audioPlayer instanceof CastAudioPlayer)) {
            return;
        }
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastDisconnect", lastState);
        AudioPlayer oldPlayer = audioPlayer;
        oldPlayer.stop()
                .thenCompose(v -> oldPlayer.destroy());
        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                storage,
                false
        );
        transferAudioPlayerState(lastState);
                // TODO: Fix preload on transfer (without breaking during app restart)
//                .thenComposeAsync(aVoid -> checkPreload(), Util.getMainThreadExecutor());
        callback.onPlayerChanged(AudioPlayer.Type.LOCAL);
        updateCurrent();
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
