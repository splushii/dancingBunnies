package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class CastAudioPlayer extends AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);
    private static final int NUM_TO_PRELOAD = 10;

    private final RemoteMediaClientCallback remoteMediaClientCallback = new RemoteMediaClientCallback();
    private final MediaQueueCallback mediaQueueCallback = new MediaQueueCallback();
    private final MusicLibraryService musicLibraryService;
    private RemoteMediaClient remoteMediaClient;
    private MediaQueue mediaQueue;
    private int playerState;
    private int idleReason;
    private SparseArray<MediaQueueItem> queueItemMap;
    private CompletableFuture<Optional<String>> lastActionFuture;
    private final Object lastActionFutureLock = new Object();
    private boolean playWhenReady = false;
    private long lastPos = 0;
    private int[] lastItemIds;
    private int lastCurrentItemId;
    private SparseArray<MediaQueueItem> lastQueueItemMap;

    private enum PlayerAction {
        PLAY,
        PAUSE,
        STOP
    }

    CastAudioPlayer(Callback audioPlayerCallback,
                    MusicLibraryService musicLibraryService,
                    AudioPlayerState state,
                    CastSession castSession) {
        super(audioPlayerCallback);
        this.musicLibraryService = musicLibraryService;
        queueItemMap = new SparseArray<>();
        lastActionFuture = CompletableFuture.completedFuture(Optional.empty());
        remoteMediaClient = castSession.getRemoteMediaClient();
        remoteMediaClient.registerCallback(remoteMediaClientCallback);
        mediaQueue = remoteMediaClient.getMediaQueue();
        mediaQueue.registerCallback(mediaQueueCallback);
        // TODO: Handle state.history and state.lastPos
        if (!state.entries.isEmpty()) {
            preload(state.entries).thenCompose(e -> {
                e.ifPresent(s -> Log.e(LC, "Could not perform initial preload: " + s));
                return checkPreload();
            });
        } else {
            checkPreload();
        }
    }

    @Override
    AudioPlayerState getLastState() {
        List<PlaybackEntry> entries = new LinkedList<>();
        List<PlaybackEntry> history = new LinkedList<>();
        boolean isHistory = lastCurrentItemId != -1;
        for (int itemId: lastItemIds) {
            if (itemId == lastCurrentItemId) {
                isHistory = false;
            }
            MediaQueueItem queueItem = lastQueueItemMap.get(itemId);
            if (queueItem == null) {
                continue;
            }
            PlaybackEntry entry = new PlaybackEntry(Meta.from(queueItem.getMedia().getMetadata()));
            if (isHistory) {
                history.add(entry);
            } else {
                entries.add(entry);
            }
        }
        return new AudioPlayerState(history, entries, lastPos);
    }

    @Override
    public long getSeekPosition() {
        if (remoteMediaClient != null) {
            lastPos = remoteMediaClient.getApproximateStreamPosition();
        }
        return lastPos;
    }

    @Override
    public CompletableFuture<Optional<String>> play() {
        if (remoteMediaClient == null) {
            return actionResult("play(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPlaying()) {
            return actionResult(null);
        }
        Log.d(LC, "play()");
        return sequentialize(e -> {
            Log.d(LC, "play() start");
            playWhenReady = true;
            return playerAction(PlayerAction.PLAY).thenCompose(e2 -> {
                if (e2.isPresent()) {
                    return CompletableFuture.completedFuture(e2);
                }
                return setAutoPlay(playWhenReady);
            });
        });
    }

    @Override
    public CompletableFuture<Optional<String>> pause() {
        if (remoteMediaClient == null) {
            return actionResult("pause(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPaused()) {
            return actionResult(null);
        }
        Log.d(LC, "pause()");
        return sequentialize(e -> {
            Log.d(LC, "pause() start");
            playWhenReady = false;
            return playerAction(PlayerAction.PAUSE).thenCompose(e2 -> {
                if (e2.isPresent()) {
                    return CompletableFuture.completedFuture(e2);
                }
                return setAutoPlay(playWhenReady);
            });
        });
    }

    @Override
    public CompletableFuture<Optional<String>> stop() {
        if (remoteMediaClient == null) {
            return actionResult("stop(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPaused()) {
            return actionResult(null);
        }
        Log.d(LC, "stop()");
        return sequentialize(e -> {
            Log.d(LC, "stop() start");
            playWhenReady = false;
            return playerAction(PlayerAction.STOP).thenCompose(e2 -> {
                if (e2.isPresent()) {
                    return CompletableFuture.completedFuture(e2);
                }
                return setAutoPlay(playWhenReady);
            });
        });
    }

    @Override
    public CompletableFuture<Optional<String>> seekTo(long pos) {
        Log.d(LC, "seekTo()");
        return sequentialize(e -> {
            Log.d(LC, "seekTo() start");
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            if (remoteMediaClient == null) {
                return actionResult("seekTo(): remoteMediaClient is null");
            }
            Log.d(LC, "playerAction: seekTo(" + pos + ") in state: "
                    + getStateString(remoteMediaClient.getPlayerState()));
            remoteMediaClient.seek(pos).setResultCallback(r -> {
                logResult("seek", r);
                if (r.getStatus().isSuccess()) {
                    result.complete(null);
                } else {
                    result.complete(Optional.of("Could not seek: " + getResultString(r)));
                }
            });
            remoteMediaClientResultCallbackTimeout(result);
            return result;
        });
    }

    private int getCurrentItemId() {
        if (remoteMediaClient == null) {
            return MediaQueueItem.INVALID_ITEM_ID;
        }
        MediaQueueItem currentItem = remoteMediaClient.getCurrentItem();
        if (currentItem != null) {
            lastCurrentItemId = currentItem.getItemId();
            return lastCurrentItemId;
        }
        return MediaQueueItem.INVALID_ITEM_ID;
    }

    private int getCurrentIndex() {
        return mediaQueue.indexOfItemWithId(getCurrentItemId());
    }

    private int getNumPreloaded() {
        int currentItemIndex = getCurrentIndex();
        if (currentItemIndex == -1) {
            return 0;
        }
        return mediaQueue.getItemCount() - currentItemIndex;
    }

    private List<MediaMetadata> getEntriesMeta(boolean excludeCurrentItem) {
        int currentItemId = getCurrentItemId();
        LinkedList<MediaMetadata> entries = new LinkedList<>();
        int currentItemIndex = getCurrentIndex();
        if (currentItemIndex == -1) {
            return entries;
        }
        for (int i = currentItemIndex; i < mediaQueue.getItemCount(); i++) {
            int itemId = mediaQueue.itemIdAtIndex(i);
            if (excludeCurrentItem && itemId == currentItemId) {
                continue;
            }
            MediaQueueItem mediaQueueItem = queueItemMap.get(itemId);
            MediaMetadata castMeta = mediaQueueItem == null ?
                    Meta.from(Meta.UNKNOWN_ENTRY, PlaybackEntry.USER_TYPE_EXTERNAL) :
                    mediaQueueItem.getMedia().getMetadata();
            entries.add(castMeta);
        }
        return entries;
    }

    private List<PlaybackEntry> getPreloadedEntries(int maxNum) {
        LinkedList<PlaybackEntry> entries = new LinkedList<>();
        for (MediaMetadata castMeta: getEntriesMeta(false)) {
            if (entries.size() >= maxNum) {
                break;
            }
            entries.add(new PlaybackEntry(Meta.from(castMeta)));
        }
        return entries;
    }

    private List<PlaybackEntry> getEntriesOfType(String type, int maxNum) {
        LinkedList<PlaybackEntry> entries = new LinkedList<>();
        for (MediaMetadata castMeta: getEntriesMeta(true)) {
            if (entries.size() > maxNum) {
                break;
            }
            PlaybackEntry playbackEntry = new PlaybackEntry(Meta.from(castMeta));
            if (playbackEntry.playbackType.equals(type)) {
                entries.add(playbackEntry);
            }
        }
        return entries;
    }

    @Override
    List<PlaybackEntry> getPreloadedQueueEntries(int maxNum) {
        return getEntriesOfType(PlaybackEntry.USER_TYPE_QUEUE, maxNum);
    }

    @Override
    List<PlaybackEntry> getPreloadedPlaylistEntries(int maxNum) {
        return getEntriesOfType(PlaybackEntry.USER_TYPE_PLAYLIST, maxNum);
    }

    @Override
    CompletableFuture<Optional<String>> queue(PlaybackEntry playbackEntry, PlaybackQueue.QueueOp op) {
        Log.d(LC, "queue()");
        return sequentialize(e -> {
            Log.d(LC, "queue() start");
            int currentIndex = getCurrentIndex();
            int numQueueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE).size();
            int beforeItemId;
            switch (op) {
                case NEXT:
                    beforeItemId = mediaQueue.itemIdAtIndex(currentIndex + 1);
                    break;
                case LAST:
                default:
                    beforeItemId = mediaQueue.itemIdAtIndex(currentIndex + numQueueEntries + 1);
                    break;
            }
            if (remoteMediaClient == null) {
                return actionResult("queue(): remoteMediaClient is null");
            }
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            Log.d(LC, "queue() inserting " + playbackEntry.toString()
                            + " before itemId " + beforeItemId);
            remoteMediaClient.queueInsertItems(
                    new MediaQueueItem[]{buildMediaQueueItem(playbackEntry, playWhenReady)},
                    beforeItemId,
                    null
            ).setResultCallback(r -> {
                logResult("queue() queueInsertItems", r);
                if (r.getStatus().isSuccess()) {
                    result.complete(Optional.empty());
                } else {
                    result.complete(
                            Optional.of("Could not insert queue item " + playbackEntry.toString()
                                    + ": " + getResultString(r))
                    );
                }
            });
            remoteMediaClientResultCallbackTimeout(result);
            return result;
        });
    }

    private void remoteMediaClientResultCallbackTimeout(
            CompletableFuture<Optional<String>> result) {
        // Sometimes remoteMediaClient errors with:
        //   E/RemoteMediaClient: Result already set when calling onRequestCompleted
        //   java.lang.IllegalStateException: Results have already been set
        // No ResultCallback is then issued, which gives a deadlock with sequentialize().
        // This timeout avoids the deadlock. Sleep time may need to be higher or could be lower.
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            if (!result.isDone()) {
                result.complete(Optional.of("Timeout while waiting for Cast action result"));
            }
        });
    }

    private CompletableFuture<Optional<String>> sequentialize(
            Function<? super Optional<String>, ? extends CompletionStage<Optional<String>>> func) {
        CompletableFuture<Optional<String>> future;
        synchronized (lastActionFutureLock) {
            future = lastActionFuture.thenCompose(func);
            lastActionFuture = future;
        }
        return future;
    }

    public CompletableFuture<Optional<String>> checkPreload() {
        Log.d(LC, "checkPreload()");
        return sequentialize(e -> {
            Log.d(LC, "checkPreload start()");
            logCurrentQueue();
            int numPreloaded = getNumPreloaded();
            if (numPreloaded < NUM_TO_PRELOAD) {
                List<PlaybackEntry> entries =
                        audioPlayerCallback.requestPreload(NUM_TO_PRELOAD - numPreloaded);
                return preload(entries);
            }
            return actionResult(null);
        });
    }

    private CompletableFuture<Optional<String>> preload(List<PlaybackEntry> entries) {
        if (remoteMediaClient == null) {
            return actionResult("remoteMediaClient is null");
        }
        if (!remoteMediaClient.hasMediaSession()) {
            return setQueue(
                    entries,
                    0,
                    playWhenReady
            );
        }
        MediaQueueItem[] items = buildMediaQueueItems(entries, playWhenReady);
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();
        Log.d(LC, "checkPreload appending " + items.length + " items.");
        remoteMediaClient.queueInsertItems(
                items,
                MediaQueueItem.INVALID_ITEM_ID,
                null
        ).setResultCallback(r -> {
            String msg = "checkPreload queueInsertItems";
            logResult(msg, r);
            if (r.getStatus().isSuccess()) {
                future.complete(Optional.empty());
            } else {
                future.complete(Optional.of("Could not perform action (" + msg + "): "
                        + getResultString(r)));
            }
        });
        remoteMediaClientResultCallbackTimeout(future);
        return future;
    }

    private CompletableFuture<Optional<String>> setAutoPlay(boolean playWhenReady) {
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        int[] itemIds = mediaQueue.getItemIds();
        int index = 0;
        int currentIndex = getCurrentIndex();
        MediaQueueItem[] queueItems = new MediaQueueItem[itemIds.length - currentIndex];
        for (int itemIndex = currentIndex; itemIndex < itemIds.length; itemIndex++) {
            queueItems[index++] = new MediaQueueItem.Builder(
                    queueItemMap.get(itemIds[itemIndex]))
                    .setAutoplay(playWhenReady)
                    .build();
        }
        if (remoteMediaClient == null) {
            return actionResult("setAutoPlay(): remoteMediaClient is null");
        }
        Log.d(LC, "setAutoPlay() updating items to playWhenReady: " + playWhenReady);
        remoteMediaClient.queueUpdateItems(
                queueItems,
                null
        ).setResultCallback(r -> {
            logResult("setAutoPlay() queueUpdateItems", r);
            if (r.getStatus().isSuccess()) {
                result.complete(null);
            } else {
                result.complete(Optional.of("Could not set autoplay to " + playWhenReady
                        + ": " + getResultString(r)));
            }
        });
        remoteMediaClientResultCallbackTimeout(result);
        return result;
    }

    private CompletableFuture<Optional<String>> dePreload() {
        Log.d(LC, "dePreload()");
        return sequentialize(e -> {
            Log.d(LC, "dePreload() start");
            List<PlaybackEntry> preloadedEntries = getPreloadedEntries(Integer.MAX_VALUE);
            int numToRemove = preloadedEntries.size() - NUM_TO_PRELOAD;
            if (numToRemove <= 0) {
                return actionResult(null);
            }
            List<PlaybackEntry> playlistEntriesToRemove = new LinkedList<>();
            List<PlaybackEntry> queueEntriesToRemove = new LinkedList<>();
            List<Integer> itemIndexesToRemove = new LinkedList<>();
            for (int i = 0; i < preloadedEntries.size(); i++) {
                if (itemIndexesToRemove.size() >= numToRemove) {
                    break;
                }
                PlaybackEntry entry = preloadedEntries.get(preloadedEntries.size() - 1 - i);
                if (PlaybackEntry.USER_TYPE_PLAYLIST.equals(entry.playbackType)) {
                    playlistEntriesToRemove.add(entry);
                } else if (PlaybackEntry.USER_TYPE_QUEUE.equals(entry.playbackType)) {
                    queueEntriesToRemove.add(entry);
                } else {
                    // There is an external (or unknown) entry
                    break;
                }
                itemIndexesToRemove.add(mediaQueue.getItemCount() - i);
            }
            if (itemIndexesToRemove.isEmpty()) {
                return actionResult(null);
            }
            if (remoteMediaClient == null) {
                return actionResult("dePreload(): remoteMediaClient is null");
            }
            Log.d(LC, "Removing " + itemIndexesToRemove.size() + " superfluous preload items: "
                    + itemIndexesToRemove.toString());
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            remoteMediaClient.queueRemoveItems(
                    itemIndexesToRemove.stream().mapToInt(i -> i).toArray(),
                    null
            ).setResultCallback(r -> {
                logResult("dePreload queueRemoveItems", r);
                if (r.getStatus().isSuccess()) {
                    audioPlayerCallback.dePreload(queueEntriesToRemove, playlistEntriesToRemove);
                    result.complete(Optional.empty());
                } else {
                    result.complete(
                            Optional.of("Could not dePreload items: "
                                    + getResultString(r))
                    );
                }
            });
            remoteMediaClientResultCallbackTimeout(result);
            return result;
        });
    }

    private CompletableFuture<Optional<String>> resetQueue() {
        final CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        Log.d(LC, "resetQueue, clearing queue");
        if (remoteMediaClient == null) {
            return actionResult("resetQueue(): remoteMediaClient is null");
        }
        remoteMediaClient.queueRemoveItems(
                mediaQueue.getItemIds(),
                null
        ).setResultCallback(r -> {
            logResult("queueRemoveItems", r);
            if (r.getStatus().isSuccess()) {
                result.complete(Optional.empty());
            } else {
                result.complete(
                        Optional.of("Could not reset queue by removing all items: "
                                + getResultString(r))
                );
            }
        });
        remoteMediaClientResultCallbackTimeout(result);
        return result;
    }

    private void logCurrentQueue() {
        int[] itemIds = mediaQueue.getItemIds();
        MediaQueueItem currentItem = remoteMediaClient == null ? null :
                remoteMediaClient.getCurrentItem();
        int currentItemId = currentItem == null ? 0 : currentItem.getItemId();
        logCurrentQueue(itemIds, currentItemId);
    }

    private void logCurrentQueue(int[] itemIds, int currentItemId) {
        StringBuilder sb = new StringBuilder("currentQueue:\n");
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
            String itemString = queueItem == null ? "null" :
                    queueItem.getMedia().getMetadata().getString(Meta.METADATA_KEY_TITLE);
            if (itemId == currentItemId) {
                sb.append("* ");
            }
            sb
                    .append("[")
                    .append(itemIndex)
                    .append("] ")
                    .append(itemId)
                    .append(" ")
                    .append(itemString)
                    .append("\n");
        }
        Log.d(LC, sb.toString());
    }

    private CompletableFuture<Optional<String>> setQueue(List<PlaybackEntry> playbackEntries,
                                                         long position,
                                                         boolean playWhenReady) {
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        int startIndex = 0;
        int repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        Log.d(LC, "setQueue, creating a new queue");
        MediaQueueItem[] queueItems = buildMediaQueueItems(playbackEntries, playWhenReady);
        if (remoteMediaClient == null) {
            return actionResult("setQueue(): remoteMediaClient is null");
        }
        remoteMediaClient.queueLoad(
                queueItems,
                startIndex,
                repeatMode,
                position,
                null
        ).setResultCallback(r -> {
            logResult("queueLoad", r);
            if (!r.getStatus().isSuccess()) {
                result.complete(
                        Optional.of("Could not load queue: " + getResultString(r))
                );
            } else {
                result.complete(Optional.empty());
            }
        });
        remoteMediaClientResultCallbackTimeout(result);
        return result;
    }

    private MediaQueueItem[] buildMediaQueueItems(List<PlaybackEntry> playbackEntries,
                                                  boolean playWhenReady) {
        int index = 0;
        MediaQueueItem[] queueItems = new MediaQueueItem[playbackEntries.size()];
        for (PlaybackEntry playbackEntry: playbackEntries) {
            MediaQueueItem queueItem = buildMediaQueueItem(playbackEntry, playWhenReady);
            if (queueItem == null) {
                Log.w(LC, "Could not add playbackEntry to queue: " + playbackEntry.toString());
                continue;
            }
            queueItems[index++] = queueItem;
        }
        StringBuilder sb = new StringBuilder("newQueueItems:");
        for (MediaQueueItem mediaQueueItem: queueItems) {
            sb.append("\n").append(mediaQueueItem.getMedia().getMetadata().getString(
                    Meta.METADATA_KEY_TITLE
            ));
        }
        Log.d(LC, sb.toString());
        return queueItems;
    }

    private MediaQueueItem buildMediaQueueItem(PlaybackEntry playbackEntry, boolean playWhenReady) {
        MediaInfo mediaInfo = buildMediaInfo(playbackEntry);
        if (mediaInfo == null) {
            return null;
        }
        long position = 0;
        return new MediaQueueItem.Builder(mediaInfo)
                .setAutoplay(playWhenReady)
                .setStartTime(position)
                .build();
    }

    private MediaInfo buildMediaInfo(PlaybackEntry playbackEntry) {
        MediaMetadataCompat meta = playbackEntry.meta;
        String URL = musicLibraryService.getAudioURL(playbackEntry.entryID);
        if (URL == null) {
            Log.e(LC, "Could not get URL for " + meta.getString(Meta.METADATA_KEY_TITLE));
            return null;
        }
        MediaMetadata castMeta = Meta.from(meta, playbackEntry.playbackType);
        long duration = meta.getLong(Meta.METADATA_KEY_DURATION);
        String contentType = meta.getString(Meta.METADATA_KEY_CONTENT_TYPE);
        return new MediaInfo.Builder(URL)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(castMeta)
                .setStreamDuration(duration)
                .build();
    }

    @Override
    CompletableFuture<Optional<String>> next() {
        Log.d(LC, "next()");
        return sequentialize(e -> {
            Log.d(LC, "next() start");
            if (remoteMediaClient == null) {
                return actionResult("next(): remoteMediaClient is null");
            }
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            remoteMediaClient.queueNext(
                    null
            ).setResultCallback(r -> {
                logResult("next", r);
                if (r.getStatus().isSuccess()) {
                    result.complete(Optional.empty());
                } else {
                    result.complete(
                            Optional.of("Could not go to next: " + getResultString(r))
                    );
                }
            });
            remoteMediaClientResultCallbackTimeout(result);
            return result;
        }).thenCompose(
                e -> checkPreload()
        ).thenApply(e -> {
            audioPlayerCallback.onPreloadChanged();
            return e;
        });
    }

    @Override
    CompletableFuture<Optional<String>> skipItems(int offset) {
        // TODO: Implement
        return actionResult("Not implemented");
    }

    @Override
    CompletableFuture<Optional<String>> previous() {
        Log.d(LC, "previous()");
        return sequentialize(e -> {
            Log.d(LC, "previous() start");
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            // TODO: Implement
            Log.e(LC, "previous not implemented");
            result.complete(Optional.of("Not implemented"));
            return result;
        });
    }

    private void logResult(String action, RemoteMediaClient.MediaChannelResult result) {
        Log.d(LC, action + " smooth? " + result.getStatus().isSuccess());
        if (!result.getStatus().isSuccess()) {
            String code = CastStatusCodes.getStatusCodeString(result.getStatus().getStatusCode());
            String msg = code + ": "
                    + result.getStatus().toString() + " "
                    + result.getStatus().getStatusMessage();
            Log.e(LC, msg);
            if (remoteMediaClient != null) {
                Log.e(LC, "state: " + getStateString(remoteMediaClient.getPlayerState()));
            }
            logCurrentQueue();
        }
    }

    private String getResultString(RemoteMediaClient.MediaChannelResult result) {
        return CastStatusCodes.getStatusCodeString(result.getStatus().getStatusCode())
                + ": " + result.getStatus().getStatusMessage();
    }

    private CompletableFuture<Optional<String>> playerAction(PlayerAction action) {
        if (remoteMediaClient == null) {
            return actionResult("playerAction(" + action.name()
                    + "): remoteMediaClient is null");
        }
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        Log.d(LC, "playerAction: " + action.name() + " in state: "
                + getStateString(remoteMediaClient.getPlayerState()));
        PendingResult<RemoteMediaClient.MediaChannelResult> pendingResult;
        switch (action) {
            case PLAY:
                pendingResult = remoteMediaClient.play();
                break;
            case PAUSE:
                pendingResult = remoteMediaClient.pause();
                break;
            case STOP:
                pendingResult = remoteMediaClient.stop();
                break;
            default:
                Log.d(LC, "Unknown PlayerAction: " + action.name());
                return actionResult("Unknown player action: " + action.name());
        }
        pendingResult.setResultCallback(r -> {
            logResult(action.name(), r);
            if (r.getStatus().isSuccess()) {
                result.complete(Optional.empty());
            } else {
                result.complete(Optional.of("Could not perform action (" + action.name()
                        + "): " + getResultString(r)));
            }
        });
        remoteMediaClientResultCallbackTimeout(result);
        return result;
    }

    private class MediaQueueCallback extends MediaQueue.Callback {
        @Override
        public void itemsInsertedInRange(int index, int count) {
            Log.d(LC, "MediaQueue: itemsInsertedInRange(index: " + index
                    + ", count: " + count + ")");
            for (int itemIndex = index; itemIndex < index + count; itemIndex++) {
                MediaQueueItem queueItem = mediaQueue.getItemAtIndex(itemIndex);
                if (queueItem != null) {
                    queueItemMap.put(mediaQueue.itemIdAtIndex(itemIndex), queueItem);
                }
            }
            cleanQueueItemMap();
            logCurrentQueue();
            dePreload();
        }

        @Override
        public void itemsReloaded() {
            Log.d(LC, "MediaQueue: itemsReloaded()");
            for (int itemIndex = 0; itemIndex < mediaQueue.getItemCount(); itemIndex++) {
                MediaQueueItem queueItem = mediaQueue.getItemAtIndex(itemIndex);
                if (queueItem != null) {
                    queueItemMap.put(mediaQueue.itemIdAtIndex(itemIndex), queueItem);
                }
            }
            cleanQueueItemMap();
            logCurrentQueue();
            dePreload();
        }

        @Override
        public void itemsRemovedAtIndexes(int[] indexes) {
            Log.d(LC, "MediaQueue: itemsRemovedAtIndexes(" + Arrays.toString(indexes) + ")");
            for (int itemIndex: indexes) {
                queueItemMap.remove(mediaQueue.itemIdAtIndex(itemIndex));
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
                    queueItemMap.put(mediaQueue.itemIdAtIndex(itemIndex), queueItem);
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
                int key = queueItemMap.keyAt(i);
                if (!currentItemIds.contains(key)) {
                    queueItemMap.remove(key);
                }
            }
        }

        @Override
        public void mediaQueueChanged() {
            Log.d(LC, "MediaQueue: mediaQueueChanged()");
            int[] itemIds = mediaQueue.getItemIds();
            if (itemIds.length > 0) { // Before disconnect the itemIds gets wiped.
                lastItemIds = mediaQueue.getItemIds();
                lastQueueItemMap = queueItemMap.clone();
            }
            audioPlayerCallback.onPreloadChanged();
        }

        @Override
        public void mediaQueueWillChange() {
            Log.d(LC, "MediaQueue: mediaQueueWillChange()");
        }
    }

    private class RemoteMediaClientCallback extends RemoteMediaClient.Callback {
        @Override
        public void onStatusUpdated() {
            if (remoteMediaClient == null) {
                return;
            }
            int newPlayerState = remoteMediaClient.getPlayerState();
            Log.d(LC, "onStatusUpdated state:" + getStateString(newPlayerState));
            if (newPlayerState == playerState) {
                return;
            }
            playerState = newPlayerState;
            switch (newPlayerState) {
                case MediaStatus.PLAYER_STATE_IDLE:
                    int newIdleReason = remoteMediaClient.getIdleReason();
                    if (newIdleReason == idleReason) {
                        return;
                    }
                    idleReason = newIdleReason;
                    switch (newIdleReason) {
                        case MediaStatus.IDLE_REASON_FINISHED:
                            break;
                        case MediaStatus.IDLE_REASON_CANCELED:
                            audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
                            break;
                        case MediaStatus.IDLE_REASON_ERROR:
                            break;
                        case MediaStatus.IDLE_REASON_INTERRUPTED:
                            break;
                        default:
                            break;
                    }
                    break;
                case MediaStatus.PLAYER_STATE_BUFFERING:
                    audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
                    break;
                case MediaStatus.PLAYER_STATE_PAUSED:
                    audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
                    break;
                case MediaStatus.PLAYER_STATE_PLAYING:
                    audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PLAYING);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onMetadataUpdated() {
            Log.d(LC, "onMetadataUpdated");
            if (remoteMediaClient == null) {
                return;
            }
            MediaInfo mediaInfo = remoteMediaClient.getMediaInfo();
            if (mediaInfo == null) {
                return;
            }
            MediaMetadata castMeta = mediaInfo.getMetadata();
            audioPlayerCallback.onMetaChanged(EntryID.from(castMeta));
        }

        @Override
        public void onQueueStatusUpdated() {
            Log.d(LC, "onQueueStatusUpdated");
            audioPlayerCallback.onPreloadChanged();
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

    private String getStateString(int state) {
        switch (state) {
            case MediaStatus.PLAYER_STATE_IDLE:
                int idleReason = remoteMediaClient.getIdleReason();
                switch (idleReason) {
                    case MediaStatus.IDLE_REASON_FINISHED:
                        return "IDLE_FINISHED";
                    case MediaStatus.IDLE_REASON_CANCELED:
                        return "IDLE_CANCELED";
                    case MediaStatus.IDLE_REASON_ERROR:
                        return "IDLE_ERROR";
                    case MediaStatus.IDLE_REASON_INTERRUPTED:
                        return "IDLE_INTERRUPTED";
                    default:
                        return "IDLE_NONE";
                }
            case MediaStatus.PLAYER_STATE_BUFFERING:
                return "BUFFERING";
            case MediaStatus.PLAYER_STATE_PAUSED:
                return "PAUSED";
            case MediaStatus.PLAYER_STATE_PLAYING:
                return "PLAYING";
            default:
                return "UNKNOWN";
        }
    }
}
