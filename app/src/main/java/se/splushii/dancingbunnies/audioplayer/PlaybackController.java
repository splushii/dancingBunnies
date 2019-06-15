package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
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

class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);
    private static final int MAX_PLAYLIST_ENTRIES_TO_PREFETCH = 3;

    private final MusicLibraryService musicLibraryService;
    private final Callback callback;
    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListenerImpl();
    private final SessionManager sessionManager;
    private final PlaybackControllerStorage storage;

    private final LiveData<List<PlaylistEntry>> currentPlaylistEntriesLiveData;
    private final MutableLiveData<PlaylistID> currentPlaylistIDLiveData = new MutableLiveData<>();

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

    // History of played items
    private final PlaybackQueue history;

    // Internal state
    private boolean isPlaying;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object executorLock = new Object();

    PlaybackController(Context context,
                       PlaybackControllerStorage playbackControllerStorage,
                       MusicLibraryService musicLibraryService,
                       Callback callback) {
        this.storage = playbackControllerStorage;
        this.musicLibraryService = musicLibraryService;
        this.callback = callback;

        currentPlaylistID = storage.getCurrentPlaylist();
        currentPlaylistPosition = storage.getPlaylistPosition();

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

        currentPlaylistIDLiveData.setValue(currentPlaylistID);
        currentPlaylistEntriesLiveData = Transformations.switchMap(
                currentPlaylistIDLiveData,
                playlistID -> {
                    if (playlistID == null) {
                        MutableLiveData<List<PlaylistEntry>> entries = new MutableLiveData<>();
                        entries.setValue(Collections.emptyList());
                        return entries;
                    }
                    return PlaylistStorage.getInstance(context).getPlaylistEntries(playlistID);
                }
        );
        currentPlaylistEntriesLiveData.observeForever(playlistEntriesObserver);

        CastContext castContext = CastContext.getSharedInstance(context);
        sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener);
    }

    void initialize() {
        isPlaying = false;
        submitCompletableFuture(this::updateState);
    }

    void onDestroy() {
        Log.d(LC, "onDestroy");
        currentPlaylistEntriesLiveData.removeObserver(playlistEntriesObserver);
        sessionManager.removeSessionManagerListener(sessionManagerListener);
        audioPlayer.stop();
        queue.onDestroy();
        playlistItems.onDestroy();
        history.onDestroy();
    }

    long getPlayerSeekPosition() {
        return audioPlayer.getSeekPosition();
    }

    CompletableFuture<Void> setPlaylist(PlaylistID playlistID) {
        Log.d(LC, "setCurrentPlaylist: " + playlistID);
        setCurrentPlaylistID(playlistID);
        setCurrentPlaylistPosition(0);
        currentPlaylistIDLiveData.setValue(currentPlaylistID);
        callback.onPlaylistSelectionChanged(currentPlaylistID, currentPlaylistPosition);
        return submitCompletableFuture(this::updateState);
    }

    CompletableFuture<Void> setPlaylistPosition(long pos) {
        Log.d(LC, "setCurrentPlaylistPosition: " + pos);
        setCurrentPlaylistPosition(pos);
        callback.onPlaylistPositionChanged(currentPlaylistPosition);
        return submitCompletableFuture(this::updateState);
    }

    private CompletableFuture<Void> removePlaylistEntries() {
        Log.d(LC, "removePlaylistEntries");
        return audioPlayer.dePreload(getPlayerPlaylistEntries())
                .thenComposeAsync(aVoid -> playlistItems.clear());
    }

    private void setCurrentPlaylistID(PlaylistID playlistID) {
        currentPlaylistID = playlistID;
        storage.setCurrentPlaylist(currentPlaylistID);
    }

    private void setCurrentPlaylistPosition(long pos) {
        currentPlaylistPosition = pos;
        storage.setPlaylistPosition(currentPlaylistPosition);
    }

    PlaylistID getCurrentPlaylistID() {
        return currentPlaylistID;
    }

    long getCurrentPlaylistPosition() {
        return currentPlaylistPosition;
    }

    CompletableFuture<Void> play() {
        Log.d(LC, "play");
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.play());
        }
    }

    CompletableFuture<Void> pause() {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.pause());
        }
    }

    CompletableFuture<Void> playPause() {
        synchronized (executorLock) {
            if (isPlaying) {
                return submitCompletableFuture(() -> audioPlayer.pause());
            } else {
                return submitCompletableFuture(() -> audioPlayer.play());
            }
        }
    }

    CompletableFuture<Void> stop() {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.stop());
        }
    }

    CompletableFuture<Void> skipToNext() {
        Log.d(LC, "skipToNext");
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.next());
        }
    }

    CompletableFuture<Void> skipToPrevious() {
        Log.e(LC, "skipToPrevious not implemented");
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.previous());
        }
    }

    private CompletableFuture<List<PlaybackEntry>> requestPlaylistEntries(int num) {
        Log.d(LC, "requestPlaylistEntries");
        PlaylistID playlistID = getCurrentPlaylistID();
        long currentPlaylistPos = getCurrentPlaylistPosition();
        int currentNumPlaylistEntries = getAllPlaylistEntries().size();
        if (playlistID == null) {
            return Util.futureResult(null, Collections.emptyList());
        }
        return musicLibraryService.playlistGetNext(
                playlistID,
                currentPlaylistPos,
                currentNumPlaylistEntries,
                num,
                getShuffleSeed()
        ).thenApply(playlistEntries -> playlistEntries.stream()
                .map(playlistEntry -> new PlaybackEntry(playlistEntry, generatePlaybackID()))
                .collect(Collectors.toList()));
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

    private CompletableFuture<Void> updateState() {
        Log.d(LC, "updateState");
        return CompletableFuture.completedFuture(null)
                .thenCompose(aVoid -> syncPlaylistEntries())
                .thenCompose(aVoid -> updatePreload());
    }

    private CompletionStage<Void> updatePreload() {
        // Check if the audioPlayer needs to be preloaded (how many entries)
        int numToPreload = numPreloadNeeded();
        Log.d(LC, "updatePreload: Preload needed: " + numToPreload);
        if (numToPreload <= 0) {
            return Util.futureResult(null);
        }
        // Get queue entries
        return queue.poll(numToPreload)
                // Get playlist entries from controller
                .thenCompose(entries -> {
                    int numPlaylistEntriesToPreload = numToPreload - entries.size();
                    Log.d(LC, "updatePreload: Preload needed from playlist: "
                            + numPlaylistEntriesToPreload);
                    if (numPlaylistEntriesToPreload > 0) {
                        return playlistItems.poll(numPlaylistEntriesToPreload)
                                .thenApply(playbackEntries -> {
                                    if (playbackEntries != null) {
                                        entries.addAll(playbackEntries);
                                    }
                                    return entries;
                                });
                    }
                    return Util.futureResult(null, entries);
                })
                .thenCompose(entries -> entries.isEmpty() ? Util.futureResult(null) :
                        audioPlayer.preload(entries, audioPlayer.getNumPreloaded()));
    }

    private CompletableFuture<Void> syncPlaylistEntries() {
        Log.d(LC, "syncPlaylistEntries");
        // Update playlist entries
        return CompletableFuture.supplyAsync(
                () -> {
                    // Check if there are too many playlist entries
                    boolean tooManyPlaylistEntries =
                            getAllPlaylistEntries().size() > MAX_PLAYLIST_ENTRIES_TO_PREFETCH;
                    Log.d(LC, "syncPlaylistEntries:"
                            + " Not too many playlist entries."
                            + " Correct: " + !tooManyPlaylistEntries);
                    if (tooManyPlaylistEntries) {
                        return true;
                    }
                    // Check if any playlist entry is before any another type of entry
                    boolean incorrect = false;
                    List<PlaybackEntry> allEntries = getAllEntries();
                    PlaybackEntry prevEntry = null;
                    for (PlaybackEntry entry: allEntries) {
                        if (prevEntry == null) {
                            prevEntry = entry;
                            continue;
                        }
                        if (prevEntry.playbackType.equals(PlaybackEntry.USER_TYPE_PLAYLIST)
                                && !entry.playbackType.equals(PlaybackEntry.USER_TYPE_PLAYLIST)) {
                            incorrect = true;
                            break;
                        }
                        prevEntry = entry;
                    }
                    Log.d(LC, "syncPlaylistEntries:"
                            + " Playlist entries not before any other type."
                            + " Correct: " + !incorrect);
                    return incorrect;
                })
                .thenCompose(playlistIsIncorrect -> {
                    if (playlistIsIncorrect) {
                        return Util.futureResult(null, true);
                    }
                    // Check if the current playlist entries is correct
                    // i.e. equals the actual playlist entries from selected playlist
                    List<PlaybackEntry> currentPlaylistEntries = getAllPlaylistEntries();
                    return musicLibraryService.playlistGetNext(
                            getCurrentPlaylistID(),
                            getCurrentPlaylistPosition(),
                            0,
                            currentPlaylistEntries.size(),
                            getShuffleSeed()
                    ).thenCompose(actualPlaylistEntries -> {
                        int actualSize = actualPlaylistEntries.size();
                        int currentSize = currentPlaylistEntries.size();
                        boolean incorrect = actualSize != currentSize;
                        if (!incorrect) {
                            for (int i = 0; i < actualSize; i++) {
                                PlaylistEntry actual = actualPlaylistEntries.get(i);
                                PlaybackEntry current = currentPlaylistEntries.get(i);
                                if (actual.pos != current.playlistPos
                                        || !EntryID.from(actual).equals(current.entryID)) {
                                    incorrect = true;
                                    break;
                                }
                            }
                        }
                        Log.d(LC, "syncPlaylistEntries: "
                                + "Current playlist entries in correct order."
                                + " Correct: " + !incorrect);
                        return Util.futureResult(null, incorrect);
                    });
                })
                .thenCompose(
                        playlistIsIncorrect -> playlistIsIncorrect ?
                                removePlaylistEntries() : Util.futureResult(null))
                .thenCompose(aVoid -> {
                    // Fill up with playlist entries
                    int numPlaylistEntriesToFetch =
                            MAX_PLAYLIST_ENTRIES_TO_PREFETCH - getAllPlaylistEntries().size();
                    Log.d(LC, "syncPlaylistEntries: request " + numPlaylistEntriesToFetch
                            + " playlist entries");
                    if (numPlaylistEntriesToFetch > 0) {
                        return requestPlaylistEntries(numPlaylistEntriesToFetch);
                    }
                    return Util.futureResult(null, Collections.emptyList());
                })
                .thenCompose(playbackEntries -> {
                    Log.d(LC, "syncPlaylistEntries: got " + playbackEntries.size()
                            + " playlist entries");
                    return playlistItems.add(playlistItems.size(), playbackEntries);
                });
    }

    CompletableFuture<Void> skip(int offset) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> _skip(offset));
        }
    }

    private CompletableFuture<Void> _skip(int offset) {
        Log.d(LC, "skip(" + offset + ")");
        if (offset == 0) {
            return CompletableFuture.completedFuture(null);
        }
        if (offset == 1) {
            return audioPlayer.next();
        }
        int numPlayerQueueEntries = getPlayerQueueEntries().size();
        int numControllerQueueEntries = queue.size();
        int totalQueueEntries = numPlayerQueueEntries + numControllerQueueEntries;
        PlaybackEntry nextEntry;
        if (offset > 0) {
            offset--;
            // Skip forward
            if (offset < totalQueueEntries) {
                // Play queue item at offset now
                CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
                if (offset < numPlayerQueueEntries) {
                    // Get the queue entry from player
                    Log.d(LC, "skip short queue offset");
                    nextEntry = audioPlayer.getPreloadEntry(offset);
                    result = result.thenCompose(v ->
                            audioPlayer.dePreload(Collections.singletonList(nextEntry))
                    );
                } else {
                    // Get the queue entry from controller
                    int controllerQueueOffset = offset - numPlayerQueueEntries;
                    Log.d(LC, "skip long queue offset");
                    nextEntry = getQueueEntry(controllerQueueOffset);
                    result = result.thenCompose(
                            v -> queue.removeEntries(Collections.singletonList(nextEntry)))
                            .thenApply(entries -> null);
                }
                // Queue after current and play
                return result
                        .thenCompose(v ->
                                        audioPlayer.preload(Collections.singletonList(nextEntry), 0))
                        .thenCompose(v -> audioPlayer.next())
                        .thenCompose(v -> audioPlayer.play());
            } else {
                // Skip all playlist items until offset
                if (offset < audioPlayer.getNumPreloaded()) {
                    // Remove all playlist items until offset, then queue and play offset
                    Log.d(LC, "skip short playlist offset");
                    nextEntry = audioPlayer.getPreloadEntry(offset);
                    List<PlaybackEntry> entriesToDePreload = getPlayerPlaylistEntries()
                            .stream()
                            .limit(offset - numPlayerQueueEntries)
                            .collect(Collectors.toList());
                    return audioPlayer.dePreload(entriesToDePreload)
                            .thenCompose(v -> audioPlayer.preload(Collections.singletonList(nextEntry), 0))
                            .thenCompose(v -> audioPlayer.next())
                            .thenCompose(v -> audioPlayer.play());
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

    long generatePlaybackID() {
        return storage.getNextPlaybackID();
    }

    private List<PlaybackEntry> generatePlaybackEntries(List<EntryID> entryIDs) {
        return entryIDs.stream()
                .map(e -> new PlaybackEntry(
                        e,
                        generatePlaybackID(),
                        PlaybackEntry.USER_TYPE_QUEUE,
                        PlaybackEntry.PLAYLIST_POS_NONE
                )).collect(Collectors.toList());
    }

    CompletableFuture<Void> queueToPos(List<EntryID> entryIDs, int toPosition) {
        long beforePlaybackID = getQueuePlaybackID(toPosition);
        Log.d(LC, "queueToPos toPos: " + toPosition
                + " beforePlaybackID: " + beforePlaybackID
                + " entries: " + entryIDs);
        return queue(entryIDs, beforePlaybackID);
    }

    CompletableFuture<Void> queue(List<EntryID> entryIDs) {
        return queue(entryIDs, PlaybackEntry.PLAYBACK_ID_INVALID);
    }

    CompletableFuture<Void> queue(List<EntryID> entryIDs, long beforePlaybackID) {
        synchronized (executorLock) {
            return submitCompletableFuture(() ->
                    queuePlaybackEntries(generatePlaybackEntries(entryIDs), beforePlaybackID)
            );
        }
    }

    private int getQueuePos(long playbackID) {
        List<PlaybackEntry> queueEntries = getAllQueueEntries();
        int pos = AudioPlayerService.QUEUE_LAST;
        for (int i = 0; i < queueEntries.size(); i++) {
            if (playbackID == queueEntries.get(i).playbackID) {
                pos = i;
                break;
            }
        }
        return pos;
    }

    private long getQueuePlaybackID(int pos) {
        List<PlaybackEntry> queueEntries = getAllQueueEntries();
        if (pos < 0 || pos > queueEntries.size()) {
            return PlaybackEntry.PLAYBACK_ID_INVALID;
        }
        return queueEntries.get(pos).playbackID;
    }

    private CompletableFuture<Void> queuePlaybackEntries(List<PlaybackEntry> entries,
                                                         long beforePlaybackID) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        int toPosition = getQueuePos(beforePlaybackID);
        Log.d(LC, "queuePlaybackEntries() to " + toPosition + " : " + entries.toString());
        List<PlaybackEntry> playerQueue = getPlayerQueueEntries();
        List<PlaybackEntry> playerPlaylist = getPlayerPlaylistEntries();
        int numPlayerQueue = playerQueue.size();
        int numPlayerPlaylist = playerPlaylist.size();
        int numControllerQueue = queue.size();
        int numTotalQueue = numPlayerQueue + numControllerQueue;
        int numNew = entries.size();
        int maxToPreload = audioPlayer.getMaxToPreload();
        if (toPosition < 0 || toPosition > numTotalQueue) {
            toPosition = numTotalQueue;
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

        return CompletableFuture.completedFuture(null)
                .thenCompose(v -> {
                    List<PlaybackEntry> entriesToDePreload = new ArrayList<>();
                    entriesToDePreload.addAll(queueEntriesToDePreload);
                    entriesToDePreload.addAll(playlistEntriesToDePreload);
                    if (!entriesToDePreload.isEmpty()) {
                        return audioPlayer.dePreload(
                                entriesToDePreload
                        ).thenCompose(aVoid -> {
                            if (!playlistEntriesToDePreload.isEmpty()) {
                                return playlistItems.add(0, playlistEntriesToDePreload);
                            }
                            return Util.futureResult(null);
                        }).thenCompose(aVoid -> {
                            if (!queueEntriesToDePreload.isEmpty()) {
                                return queue.add(0, queueEntriesToDePreload);
                            }
                            return Util.futureResult(null);
                        });
                    } else {
                        return Util.futureResult(null);
                    }
                })
                .thenCompose(v -> newEntriesToPreload.isEmpty() ?
                        CompletableFuture.completedFuture(null)
                        :
                        audioPlayer.preload(newEntriesToPreload, newEntriesToPreloadOffset)
                )
                .thenCompose(aVoid -> {
                    if (newEntriesToController.size() > 0) {
                        return queue.add(newEntriesToControllerOffset, newEntriesToController);
                    }
                    return Util.futureResult(null);
                })
                .thenCompose(v -> updateState());
    }

    void onQueueChanged() {
        List<PlaybackEntry> entries = getAllQueueEntries();
        List<PlaybackEntry> playlistEntries = getAllPlaylistEntries();
        if (!playlistEntries.isEmpty()) {
            long actualPlaylistPos = playlistEntries.get(0).playlistPos;
            if (actualPlaylistPos != getCurrentPlaylistPosition()) {
                setCurrentPlaylistPosition(actualPlaylistPos);
                callback.onPlaylistPositionChanged(actualPlaylistPos);
            }
        }
        entries.addAll(playlistEntries);
        callback.onQueueChanged(getAllEntries());
    }

    CompletableFuture<Void> deQueue(List<PlaybackEntry> playbackEntries) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> _deQueue(playbackEntries, true));
        }
    }

    private CompletableFuture<Void> _deQueue(List<PlaybackEntry> playbackEntries, boolean thenUpdateState) {
        Log.d(LC, "deQueue");
        CompletableFuture<Void> result = queue.removeEntries(playbackEntries)
                .thenCompose(v -> audioPlayer.dePreload(playbackEntries));
        return thenUpdateState ? result.thenCompose(aVoid -> updateState()) : result;
    }

    CompletableFuture<Void> moveQueueItems(List<PlaybackEntry> playbackEntries,
                                           long beforePlaybackID) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> {
                Log.d(LC, "moveQueueItems("
                        + playbackEntries + ", beforePlaybackID: " + beforePlaybackID + ")");
                return _deQueue(playbackEntries, false)
                        .thenCompose(v -> queuePlaybackEntries(playbackEntries, beforePlaybackID));
            });
        }
    }

    CompletableFuture<Void> seekTo(long pos) {
        synchronized (executorLock) {
            return submitCompletableFuture(() ->
                    audioPlayer.seekTo(pos)
                            .thenRun(() -> callback.onPlayerSeekPositionChanged(pos)));
        }
    }

    CompletableFuture<Void> playNow(List<EntryID> entryIDs) {
        synchronized (executorLock) {
            return submitCompletableFuture(
                    () -> queuePlaybackEntries(
                            generatePlaybackEntries(entryIDs),
                            getQueuePlaybackID(0)
                    ).thenCompose(r -> getNumTotalQueueEntries() > 1 ?
                            audioPlayer.next() : Util.futureResult(null)
                    ).thenCompose(r -> audioPlayer.play()));
        }
    }

    private int getNumTotalQueueEntries() {
        return getPlayerQueueEntries().size() + queue.size();
    }

    void updateCurrent() {
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        EntryID entryID = currentEntry == null ? EntryID.UNKOWN : currentEntry.entryID;
        callback.onMetaChanged(entryID);
    }

    private List<PlaybackEntry> getPlayerEntries() {
        return audioPlayer.getPreloadEntries();
    }

    private List<PlaybackEntry> getPlayerQueueEntries() {
        return getPlayerEntries().stream()
                .filter(p -> !PlaybackEntry.USER_TYPE_PLAYLIST.equals(p.playbackType))
                .collect(Collectors.toList());
    }

    private List<PlaybackEntry> getPlayerPlaylistEntries() {
        return getPlayerEntries().stream()
                .filter(p -> PlaybackEntry.USER_TYPE_PLAYLIST.equals(p.playbackType))
                .collect(Collectors.toList());
    }

    private List<PlaybackEntry> getAllQueueEntries() {
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        playbackEntries.addAll(getPlayerQueueEntries());
        playbackEntries.addAll(queue.getEntries());
        return playbackEntries;
    }

    private List<PlaybackEntry> getAllPlaylistEntries() {
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        playbackEntries.addAll(getPlayerPlaylistEntries());
        playbackEntries.addAll(playlistItems.getEntries());
        return playbackEntries;
    }

    private List<PlaybackEntry> getAllEntries() {
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        playbackEntries.addAll(getAllQueueEntries());
        playbackEntries.addAll(getAllPlaylistEntries());
        return playbackEntries;
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
            submitCompletableFuture(PlaybackController.this::updateState);
        }

        @Override
        public void onSongEnded() {
            Log.d(LC, "onSongEnded");
            submitCompletableFuture(PlaybackController.this::updateState);
        }
    }

    private CompletableFuture<Void> submitCompletableFuture(Supplier<CompletableFuture<Void>> futureSupplier) {
        CompletableFuture<Void> ret = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                futureSupplier.get().get();
                ret.complete(null);
            } catch (ExecutionException e) {
                e.printStackTrace();
                ret.completeExceptionally(e);
            } catch (InterruptedException e) {
                e.printStackTrace();
                ret.completeExceptionally(e);
            }
        });
        return ret;
    }

    private CompletableFuture<Void> transferAudioPlayerState(AudioPlayer.AudioPlayerState state) {
        Log.d(LC, "transferAudioPlayerState");
        List<PlaybackEntry> queueEntries = state.entries.stream()
                .filter(p -> PlaybackEntry.USER_TYPE_QUEUE.equals(p.playbackType))
                .collect(Collectors.toList());
        List<PlaybackEntry> playlistEntries = state.entries.stream()
                .filter(p -> PlaybackEntry.USER_TYPE_PLAYLIST.equals(p.playbackType))
                .collect(Collectors.toList());
        if (state.currentEntry != null) {
            queueEntries.add(0, state.currentEntry);
        }
        return audioPlayer.initialize()
                .thenCompose(aVoid -> queue.add(0, queueEntries))
                .thenCompose(aVoid -> playlistItems.add(0, playlistEntries))
                .thenCompose(aVoid -> history.add(0, state.history))
                .thenCompose(aVoid -> updateState());
    }

    private AudioPlayer.AudioPlayerState resetController() {
        executor.shutdownNow();
        try {
            if (executor.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.e(LC, "Terminated");
            } else {
                Log.e(LC, "Not terminated. Timeout");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastConnect", lastState);
        audioPlayer.destroy();
        executor = Executors.newSingleThreadExecutor();
        return lastState;
    }

    private void onCastConnect(CastSession session) {
        if (audioPlayer instanceof CastAudioPlayer) {
            // TODO: What if sessions differ? Transfer state?
            Log.w(LC, "onCastConnect: Replacing session for CastAudioPlayer");
            ((CastAudioPlayer)audioPlayer).setCastSession(session);
            return;
        }
        AudioPlayer.AudioPlayerState lastPlayerState = resetController();
        audioPlayer = new CastAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                session
        );
        submitCompletableFuture(() -> transferAudioPlayerState(lastPlayerState));
        callback.onPlayerChanged(AudioPlayer.Type.CAST);
        updateCurrent();
    }

    private void onCastDisconnect() {
        if (!(audioPlayer instanceof CastAudioPlayer)) {
            return;
        }
        AudioPlayer.AudioPlayerState lastPlayerState = resetController();
        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                storage,
                false
        );
        submitCompletableFuture(() -> transferAudioPlayerState(lastPlayerState));
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

    private Observer<List<PlaylistEntry>> playlistEntriesObserver = playlistEntries -> {
        Log.d(LC, "playlistEntries changed for: " + currentPlaylistIDLiveData.getValue());
        submitCompletableFuture(this::updateState);
    };
}
