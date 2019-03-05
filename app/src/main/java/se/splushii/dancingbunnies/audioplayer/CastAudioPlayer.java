package se.splushii.dancingbunnies.audioplayer;

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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class CastAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);

    private final MusicLibraryService musicLibraryService;
    private final Callback callback;
    private final RemoteMediaClient remoteMediaClient;
    private final MediaQueue mediaQueue;
    private final SparseArray<MediaQueueItem> queueItemMap;
    private int playerState;
    private int idleReason;
    private boolean playWhenReady = false;
    private long lastPos = 0L;
    private int[] lastItemIds = new int[0];
    private int lastCurrentItemId;
    private SparseArray<MediaQueueItem> lastQueueItemMap;

    private enum PlayerAction {
        PLAY,
        PAUSE,
        STOP
    }

    CastAudioPlayer(Callback callback,
                    MusicLibraryService musicLibraryService,
                    CastSession castSession) {
        this.callback = callback;
        this.musicLibraryService = musicLibraryService;
        queueItemMap = new SparseArray<>();
        remoteMediaClient = castSession.getRemoteMediaClient();
        RemoteMediaClientCallback remoteMediaClientCallback = new RemoteMediaClientCallback();
        remoteMediaClient.registerCallback(remoteMediaClientCallback);
        mediaQueue = remoteMediaClient.getMediaQueue();
        MediaQueueCallback mediaQueueCallback = new MediaQueueCallback();
        mediaQueue.registerCallback(mediaQueueCallback);
    }

    @Override
    public AudioPlayerState getLastState() {
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
            PlaybackEntry entry = new PlaybackEntry(new Meta(queueItem.getMedia().getMetadata()));
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
        if (remoteMediaClient != null) {
            lastPos = remoteMediaClient.getApproximateStreamPosition();
        }
        return lastPos;
    }

    @Override
    public CompletableFuture<Void> play() {
        if (remoteMediaClient == null) {
            return Util.futureResult("play(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPlaying()) {
            return Util.futureResult(null);
        }
        Log.d(LC, "play()");
        playWhenReady = true;
        return playerAction(PlayerAction.PLAY)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    @Override
    public CompletableFuture<Void> pause() {
        if (remoteMediaClient == null) {
            return Util.futureResult("pause(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPaused()) {
            return Util.futureResult(null);
        }
        Log.d(LC, "pause()");
        playWhenReady = false;
        return playerAction(PlayerAction.PAUSE)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (remoteMediaClient == null) {
            return Util.futureResult("stop(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPaused()) {
            return Util.futureResult(null);
        }
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
        req.setResultCallback(r -> handleResultCallback(result, r, desc,
                errorMsg + ": " + getResultString(r)));
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
        if (remoteMediaClient == null) {
            return Util.futureResult("seekTo(): remoteMediaClient is null");
        }
        Log.d(LC, "playerAction: seekTo(" + pos + ") in state: " + getStateString(
                remoteMediaClient.getPlayerState(),
                remoteMediaClient.getIdleReason()
        ));
        return handleMediaClientRequest("seek", "Could not seek", remoteMediaClient.seek(pos));
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

    @Override
    public int getNumPreloaded() {
        int currentItemIndex = getCurrentIndex();
        if (currentItemIndex == -1) {
            return 0;
        }
        return mediaQueue.getItemCount() - currentItemIndex - 1;
    }

    private PlaybackEntry mediaQueueItem2PlaybackEntry(MediaQueueItem mediaQueueItem) {
        if (mediaQueueItem == null) {
            return null;
        }
        MediaMetadata castMeta = mediaQueueItem.getMedia().getMetadata();
        return new PlaybackEntry(new Meta(castMeta));
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
                    Meta.UNKNOWN_ENTRY.toCastMediaMetadata(PlaybackEntry.USER_TYPE_EXTERNAL) :
                    mediaQueueItem.getMedia().getMetadata();
            entries.add(castMeta);
        }
        return entries;
    }

    private List<PlaybackEntry> getEntriesOfType(String type, int maxNum) {
        LinkedList<PlaybackEntry> entries = new LinkedList<>();
        for (MediaMetadata castMeta: getEntriesMeta(true)) {
            if (entries.size() > maxNum) {
                break;
            }
            PlaybackEntry playbackEntry = new PlaybackEntry(new Meta(castMeta));
            playbackEntry.setPreloaded(true);
            if (playbackEntry.playbackType.equals(type)) {
                entries.add(playbackEntry);
            }
        }
        return entries;
    }

    @Override
    public List<PlaybackEntry> getQueueEntries(int maxNum) {
        return getEntriesOfType(PlaybackEntry.USER_TYPE_QUEUE, maxNum);
    }

    @Override
    public List<PlaybackEntry> getPlaylistEntries(int maxNum) {
        return getEntriesOfType(PlaybackEntry.USER_TYPE_PLAYLIST, maxNum);
    }

    @Override
    public PlaybackEntry getCurrentEntry() {
        int itemIndex = getCurrentIndex();
        return getEntry(itemIndex);
    }

    @Override
    public PlaybackEntry getQueueEntry(int queuePosition) {
        int itemIndex = getCurrentIndex() + 1 + queuePosition;
        return getEntry(itemIndex);
    }

    @Override
    public PlaybackEntry getPlaylistEntry(int playlistPosition) {
        int itemIndex = getCurrentIndex() + 1 + getNumPlaylistEntries() + playlistPosition;
        return getEntry(itemIndex);
    }

    private PlaybackEntry getEntry(int mediaQueueItemIndex) {
        int mediaQueueItemId = mediaQueue.itemIdAtIndex(mediaQueueItemIndex);
        return mediaQueueItem2PlaybackEntry(queueItemMap.get(mediaQueueItemId));
    }

    @Override
    public int getNumPlaylistEntries() {
        return getPlaylistEntries(Integer.MAX_VALUE).size();
    }

    @Override
    public int getNumQueueEntries() {
        return getQueueEntries(Integer.MAX_VALUE).size();
    }

    public CompletableFuture<Void> dePreload(int numQueueEntriesToDepreload,
                                             int queueOffset,
                                             int numPlaylistEntriesToDepreload,
                                             int playlistOffset) {
        int queueEntriesToDepreloadItemIds[] = getItemIds(numQueueEntriesToDepreload, queueOffset);
        int playlistEntriesToDepreloadItemIds[] = getItemIds(
                numPlaylistEntriesToDepreload,
                getNumQueueEntries() + playlistOffset
        );
        logCurrentQueue();
        Log.d(LC, "dePreload()"
                + "\nQueue entries to de-preload: "
                + queueEntriesToDepreloadItemIds.length + " items "
                + Arrays.toString(queueEntriesToDepreloadItemIds)
                + "\nPlaylist entries to de-preload: "
                + playlistEntriesToDepreloadItemIds.length + " items "
                + Arrays.toString(playlistEntriesToDepreloadItemIds));
        int[] itemIdsToRemove = IntStream.concat(
                Arrays.stream(queueEntriesToDepreloadItemIds),
                Arrays.stream(playlistEntriesToDepreloadItemIds)
        ).toArray();
        if (itemIdsToRemove.length <= 0) {
            return Util.futureResult(null);
        }
        return handleMediaClientRequest(
                "dePreload() queueRemoveItems superfluous entries",
                "Could not depreload superfluous items",
                remoteMediaClient.queueRemoveItems(itemIdsToRemove, null)
        );
    }

    @Override
    public CompletableFuture<Void> destroy() {
        return Util.futureResult("Not implemented");
    }

    @Override
    public CompletableFuture<Void> queue(List<PlaybackEntry> entries, int offset) {
        Log.d(LC, "queue()");
        if (!remoteMediaClient.hasMediaSession()) {
            return setQueue(entries, 0);
        }

        MediaQueueItem[] queueItemsToQueue = getQueueItemsToQueue(entries);
        int queueInsertBeforeItemId = getInsertBeforeItemId(offset);

        logCurrentQueue();
        Log.d(LC, "queue()"
                + "\nNew entries to queue: " + queueItemsToQueue.length
                + " before "+ queueInsertBeforeItemId);

        return handleMediaClientRequest(
                "queue() queueInsertItems",
                "Could not insert queue items.",
                remoteMediaClient.queueInsertItems(
                        queueItemsToQueue,
                        queueInsertBeforeItemId,
                        null
                )
        );
    }

    private int getInsertBeforeItemId(int offset) {
        int currentIndex = getCurrentIndex();
        int queueInsertBeforeIndex = currentIndex + 1 + offset;
        return mediaQueue.itemIdAtIndex(queueInsertBeforeIndex);
    }

    private MediaQueueItem[] getQueueItemsToQueue(List<PlaybackEntry> entriesToQueue) {
        if (entriesToQueue.isEmpty()) {
            return new MediaQueueItem[0];
        }
        return buildMediaQueueItems(entriesToQueue, playWhenReady);
    }

    private int[] getItemIds(int num, int offset) {
        int startIndex = getCurrentIndex() + 1 + offset;
        int itemIds[] = new int[num];
        for (int i = 0; i < num; i++) {
            int itemId = mediaQueue.itemIdAtIndex( startIndex + i);
            itemIds[i] = itemId;
        }
        return itemIds;
    }

    @Override
    public CompletableFuture<Void> deQueue(List<Integer> positions) {
        List<Integer> queueItemIdsToRemoveFromPlayer = new LinkedList<>();
        for (long queuePosition: positions) {
            int mediaQueueItemIndex = getCurrentIndex() + (int) queuePosition + 1;
            int mediaQueueItemId = mediaQueue.itemIdAtIndex(mediaQueueItemIndex);
            queueItemIdsToRemoveFromPlayer.add(mediaQueueItemId);
        }

        logCurrentQueue();
        Log.d(LC, "deQueue()"
                + "\nqueueItemIdsToRemoveFromPlayer: "
                + queueItemIdsToRemoveFromPlayer.size()
                + ": " + queueItemIdsToRemoveFromPlayer);

        return queueItemIdsToRemoveFromPlayer.size() <= 0 ? Util.futureResult(null) :
                handleMediaClientRequest(
                        "deQueue() queueRemoveItems ",
                        "Could not remove queue items",
                        remoteMediaClient.queueRemoveItems(
                                queueItemIdsToRemoveFromPlayer.stream().mapToInt(i -> i).toArray(),
                            null
                        )
                );
    }

    private void remoteMediaClientResultCallbackTimeout(
            CompletableFuture<Void> result) {
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

    @Override
    public CompletableFuture<Void> preload(List<PlaybackEntry> entries) {
        Log.d(LC, "preload()");
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        if (remoteMediaClient == null) {
            return Util.futureResult("remoteMediaClient is null");
        }
        if (!remoteMediaClient.hasMediaSession()) {
            return setQueue(entries, 0);
        }
        MediaQueueItem[] items = buildMediaQueueItems(entries, playWhenReady);
        logCurrentQueue();
        Log.d(LC, "preload appending " + items.length + " items.");
        return handleMediaClientRequest(
                "preload queueInsertItems",
                "Could not insert queue items",
                remoteMediaClient.queueInsertItems(items, MediaQueueItem.INVALID_ITEM_ID, null)
        );
    }

    private CompletableFuture<Void> setAutoPlay(boolean playWhenReady) {
        int[] itemIds = mediaQueue.getItemIds();
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
        if (remoteMediaClient == null) {
            return Util.futureResult("setAutoPlay(): remoteMediaClient is null");
        }
        Log.d(LC, "setAutoPlay() updating items to playWhenReady: " + playWhenReady);
        return handleMediaClientRequest(
                "setAutoPlay() queueUpdateItems",
                "Could not set autoplay to " + playWhenReady,
                remoteMediaClient.queueUpdateItems(queueItems.toArray(new MediaQueueItem[0]), null)
        );
    }

    private void logCurrentQueue() {
        int[] itemIds = mediaQueue.getItemIds();
        MediaQueueItem currentItem = remoteMediaClient == null ? null :
                remoteMediaClient.getCurrentItem();
        int currentItemId = currentItem == null ? 0 : currentItem.getItemId();
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
            String itemString = queueItem == null ? "null" :
                    queueItem.getMedia().getMetadata().getString(Meta.METADATA_KEY_TITLE);
            String type = queueItem == null ? "?" :
                    queueItem.getMedia().getMetadata().getString(Meta.METADATA_KEY_PLAYBACK_TYPE);
            if (itemId == currentItemId) {
                sb.append("* ");
            }
            sb
                    .append(type)
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

    private CompletableFuture<Void> setQueue(List<PlaybackEntry> playbackEntries,
                                             long seekPosition) {
        if (playbackEntries == null || playbackEntries.isEmpty()) {
            return Util.futureResult(null);
        }
        int startIndex = 0;
        int repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        Log.d(LC, "setQueue, creating a new queue");
        MediaQueueItem[] queueItems = buildMediaQueueItems(playbackEntries, playWhenReady);
        if (remoteMediaClient == null) {
            return Util.futureResult("setQueue(): remoteMediaClient is null");
        }
        return handleMediaClientRequest(
                "queueLoad",
                "Could not load queue",
                remoteMediaClient.queueLoad(queueItems, startIndex, repeatMode, seekPosition, null)
        );
    }

    private MediaQueueItem[] buildMediaQueueItems(List<PlaybackEntry> playbackEntries,
                                                  boolean playWhenReady) {
        List<MediaQueueItem> queueItems = new LinkedList<>();
        for (PlaybackEntry playbackEntry: playbackEntries) {
            MediaQueueItem queueItem = buildMediaQueueItem(playbackEntry, playWhenReady);
            if (queueItem == null) {
                Log.e(LC, "Could not add playbackEntry to queue: " + playbackEntry.toString());
                continue;
            }
            queueItems.add(queueItem);
        }
        StringBuilder sb = new StringBuilder("newQueueItems:");
        for (MediaQueueItem mediaQueueItem: queueItems) {
            sb.append("\n").append(mediaQueueItem.getMedia().getMetadata().getString(
                    Meta.METADATA_KEY_TITLE
            ));
        }
        Log.d(LC, sb.toString());
        return queueItems.toArray(new MediaQueueItem[0]);
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
        Meta meta = musicLibraryService.getSongMetaData(playbackEntry.entryID);
        String URL = musicLibraryService.getAudioURL(playbackEntry.entryID);
        if (URL == null) {
            Log.e(LC, "Could not get URL for " + meta.getString(Meta.METADATA_KEY_TITLE));
            return null;
        }
        MediaMetadata castMeta = meta.toCastMediaMetadata(playbackEntry.playbackType);
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
    public CompletableFuture<Void> next() {
        Log.d(LC, "next()");
        if (remoteMediaClient == null) {
            return Util.futureResult("next(): remoteMediaClient is null");
        }
        return handleMediaClientRequest(
                "next",
                "Could not go to next",
                remoteMediaClient.queueNext(null)
        );
    }


    @Override
    public CompletableFuture<Void> previous() {
        Log.d(LC, "previous()");
        // TODO: Implement
        Log.e(LC, "previous not implemented");
        return Util.futureResult("Not implemented");
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
                Log.e(LC, "state: " + getStateString(
                        remoteMediaClient.getPlayerState(),
                        remoteMediaClient.getIdleReason()
                ));
            }
            logCurrentQueue();
        }
    }

    private String getResultString(RemoteMediaClient.MediaChannelResult result) {
        return CastStatusCodes.getStatusCodeString(result.getStatus().getStatusCode())
                + ": " + result.getStatus().getStatusMessage();
    }

    private CompletableFuture<Void> playerAction(PlayerAction action) {
        if (remoteMediaClient == null) {
            return Util.futureResult("playerAction(" + action.name()
                    + "): remoteMediaClient is null");
        }
        Log.d(LC, "playerAction: " + action.name() + " in state: " + getStateString(
                remoteMediaClient.getPlayerState(),
                remoteMediaClient.getIdleReason()
        ));
        PendingResult<RemoteMediaClient.MediaChannelResult> request;
        switch (action) {
            case PLAY:
                request = remoteMediaClient.play();
                break;
            case PAUSE:
                request = remoteMediaClient.pause();
                break;
            case STOP:
                request = remoteMediaClient.stop();
                break;
            default:
                Log.d(LC, "Unknown PlayerAction: " + action.name());
                return Util.futureResult("Unknown player action: " + action.name());
        }
        return handleMediaClientRequest(
                action.name(),
                "Could not perform action (" + action.name() + ")",
                request
        );
    }

    private class MediaQueueCallback extends MediaQueue.Callback {
        private int[] preChangeItemIds;

        @Override
        public void itemsInsertedInRange(int index, int count) {
            Log.d(LC, "MediaQueue: itemsInsertedInRange(index: " + index
                    + ", count: " + count + ")");
            for (int itemIndex = index; itemIndex < index + count; itemIndex++) {
                MediaQueueItem queueItem = mediaQueue.getItemAtIndex(itemIndex);
                if (queueItem != null) {
                    int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                    queueItemMap.put(itemId, queueItem);
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
            callback.onPreloadChanged();
        }

        @Override
        public void mediaQueueWillChange() {
            preChangeItemIds = mediaQueue.getItemIds();
            Log.d(LC, "MediaQueue: mediaQueueWillChange()");
        }
    }

    private class RemoteMediaClientCallback extends RemoteMediaClient.Callback {
        @Override
        public void onStatusUpdated() {
            if (remoteMediaClient == null) {
                return;
            }
            int oldPlayerState = playerState;
            int oldIdleReason = idleReason;
            playerState = remoteMediaClient.getPlayerState();
            idleReason = remoteMediaClient.getIdleReason();
            Log.d(LC, "onStatusUpdated from " + getStateString(oldPlayerState, oldIdleReason)
                            + " to " + getStateString(playerState, idleReason));
            switch (playerState) {
                case MediaStatus.PLAYER_STATE_IDLE:
                    switch (idleReason) {
                        case MediaStatus.IDLE_REASON_FINISHED:
                            if (getNumPreloaded() <= 0) {
                                callback.onMetaChanged(EntryID.from(Meta.UNKNOWN_ENTRY));
                                callback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
                            }
                            callback.onSongEnded();
                            break;
                        case MediaStatus.IDLE_REASON_CANCELED:
                            callback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
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
            callback.onMetaChanged(EntryID.from(castMeta));
        }

        @Override
        public void onQueueStatusUpdated() {
            Log.d(LC, "onQueueStatusUpdated");
            callback.onPreloadChanged();
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