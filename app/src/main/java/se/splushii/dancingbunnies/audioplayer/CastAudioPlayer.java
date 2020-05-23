package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;

import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaError;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class CastAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);
    static final String CASTMETA_KEY_PLAYBACK_ID = "dancingbunnies.castmeta.PLAYBACK_ID";
    static final String CASTMETA_KEY_PLAYBACK_TYPE = "dancingbunnies.castmeta.PLAYBACK_TYPE";
    static final String CASTMETA_KEY_PLAYLIST_POS = "dancingbunnies.castmeta.PLAYLIST_POS";
    static final String CASTMETA_KEY_PLAYLIST_SELECTION_ID = "dancingbunnies.castmeta.PLAYLIST_SELECTION_ID";

    private final Context context;
    private Callback callback;
    private final SparseArray<MediaQueueItem> queueItemMap;
    private final MediaQueueCallback mediaQueueCallback;
    private final RemoteMediaClientCallback remoteMediaClientCallback;
    private CastSession castSession;
    private RemoteMediaClient remoteMediaClient;
    private MediaQueue mediaQueue;
    private int playerState;
    private int idleReason;
    private boolean playWhenReady;
    private long lastPos = 0L;
    private int[] lastItemIds = new int[0];
    private int lastCurrentItemId = MediaQueueItem.INVALID_ITEM_ID;
    private SparseArray<MediaQueueItem> lastQueueItemMap;
    private LongSparseArray<Integer> playbackIDToCastItemIDMap;
    private PlaybackEntry playbackFinishedEntry;

    private Semaphore queueChangeLock = new Semaphore(1);
    private StampedLock queueStateLock = new StampedLock();

    private volatile boolean waitingForResume;

    private enum PlayerAction {
        PLAY,
        PAUSE,
        STOP
    }

    CastAudioPlayer(Callback callback,
                    Context context,
                    CastSession castSession,
                    boolean waitForResume,
                    boolean playWhenReady) {
        this.callback = callback;
        this.context = context;
        this.waitingForResume = waitForResume;
        this.playWhenReady = playWhenReady;
        queueChangeLock.drainPermits();
        queueItemMap = new SparseArray<>();
        playbackIDToCastItemIDMap = new LongSparseArray<>();
        mediaQueueCallback = new MediaQueueCallback();
        remoteMediaClientCallback = new RemoteMediaClientCallback();
        if (castSession != null) {
            setCastSession(castSession);
        }
    }

    void setCastSession(CastSession castSession) {
        this.castSession = castSession;
        getRemote();
    }

    private RemoteMediaClient getRemote() {
        if (castSession == null) {
            return null;
        }
        if (remoteMediaClient == null) {
            Log.d(LC, "getRemote: Setting up remoteMediaClient");
            remoteMediaClient = castSession.getRemoteMediaClient();
            if (remoteMediaClient == null) {
                Log.w(LC, "getRemote: Could not get remoteMediaClient from castSession");
                return null;
            }
            remoteMediaClient.registerCallback(remoteMediaClientCallback);
            mediaQueue = remoteMediaClient.getMediaQueue();
            mediaQueue.registerCallback(mediaQueueCallback);
        }
        return remoteMediaClient;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        Log.d(LC, "initialize. resuming: " + waitingForResume);
        return CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    if (!queueChangeLock.tryAcquire(1, TimeUnit.SECONDS)) {
                        Log.w(LC, "initialize: Got timeout waiting for queueChangeLock");
                    } else {
                        if (!waitingForResume) {
                            Log.d(LC, "initialize: Got queueChangeLock. Not waiting for resume");
                            queueChangeLock.release();
                            break;
                        } else {
                            Log.d(LC, "initialize: Got queueChangeLock. Waiting for resume");
                        }
                    }
                }
                long stamp = queueStateLock.tryWriteLock(5, TimeUnit.SECONDS);
                if (stamp == 0) {
                    Log.w(LC, "initialize: Got timeout waiting for queueStateLock");
                } else {
                    queueStateLock.unlockWrite(stamp);
                }
                Log.d(LC, "initialized");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public AudioPlayerState getLastState() {
        if (playerState == MediaStatus.PLAYER_STATE_IDLE
                && (idleReason == MediaStatus.IDLE_REASON_FINISHED
                || idleReason == MediaStatus.IDLE_REASON_INTERRUPTED)) {
            // All entries has already been returned to PlaybackController
            return new AudioPlayerState(
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    0
            );
        }
        long lastPos = getSeekPosition();
        PlaybackEntry currentEntry = null;
        List<PlaybackEntry> entries = new LinkedList<>();
        List<PlaybackEntry> history = new LinkedList<>();
        boolean isHistory = lastCurrentItemId != -1;
        for (int itemId: lastItemIds) {
            MediaQueueItem queueItem = lastQueueItemMap.get(itemId);
            if (queueItem == null) {
                continue;
            }
            PlaybackEntry entry = mediaQueueItem2PlaybackEntry(queueItem);
            if (itemId == lastCurrentItemId) {
                currentEntry = entry;
                isHistory = false;
                continue;
            }
            if (isHistory) {
                history.add(entry);
            } else {
                entries.add(entry);
            }
        }
        return new AudioPlayerState(currentEntry, history, entries, lastPos);
    }

    @Override
    public long getSeekPosition() {
        RemoteMediaClient remote = getRemote();
        if (remote != null) {
            lastPos = remote.getApproximateStreamPosition();
        }
        return lastPos;
    }

    @Override
    public CompletableFuture<Void> play() {
        Log.d(LC, "play()");
        playWhenReady = true;
        return playerAction(PlayerAction.PLAY)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    @Override
    public CompletableFuture<Void> pause() {
        Log.d(LC, "pause()");
        playWhenReady = false;
        return playerAction(PlayerAction.PAUSE)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    @Override
    public CompletableFuture<Void> stop() {
        Log.d(LC, "stop()");
        playWhenReady = false;
        return playerAction(PlayerAction.STOP)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    private CompletableFuture<Void> handleMediaClientRequest(
            String desc,
            String errorMsg,
            PendingResult<RemoteMediaClient.MediaChannelResult> req
    ) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        req.setResultCallback(r -> handleResultCallback(
                result,
                r,
                desc,
                errorMsg + ": " + getResultString(r)
        ));
        remoteMediaClientResultCallbackTimeout(result);
        return result;
    }

    private void handleResultCallback(CompletableFuture<Void> future,
                                      RemoteMediaClient.MediaChannelResult result,
                                      String desc,
                                      String error) {
        logResult(desc, result);
        if (result.getStatus().isSuccess()) {
            future.complete(null);
        } else {
            future.completeExceptionally(new Util.FutureException(error));
        }
    }

    @Override
    public CompletableFuture<Void> seekTo(long pos) {
        Log.d(LC, "seekTo()");
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(aVoid -> {
                    RemoteMediaClient remote = getRemote();
                    if (remote == null) {
                        return Util.futureResult("seekTo(): remoteMediaClient is null");
                    }

                    Log.d(LC, "playerAction: seekTo(" + pos + ") in state: " + getStateString(
                            remote.getPlayerState(),
                            remote.getIdleReason()
                    ));
                    return handleMediaClientRequest(
                            "seek",
                            "Could not seek",
                            remote.seek(pos)

                    );
                }, Util.getMainThreadExecutor());
    }

    private int getCurrentItemId() {
        return lastCurrentItemId;
    }

    private int getCurrentIndex() {
        int currentItemId = getCurrentItemId();
        for (int i = 0; i < lastItemIds.length; i++) {
            if (lastItemIds[i] == currentItemId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getNumPreloaded() {
        int currentItemIndex = getCurrentIndex();
        if (currentItemIndex == -1) {
            return 0;
        }
        return lastItemIds.length - currentItemIndex - 1;
    }

    private PlaybackEntry mediaQueueItem2PlaybackEntry(MediaQueueItem mediaQueueItem) {
        if (mediaQueueItem == null) {
            return null;
        }
        MediaMetadata castMeta = mediaQueueItem.getMedia().getMetadata();
        return new PlaybackEntry(castMeta);
    }

    @Override
    public PlaybackEntry getCurrentEntry() {
        int itemIndex = getCurrentIndex();
        return getMediaQueueEntry(itemIndex);
    }

    @Override
    public PlaybackEntry getPreloadEntry(int position) {
        int itemIndex = getCurrentIndex() + 1 + position;
        return getMediaQueueEntry(itemIndex);
    }

    private PlaybackEntry getMediaQueueEntry(int mediaQueueItemIndex) {
        int mediaQueueItemId = getItemId(mediaQueueItemIndex);
        return mediaQueueItem2PlaybackEntry(queueItemMap.get(mediaQueueItemId));
    }

    private List<PlaybackEntry> getEntries(boolean excludeCurrentItem) {
        int currentItemId = getCurrentItemId();
        LinkedList<PlaybackEntry> entries = new LinkedList<>();
        int currentItemIndex = getCurrentIndex();
        if (currentItemIndex == -1) {
            return entries;
        }
        for (int i = currentItemIndex; i < lastItemIds.length; i++) {
            int itemId = lastItemIds[i];
            if (excludeCurrentItem && itemId == currentItemId) {
                continue;
            }
            MediaQueueItem mediaQueueItem = queueItemMap.get(itemId);
            PlaybackEntry playbackEntry = mediaQueueItem == null ?
                    new PlaybackEntry(
                            EntryID.UNKOWN,
                            PlaybackEntry.PLAYBACK_ID_INVALID,
                            PlaybackEntry.USER_TYPE_EXTERNAL,
                            PlaybackEntry.PLAYLIST_POS_NONE,
                            PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID
                    ) :
                    new PlaybackEntry(mediaQueueItem.getMedia().getMetadata());
            playbackEntry.setPreloaded(true);
            entries.add(playbackEntry);
        }
        return entries;
    }

    @Override
    public List<PlaybackEntry> getPreloadEntries() {
        return getEntries(true);
    }

    @Override
    public List<PlaybackEntry> getHistory() {
        LinkedList<PlaybackEntry> entries = new LinkedList<>();
        int currentItemIndex = getCurrentIndex();
        for (int i = 0; i < lastItemIds.length && i < currentItemIndex; i++) {
            int itemId = lastItemIds[i];
            MediaQueueItem mediaQueueItem = queueItemMap.get(itemId);
            PlaybackEntry playbackEntry = mediaQueueItem == null ?
                    new PlaybackEntry(
                            EntryID.UNKOWN,
                            PlaybackEntry.PLAYBACK_ID_INVALID,
                            PlaybackEntry.USER_TYPE_EXTERNAL,
                            PlaybackEntry.PLAYLIST_POS_NONE,
                            PlaybackEntry.PLAYLIST_SELECTION_ID_INVALID
                    ) :
                    new PlaybackEntry(mediaQueueItem.getMedia().getMetadata());
            playbackEntry.setPreloaded(true);
            entries.add(playbackEntry);
        }
        if (playbackFinishedEntry != null) {
            entries.add(playbackFinishedEntry);
            playbackFinishedEntry = null;
        }
        return entries;
    }

    private int getItemId(int mediaQueueItemIndex) {
        return mediaQueueItemIndex < 0 || mediaQueueItemIndex >= lastItemIds.length ?
                MediaQueueItem.INVALID_ITEM_ID : lastItemIds[mediaQueueItemIndex];
    }

    @Override
    public CompletableFuture<Void> destroy() {
        Log.d(LC, "destroy");
        callback = AudioPlayer.dummyCallback;
        Log.d(LC, "destroyed");
        return Util.futureResult(null);
    }

    // Changes queue
    @Override
    public CompletableFuture<Void> preload(List<PlaybackEntry> entries, int offset) {
        Log.d(LC, "preload()");
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(aVoid -> {
                    RemoteMediaClient remote = getRemote();
                    if (remote == null) {
                        return Util.futureResult("preload(): remoteMediaClient is null");
                    }
                    if (!remote.hasMediaSession()) {
                        return setQueue(entries, 0);
                    }
                    return buildMediaQueueItems(entries, playWhenReady)
                            .thenComposeAsync(items -> {
                                int beforeItemId = getInsertBeforeItemId(offset);
                                logCurrentQueue();
                                Log.d(LC, "preload inserting " + items.length + " items"
                                        + " before " + beforeItemId);
                                return handleMediaClientQueueRequest(
                                        "preload queueInsertItems",
                                        "Could not insert queue items",
                                        remote.queueInsertItems(
                                                items,
                                                beforeItemId,
                                                null
                                        )
                                );
                            }, Util.getMainThreadExecutor());
                }, Util.getMainThreadExecutor());
    }

    private int getInsertBeforeItemId(int offset) {
        int currentIndex = getCurrentIndex();
        int queueInsertBeforeIndex = currentIndex + 1 + offset;
        return getItemId(queueInsertBeforeIndex);
    }

    @Override
    public CompletableFuture<Void> dePreload(List<PlaybackEntry> playbackEntries) {
        List<Integer> queueItemIdsToRemove = playbackEntries.stream()
                .map(playbackEntry -> playbackIDToCastItemIDMap.get(playbackEntry.playbackID))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        logCurrentQueue();
        Log.d(LC, "dePreload()"
                + "\nqueueItemIdsToRemove: "
                + queueItemIdsToRemove.size()
                + ": " + queueItemIdsToRemove);
        if (queueItemIdsToRemove.size() <= 0) {
            return Util.futureResult(null);
        }
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(aVoid -> {
                    RemoteMediaClient remote = getRemote();
                    if (remote == null) {
                        return Util.futureResult("dePreload(): remoteMediaClient is null");
                    }
                    return handleMediaClientQueueRequest(
                            "dePreload() queueRemoveItems",
                            "Could not remove queue items",
                            remote.queueRemoveItems(
                                    queueItemIdsToRemove.stream().mapToInt(i -> i).toArray(),
                                    null
                            )
                    );
                }, Util.getMainThreadExecutor());
    }

    private CompletableFuture<Void> handleMediaClientQueueRequest(
            String desc,
            String errorMsg,
            PendingResult<RemoteMediaClient.MediaChannelResult> req
    ) {
        queueChangeLock.drainPermits();
        Log.d(LC, "handleQueueReq: " + desc + " started");
        return handleMediaClientRequest(desc, errorMsg, req)
                .thenComposeAsync(aVoid -> {
                    try {
                        if (!queueChangeLock.tryAcquire(5, TimeUnit.SECONDS)) {
                            Log.e(LC, "handleQueueReq: Got timeout waiting for "
                                    + "queueChangeLock");
                        } else {
                            queueChangeLock.release();
                        }
                        long stamp = queueStateLock.tryWriteLock(5, TimeUnit.SECONDS);
                        if (stamp == 0) {
                            Log.e(LC, "handleQueueReq: Got timeout waiting for "
                                    + " queueStateLock");
                        } else {
                            queueStateLock.unlockWrite(stamp);
                        }
                        Log.d(LC, "handleQueueReq: " + desc + " finished");
                        return Util.futureResult(null);
                    } catch (InterruptedException e) {
                        return Util.futureResult(e.getMessage());
                    }
                });
    }

    private void remoteMediaClientResultCallbackTimeout(CompletableFuture<Void> result) {
        // Sometimes remoteMediaClient errors with:
        //   E/RemoteMediaClient: Result already set when calling onRequestCompleted
        //   java.lang.IllegalStateException: Results have already been set
        // No ResultCallback is then issued, which deadlocks wrapping futures.
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            if (!result.isDone()) {
                result.completeExceptionally(
                        new Util.FutureException("Timeout while waiting for Cast action result")
                );
            }
        });
    }

    @Override
    public int getMaxToPreload() {
        return 3;
    }

    // Changes queue
    private CompletableFuture<Void> setAutoPlay(boolean playWhenReady) {
        int[] itemIds = lastItemIds;
        int currentIndex = getCurrentIndex();
        List<MediaQueueItem> queueItems = new LinkedList<>();
        for (int itemIndex = currentIndex; itemIndex < itemIds.length; itemIndex++) {
            MediaQueueItem currentItem = queueItemMap.get(itemIds[itemIndex]);
            if (currentItem == null) {
                Log.w(LC, "setAutoPlay(): A mediaQueueItem is null: [" + itemIndex + "]"
                        + itemIds[itemIndex]);
                continue;
            }
            MediaQueueItem newItem = new MediaQueueItem.Builder(currentItem)
                    .setAutoplay(playWhenReady)
                    .build();
            queueItems.add(newItem);
        }
        Log.d(LC, "setAutoPlay() updating items to playWhenReady: " + playWhenReady);
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(aVoid -> {
                    RemoteMediaClient remote = getRemote();
                    if (remote == null) {
                        return Util.futureResult("setAutoPlay(): remoteMediaClient is null");
                    }
                    return handleMediaClientQueueRequest(
                            "setAutoPlay() queueUpdateItems",
                            "Could not set autoplay to " + playWhenReady,
                            remote.queueUpdateItems(
                                    queueItems.toArray(new MediaQueueItem[0]),
                                    null
                            )
                    );
                }, Util.getMainThreadExecutor());
    }

    private void logCurrentQueue() {
        int[] itemIds = lastItemIds;
        int currentItemId = getCurrentItemId();
        logQueue(itemIds, currentItemId);
    }

    private void logQueue(int[] itemIds, int currentItemId) {
        StringBuilder sb = new StringBuilder("castQueue:\n");
        if (itemIds == null || itemIds.length <= 0) {
            Log.d(LC, sb.append("queue is empty").toString());
            return;
        }
        int index = 0;
        for (int itemId: itemIds) {
            int itemIndex = index++;
            MediaQueueItem queueItem = queueItemMap.get(
                    itemId,
                    null
            );
            if (itemId == currentItemId) {
                sb.append("* ");
            }
            sb.append("[").append(itemIndex).append("] ").append(itemId).append(": ");
            if (queueItem == null) {
                sb.append("?\n");
            } else {
                MediaMetadata castMeta = queueItem.getMedia().getMetadata();
                String type = castMeta.getString(CASTMETA_KEY_PLAYBACK_TYPE);
                sb.append(castMeta.getString(CASTMETA_KEY_PLAYBACK_ID));
                sb.append(".");
                sb.append(type);
                if (PlaybackEntry.USER_TYPE_PLAYLIST.equals(type)) {
                    sb.append("-");
                    sb.append(castMeta.getString(CASTMETA_KEY_PLAYLIST_SELECTION_ID));
                    sb.append("[");
                    sb.append(castMeta.getString(CASTMETA_KEY_PLAYLIST_POS));
                    sb.append("]");
                }
                sb.append(" ");
                sb.append(castMeta.getString(MediaMetadata.KEY_TITLE));
                sb.append("\n");
            }
        }
        Log.d(LC, sb.toString());
    }

    // Changes queue
    private CompletableFuture<Void> setQueue(List<PlaybackEntry> playbackEntries,
                                             long seekPosition) {
        if (playbackEntries == null || playbackEntries.isEmpty()) {
            return Util.futureResult(null);
        }
        Log.d(LC, "setQueue, creating a new queue");
        int startIndex = 0;
        int repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        return buildMediaQueueItems(playbackEntries, playWhenReady)
                .thenComposeAsync(queueItems -> {
                    RemoteMediaClient remote = getRemote();
                    if (remote == null) {
                        return Util.futureResult("setQueue(): remoteMediaClient is null");
                    }
                    return handleMediaClientQueueRequest(
                            "queueLoad",
                            "Could not load queue",
                            remote.queueLoad(
                                    queueItems,
                                    startIndex,
                                    repeatMode,
                                    seekPosition,
                                    null
                            )
                    );
                }, Util.getMainThreadExecutor());
    }

    private CompletableFuture<MediaQueueItem[]> buildMediaQueueItems(
            List<PlaybackEntry> playbackEntries,
            boolean playWhenReady
    ) {
        return MusicLibraryService.getSongMetas(
                context,
                playbackEntries.stream()
                        .map(p -> p.entryID)
                        .collect(Collectors.toList())
        ).thenApplyAsync(metas -> {
            Iterator<PlaybackEntry> pIter = playbackEntries.iterator();
            Iterator<Meta> mIter = metas.iterator();
            List<MediaQueueItem> queueItems = new LinkedList<>();
            while (pIter.hasNext() && mIter.hasNext()) {
                PlaybackEntry playbackEntry = pIter.next();
                Meta meta = mIter.next();
                MediaQueueItem queueItem = buildMediaQueueItem(playbackEntry, meta, playWhenReady);
                if (queueItem == null) {
                    Log.e(LC, "Could not add playbackEntry to queue: "
                            + playbackEntry.toString());
                    continue;
                }
                queueItems.add(queueItem);
            }
            StringBuilder sb = new StringBuilder("newQueueItems:");
            for (MediaQueueItem mediaQueueItem: queueItems) {
                MediaMetadata castMeta = mediaQueueItem.getMedia().getMetadata();
                String title = castMeta.getString(MediaMetadata.KEY_TITLE);
                PlaybackEntry entry = new PlaybackEntry(castMeta);
                sb.append("\n").append(entry).append(": ").append(title);
            }
            Log.d(LC, sb.toString());
            return queueItems.toArray(new MediaQueueItem[0]);
        }, Util.getMainThreadExecutor());
    }

    private MediaQueueItem buildMediaQueueItem(PlaybackEntry playbackEntry,
                                               Meta meta,
                                               boolean playWhenReady) {
        MediaInfo mediaInfo = buildMediaInfo(playbackEntry, meta);
        if (mediaInfo == null) {
            return null;
        }
        long position = 0;
        return new MediaQueueItem.Builder(mediaInfo)
                .setAutoplay(playWhenReady)
                .setStartTime(position)
                .build();
    }

    private MediaInfo buildMediaInfo(PlaybackEntry playbackEntry, Meta meta) {
        String URL = MusicLibraryService.getAudioURL(context, playbackEntry.entryID);
        if (URL == null) {
            Log.e(LC, "Could not get URL for " + playbackEntry);
            return null;
        }
        MediaMetadata castMeta = meta.toCastMediaMetadata();
        castMeta.putString(CASTMETA_KEY_PLAYBACK_ID, Long.toString(playbackEntry.playbackID));
        castMeta.putString(CASTMETA_KEY_PLAYBACK_TYPE, playbackEntry.playbackType);
        castMeta.putString(CASTMETA_KEY_PLAYLIST_POS, Long.toString(playbackEntry.playlistPos));
        castMeta.putString(CASTMETA_KEY_PLAYLIST_SELECTION_ID, Long.toString(playbackEntry.playlistSelectionID));
        long duration = meta.getFirstLong(Meta.FIELD_DURATION, 0);
        String contentType = meta.getFirstString(Meta.FIELD_CONTENT_TYPE);
        return new MediaInfo.Builder(URL)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(castMeta)
                .setStreamDuration(duration)
                .build();
    }

    // Changes queue
    @Override
    public CompletableFuture<Void> next() {
        Log.d(LC, "next()");
        return CompletableFuture.completedFuture(null)
                .thenComposeAsync(aVoid -> {
                    RemoteMediaClient remote = getRemote();
                    if (remote == null) {
                        return Util.futureResult("next(): remoteMediaClient is null");
                    }
                    return handleMediaClientQueueRequest(
                            "next",
                            "Could not go to next",
                            remote.queueNext(null)
                    );
                }, Util.getMainThreadExecutor());
    }

    private void logResult(String action, RemoteMediaClient.MediaChannelResult result) {
        Log.d(LC, action + " smooth? " + result.getStatus().isSuccess());
        if (!result.getStatus().isSuccess()) {
            String code = CastStatusCodes.getStatusCodeString(result.getStatus().getStatusCode());
            String msg = code + ": "
                    + result.getStatus().toString() + " "
                    + result.getStatus().getStatusMessage();
            MediaError mediaError = result.getMediaError();
            if (mediaError != null) {
                msg += "\nmedia error (" + mediaError.getDetailedErrorCode() + ")"
                        + ": " + mediaError.getReason();
            }
            RemoteMediaClient remote = getRemote();
            if (remote != null) {
                msg += "\nstate: " + getStateString(
                        remote.getPlayerState(),
                        remote.getIdleReason()
                );
            }
            Log.e(LC, msg);
            logCurrentQueue();
        }
    }

    private String getResultString(RemoteMediaClient.MediaChannelResult result) {
        return CastStatusCodes.getStatusCodeString(result.getStatus().getStatusCode())
                + ": " + result.getStatus().getStatusMessage();
    }

    private CompletableFuture<Void> playerAction(PlayerAction action) {
        return CompletableFuture.supplyAsync(() -> {
            RemoteMediaClient remote = getRemote();
            if (remote == null) {
                throw new RuntimeException(
                        "playerAction(" + action.name() + "): remoteMediaClient is null"
                );
            }
            Log.d(LC, "playerAction: " + action.name() + " in state: " + getStateString(
                    remote.getPlayerState(),
                    remote.getIdleReason()
            ));
            switch (action) {
                case PLAY:
                    return remote.isPlaying() ? null : remote.play();
                case PAUSE:
                    return remote.isPaused() ? null : remote.pause();
                case STOP:
                    return remote.isPaused() ? null : remote.stop();
                default:
                    Log.d(LC, "Unknown PlayerAction: " + action.name());
                    return null;
            }
        }, Util.getMainThreadExecutor())
                .thenCompose(request -> request == null ? Util.futureResult(null) :
                        handleMediaClientRequest(
                                action.name(),
                                "Could not perform action (" + action.name() + ")",
                                request
                        ));
    }

    private class MediaQueueCallback extends MediaQueue.Callback {
        private int[] preChangeItemIds;
        private long queueStateLockStamp;

        // Called before any changes are made
        @Override
        public void mediaQueueWillChange() {
            Log.d(LC, "MediaQueue: mediaQueueWillChange()");
            preChangeItemIds = mediaQueue.getItemIds();
            long stamp = queueStateLock.tryWriteLock();
            if (stamp != 0) {
                Log.d(LC, "mediaQueueWillChange: queueStateLock taken");
                queueStateLockStamp = stamp;
            }
        }

        @Override
        public void itemsInsertedInRange(int index, int count) {
            Log.d(LC, "MediaQueue: itemsInsertedInRange(index: " + index
                    + ", count: " + count + ")");
            for (int itemIndex = index; itemIndex < index + count; itemIndex++) {
                MediaQueueItem queueItem = mediaQueue.getItemAtIndex(itemIndex);
                if (queueItem != null) {
                    int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                    queueItemMap.put(itemId, queueItem);
                    PlaybackEntry playbackEntry = mediaQueueItem2PlaybackEntry(queueItem);
                    playbackIDToCastItemIDMap.put(playbackEntry.playbackID, itemId);
                }
            }
            cleanQueueItemMap();
            logCurrentQueue();
        }

        @Override
        public void itemsReloaded() {
            Log.d(LC, "MediaQueue: itemsReloaded()");
            for (int itemIndex = 0; itemIndex < mediaQueue.getItemCount(); itemIndex++) {
                MediaQueueItem queueItem = mediaQueue.getItemAtIndex(itemIndex);
                if (queueItem != null) {
                    int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                    queueItemMap.put(itemId, queueItem);
                    PlaybackEntry playbackEntry = mediaQueueItem2PlaybackEntry(queueItem);
                    playbackIDToCastItemIDMap.put(playbackEntry.playbackID, itemId);
                }
            }
            cleanQueueItemMap();
            logCurrentQueue();
        }

        @Override
        public void itemsRemovedAtIndexes(int[] indexes) {
            Log.d(LC, "MediaQueue: itemsRemovedAtIndexes(" + Arrays.toString(indexes) + ")");
            for (int itemIndex: indexes) {
                int itemId = preChangeItemIds[itemIndex];
                PlaybackEntry playbackEntry = mediaQueueItem2PlaybackEntry(queueItemMap.get(itemId));
                playbackIDToCastItemIDMap.remove(playbackEntry.playbackID);
                queueItemMap.remove(itemId);
            }
            cleanQueueItemMap();
            logCurrentQueue();
        }

        @Override
        public void itemsUpdatedAtIndexes(int[] indexes) {
            Log.d(LC, "MediaQueue: itemsUpdatedAtIndexes(" + Arrays.toString(indexes) + ")");
            for (int itemIndex: indexes) {
                int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                if (queueItemMap.get(itemId) == null) {
                    MediaQueueItem queueItem = mediaQueue.getItemAtIndex(itemIndex);
                    if (queueItem == null) {
                        Log.d(LC, "queueItem at " + itemIndex + " is null. Waiting for "
                                + "mediaQueue to fetch...");
                        break;
                    }
                    queueItemMap.put(itemId, queueItem);
                    PlaybackEntry playbackEntry = mediaQueueItem2PlaybackEntry(queueItem);
                    playbackIDToCastItemIDMap.put(playbackEntry.playbackID, itemId);
                }
            }
            cleanQueueItemMap();
            logCurrentQueue();
        }

        private void cleanQueueItemMap() {
            HashSet<Integer> currentItemIds = Arrays.stream(mediaQueue.getItemIds())
                    .boxed()
                    .collect(Collectors.toCollection(HashSet::new));
            for (int i = 0; i < queueItemMap.size(); i++) {
                int itemID = queueItemMap.keyAt(i);
                if (!currentItemIds.contains(itemID)) {
                    PlaybackEntry playbackEntry = mediaQueueItem2PlaybackEntry(queueItemMap.get(itemID));
                    playbackIDToCastItemIDMap.remove(playbackEntry.playbackID);
                    queueItemMap.remove(itemID);
                }
            }
        }

        // Called when one ore more changes has been made
        @Override
        public void mediaQueueChanged() {
            Log.d(LC, "MediaQueue: mediaQueueChanged()");
            int[] itemIds = mediaQueue.getItemIds();
            if (itemIds.length > 0) { // Before disconnect the itemIds gets wiped.
                lastItemIds = mediaQueue.getItemIds();
                lastQueueItemMap = queueItemMap.clone();
            }
            logCurrentQueue();

            queueChangeLock.drainPermits();
            queueChangeLock.release();
            if (waitingForResume && itemIds.length > 0) {
                waitingForResume = false;
            }
            Log.d(LC, "mediaQueueChanged: queueChangeLock released");

            // Check if the queue state is up to date
            boolean isQueueStateUpToDate = isQueueUpToDate(true);
            if (isQueueStateUpToDate) {
                queueStateLock.unlockWrite(queueStateLockStamp);
                Log.d(LC, "mediaQueueChanged: queueStateLock released");
                callback.onPreloadChanged();
            }
        }
    }

    private boolean isQueueUpToDate(boolean triggerFetch) {
        boolean isQueueStateUpToDate = true;
        for (int itemId: mediaQueue.getItemIds()) {
            if (queueItemMap.get(itemId) == null) {
                if (triggerFetch) {
                    isQueueStateUpToDate = false;
                    mediaQueue.getItemAtIndex(mediaQueue.indexOfItemWithId(itemId));
                } else {
                    return false;
                }
            }
        }
        return isQueueStateUpToDate;
    }

    private class RemoteMediaClientCallback extends RemoteMediaClient.Callback {
        @Override
        public void onStatusUpdated() {
            RemoteMediaClient remote = getRemote();
            if (remote == null) {
                return;
            }
            int oldPlayerState = playerState;
            int oldIdleReason = idleReason;
            playerState = remote.getPlayerState();
            idleReason = remote.getIdleReason();
            // Return if state has not changed
            if (playerState == oldPlayerState) {
                if (playerState != MediaStatus.PLAYER_STATE_IDLE) {
                    return;
                } else if (idleReason == oldIdleReason) {
                    return;
                }
            }
            Log.d(LC, "onStatusUpdated from " + getStateString(oldPlayerState, oldIdleReason)
                            + " to " + getStateString(playerState, idleReason));
            switch (playerState) {
                case MediaStatus.PLAYER_STATE_IDLE:
                    switch (idleReason) {
                        case MediaStatus.IDLE_REASON_FINISHED:
                            if (getNumPreloaded() <= 0) {
                                onPlaybackFinished();
                            }
                            callback.onSongEnded();
                            break;
                        case MediaStatus.IDLE_REASON_CANCELED:
                            callback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
                            break;
                        case MediaStatus.IDLE_REASON_ERROR:
                            break;
                        case MediaStatus.IDLE_REASON_INTERRUPTED:
                            // Constant indicating that the player is idle because playback has
                            // been interrupted by a LOAD command.
                            // Happens when last track explicitly receives next()
                            if (lastItemIds.length > 0
                                    && lastCurrentItemId == lastItemIds[lastItemIds.length - 1]) {
                                onPlaybackFinished();
                                break;
                            }
                            // Also happens when song is changed by outside force (e.g. assistant)
                            callback.onSongEnded();
                            break;
                        default:
                            break;
                    }
                    break;
                case MediaStatus.PLAYER_STATE_BUFFERING:
                    callback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
                    break;
                case MediaStatus.PLAYER_STATE_PAUSED:
                    callback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
                    break;
                case MediaStatus.PLAYER_STATE_PLAYING:
                    callback.onStateChanged(PlaybackStateCompat.STATE_PLAYING);
                    break;
                default:
                    break;
            }
        }

        private void onPlaybackFinished() {
            MediaQueueItem queueItem = lastQueueItemMap.get(lastCurrentItemId);
            playbackFinishedEntry = mediaQueueItem2PlaybackEntry(queueItem);
            callback.onCurrentEntryChanged(null);
            callback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
        }

        @Override
        public void onMetadataUpdated() {
            Log.d(LC, "onMetadataUpdated");
            RemoteMediaClient remote = getRemote();
            if (remote == null) {
                return;
            }
            MediaInfo mediaInfo = remote.getMediaInfo();
            if (mediaInfo == null) {
                return;
            }
            MediaMetadata castMeta = mediaInfo.getMetadata();
            callback.onCurrentEntryChanged(new PlaybackEntry(castMeta));
        }

        @Override
        public void onQueueStatusUpdated() {
            Log.d(LC, "onQueueStatusUpdated");
            queueChangeLock.drainPermits();
            queueChangeLock.release();
            RemoteMediaClient remote = getRemote();
            if (remote == null) {
                return;
            }
            MediaQueueItem currentItem = remote.getCurrentItem();
            if (currentItem != null) {
                lastCurrentItemId = currentItem.getItemId();
            }
            Log.d(LC, "onQueueStatusUpdated queueChangeLock released");
            if (isQueueUpToDate(false)) {
                callback.onPreloadChanged();
            }
        }

        @Override
        public void onPreloadStatusUpdated() {
            Log.d(LC, "onPreloadStatusUpdated");
        }

        @Override
        public void onSendingRemoteMediaRequest() {
            Log.d(LC, "onSendingRemoteMediaRequest");
        }

        @Override
        public void onAdBreakStatusUpdated() {
            Log.d(LC, "onAdBreakStatusUpdated");
        }
    }

    private String getStateString(int state, int idleReason) {
        switch (state) {
            case MediaStatus.PLAYER_STATE_UNKNOWN:
                return "UNKNOWN";
            case MediaStatus.PLAYER_STATE_IDLE:
                switch (idleReason) {
                    case MediaStatus.IDLE_REASON_FINISHED:
                        return "IDLE_FINISHED";
                    case MediaStatus.IDLE_REASON_CANCELED:
                        return "IDLE_CANCELED";
                    case MediaStatus.IDLE_REASON_ERROR:
                        return "IDLE_ERROR";
                    case MediaStatus.IDLE_REASON_INTERRUPTED:
                        return "IDLE_INTERRUPTED";
                    case MediaStatus.IDLE_REASON_NONE:
                        return "IDLE_NONE";
                    default:
                        return "<unhandled IDLE state>";
                }
            case MediaStatus.PLAYER_STATE_PLAYING:
                return "PLAYING";
            case MediaStatus.PLAYER_STATE_PAUSED:
                return "PAUSED";
            case MediaStatus.PLAYER_STATE_BUFFERING:
                return "BUFFERING";
            case MediaStatus.PLAYER_STATE_LOADING:
                return "LOADING";
            default:
                return "<unhandled state>";
        }
    }
}