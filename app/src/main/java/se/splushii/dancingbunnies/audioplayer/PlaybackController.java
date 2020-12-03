package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Diff;
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

    private final Context context;
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

    // Current audio player
    private AudioPlayer.Type currentPlayerType;
    // Current playlist reference
    private long currentPlaylistSelectionID;
    private boolean currentPlaylistPlaybackRandom;
    private boolean currentPlaylistPlaybackRepeatMode;
    private final MutableLiveData<EntryID> currentPlaylistIDLiveData = new MutableLiveData<>();
    private EntryID currentPlaylistID;
    // Current playlist entries
    private final LiveData<List<PlaylistEntry>> currentPlaylistEntriesLiveData;
    private final Observer<List<PlaylistEntry>> currentPlaylistEntriesObserver;
    // Current playlist entries in playback order.
    // Generated from current playlist entries and possibly reordered by user.
    private final PlaybackQueue currentPlaylistPlaybackEntries;
    private long currentPlaylistPosition;
    private long currentPlaylistPlaybackPosition;
    private final HashMap<Long, Long> playbackIDToRandomMap = new HashMap<>();

    // History of played items
    private final PlaybackQueue history;

    // Internal state
    private boolean playWhenReady;
    private boolean isPlaying;
    private boolean endOfPlaylistPlayback = false;

    private final AtomicBoolean silenceOnQueueChanged = new AtomicBoolean();

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object executorLock = new Object();

    PlaybackController(Context context,
                       PlaybackControllerStorage playbackControllerStorage,
                       Callback callback) {
        this.context = context;
        this.storage = playbackControllerStorage;
        this.callback = callback;

        currentPlayerType = storage.getCurrentPlayerType();
        playWhenReady = storage.getPlayWhenReady();
        currentPlaylistSelectionID = storage.getCurrentPlaylistSelectionID();
        currentPlaylistID = storage.getCurrentPlaylist();
        currentPlaylistPosition = storage.getCurrentPlaylistPosition();
        currentPlaylistPlaybackPosition = storage.getCurrentPlaylistPlaybackPosition();
        currentPlaylistPlaybackRandom = storage.isCurrentPlaylistPlaybackRandom();
        currentPlaylistPlaybackRepeatMode = storage.getCurrentPlaylistPlaybackRepeatMode();
        Log.d(LC, "Construct:"
                + "\ncurrentPlayerType: " + currentPlayerType.name()
                + "\nplayWhenReady: " + playWhenReady
                + "\ncurrentPlaylistSelectionID: " + currentPlaylistSelectionID
                + "\ncurrentPlaylistID: " + currentPlaylistID
                + "\ncurrentPlaylistPlaybackPosition: " + currentPlaylistPlaybackPosition
                + "\ncurrentPlaylistPlaybackRandom: " + currentPlaylistPlaybackRandom
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
                () -> {
                    Log.d(LC, "playlist playback entries changed");
                    onCurrentPlaylistPlaybackEntriesChanged();
                    submitCompletableFuture(PlaybackController.this::updateState);
                }
        );

        currentPlaylistIDLiveData.setValue(currentPlaylistID);
        currentPlaylistEntriesLiveData = Transformations.switchMap(
                currentPlaylistIDLiveData,
                playlistID -> MusicLibraryService.getPlaylistEntries(context, playlistID)
        );
        currentPlaylistEntriesObserver = this::onCurrentPlaylistEntriesChanged;
        currentPlaylistEntriesLiveData.observeForever(currentPlaylistEntriesObserver);

        CastContext castContext = CastContext.getSharedInstance(context);
        sessionManager = castContext.getSessionManager();
        castContext.addCastStateListener(castState -> {
            switch (castState) {
                case CastState.NO_DEVICES_AVAILABLE:
                    Log.d(LC, "CastState: NO_DEVICES_AVAILABLE");
                    break;
                case CastState.NOT_CONNECTED:
                    Log.d(LC, "CastState: NOT_CONNECTED");
                    break;
                case CastState.CONNECTING:
                    Log.d(LC, "CastState: CONNECTING");
                    break;
                case CastState.CONNECTED:
                    Log.d(LC, "CastState: CONNECTED");
                    break;
                default:
                    Log.e(LC, "CastState: UNKNOWN");
                    break;
            }
        });
        sessionManager.addSessionManagerListener(sessionManagerListener);
        switch (currentPlayerType) {
            default:
            case LOCAL:
                audioPlayer = new LocalAudioPlayer(
                        audioPlayerCallback,
                        context,
                        storage,
                        true,
                        playWhenReady
                );
                setCurrentPlayerType(AudioPlayer.Type.LOCAL);
                break;
            case CAST:
                audioPlayer = new CastAudioPlayer(
                        audioPlayerCallback,
                        context,
                        sessionManager.getCurrentCastSession(),
                        true,
                        playWhenReady
                );
                setCurrentPlayerType(AudioPlayer.Type.CAST);
                break;
        }
    }

    void initialize() {
        isPlaying = false;
        callback.onPlayerChanged(getCurrentPlayerType());
        callback.onPlaybackOrderChanged(isCurrentPlaylistPlaybackOrdered());
        callback.onPlaybackRandomChanged(isCurrentPlaylistPlaybackRandom());
        callback.onRepeatModeChanged(getCurrentPlaylistPlaybackRepeatMode());
        callback.onPlaylistSelectionChanged(
                getCurrentPlaylistID(),
                getCurrentPlaylistPosition()
        );
        onQueueChanged();
        audioPlayer.initialize();
        onCurrentPlaylistPlaybackEntriesChanged();
        submitCompletableFuture(this::updateState);
    }

    void onDestroy(boolean stopPlayer) {
        Log.d(LC, "onDestroy");
        currentPlaylistEntriesLiveData.removeObserver(currentPlaylistEntriesObserver);
        sessionManager.removeSessionManagerListener(sessionManagerListener);
        setPlayWhenReady(false);
        if (stopPlayer) {
            audioPlayer.stop();
        }
        audioPlayer.destroy(false);
        audioPlayer = null;
        queue.onDestroy();
        playlistItems.onDestroy();
        history.onDestroy();
    }

    private void setPlayWhenReady(boolean playWhenReady) {
        storage.setPlayWhenReady(playWhenReady);
        this.playWhenReady = playWhenReady;
    }

    long getPlayerSeekPosition() {
        return audioPlayer.getSeekPosition();
    }

    CompletableFuture<Void> setPlaylist(EntryID playlistID, long pos) {
        _setPlaylist(playlistID, pos);
        return submitCompletableFuture(this::updateState);
    }

    private void _setPlaylist(EntryID playlistID, long pos) {
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
                .thenCompose(aVoid -> audioPlayer.remove(getPlayerPlaylistEntries()));
    }

    private void setCurrentPlayerType(AudioPlayer.Type type) {
        currentPlayerType = type;
        storage.setCurrentPlayerType(type);
    }

    private AudioPlayer.Type getCurrentPlayerType() {
        return currentPlayerType;
    }

    private void setCurrentPlaylistSelectionID(long id) {
        Log.d(LC, "setCurrentPlaylistSelectionID: " + id);
        currentPlaylistSelectionID = id;
        storage.setCurrentPlaylistSelectionID(id);
    }

    private long getCurrentPlaylistSelectionID() {
        return currentPlaylistSelectionID;
    }

    private void setCurrentPlaylistPlaybackRandom(boolean random) {
        currentPlaylistPlaybackRandom = random;
        storage.setCurrentPlaylistPlaybackRandom(random);
        callback.onPlaybackRandomChanged(random);
    }

    private boolean isCurrentPlaylistPlaybackRandom() {
        return currentPlaylistPlaybackRandom;
    }

    private void setCurrentPlaylistPlaybackRepeatMode(boolean repeat) {
        currentPlaylistPlaybackRepeatMode = repeat;
        storage.setCurrentPlaylistPlaybackRepeatMode(repeat);
        callback.onRepeatModeChanged(repeat);
    }

    private boolean getCurrentPlaylistPlaybackRepeatMode() {
        return currentPlaylistPlaybackRepeatMode;
    }

    private void setCurrentPlaylistID(EntryID playlistID) {
        currentPlaylistID = playlistID;
        storage.setCurrentPlaylist(playlistID);
    }

    private EntryID getCurrentPlaylistID() {
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
                        setCurrentPlaylistSelectionID(storage.getNextPlaylistSelectionID());
                        PlaybackEntry entry = currentPlaylistPlaybackEntries.get(0);
                        long playlistPos = entry == null ? 0: entry.playlistPos;
                        setCurrentPlaylistPosition(playlistPos, 0);
                        callback.onPlaylistSelectionChanged(getCurrentPlaylistID(), playlistPos);
                    })
                    .thenRun(this::updateState);
        });
    }

    CompletableFuture<Void> toggleRandomPlayback() {
        Log.d(LC, "toggleRandomPlayback");
        return submitCompletableFuture(() -> {
            setCurrentPlaylistPlaybackRandom(!isCurrentPlaylistPlaybackRandom());
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
        setPlayWhenReady(true);
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.play());
        }
    }

    CompletableFuture<Void> pause() {
        setPlayWhenReady(false);
        synchronized (executorLock) {
            return submitCompletableFuture(() -> audioPlayer.pause());
        }
    }

    CompletableFuture<Void> playPause() {
        return isPlaying ? pause() : play();
    }

    CompletableFuture<Void> stop() {
        setPlayWhenReady(false);
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
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        if (currentEntry != null
                && PlaybackEntry.USER_TYPE_PLAYLIST.equals(currentEntry.playbackType)
                && currentEntry.playlistSelectionID == getCurrentPlaylistSelectionID()) {
            return currentEntry;
        }
        for (PlaybackEntry entry: history.getEntries()) {
            if (entry != null
                    && PlaybackEntry.USER_TYPE_PLAYLIST.equals(entry.playbackType)
                    && entry.playlistSelectionID == getCurrentPlaylistSelectionID()) {
                return entry;
            }
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
        boolean random = isCurrentPlaylistPlaybackRandom();
        long nextRandom = 0L;
        if (random) {
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
        nextRandom = random ? getNextRandomSeed(nextRandom) : 0L;
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
            nextRandom = random ? getNextRandomSeed(nextRandom) : 0L;
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
        if (audioPlayer == null) {
            return Util.futureResult("updateState: audioPlayer is null");
        }
        return CompletableFuture.completedFuture(null)
                .thenCompose(aVoid -> cleanDuplicateEntries())
                .thenCompose(aVoid -> syncPlaylistEntries())
                .thenCompose(aVoid -> updateHistory())
                .thenCompose(aVoid -> updatePreload());
    }

    private CompletionStage<Void> cleanDuplicateEntries() {
        // Start with entries in history
        HashSet<Long> playbackIDs = history.getEntries().stream()
                .map(p -> p.playbackID)
                .collect(Collectors.toCollection(HashSet::new));
        // Check current entry
        PlaybackEntry current = audioPlayer.getCurrentEntry();
        if (current != null) {
            if (playbackIDs.contains(current.playbackID)) {
                // De-preload and schedule another updateState
                submitCompletableFuture(this::updateState);
                return audioPlayer.remove(Collections.singletonList(current))
                        .thenCompose(aVoid -> Util.futureResult(
                                "cleanDuplicateEntries: Current entry is a duplicate"
                        ));
            }
            playbackIDs.add(current.playbackID);
        }
        // Check AudioPlayer preload
        for (PlaybackEntry entry: audioPlayer.getPreloadEntries()) {
            if (playbackIDs.contains(entry.playbackID)) {
                // De-preload and schedule another updateState
                submitCompletableFuture(this::updateState);
                return audioPlayer.remove(Collections.singletonList(entry))
                        .thenCompose(aVoid -> Util.futureResult(
                                "cleanDuplicateEntries: AudioPlayer preload contains a duplicate"
                        ));
            }
            playbackIDs.add(entry.playbackID);
        }
        // Check PlaybackController queue
        for (PlaybackEntry entry: queue.getEntries()) {
            if (playbackIDs.contains(entry.playbackID)) {
                // De-preload and schedule another updateState
                submitCompletableFuture(this::updateState);
                queue.remove(Collections.singletonList(entry))
                        .thenCompose(aVoid -> Util.futureResult(
                                "cleanDuplicateEntries: PlaybackController queue contains a duplicate"
                        ));
            }
            playbackIDs.add(entry.playbackID);
        }
        // Check PlaybackController playlist entries
        for (PlaybackEntry entry: playlistItems.getEntries()) {
            if (playbackIDs.contains(entry.playbackID)) {
                // De-preload and schedule another updateState
                submitCompletableFuture(this::updateState);
                playlistItems.remove(Collections.singletonList(entry))
                        .thenCompose(aVoid -> Util.futureResult(
                                "cleanDuplicateEntries: PlaybackController playlist entries contains a duplicate"
                        ));
            }
            playbackIDs.add(entry.playbackID);
        }
        return Util.futureResult();
    }

    private CompletionStage<Void> updateHistory() {
        List<PlaybackEntry> historyEntries = audioPlayer.getHistory();
        historyEntries.forEach(p -> p.setPreloaded(false));
        Log.d(LC, "updateHistory getting " + historyEntries.size());
        return audioPlayer.remove(historyEntries)
                .thenCompose(aVoid -> history.add(0, historyEntries));
    }

    private CompletionStage<Void> updatePreload() {
        // Check if the audioPlayer needs to be preloaded (how many entries)
        int numToPreload = numPreloadNeeded();
        Log.d(LC, "updatePreload: Preload needed: " + numToPreload);
        if (numToPreload <= 0) {
            return Util.futureResult();
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
                    return Util.futureResult(entries);
                })
                .thenCompose(entries -> entries.isEmpty() ? Util.futureResult() :
                        audioPlayer.preload(entries, audioPlayer.getNumPreloaded()));
    }

    private boolean isCurrentPlaylistCorrect() {
        List<PlaybackEntry> allEntries = getAllEntries();
        List<PlaybackEntry> currentPlaybackPlaylistEntries = getAllPlaylistEntries();
        // Check if the current playlist entries have
        // currentPlaylistSelectionID as playlistSelectionID
        long currentSelectionID = getCurrentPlaylistSelectionID();
        for (PlaybackEntry entry: currentPlaybackPlaylistEntries) {
            if (entry.playlistSelectionID != currentSelectionID) {
                Log.d(LC, "isCurrentPlaylistCorrect: No."
                        +" There are playlist entries with the wrong playlist selection ID.");
                return false;
            }
        }
        // Check if there are too many playlist entries
        if (currentPlaybackPlaylistEntries.size() > MAX_PLAYLIST_ENTRIES_TO_PREFETCH) {
            Log.d(LC, "isCurrentPlaylistCorrect: No."
                    + " Too many playlist entries (" + currentPlaybackPlaylistEntries.size()  + ")."
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
                currentPlaybackPlaylistEntries.size(),
                true
        );
        if (currentPlaybackPlaylistEntries.size() > expectedPlaylistEntries.size()) {
            Log.d(LC, "isCurrentPlaylistCorrect: No."
                    + " Number of entries (" + currentPlaybackPlaylistEntries.size() + ")"
                    + " exceeding number of expected entries: " + expectedPlaylistEntries.size());
            return false;
        } else if (currentPlaybackPlaylistEntries.size() < expectedPlaylistEntries.size()) {
            Log.e(LC, "isCurrentPlaylistCorrect: No."
                    + " Got more expected entries than we asked for. This should never happen...");
            return false;
        }
        for (int i = 0; i < expectedPlaylistEntries.size(); i++) {
            PlaybackEntry expected = expectedPlaylistEntries.get(i);
            PlaybackEntry current = currentPlaybackPlaylistEntries.get(i);
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
            boolean random = isCurrentPlaylistPlaybackRandom();
            long currentRandomSeed = random ? nextRandom(lastPlaylistPlaybackEntry) : 0L;
            long expectedPlaylistPlaybackPosition = nextPlaylistPosition(
                    prevPlaylistPlaybackPos,
                    currentPlaylistPlaybackEntries.size(),
                    currentRandomSeed
            );
            long currentPlaylistPosition = getCurrentPlaylistPosition();
            long currentPlaylistPlaybackPosition = getCurrentPlaylistPlaybackPosition();
            if (currentPlaylistPlaybackPosition != expectedPlaylistPlaybackPosition) {
                if (!repeatMode
                        && expectedPlaylistPlaybackPosition < currentPlaylistPlaybackPosition) {
                    Log.d(LC, "syncPlaylistEntries: End of playlist playback reached.");
                    endOfPlaylistPlayback = true;
                }
                PlaybackEntry expectedPlaylistPlaybackEntry =
                        currentPlaylistPlaybackEntries.get((int) expectedPlaylistPlaybackPosition);
                if (expectedPlaylistPlaybackEntry == null) {
                    submitCompletableFuture(this::updateState);
                    return Util.futureResult(
                            "syncPlaylistEntries: Could not get expected playlist playbackentry."
                    );
                }
                long expectedPlaylistPosition = expectedPlaylistPlaybackEntry.playlistPos;
                Log.d(LC, "syncPlaylistEntries:"
                        + " Setting current playlist pos"
                        + " " + currentPlaylistPosition
                        + " (playback: " + currentPlaylistPlaybackPosition + ")"
                        + " to expected playlist playback pos"
                        + " " + expectedPlaylistPosition
                        + " (playback: " + expectedPlaylistPlaybackPosition + ")"
                );
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
            setPlayWhenReady(false);
            audioPlayer.stop();
        }

        // Update playlist entries
        return CompletableFuture.supplyAsync(this::isCurrentPlaylistCorrect)
                .thenCompose(correct -> correct ? Util.futureResult() :
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
                    playbackEntries.forEach(p -> p.setPreloaded(false));
                    return playlistItems.add(playlistItems.size(), playbackEntries);
                });
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
            return Util.futureResult();
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
                        return audioPlayer.remove(
                                entriesToDePreload
                        ).thenCompose(aVoid -> {
                            if (!playlistEntriesToDePreload.isEmpty()) {
                                playlistEntriesToDePreload.forEach(p -> p.setPreloaded(false));
                                return playlistItems.add(0, playlistEntriesToDePreload);
                            }
                            return Util.futureResult();
                        }).thenCompose(aVoid -> {
                            if (!queueEntriesToDePreload.isEmpty()) {
                                queueEntriesToDePreload.forEach(p -> p.setPreloaded(false));
                                return queue.add(0, queueEntriesToDePreload);
                            }
                            return Util.futureResult();
                        });
                    } else {
                        return Util.futureResult();
                    }
                })
                .thenCompose(v -> newEntriesToPreload.isEmpty() ?
                        Util.futureResult()
                        :
                        audioPlayer.preload(newEntriesToPreload, newEntriesToPreloadOffset)
                )
                .thenCompose(aVoid -> {
                    if (newEntriesToController.size() > 0) {
                        newEntriesToController.forEach(p -> p.setPreloaded(false));
                        return queue.add(newEntriesToControllerOffset, newEntriesToController);
                    }
                    return Util.futureResult();
                })
                .thenCompose(v -> updateState());
    }

    private void onQueueChanged() {
        if (!silenceOnQueueChanged.get()) {
            callback.onQueueChanged(getAllEntries());
        }
    }

    private void onCurrentPlaylistEntriesChanged(List<PlaylistEntry> newPlaylistEntries) {
        Log.d(LC, "playlistEntriesObserver. Changed for: " + currentPlaylistIDLiveData.getValue());
        synchronized (executorLock) {
            submitCompletableFuture(() -> {
                long startTime = System.currentTimeMillis();
                Log.d(LC, "playlistEntriesObserver. Start...");
                if (newPlaylistEntries.isEmpty()) {
                    Log.d(LC, "playlistEntriesObserver."
                            + " No playlist entries. Clearing playlist playback entries.");
                    currentPlaylistPlaybackEntries.clear();
                } else if (currentPlaylistPlaybackEntries.isEmpty()) {
                    Log.d(LC, "playlistEntriesObserver."
                            + " No playlist playback entries. Initializing from playlist entries.");
                    List<PlaybackEntry> playlistPlaybackEntries = new ArrayList<>();
                    long playbackID = reservePlaybackIDs(newPlaylistEntries.size());
                    for (int i = 0; i < newPlaylistEntries.size(); i++) {
                        PlaylistEntry playlistEntry = newPlaylistEntries.get(i);
                        playlistPlaybackEntries.add(new PlaybackEntry(
                                playlistEntry,
                                i,
                                PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID,
                                playbackID++
                        ));
                    }
                    playlistPlaybackEntries.forEach(p -> p.setPreloaded(false));
                    currentPlaylistPlaybackEntries.add(0, playlistPlaybackEntries);
                } else {
                    // Check if playlistEntries have changed.
                    Log.d(LC, "playlistEntriesObserver."
                            + " Calculating diff between current and new playlist entries.");
                    // Compare the new playlist entries to what the list of playlist playback
                    // entries think the playlist entries look like
                    Diff diff = Diff.diff(
                            newPlaylistEntries.stream()
                                    .map(PlaylistEntry::entryID)
                                    .collect(Collectors.toList()),
                            currentPlaylistPlaybackEntries.getEntries()
                                    .stream()
                                    .sorted((p1, p2) -> Long.compare(p1.playlistPos, p2.playlistPos))
                                    .map(p -> p.entryID)
                                    .collect(Collectors.toList()),
                            false,
                            true
                    );

                    List<PlaybackEntry> playbackEntries = currentPlaylistPlaybackEntries.getEntries();

                    // On deletion, remove entries with deleted playlistPos.
                    List<PlaybackEntry> deletedPlaybackEntries = new ArrayList<>();
                    HashSet<Integer> deletedPositionsSet = new HashSet<>(diff.deleted);
                    for (PlaybackEntry e: playbackEntries) {
                        if (deletedPositionsSet.contains((int) e.playlistPos)) {
                            deletedPlaybackEntries.add(e);
                        }
                    }

                    // On addition, add new entries according to shuffle algorithm.
                    List<PlaybackEntry> addedPlaybackEntries = new ArrayList<>();
                    long playbackID = reservePlaybackIDs(diff.added.size());
                    for (int addedPos: diff.added) {
                        PlaylistEntry addedPlaylistEntry = newPlaylistEntries.get(addedPos);
                        boolean alreadyPresent = false;
                        for (PlaybackEntry entry: playbackEntries) {
                            if (entry.playlistPos == addedPos) {
                                alreadyPresent = true;
                                break;
                            }
                        }
                        if (!alreadyPresent) {
                            addedPlaybackEntries.add(new PlaybackEntry(
                                    addedPlaylistEntry,
                                    addedPos,
                                    PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID,
                                    playbackID++
                            ));
                        }
                    }

                    // On reorder, update playlistPos.
                    HashMap<Integer, Integer> movedFromToMap = new HashMap<>();
                    for (Pair<Integer, Integer> movedPos: diff.moved) {
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
                            + "\ndeleted: " + diff.deleted.size()
                            + "\nadded: " + diff.added.size()
                            + "\nmoved: " + diff.moved.size()
                            + "\nPlaylist playback del " + deletedPlaybackEntries.size()
                            + "\nPlaylist playback add " + addedPlaybackEntries.size()
                            + "\nPlaylist playback mov " + movedPlaybackEntries.size()
                    );
                    if (!deletedPlaybackEntries.isEmpty()) {
                        currentPlaylistPlaybackEntries.remove(deletedPlaybackEntries);
                    }
                    // TODO: Shuffle in new entries (if playback order is shuffle)
                    addedPlaybackEntries.forEach(p -> p.setPreloaded(false));
                    if (!addedPlaybackEntries.isEmpty()) {
                        currentPlaylistPlaybackEntries.add(
                                currentPlaylistPlaybackEntries.size(),
                                addedPlaybackEntries
                        );
                    }
                    if (!movedPlaybackEntries.isEmpty()) {
                        currentPlaylistPlaybackEntries.updatePositions(movedPlaybackEntries);
                    }
                    if (currentPlaylistPlaybackEntries.size() != newPlaylistEntries.size()) {
                        Log.e(LC, "Number of playlist playback entries ("
                                + currentPlaylistPlaybackEntries.size()
                                + ") differ"
                                + " from number of playlist entries ("
                                + newPlaylistEntries.size()
                                + "). This should never happen..."
                        );
                    }
                }
                long time = System.currentTimeMillis() - startTime;
                Log.d(LC, "playlistEntriesObserver. Finish! Time: " + time + "ms");
                return Util.futureResult();
            }).thenCompose(aVoid -> updateState());
        }
    }

    private void onCurrentPlaylistPlaybackEntriesChanged() {
        synchronized (executorLock) {
            submitCompletableFuture(() -> CompletableFuture
                    .supplyAsync(this::isCurrentPlaylistPlaybackOrdered)
                    .thenApplyAsync(
                            ordered -> {
                                callback.onPlaybackOrderChanged(ordered);
                                return null;
                            },
                            Util.getMainThreadExecutor()
                    )
            );
        }
    }

    private boolean isCurrentPlaylistPlaybackOrdered() {
        List<PlaylistEntry> currentPlaylistEntries = currentPlaylistEntriesLiveData.getValue();
        boolean diff;
        if (currentPlaylistEntries == null) {
            diff = false;
        } else {
            diff = Diff.fastDiff(
                    currentPlaylistEntries.stream()
                            .map(PlaylistEntry::entryID)
                            .collect(Collectors.toList()),
                    currentPlaylistPlaybackEntries.getEntries()
                            .stream()
                            .map(p -> p.entryID)
                            .collect(Collectors.toList())
            );
        }
        return !diff;
    }

    CompletableFuture<Void> deQueue(List<PlaybackEntry> playbackEntries) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> _deQueue(playbackEntries, true));
        }
    }

    private CompletableFuture<Void> _deQueue(List<PlaybackEntry> playbackEntries, boolean thenUpdateState) {
        Log.d(LC, "deQueue");
        CompletableFuture<Void> result = queue.remove(playbackEntries)
                .thenCompose(v -> audioPlayer.remove(playbackEntries));
        return thenUpdateState ? result.thenCompose(aVoid -> updateState()) : result;
    }

    CompletableFuture<Void> clearQueue() {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> queue.clear()
                    .thenCompose(v -> audioPlayer.remove(getPlayerEntries()))
            );
        }
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
        silenceOnQueueChanged.set(true);
        return _deQueue(playbackEntries, false)
                .thenCompose(v -> queuePlaybackEntries(playbackEntries, beforePlaybackID))
                .thenRun(() -> {
                    silenceOnQueueChanged.set(false);
                    onQueueChanged();
                })
                .exceptionally(throwable -> {
                    silenceOnQueueChanged.set(false);
                    onQueueChanged();
                    throw new CompletionException(throwable);
                });
    }

    CompletableFuture<Void> shuffleQueueItems(List<PlaybackEntry> playbackEntries) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> {
                Log.d(LC, "shuffleQueueItems(" + playbackEntries.size() + ")");
                List<PlaybackEntry> queueEntries = getAllQueueEntries();
                List<PlaybackEntry> shuffledEntries = new ArrayList<>(playbackEntries);
                Collections.shuffle(shuffledEntries);
                return reorderQueueItemsAccordingToList(queueEntries, shuffledEntries);
            });
        }
    }

    CompletableFuture<Void> sortQueueItems(List<PlaybackEntry> playbackEntries,
                                           List<String> sortBy) {
        synchronized (executorLock) {
            return submitCompletableFuture(() -> {
                Log.d(LC, "shuffleQueueItems(" + playbackEntries.size() + ")");
                List<PlaybackEntry> queueEntries = getAllQueueEntries();
                return sorted(playbackEntries, sortBy)
                        .thenCompose(sortedEntries -> reorderQueueItemsAccordingToList(
                                queueEntries,
                                sortedEntries
                        ));
            });
        }
    }

    private CompletableFuture<List<PlaybackEntry>> sorted(List<PlaybackEntry> playbackEntries,
                                                          List<String> sortBy) {
        return sorted(context, playbackEntries, sortBy);
    }

    public static CompletableFuture<List<PlaybackEntry>> sorted(Context context,
                                                                List<PlaybackEntry> playbackEntries,
                                                                List<String> sortBy) {
        return MetaStorage.getInstance(context).getTrackMetasOnce(
                playbackEntries.stream()
                        .map(p -> p.entryID)
                        .collect(Collectors.toList())
        ).thenApply(metas -> {
            if (metas.size() != playbackEntries.size()) {
                return playbackEntries;
            }
            List<Pair<PlaybackEntry, Meta>> sortedPairs = new ArrayList<>();
            for (int i = 0; i < playbackEntries.size(); i++) {
                sortedPairs.add(new Pair<>(playbackEntries.get(i), metas.get(i)));
            }
            Comparator<Pair<PlaybackEntry, Meta>> comparator = null;
            for (String sortByField: sortBy) {
                if (comparator == null) {
                    comparator = Comparator.comparing(meta ->
                            meta.second.getAsComparable(sortByField)
                    );
                    continue;
                }
                comparator = comparator.thenComparing(meta ->
                        meta.second.getAsComparable(sortByField)
                );
            }
            if (comparator != null) {
                Collections.sort(sortedPairs, comparator);
            }
            return sortedPairs.stream()
                    .map(pair -> pair.first)
                    .collect(Collectors.toList());
        });
    }

    private CompletableFuture<Void> reorderQueueItemsAccordingToList(List<PlaybackEntry> allEntries,
                                                                     List<PlaybackEntry> list) {
        List<Pair<Long, List<PlaybackEntry>>> entryChunksToMove = getEntryChunksToMove(
                allEntries,
                list
        );
        return moveEntryChunks(entryChunksToMove);
    }

    public static List<Pair<Long, List<PlaybackEntry>>> getEntryChunksToMove(
            List<PlaybackEntry> allEntries,
            List<PlaybackEntry> list
    ) {
        // Calculate entry chunk sizes to move to the position before specific playbackID:s
        List<Pair<Long, Integer>> entryChunkSizesBeforePlaybackIDs = new ArrayList<>();
        int streak = 0;
        for (int i = 0; i < allEntries.size(); i++) {
            PlaybackEntry queueEntry = allEntries.get(i);
            if (list.contains(queueEntry)) {
                streak++;
            } else {
                entryChunkSizesBeforePlaybackIDs.add(new Pair<>(
                        queueEntry.playbackID,
                        streak
                ));
                streak = 0;
            }
        }
        if (streak > 0) {
            for (int j = 0; j < streak; j++) {
                entryChunkSizesBeforePlaybackIDs.add(new Pair<>(
                        PlaybackEntry.PLAYBACK_ID_INVALID,
                        streak
                ));
            }
        }
        // Chunk entries to move to the position before specific playbackID:s
        List<Pair<Long, List<PlaybackEntry>>> entryChunksToMove = new ArrayList<>();
        int count = 0;
        for (Pair<Long, Integer> numBeforePlaybackID: entryChunkSizesBeforePlaybackIDs) {
            long beforePlaybackID = numBeforePlaybackID.first;
            int num = numBeforePlaybackID.second;
            List<PlaybackEntry> entriesToMove = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                if (count >= list.size()) {
                    break;
                }
                entriesToMove.add(list.get(count++));
            }
            if (!entriesToMove.isEmpty()) {
                entryChunksToMove.add(new Pair<>(beforePlaybackID, entriesToMove));
            }
        }
        return entryChunksToMove;
    }

    private CompletableFuture<Void> moveEntryChunks(
            List<Pair<Long, List<PlaybackEntry>>> entryChunksToMove
    ) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Pair<Long, List<PlaybackEntry>> entry: entryChunksToMove) {
            long beforePlaybackID = entry.first;
            List<PlaybackEntry> entriesToMove = entry.second;
            future = future.thenCompose(aVoid -> _moveQueueItems(
                    entriesToMove,
                    beforePlaybackID
            ));
        }
        return future;
    }

    CompletableFuture<Void> seekTo(long pos) {
        synchronized (executorLock) {
            return submitCompletableFuture(() ->
                    audioPlayer.seekTo(pos)
                            .thenRun(() -> callback.onPlayerSeekPositionChanged(pos)));
        }
    }

    CompletableFuture<Void> playNow(List<EntryID> entryIDs) {
        setPlayWhenReady(true);
        long beforePlaybackID = getQueuePlaybackID(0);
        synchronized (executorLock) {
            return submitCompletableFuture(() -> queueEntries(entryIDs, beforePlaybackID))
                    .thenCompose(r -> getNumTotalQueueEntries() > 0 ?
                            audioPlayer.next() : Util.futureResult())
                    .thenCompose(r -> audioPlayer.play());
        }
    }

    private int getNumTotalQueueEntries() {
        return getPlayerQueueEntries().size() + queue.size();
    }

    private void updateCurrent() {
        PlaybackEntry currentEntry = audioPlayer.getCurrentEntry();
        EntryID entryID = currentEntry == null ? EntryID.UNKOWN : currentEntry.entryID;
        callback.onCurrentEntryChanged(currentEntry);
        callback.onMetaChanged(entryID);
    }

    private List<PlaybackEntry> getUniqueEntries(List<PlaybackEntry> entries) {
        List<PlaybackEntry> uniqueEntries = new ArrayList<>();
        HashSet<Long> playbackIDs = new HashSet<>();
        for (PlaybackEntry entry: entries) {
            if (playbackIDs.contains(entry.playbackID)) {
                Log.w(LC, "Duplicate playbackID: " + entry.playbackID
                        + "(" + entry.toString() + ")");
                continue;
            }
            uniqueEntries.add(entry);
            playbackIDs.add(entry.playbackID);
        }
        return uniqueEntries;
    }

    private List<PlaybackEntry> getPlayerEntries() {
        return getUniqueEntries(audioPlayer.getPreloadEntries());
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
        return getUniqueEntries(playbackEntries);
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
        return getUniqueEntries(playbackEntries);
    }

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onCurrentEntryChanged(PlaybackEntry entry);
        void onQueueChanged(List<PlaybackEntry> queue);
        void onPlaylistSelectionChanged(EntryID playlistID, long pos);
        void onPlayerSeekPositionChanged(long pos);
        void onPlaybackOrderChanged(boolean ordered);
        void onPlaybackRandomChanged(boolean random);
        void onRepeatModeChanged(boolean repeat);
    }

    private class AudioPlayerCallback implements AudioPlayer.Callback {
        @Override
        public void onStateChanged(int newPlaybackState) {
            isPlaying = AudioPlayerService.isPlayingState(newPlaybackState);
            if (AudioPlayerService.isStoppedState(newPlaybackState)) {
                Log.d(LC, "AudioPlayer playback stopped. Setting playWhenReady to false");
                setPlayWhenReady(false);
            }
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

        @Override
        public void begToBeDisconnected() {
            Log.d(LC, "disconnect");
            onCastDisconnect();
        }
    }

    private CompletableFuture<Void> submitCompletableFuture(Supplier<CompletableFuture<Void>> futureSupplier) {
        CompletableFuture<Void> ret = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                futureSupplier.get().handle(Util::printFutureError).get();
                ret.complete(null);
            } catch (ExecutionException | InterruptedException e) {
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
        queueEntries.forEach(p -> p.setPreloaded(false));
        playlistEntries.forEach(p -> p.setPreloaded(false));
        state.history.forEach(p -> p.setPreloaded(false));
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
        audioPlayer.destroy(true);
        executor = Executors.newSingleThreadExecutor();
        return lastState;
    }

    private void onCastConnect(CastSession session, boolean resumed) {
        if (audioPlayer instanceof CastAudioPlayer) {
            // TODO: What if sessions differ? Transfer state?
            Log.w(LC, "onCastConnect: Replacing session for CastAudioPlayer");
            ((CastAudioPlayer)audioPlayer).setCastSession(session);
            audioPlayer.initialize();
            return;
        }
        AudioPlayer.AudioPlayerState lastPlayerState = resetController();
        audioPlayer = new CastAudioPlayer(
                audioPlayerCallback,
                context,
                session,
                resumed,
                playWhenReady
        );
        submitCompletableFuture(() -> transferAudioPlayerState(lastPlayerState));
        setCurrentPlayerType(AudioPlayer.Type.CAST);
        callback.onPlayerChanged(AudioPlayer.Type.CAST);
        updateCurrent();
    }

    private void onCastDisconnect() {
        if (!(audioPlayer instanceof CastAudioPlayer)) {
            return;
        }
        AudioPlayer.AudioPlayerState lastPlayerState = resetController();
        setPlayWhenReady(false);
        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                context,
                storage,
                false,
                playWhenReady
        );
        submitCompletableFuture(() -> transferAudioPlayerState(lastPlayerState));
        setCurrentPlayerType(AudioPlayer.Type.LOCAL);
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
