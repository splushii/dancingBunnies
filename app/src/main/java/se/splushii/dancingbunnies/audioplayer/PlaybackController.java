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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
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

public class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);
    private static final int MAX_PLAYLIST_ENTRIES_TO_PREFETCH = 3;

    public static final int PLAYBACK_ORDER_SEQUENTIAL = 0;
    public static final int PLAYBACK_ORDER_SHUFFLE = 1;
    public static final int PLAYBACK_ORDER_RANDOM = 2;

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
    private long currentPlaylistSelectionID;
    private int currentPlaylistPlaybackOrderMode;
    private boolean currentPlaylistPlaybackRepeatMode;
    private final MutableLiveData<PlaylistID> currentPlaylistIDLiveData = new MutableLiveData<>();
    private PlaylistID currentPlaylistID;
    // Current playlist entries
    private final LiveData<List<PlaylistEntry>> currentPlaylistEntriesLiveData;
    private final Observer<List<PlaylistEntry>> currentPlaylistEntriesObserver;
    private List<PlaylistEntry> currentPlaylistEntries;
    // Current playlist entries in playback order.
    // Generated from current playlist entries and possibly reordered by user.
    private final PlaybackQueue currentPlaylistPlaybackEntries;
    private long currentPlaylistPosition;
    private long currentPlaylistPlaybackPosition;
    private HashMap<Long, Long> playbackIDToRandomMap = new HashMap<>();

    // History of played items
    private final PlaybackQueue history;

    // Internal state
    private boolean isPlaying;
    private boolean endOfPlaylistPlayback = false;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object executorLock = new Object();

    PlaybackController(Context context,
                       PlaybackControllerStorage playbackControllerStorage,
                       MusicLibraryService musicLibraryService,
                       Callback callback) {
        this.storage = playbackControllerStorage;
        this.musicLibraryService = musicLibraryService;
        this.callback = callback;

        currentPlaylistSelectionID = storage.getCurrentPlaylistSelectionID();
        currentPlaylistID = storage.getCurrentPlaylist();
        currentPlaylistPosition = storage.getCurrentPlaylistPosition();
        currentPlaylistPlaybackPosition = storage.getCurrentPlaylistPlaybackPosition();
        currentPlaylistPlaybackOrderMode = storage.getCurrentPlaylistPlaybackOrderMode();
        currentPlaylistPlaybackRepeatMode = storage.getCurrentPlaylistPlaybackRepeatMode();
        Log.d(LC, "Construct:"
                + "\ncurrentPlaylistSelectionID: " + currentPlaylistSelectionID
                + "\ncurrentPlaylistID: " + currentPlaylistID
                + "\ncurrentPlaylistPlaybackPosition: " + currentPlaylistPlaybackPosition
                + "\ncurrentPlaylistPlaybackOrderMode: " + currentPlaylistPlaybackOrderMode
                + "\ncurrentPlaylistPlaybackRepeatMode: " + currentPlaylistPlaybackRepeatMode
        );

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
                () -> Log.d(LC, "playback history entries changed")
        );
        LiveData<List<PlaybackEntry>> currentPlaylistPlaybackEntriesLiveData =
                storage.getCurrentPlaylistPlaybackEntries();
        currentPlaylistPlaybackEntries = new PlaybackQueue(
                PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK,
                storage,
                currentPlaylistPlaybackEntriesLiveData,
                () -> Log.d(LC, "playlist playback entries changed")
        );

        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                storage,
                true
        );

        currentPlaylistIDLiveData.setValue(currentPlaylistID);
        currentPlaylistEntries = new ArrayList<>();
        currentPlaylistEntriesLiveData = Transformations.switchMap(
                currentPlaylistIDLiveData,
                playlistID -> MusicLibraryService.getPlaylistEntries(context, playlistID)
        );
        currentPlaylistEntriesObserver = this::onCurrentPlaylistEntriesChanged;
        currentPlaylistEntriesLiveData.observeForever(currentPlaylistEntriesObserver);

        CastContext castContext = CastContext.getSharedInstance(context);
        sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener);
    }

    void initialize() {
        isPlaying = false;
        callback.onPlaybackOrderChanged(getCurrentPlaylistPlaybackOrderMode());
        callback.onRepeatModeChanged(getCurrentPlaylistPlaybackRepeatMode());
        callback.onPlaylistSelectionChanged(
                getCurrentPlaylistID(),
                getCurrentPlaylistPosition()
        );
        onQueueChanged();
        submitCompletableFuture(this::updateState);
    }

    void onDestroy() {
        Log.d(LC, "onDestroy");
        currentPlaylistEntriesLiveData.removeObserver(currentPlaylistEntriesObserver);
        sessionManager.removeSessionManagerListener(sessionManagerListener);
        audioPlayer.stop();
        queue.onDestroy();
        playlistItems.onDestroy();
        history.onDestroy();
    }

    long getPlayerSeekPosition() {
        return audioPlayer.getSeekPosition();
    }

    CompletableFuture<Void> setPlaylist(PlaylistID playlistID, long pos) {
        _setPlaylist(playlistID, pos);
        return submitCompletableFuture(this::updateState);
    }

    private void _setPlaylist(PlaylistID playlistID, long pos) {
        Log.d(LC, "setCurrentPlaylist: " + playlistID + " pos: " + pos);
        setCurrentPlaylistSelectionID(storage.getNextPlaylistSelectionID());
        if (playlistID == null || !playlistID.equals(getCurrentPlaylistID())) {
            setCurrentPlaylistID(playlistID);
            currentPlaylistPlaybackEntries.clear();
            currentPlaylistIDLiveData.setValue(playlistID);
        }
        setCurrentPlaylistPosition(pos, playlistPlaybackPosFromPlaylistPos(pos));
        callback.onPlaylistSelectionChanged(playlistID, pos);
    }

    private long playlistPlaybackPosFromPlaylistPos(long playlistPos) {
        int index = 0;
        for (PlaybackEntry e: currentPlaylistPlaybackEntries.getEntries()) {
            if (e.playlistPos == playlistPos) {
                return index;
            }
            index++;
        }
        return playlistPos;
    }

    private CompletableFuture<Void> removePlaylistEntries() {
        Log.d(LC, "removePlaylistEntries");
        return playlistItems.clear()
                .thenCompose(aVoid -> audioPlayer.dePreload(getPlayerPlaylistEntries()));
    }

    private void setCurrentPlaylistSelectionID(long id) {
        Log.d(LC, "setCurrentPlaylistSelectionID: " + id);
        currentPlaylistSelectionID = id;
        storage.setCurrentPlaylistSelectionID(id);
    }

    private long getCurrentPlaylistSelectionID() {
        return currentPlaylistSelectionID;
    }

    private void setCurrentPlaylistPlaybackOrderMode(int playbackOrderMode) {
        currentPlaylistPlaybackOrderMode = playbackOrderMode;
        storage.setCurrentPlaylistPlaybackOrderMode(playbackOrderMode);
        callback.onPlaybackOrderChanged(playbackOrderMode);
    }

    private int getCurrentPlaylistPlaybackOrderMode() {
        return currentPlaylistPlaybackOrderMode;
    }

    private void setCurrentPlaylistPlaybackRepeatMode(boolean repeat) {
        currentPlaylistPlaybackRepeatMode = repeat;
        storage.setCurrentPlaylistPlaybackRepeatMode(repeat);
        callback.onRepeatModeChanged(repeat);
    }

    private boolean getCurrentPlaylistPlaybackRepeatMode() {
        return currentPlaylistPlaybackRepeatMode;
    }

    private void setCurrentPlaylistID(PlaylistID playlistID) {
        currentPlaylistID = playlistID;
        storage.setCurrentPlaylist(playlistID);
    }

    PlaylistID getCurrentPlaylistID() {
        return currentPlaylistID;
    }

    private void setCurrentPlaylistPosition(long playlistPos, long playlistPlaybackPos) {
        Log.d(LC, "setCurrentPlaylistPosition"
                + " playlist: " + playlistPos + " playback: " + playlistPlaybackPos);
        currentPlaylistPosition = playlistPos;
        currentPlaylistPlaybackPosition = playlistPlaybackPos;
        storage.setCurrentPlaylistPosition(playlistPos, playlistPlaybackPos);
    }

    private long getCurrentPlaylistPlaybackPosition() {
        return currentPlaylistPlaybackPosition;
    }

    private long getCurrentPlaylistPosition() {
        return currentPlaylistPosition;
    }

    CompletableFuture<Void> resetPlaybackOrder() {
        Log.d(LC, "resetPlaybackOrder");
        return submitCompletableFuture(() -> {
            List<PlaybackEntry> playlistPlaybackEntries = currentPlaylistPlaybackEntries.getEntries();
            Collections.sort(playlistPlaybackEntries, (a, b) -> Long.compare(a.playlistPos, b.playlistPos));
            return currentPlaylistPlaybackEntries.replaceWith(playlistPlaybackEntries)
                    .thenRun(() -> {
                        setCurrentPlaylistPlaybackOrderMode(PLAYBACK_ORDER_SEQUENTIAL);
                        setCurrentPlaylistSelectionID(storage.getNextPlaylistSelectionID());
                        PlaybackEntry entry = currentPlaylistPlaybackEntries.get(0);
                        long playlistPos = entry == null ? 0: entry.playlistPos;
                        setCurrentPlaylistPosition(playlistPos, 0);
                        callback.onPlaylistSelectionChanged(getCurrentPlaylistID(), playlistPos);
                    })
                    .thenRun(this::updateState);
        });
    }

    CompletableFuture<Void> shufflePlaybackOrder() {
        Log.d(LC, "shufflePlaybackOrder");
        return submitCompletableFuture(() -> {
            List<PlaybackEntry> playlistPlaybackEntries = currentPlaylistPlaybackEntries.getEntries();
            Collections.shuffle(playlistPlaybackEntries);
            return currentPlaylistPlaybackEntries.replaceWith(playlistPlaybackEntries)
                    .thenRun(() -> {
                        setCurrentPlaylistPlaybackOrderMode(PLAYBACK_ORDER_SHUFFLE);
                        setCurrentPlaylistSelectionID(storage.getNextPlaylistSelectionID());
                        PlaybackEntry entry = currentPlaylistPlaybackEntries.get(0);
                        long playlistPos = entry == null ? 0: entry.playlistPos;
                        setCurrentPlaylistPosition(playlistPos, 0);
                        callback.onPlaylistSelectionChanged(getCurrentPlaylistID(), playlistPos);
                    })
                    .thenRun(this::updateState);
        });
    }

    CompletableFuture<Void> setRandomPlayback() {
        Log.d(LC, "setRandomPlayback");
        return submitCompletableFuture(() -> {
            setCurrentPlaylistPlaybackOrderMode(PLAYBACK_ORDER_RANDOM);
            setCurrentPlaylistPlaybackRepeatMode(true);
            return updateState();
        });
    }

    CompletableFuture<Void> setRepeat(boolean repeat) {
        return submitCompletableFuture(() -> {
            setCurrentPlaylistPlaybackRepeatMode(repeat);
            return updateState();
        });
    }

    private long getNextRandomSeed(long seed) {
        return new Random(seed).nextLong();
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

    private long nextRandom(PlaybackEntry entry) {
        Long rand = playbackIDToRandomMap.get(entry.playbackID);
        if (rand == null) {
            Log.d(LC, "nextRandom generating new rand for entry: "
                    + entry.playbackID + "[" + entry.playlistPos + "]");
            rand = getNextRandomSeed(entry.playbackID);
            playbackIDToRandomMap.put(entry.playbackID, rand);
        }
        return getNextRandomSeed(rand);
    }

    private void setRandom(List<PlaybackEntry> playlistPlaybackEntries) {
        long nextRandom;
        PlaybackEntry lastPlaylistPlaybackEntry = getLastPlaylistPlaybackEntry();
        if (lastPlaylistPlaybackEntry != null) {
            nextRandom = nextRandom(lastPlaylistPlaybackEntry);
        } else {
            Log.w(LC, "setRandom: "
                    + "Could not get random seed from last playlist playback entry. Reseeding.");
            nextRandom = new Random().nextLong();
        }
        for (PlaybackEntry entry: playlistPlaybackEntries) {
            playbackIDToRandomMap.put(entry.playbackID, nextRandom);
            nextRandom = getNextRandomSeed(nextRandom);
        }
    }

    private PlaybackEntry getLastPlaylistPlaybackEntry() {
        // TODO: A more stable way would probably be to search history for the last
        // TODO: playback entry (with correct type and selection id), instead of only
        // TODO: checking the current entry (because it could for example be a queue entry).
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        if (currentEntry != null
                && PlaybackEntry.USER_TYPE_PLAYLIST.equals(currentEntry.playbackType)
                && currentEntry.playlistSelectionID == getCurrentPlaylistSelectionID()) {
            return currentEntry;
        }
        Log.w(LC, "getLastPlaylistPlaybackEntry: Not found");
        return null;
    }

    List<PlaybackEntry> playlistGetNext(int offset, int maxEntries, boolean dummyPlaybackID) {
        List<PlaybackEntry> entries = currentPlaylistPlaybackEntries.getEntries();
        if (entries == null || entries.isEmpty() || endOfPlaylistPlayback) {
            return Collections.emptyList();
        }
        List<PlaybackEntry> chosenEntries = new ArrayList<>();
        int nextIndex = (int) getCurrentPlaylistPlaybackPosition();
        PlaybackEntry nextEntry = entries.get(nextIndex);
        boolean randomOrder = getCurrentPlaylistPlaybackOrderMode() == PLAYBACK_ORDER_RANDOM;
        long nextRandom = 0L;
        if (randomOrder) {
            // Use the last playlist playback entry as seed for random
            PlaybackEntry lastPlaylistPlaybackEntry = getLastPlaylistPlaybackEntry();
            if (lastPlaylistPlaybackEntry != null) {
                nextRandom = nextRandom(lastPlaylistPlaybackEntry);
                nextIndex = nextPlaylistPosition(
                        playlistPlaybackPosFromPlaylistPos(lastPlaylistPlaybackEntry.playlistPos),
                        entries.size(),
                        nextRandom
                );
                nextEntry = entries.get(nextIndex);
            } else {
                Log.w(LC, "playlistGetNext: "
                        + "Could not get random seed from last playlist playback entry. Reseeding.");
                nextRandom = getNextRandomSeed(nextEntry.playlistPos);
            }
        }
        nextRandom = randomOrder ? getNextRandomSeed(nextRandom) : 0L;
        boolean repeat = getCurrentPlaylistPlaybackRepeatMode();
        long playbackID = dummyPlaybackID ?
                PlaybackEntry.PLAYBACK_ID_INVALID : reservePlaybackIDs(maxEntries);
        for (int i = 0; i < offset + maxEntries; i++) {
            if (i >= offset) {
                chosenEntries.add(new PlaybackEntry(
                        nextEntry.entryID,
                        // Set new ID:s, or else there may be multiple playback entries with same
                        // playback id in the queue.
                        dummyPlaybackID ? PlaybackEntry.PLAYBACK_ID_INVALID : playbackID++,
                        nextEntry.playbackType,
                        nextEntry.playlistPos,
                        // Overwrite dummy selection id with current playlist selection id
                        getCurrentPlaylistSelectionID()
                ));
            }
            long prevIndex = nextIndex;
            nextIndex = nextPlaylistPosition(nextIndex, entries.size(), nextRandom);
            if (!repeat && nextIndex < prevIndex) {
                return chosenEntries;
            }
            nextEntry = entries.get(nextIndex);
            nextRandom = randomOrder ? getNextRandomSeed(nextRandom) : 0L;
        }
        return chosenEntries;
    }

    private int nextPlaylistPosition(long index, int playlistSize, long random) {
        return validPlaylistPosition((int) (index + 1 + random), playlistSize);
    }

    private int validPlaylistPosition(int index, int playlistSize) {
        if (playlistSize <= 0) {
            return 0;
        }
        int nextIndex = index % playlistSize;
        return nextIndex < 0 ? nextIndex + playlistSize : nextIndex;
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
                .thenCompose(aVoid -> updateHistory())
                .thenCompose(aVoid -> updatePreload());
    }

    private CompletionStage<Void> updateHistory() {
        List<PlaybackEntry> historyEntries = audioPlayer.getHistory();
        Log.d(LC, "updateHistory getting " + historyEntries.size());
        return audioPlayer.dePreload(historyEntries)
                .thenCompose(aVoid -> history.add(0, historyEntries));
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

    private boolean isCurrentPlaylistCorrect() {
        List<PlaybackEntry> allEntries = getAllEntries();
        List<PlaybackEntry> currentPlaylistEntries = getAllPlaylistEntries();
        // Check if the current playlist entries have
        // currentPlaylistSelectionID as playlistSelectionID
        long currentSelectionID = getCurrentPlaylistSelectionID();
        for (PlaybackEntry entry: currentPlaylistEntries) {
            if (entry.playlistSelectionID != currentSelectionID) {
                Log.d(LC, "isCurrentPlaylistCorrect: No."
                        +" There are playlist entries with the wrong playlist selection ID.");
                return false;
            }
        }
        // Check if there are too many playlist entries
        if (currentPlaylistEntries.size() > MAX_PLAYLIST_ENTRIES_TO_PREFETCH) {
            Log.d(LC, "isCurrentPlaylistCorrect: No."
                    + " Too many playlist entries (" + currentPlaylistEntries.size()  + ")."
                    + " Exceeding max entries to prefetch: " + MAX_PLAYLIST_ENTRIES_TO_PREFETCH);
            return false;
        }
        // Check if any playlist entry is before any another type of entry
        PlaybackEntry prevEntry = null;
        for (PlaybackEntry entry: allEntries) {
            if (prevEntry == null) {
                prevEntry = entry;
                continue;
            }
            if (prevEntry.playbackType.equals(PlaybackEntry.USER_TYPE_PLAYLIST)
                    && !entry.playbackType.equals(PlaybackEntry.USER_TYPE_PLAYLIST)) {
                Log.d(LC, "isCurrentPlaylistCorrect: No."
                        + " There are playlist entries before other entry types.");
                return false;
            }
            prevEntry = entry;
        }
        // Check if the all entries in the current playlist equals the
        // expected playlist entries from the selected playlist
        List<PlaybackEntry> expectedPlaylistEntries = playlistGetNext(
                0,
                currentPlaylistEntries.size(),
                true
        );
        if (currentPlaylistEntries.size() > expectedPlaylistEntries.size()) {
            Log.d(LC, "isCurrentPlaylistCorrect: No."
                    + " Number of entries (" + currentPlaylistEntries.size() + ")"
                    + " exceeding number of expected entries: " + expectedPlaylistEntries.size());
            return false;
        } else if (currentPlaylistEntries.size() < expectedPlaylistEntries.size()) {
            Log.e(LC, "isCurrentPlaylistCorrect: No."
                    + " Got more expected entries than we asked for. This should never happen...");
            return false;
        }
        for (int i = 0; i < expectedPlaylistEntries.size(); i++) {
            PlaybackEntry expected = expectedPlaylistEntries.get(i);
            PlaybackEntry current = currentPlaylistEntries.get(i);
            if (expected.playlistPos != current.playlistPos
                    || !expected.entryID.equals(current.entryID)) {
                Log.d(LC, "isCurrentPlaylistCorrect: No."
                        + " Current playlist entries differ from expected playlist entries.");
                return false;
            }
        }
        Log.d(LC, "isCurrentPlaylistCorrect: Yes.");
        return true;
    }

    private CompletableFuture<Void> syncPlaylistEntries() {
        Log.d(LC, "syncPlaylistEntries");
        // Check if the current entry has the same playlistSelectionID as
        // currentPlaylistSelectionID and compare the current entry's (expected) next pos with
        // currentPlaylistPos to see if currentPlaylistPos needs to advance.
        long prevPlaylistPlaybackPos = -1L;
        boolean repeatMode = getCurrentPlaylistPlaybackRepeatMode();
        PlaybackEntry lastPlaylistPlaybackEntry = getLastPlaylistPlaybackEntry();
        if (lastPlaylistPlaybackEntry != null) {
            long prevPlaylistPos = lastPlaylistPlaybackEntry.playlistPos;
            prevPlaylistPlaybackPos = playlistPlaybackPosFromPlaylistPos(prevPlaylistPos);
            boolean randomPlaybackOrderMode =
                    getCurrentPlaylistPlaybackOrderMode() == PLAYBACK_ORDER_RANDOM;
            long currentRandomSeed = randomPlaybackOrderMode ? nextRandom(lastPlaylistPlaybackEntry) : 0L;
            long expectedPlaylistPlaybackPosition = nextPlaylistPosition(
                    prevPlaylistPlaybackPos,
                    currentPlaylistPlaybackEntries.size(),
                    currentRandomSeed
            );
            long currentPlaylistPlaybackPosition = getCurrentPlaylistPlaybackPosition();
            long expectedPlaylistPosition = currentPlaylistPlaybackEntries.get((int) expectedPlaylistPlaybackPosition).playlistPos;
            if (currentPlaylistPlaybackPosition != expectedPlaylistPlaybackPosition) {
                if (!repeatMode
                        && expectedPlaylistPlaybackPosition < currentPlaylistPlaybackPosition) {
                    Log.d(LC, "syncPlaylistEntries: End of playlist playback reached.");
                    endOfPlaylistPlayback = true;
                }
                Log.d(LC, "syncPlaylistEntries:"
                        + " Setting current playlist playback pos"
                        + " (" + currentPlaylistPlaybackPosition + ")"
                        + " to expected playlist playback pos: "
                        + expectedPlaylistPlaybackPosition);
                setCurrentPlaylistPosition(
                        expectedPlaylistPosition,
                        expectedPlaylistPlaybackPosition
                );
                callback.onPlaylistSelectionChanged(
                        getCurrentPlaylistID(),
                        expectedPlaylistPosition
                );
            }
        }
        if (!repeatMode
                && endOfPlaylistPlayback
                && prevPlaylistPlaybackPos <= 0) {
            Log.d(LC, "syncPlaylistEntries: End reached. Back at start. Repeat false."
                    + " Stop and reset endOfPlaylistPlayback");
            endOfPlaylistPlayback = false;
            audioPlayer.stop();
        }

        // Update playlist entries
        return CompletableFuture.supplyAsync(this::isCurrentPlaylistCorrect)
                .thenCompose(correct -> correct ? Util.futureResult(null) :
                        removePlaylistEntries())
                .thenApply(aVoid -> {
                    // Fill up with playlist entries
                    int numPlaylistEntriesToFetch =
                            MAX_PLAYLIST_ENTRIES_TO_PREFETCH - getAllPlaylistEntries().size();
                    List<PlaybackEntry> allEntries = getAllEntries();
                    Log.d(LC, "syncPlaylistEntries: request " + numPlaylistEntriesToFetch
                            + " playlist entries");
                    List<PlaybackEntry> newPlaylistPlaybackEntries = numPlaylistEntriesToFetch > 0 ?
                            playlistGetNext(
                                    getAllPlaylistEntries().size(),
                                    numPlaylistEntriesToFetch,
                                    false)
                                    .stream()
                                    .filter(p -> !allEntries.contains(p))
                                    .collect(Collectors.toList())
                            :
                            Collections.emptyList();
                    setRandom(newPlaylistPlaybackEntries);
                    return newPlaylistPlaybackEntries;
                })
                .thenCompose(playbackEntries -> {
                    Log.d(LC, "syncPlaylistEntries:"
                            + " got " + playbackEntries.size() + " playlist entries");
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

    long reservePlaybackIDs(int num) {
        return storage.getNextPlaybackIDs(num);
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
            return submitCompletableFuture(() -> queueEntries(entryIDs, beforePlaybackID));
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
        if (pos < 0 || pos >= queueEntries.size()) {
            return PlaybackEntry.PLAYBACK_ID_INVALID;
        }
        return queueEntries.get(pos).playbackID;
    }

    private CompletableFuture<Void> queueEntries(List<EntryID> entries, long beforePlaybackID) {
        Log.d(LC, "queueEntries("
                + "entries.size: " + entries.size()
                + ", beforePlaybackID: " + beforePlaybackID
                + ")");
        List<PlaybackEntry> playbackEntries = new ArrayList<>();
        long playbackID = reservePlaybackIDs(entries.size());
        for (EntryID entryID: entries) {
            playbackEntries.add(new PlaybackEntry(
                    entryID,
                    playbackID++,
                    PlaybackEntry.USER_TYPE_QUEUE,
                    PlaybackEntry.PLAYLIST_POS_NONE,
                    PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID
            ));
        }
        return queuePlaybackEntries(playbackEntries, beforePlaybackID);
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
        callback.onQueueChanged(getAllEntries());
    }

    private void onCurrentPlaylistEntriesChanged(List<PlaylistEntry> newPlaylistEntries) {
        Log.d(LC, "playlistEntriesObserver. Changed for: " + currentPlaylistIDLiveData.getValue());
        if (newPlaylistEntries.isEmpty()) {
            Log.d(LC, "playlistEntriesObserver."
                    + " No playlist entries. Clearing playlist playback entries.");
            currentPlaylistPlaybackEntries.clear();
        } else if (currentPlaylistPlaybackEntries.isEmpty()) {
            Log.d(LC, "playlistEntriesObserver."
                    + " No playlist playback entries. Initializing from playlist entries.");
            List<PlaybackEntry> playlistPlaybackEntries = new ArrayList<>();
            long playbackID = reservePlaybackIDs(newPlaylistEntries.size());
            for (PlaylistEntry playlistEntry: newPlaylistEntries) {
                playlistPlaybackEntries.add(new PlaybackEntry(
                        playlistEntry,
                        PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID,
                        playbackID++
                ));
            }
            currentPlaylistPlaybackEntries.add(0, playlistPlaybackEntries);
        } else {
            // Check if playlistEntries have changed.
            Log.d(LC, "playlistEntriesObserver."
                    + " Calculating diff between current and new playlist entries.");
            List<PlaylistEntry> currentEntries = currentPlaylistEntries;
            HashMap<EntryID, List<Integer>> oldMap = new HashMap<>();
            for (int i = 0; i < currentEntries.size(); i++) {
                EntryID entryID = EntryID.from(currentEntries.get(i));
                List<Integer> indices = oldMap.getOrDefault(entryID, new ArrayList<>());
                indices.add(i);
                oldMap.put(entryID, indices);
            }
            HashMap<EntryID, List<Integer>> newMap = new HashMap<>();
            for (int i = 0; i < newPlaylistEntries.size(); i++) {
                EntryID entryID = EntryID.from(newPlaylistEntries.get(i));
                List<Integer> indices = newMap.getOrDefault(entryID, new ArrayList<>());
                indices.add(i);
                newMap.put(entryID, indices);
            }
            // Find deleted and moved
            List<Integer> deletedPositions = new ArrayList<>();
            List<Integer> addedPositions = new ArrayList<>();
            List<Pair<Integer, Integer>> movedPositions = new ArrayList<>();
            for (EntryID entryID: oldMap.keySet()) {
                List<Integer> oldPositions = oldMap.get(entryID);
                List<Integer> newPositions = newMap.get(entryID);
                // Find deleted
                for (int oldPosIndex = 0; oldPosIndex < oldPositions.size(); oldPosIndex++) {
                    int oldPos = oldPositions.get(oldPosIndex);
                    if (newPositions == null) {
                        // entryID at oldPos has been deleted
                        deletedPositions.add(oldPos);
                        oldPositions.remove(oldPosIndex);
                        oldPosIndex--;
                        continue;
                    }
                    boolean moved = true;
                    for (int newPosIndex = 0; newPosIndex < newPositions.size(); newPosIndex++) {
                        int newPosition = newPositions.get(newPosIndex);
                        if (oldPos == newPosition) {
                            // entryID at oldPos has not moved
                            oldPositions.remove(oldPosIndex);
                            oldPosIndex--;
                            newPositions.remove(newPosIndex);
                            moved = false;
                            break;
                        }
                    }
                    if (moved && newPositions.isEmpty()) {
                        // entryID at oldPos has been deleted
                        deletedPositions.add(oldPos);
                        oldPositions.remove(oldPosIndex);
                        oldPosIndex--;
                    }
                }
                // Find moved
                while (!oldPositions.isEmpty()) {
                    int oldPos = oldPositions.remove(oldPositions.size() - 1);
                    int newPos = newPositions.remove(newPositions.size() - 1);
                    movedPositions.add(new Pair<>(oldPos, newPos));
                }
            }
            // Find added
            for (EntryID entryID: newMap.keySet()) {
                List<Integer> newPositions = newMap.get(entryID);
                if (newPositions == null) {
                    continue;
                }
                addedPositions.addAll(newPositions);
            }
            Collections.sort(deletedPositions);
            Collections.sort(addedPositions);

            List<PlaybackEntry> playbackEntries = currentPlaylistPlaybackEntries.getEntries();

            // On deletion, remove entries with deleted playlistPos.
            List<PlaybackEntry> deletedPlaybackEntries = new ArrayList<>();
            HashSet<Integer> deletedPositionsSet = new HashSet<>(deletedPositions);
            for (PlaybackEntry e: playbackEntries) {
                if (deletedPositionsSet.contains((int) e.playlistPos)) {
                    deletedPlaybackEntries.add(e);
                }
            }

            // On addition, add new entries according to shuffle algorithm.
            List<PlaybackEntry> addedPlaybackEntries = new ArrayList<>();
            long playbackID = reservePlaybackIDs(addedPositions.size());
            for (int addedPos: addedPositions) {
                PlaylistEntry addedPlaylistEntry = newPlaylistEntries.get(addedPos);
                boolean alreadyPresent = false;
                for (PlaybackEntry entry: playbackEntries) {
                    if (entry.playlistPos == addedPlaylistEntry.pos) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    addedPlaybackEntries.add(new PlaybackEntry(
                            addedPlaylistEntry,
                            PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID,
                            playbackID++
                    ));
                }
            }

            // On reorder, update playlistPos.
            HashMap<Integer, Integer> movedFromToMap = new HashMap<>();
            for (Pair<Integer, Integer> movedPos: movedPositions) {
                movedFromToMap.put(movedPos.first, movedPos.second);
            }
            List<PlaybackEntry> movedPlaybackEntries = new ArrayList<>();
            for (PlaybackEntry e: playbackEntries) {
                Integer destPos = movedFromToMap.get((int) e.playlistPos);
                if (destPos != null) {
                    PlaybackEntry movedPlaybackEntry = new PlaybackEntry(
                            e.entryID,
                            e.playbackID,
                            e.playbackType,
                            destPos,
                            e.playlistSelectionID
                    );
                    movedPlaybackEntries.add(movedPlaybackEntry);
                }
            }
            Log.d(LC, "onCurrentPlaylistEntriesChanged diff:"
                    + "\ndeleted: " + deletedPositions
                    + "\nadded: " + addedPositions
                    + "\nmoved: " + movedPositions
                    + "\nPlaylist playback del " + deletedPlaybackEntries.size()
                    + "\nPlaylist playback add " + addedPlaybackEntries.size()
                    + "\nPlaylist playback mov " + movedPlaybackEntries.size()
            );
            currentPlaylistPlaybackEntries.removeEntries(deletedPlaybackEntries);
            // TODO: Shuffle in new entries (if playback order is shuffle)
            currentPlaylistPlaybackEntries.add(
                    currentPlaylistPlaybackEntries.size(),
                    addedPlaybackEntries
            );
            currentPlaylistPlaybackEntries.update(movedPlaybackEntries);
        }
        currentPlaylistEntries = newPlaylistEntries;
        submitCompletableFuture(this::updateState);
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
            return submitCompletableFuture(() -> _moveQueueItems(playbackEntries, beforePlaybackID));
        }
    }

    private CompletableFuture<Void> _moveQueueItems(List<PlaybackEntry> playbackEntries,
                                                    long beforePlaybackID) {
        Log.d(LC, "moveQueueItems(" + playbackEntries
                + ", beforePlaybackID: " + beforePlaybackID + ")");
        return _deQueue(playbackEntries, false)
                .thenCompose(v -> queuePlaybackEntries(playbackEntries, beforePlaybackID));
    }

    CompletableFuture<Void> shuffleQueueItems(List<PlaybackEntry> playbackEntries) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> {
                Log.d(LC, "shuffleQueueItems(" + playbackEntries.size() + ")");
                CompletableFuture<Void> ret = CompletableFuture.completedFuture(null);
                List<PlaybackEntry> queueEntries = getAllQueueEntries();
                HashMap<Long, List<PlaybackEntry>> entriesToMoveMap = getShuffledEntriesToMove(
                        queueEntries,
                        playbackEntries
                );
                for (Map.Entry<Long, List<PlaybackEntry>> entry: entriesToMoveMap.entrySet()) {
                    long beforePlaybackID = entry.getKey();
                    List<PlaybackEntry> entriesToMove = entry.getValue();
                    ret = ret.thenCompose(aVoid -> _moveQueueItems(
                            entriesToMove,
                            beforePlaybackID
                    ));
                }
                return ret;
            });
        }
    }

    public static HashMap<Long, List<PlaybackEntry>> getShuffledEntriesToMove(
            List<PlaybackEntry> allEntries,
            List<PlaybackEntry> entriesToShuffle
    ) {
        HashMap<Long, Integer> entriesBeforePlaybackIDMap = new HashMap<>();
        int streak = 0;
        for (int i = 0; i < allEntries.size(); i++) {
            PlaybackEntry queueEntry = allEntries.get(i);
            if (entriesToShuffle.contains(queueEntry)) {
                streak++;
            } else {
                entriesBeforePlaybackIDMap.put(queueEntry.playbackID, streak);
                streak = 0;
            }
        }
        if (streak > 0) {
            for (int j = 0; j < streak; j++) {
                entriesBeforePlaybackIDMap.put(PlaybackEntry.PLAYBACK_ID_INVALID, streak);
            }
        }
        List<PlaybackEntry> shuffledEntries = new ArrayList<>(entriesToShuffle);
        Collections.shuffle(shuffledEntries);
        HashMap<Long, List<PlaybackEntry>> entriesToMoveMap = new HashMap<>();
        for (Map.Entry<Long, Integer> entry: entriesBeforePlaybackIDMap.entrySet()) {
            long beforePlaybackID = entry.getKey();
            int num = entry.getValue();
            List<PlaybackEntry> entriesToMove = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                if (shuffledEntries.isEmpty()) {
                    break;
                }
                entriesToMove.add(shuffledEntries.remove(shuffledEntries.size() - 1));
            }
            if (!entriesToMove.isEmpty()) {
                entriesToMoveMap.put(beforePlaybackID, entriesToMove);
            }
        }
        return entriesToMoveMap;
    }

    CompletableFuture<Void> seekTo(long pos) {
        synchronized (executorLock) {
            return submitCompletableFuture(() ->
                    audioPlayer.seekTo(pos)
                            .thenRun(() -> callback.onPlayerSeekPositionChanged(pos)));
        }
    }

    CompletableFuture<Void> playNow(List<EntryID> entryIDs) {
        long beforePlaybackID = getQueuePlaybackID(0);
        synchronized (executorLock) {
            return submitCompletableFuture(() -> queueEntries(entryIDs, beforePlaybackID))
                    .thenCompose(r -> getNumTotalQueueEntries() > 0 ?
                            audioPlayer.next() : Util.futureResult(null))
                    .thenCompose(r -> audioPlayer.play());
        }
    }

    private int getNumTotalQueueEntries() {
        return getPlayerQueueEntries().size() + queue.size();
    }

    void updateCurrent() {
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        EntryID entryID = currentEntry == null ? EntryID.UNKOWN : currentEntry.entryID;
        callback.onCurrentEntryChanged(currentEntry);
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

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onCurrentEntryChanged(PlaybackEntry entry);
        void onQueueChanged(List<PlaybackEntry> queue);
        void onPlaylistSelectionChanged(PlaylistID playlistID, long pos);
        void onPlayerSeekPositionChanged(long pos);
        void onPlaybackOrderChanged(int playbackOrder);
        void onRepeatModeChanged(boolean repeat);
    }

    private class AudioPlayerCallback implements AudioPlayer.Callback {
        @Override
        public void onStateChanged(int newPlaybackState) {
            isPlaying = newPlaybackState  == PlaybackStateCompat.STATE_PLAYING;
            callback.onStateChanged(newPlaybackState);
        }

        @Override
        public void onCurrentEntryChanged(PlaybackEntry entry) {
            Log.d(LC, "onCurrentEntryChanged: " + entry);
            callback.onCurrentEntryChanged(entry);
            callback.onMetaChanged(entry == null ? EntryID.UNKOWN : entry.entryID);
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
                Log.d(LC, "Terminated");
            } else {
                Log.e(LC, "Not terminated. Timeout");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("resetController. State:", lastState);
        audioPlayer.destroy();
        executor = Executors.newSingleThreadExecutor();
        return lastState;
    }

    private void onCastConnect(CastSession session, boolean resumed) {
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
                session,
                resumed
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
            onCastConnect((CastSession) session, false);
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
            onCastConnect((CastSession) session, true);
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
