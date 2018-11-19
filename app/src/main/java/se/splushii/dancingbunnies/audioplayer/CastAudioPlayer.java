package se.splushii.dancingbunnies.audioplayer;

import android.os.Bundle;
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
import java.util.Collections;
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
    private boolean playWhenReady = false;
    private long lastPos = 0;
    private int[] lastItemIds = new int[0];
    private int lastCurrentItemId;
    private SparseArray<MediaQueueItem> lastQueueItemMap;
    private final Object preloadLock = new Object();

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
        remoteMediaClient = castSession.getRemoteMediaClient();
        remoteMediaClient.registerCallback(remoteMediaClientCallback);
        mediaQueue = remoteMediaClient.getMediaQueue();
        mediaQueue.registerCallback(mediaQueueCallback);
        // TODO: Handle state.history and state.lastPos
        if (!state.entries.isEmpty()) {
            preload(state.entries)
                    .thenCompose((Void v) -> checkPreload());
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
    public CompletableFuture<Void> play() {
        if (remoteMediaClient == null) {
            return actionResult("play(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPlaying()) {
            return actionResult(null);
        }
        Log.d(LC, "play()");
        playWhenReady = true;
        return playerAction(PlayerAction.PLAY)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    @Override
    public CompletableFuture<Void> pause() {
        if (remoteMediaClient == null) {
            return actionResult("pause(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPaused()) {
            return actionResult(null);
        }
        Log.d(LC, "pause()");
        playWhenReady = false;
        return playerAction(PlayerAction.PAUSE)
                .thenCompose(r -> setAutoPlay(playWhenReady));
    }

    @Override
    public CompletableFuture<Void> stop() {
        if (remoteMediaClient == null) {
            return actionResult("stop(): remoteMediaClient is null");
        }
        if (remoteMediaClient.isPaused()) {
            return actionResult(null);
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
            future.completeExceptionally(new AudioPlayerException(error));
        }
    }

    @Override
    public CompletableFuture<Void> seekTo(long pos) {
        Log.d(LC, "seekTo()");
        if (remoteMediaClient == null) {
            return actionResult("seekTo(): remoteMediaClient is null");
        }
        Log.d(LC, "playerAction: seekTo(" + pos + ") in state: "
                + getStateString(remoteMediaClient.getPlayerState()));
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

    private int getNumPreloaded() {
        int currentItemIndex = getCurrentIndex();
        if (currentItemIndex == -1) {
            return 0;
        }
        return mediaQueue.getItemCount() - currentItemIndex;
    }

    private PlaybackEntry mediaQueueItem2PlaybackEntry(MediaQueueItem mediaQueueItem) {
        MediaMetadata castMeta = mediaQueueItem == null ?
                Meta.from(Meta.UNKNOWN_ENTRY, PlaybackEntry.USER_TYPE_EXTERNAL) :
                mediaQueueItem.getMedia().getMetadata();
        return new PlaybackEntry(Meta.from(castMeta));
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
            Bundle extras = new Bundle();
            extras.putString(
                    Meta.METADATA_KEY_PLAYBACK_PRELOADSTATUS,
                    PlaybackEntry.PRELOADSTATUS_PRELOADED
            );
            PlaybackEntry playbackEntry = new PlaybackEntry(Meta.from(castMeta, extras));
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

    private List<Integer> getItemIdsOfType(String type) {
        List<Integer> itemIds = new LinkedList<>();
        for (int itemId: mediaQueue.getItemIds()) {
            if (type.equals(queueItemMap.get(itemId).getMedia().getMetadata().getString(
                    Meta.METADATA_KEY_PLAYBACK_TYPE))) {
                itemIds.add(itemId);
            }
        }
        return itemIds;
    }

    List<Integer> getQueueItemIds() {
        return getItemIdsOfType(PlaybackEntry.USER_TYPE_QUEUE);
    }

    List<Integer> getPlaylistItemIds() {
        return getItemIdsOfType(PlaybackEntry.USER_TYPE_PLAYLIST);
    }

    @Override
    CompletableFuture<Void> queue(List<PlaybackEntry> playbackEntries, int toPosition) {
        if (!remoteMediaClient.hasMediaSession()) {
            return setQueue(playbackEntries, 0);
        }

        List<PlaybackEntry> queueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE);
        List<PlaybackEntry> playlistEntries = getPreloadedPlaylistEntries(Integer.MAX_VALUE);
        int numToDepreload = queueEntries.size() + playlistEntries.size()
                + playbackEntries.size() - (NUM_TO_PRELOAD - 1);

        // Get playlistEntries to de-preload
        int playlistEntriesToDepreloadItemIds[] = getPlaylistItemIdsToDepreload(
                numToDepreload,
                queueEntries.size(),
                playlistEntries.size()
        );
        numToDepreload -= playlistEntriesToDepreloadItemIds.length;

        // Get queueEntries to de-preload
        int queueEntriesToDepreloadItemIds[] = getQueueItemIdsToDepreload(
                numToDepreload,
                toPosition,
                queueEntries.size()
        );
        numToDepreload -= queueEntriesToDepreloadItemIds.length;

        // Fill with entries from controller if needed
        int filledFromController = 0;
        int maxToFill = toPosition == AudioPlayerService.QUEUE_LAST ? NUM_TO_PRELOAD - 1 : toPosition;
        for (; queueEntries.size() + filledFromController < maxToFill; filledFromController++) {
            PlaybackEntry entry = controller.consumeQueueEntry(0);
            if (entry == null) {
                Log.w(LC, "getQueueItemsToQueue(): " +
                        "Could not get enough items from controller.");
                break;
            }
            playbackEntries.add(filledFromController, entry);
        }
        numToDepreload += filledFromController;

        // Get new entries to queue
        MediaQueueItem[] queueItemsToQueue = getQueueItemsToQueue(
                numToDepreload,
                toPosition,
                playbackEntries
        );
        int queueInsertBeforeItemId = getInsertBeforeItemId(toPosition, queueEntries.size());

        // Get new entries to de-preload
        List<PlaybackEntry> entriesToDePreload = playbackEntries.subList(
                queueItemsToQueue.length,
                playbackEntries.size()
        );
        int targetQueueSize = queueEntries.size()
                - queueEntriesToDepreloadItemIds.length
                + queueItemsToQueue.length;
        int entriesToDepreloadOffset = getDepreloadOffset(toPosition, filledFromController, targetQueueSize);

        logCurrentQueue();
        Log.d(LC, "queue()"
                + "\nQueue entries to de-preload: "
                + queueEntriesToDepreloadItemIds.length + " items "
                + Arrays.toString(queueEntriesToDepreloadItemIds)
                + "\nPlaylist entries to de-preload: "
                + playlistEntriesToDepreloadItemIds.length + " items "
                + Arrays.toString(playlistEntriesToDepreloadItemIds)
                + "\nFilled from controller: " + filledFromController
                + "\nNew entries to queue: " + queueItemsToQueue.length
                + " before "+ queueInsertBeforeItemId
                + "\nNew entries to de-preload: " + entriesToDePreload.size()
                + " at " + entriesToDepreloadOffset);

        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        if (queueItemsToQueue.length > 0) {
            result = result.thenCompose(e -> handleMediaClientRequest(
                    "queue() queueInsertItems",
                    "Could not insert queue items.",
                    remoteMediaClient.queueInsertItems(
                            queueItemsToQueue,
                            queueInsertBeforeItemId,
                            null
                    )
            ));
        }

        if (queueEntriesToDepreloadItemIds.length > 0 || playlistEntriesToDepreloadItemIds.length > 0) {
            List<PlaybackEntry> playlistEntriesToDepreload = getPlaybackEntries(playlistEntriesToDepreloadItemIds);
            List<PlaybackEntry> queueEntriesToDepreload = getPlaybackEntries(queueEntriesToDepreloadItemIds);
            int[] itemIdsToRemove = IntStream.concat(
                    Arrays.stream(queueEntriesToDepreloadItemIds),
                    Arrays.stream(playlistEntriesToDepreloadItemIds)
            ).toArray();
            result = result.thenCompose(r -> handleMediaClientRequest(
                    "queue() queueRemoveItems superfluous entries",
                    "Could not depreload superfluous items",
                    remoteMediaClient.queueRemoveItems(itemIdsToRemove, null)
            )).thenRun(() -> {
                controller.dePreloadQueueEntries(queueEntriesToDepreload, 0);
                controller.dePreloadPlaylistEntries(playlistEntriesToDepreload);
            });
        }

        if (entriesToDePreload.size() > 0) {
            result.thenRun(() ->
                    controller.dePreloadQueueEntries(entriesToDePreload, entriesToDepreloadOffset)
            );
        }
        return result;
    }

    private int getDepreloadOffset(int toPosition, int filledFromController, int numPlayerQueueEntries) {
        if (toPosition == AudioPlayerService.QUEUE_LAST) {
            return AudioPlayerService.QUEUE_LAST;
        }
        return toPosition < numPlayerQueueEntries + filledFromController ? 0 :
                toPosition - numPlayerQueueEntries - filledFromController;
    }

    private int getInsertBeforeItemId(int toPosition, int numQueueEntries) {
        int currentIndex = getCurrentIndex();
        int queueInsertBeforeIndex = toPosition == AudioPlayerService.QUEUE_LAST ?
                currentIndex + 1 + numQueueEntries: currentIndex + 1 + toPosition;
        return mediaQueue.itemIdAtIndex(queueInsertBeforeIndex);
    }

    private MediaQueueItem[] getQueueItemsToQueue(int numToDepreload,
                                                  int toPosition,
                                                  List<PlaybackEntry> newEntries) {
        if (toPosition >= NUM_TO_PRELOAD - 1
                || newEntries.isEmpty()
                || numToDepreload >= newEntries.size() ) {
            return new MediaQueueItem[0];
        }
        int numToQueue = numToDepreload <= 0 ?
                newEntries.size() : newEntries.size() - numToDepreload;
        List<PlaybackEntry> entriesToQueue = newEntries.subList(0, numToQueue);
        return buildMediaQueueItems(entriesToQueue, playWhenReady);
    }

    private int[] getQueueItemIdsToDepreload(int maxToDepreload,
                                             int toPosition,
                                             int numQueueEntries) {
        if (maxToDepreload <= 0
                || numQueueEntries <= 0
                || toPosition >= NUM_TO_PRELOAD
                || toPosition == AudioPlayerService.QUEUE_LAST) {
            return new int[0];
        }

        int numQueueEntriesToDepreload = Integer.min(NUM_TO_PRELOAD - toPosition, numQueueEntries);
        numQueueEntriesToDepreload = Integer.min(numQueueEntriesToDepreload, maxToDepreload);
        int queueEntriesToDepreloadStartIndex = getCurrentIndex() + 1 + toPosition;
        int queueEntriesToDepreloadItemIds[] = new int[numQueueEntriesToDepreload];
        for (int i = 0; i < numQueueEntriesToDepreload; i++) {
            int itemId = mediaQueue.itemIdAtIndex(
                    queueEntriesToDepreloadStartIndex + numQueueEntries - i - 1
            );
            queueEntriesToDepreloadItemIds[i] = itemId;
        }
        return queueEntriesToDepreloadItemIds;
    }

    private List<PlaybackEntry> getPlaybackEntries(int[] itemIds) {
        LinkedList<PlaybackEntry> playbackEntries = new LinkedList<>();
        for (int itemId: itemIds) {
            playbackEntries.addFirst(mediaQueueItem2PlaybackEntry(queueItemMap.get(itemId)));
        }
        return playbackEntries;
    }

    private int[] getPlaylistItemIdsToDepreload(int maxToDepreload,
                                                int numQueueEntries,
                                                int numPlaylistEntries) {
        if (maxToDepreload <= 0 || numPlaylistEntries <= 0) {
            return new int[0];
        }
        int numPlaylistEntriesToDepreload = Integer.min(maxToDepreload, numPlaylistEntries);
        int playlistEntriesToDepreloadStartIndex = getCurrentIndex() + 1 + numQueueEntries;
        int playlistEntriesToDepreloadItemIds[] = new int[numPlaylistEntriesToDepreload];
        for (int i = 0; i < numPlaylistEntriesToDepreload; i++) {
            int itemId = mediaQueue.itemIdAtIndex(
                    playlistEntriesToDepreloadStartIndex + numPlaylistEntries - i - 1
            );
            playlistEntriesToDepreloadItemIds[i] = itemId;
        }
        return playlistEntriesToDepreloadItemIds;
    }

    @Override
    CompletableFuture<Void> dequeue(long[] positions) {
        Arrays.sort(positions);
        List<PlaybackEntry> queueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE);
        List<Integer> queueItemIdsToRemoveFromPlayer = new LinkedList<>();
        List<Integer> queuePositionsToRemoveFromController = new LinkedList<>();
        for (long queuePosition: positions) {
            if (queuePosition < 0) {
                Log.e(LC, "Can not dequeue negative index: " + queuePosition);
                continue;
            }
            if (queuePosition < queueEntries.size()) {
                int mediaQueueItemIndex = getCurrentIndex() + (int) queuePosition + 1;
                int mediaQueueItemId = mediaQueue.itemIdAtIndex(mediaQueueItemIndex);
                queueItemIdsToRemoveFromPlayer.add(mediaQueueItemId);
            } else {
                queuePositionsToRemoveFromController.add((int) queuePosition - queueEntries.size());
            }
        }

        logCurrentQueue();
        Log.d(LC, "dequeue()"
                + "\nqueueItemIdsToRemoveFromPlayer: "
                + queueItemIdsToRemoveFromPlayer.size()
                + ": " + queueItemIdsToRemoveFromPlayer
                + "\nqueueEntriesToRemoveFromController: "
                + queuePositionsToRemoveFromController.size()
                + ": " + queuePositionsToRemoveFromController);

        CompletableFuture<Void> result = actionResult(null);
        if (queueItemIdsToRemoveFromPlayer.size() > 0) {
            result = result.thenCompose(e -> handleMediaClientRequest(
                    "dequeue() queueRemoveItems ",
                    "Could not remove queue items",
                    remoteMediaClient.queueRemoveItems(
                            queueItemIdsToRemoveFromPlayer.stream().mapToInt(i -> i).toArray(),
                            null
                    )
            ));
        }

        if (!queuePositionsToRemoveFromController.isEmpty()) {
            result = result.thenRun(() -> {
                int len = queuePositionsToRemoveFromController.size();
                for (int i = 0; i < len; i++) {
                    controller.consumeQueueEntry(
                            queuePositionsToRemoveFromController.get(len - 1 - i)
                    );
                }
            });
        }
        return result;
    }

    @Override
    CompletableFuture<Void> moveQueueItems(long[] positions, int toPosition) {
        Arrays.sort(positions);
        List<PlaybackEntry> queueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE);
        List<PlaybackEntry> moveEntries = new LinkedList<>();
        for (long queuePosition: positions) {
            if (queuePosition < 0) {
                Log.e(LC, "Can not dequeue negative index: " + queuePosition);
                continue;
            }
            PlaybackEntry entry;
            if (queuePosition < queueEntries.size()) {
                int mediaQueueItemIndex = getCurrentIndex() + (int) queuePosition + 1;
                int mediaQueueItemId = mediaQueue.itemIdAtIndex(mediaQueueItemIndex);
                entry = mediaQueueItem2PlaybackEntry(queueItemMap.get(mediaQueueItemId));
            } else {
                entry = controller.getQueueEntry((int) queuePosition - queueEntries.size());
            }
            moveEntries.add(entry);
        }
        return dequeue(positions)
                .thenCompose(v -> queue(moveEntries, toPosition));
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
                        new AudioPlayerException("Timeout while waiting for Cast action result")
                );
            }
        });
    }

    public CompletableFuture<Void> checkPreload() {
        Log.d(LC, "checkPreload()");
        synchronized (preloadLock) {
            Log.d(LC, "checkPreload() start");
            logCurrentQueue();
            int numPreloaded = getNumPreloaded();
            if (numPreloaded < NUM_TO_PRELOAD) {
                List<PlaybackEntry> entries =
                        controller.requestPreload(NUM_TO_PRELOAD - numPreloaded);
                return preload(entries);
            }
        }
        return actionResult(null);
    }

    private CompletableFuture<Void> preload(List<PlaybackEntry> entries) {
        if (remoteMediaClient == null) {
            return actionResult("remoteMediaClient is null");
        }
        if (!remoteMediaClient.hasMediaSession()) {
            return setQueue(entries, 0);
        }
        MediaQueueItem[] items = buildMediaQueueItems(entries, playWhenReady);
        Log.d(LC, "preload appending " + items.length + " items.");
        return handleMediaClientRequest(
                "preload queueInsertItems",
                "Could not insert queue items",
                remoteMediaClient.queueInsertItems(items, MediaQueueItem.INVALID_ITEM_ID, null)
        );
    }

    private CompletableFuture<Void> setAutoPlay(boolean playWhenReady) {
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
        return handleMediaClientRequest(
                "setAutoPlay() queueUpdateItems",
                "Could not set autoplay to " + playWhenReady,
                remoteMediaClient.queueUpdateItems(queueItems, null)
        );
    }

    private CompletableFuture<Void> dePreload() {
        Log.d(LC, "dePreload()");
        synchronized (preloadLock) {
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
                itemIndexesToRemove.add(mediaQueue.itemIdAtIndex(mediaQueue.getItemCount() - i - 1));
            }
            if (itemIndexesToRemove.isEmpty()) {
                return actionResult(null);
            }
            if (remoteMediaClient == null) {
                return actionResult("dePreload(): remoteMediaClient is null");
            }
            Log.d(LC, "Removing " + itemIndexesToRemove.size() + " superfluous preload items: "
                    + itemIndexesToRemove.toString());
            return handleMediaClientRequest(
                    "dePreload queueRemoveItems",
                    "Could not dePreload items",
                    remoteMediaClient.queueRemoveItems(
                            itemIndexesToRemove.stream().mapToInt(i -> i).toArray(),
                            null
                    )
            ).thenRun(() -> {
                controller.dePreloadQueueEntries(queueEntriesToRemove, 0);
                controller.dePreloadPlaylistEntries(playlistEntriesToRemove);
            });
        }
    }

    private CompletableFuture<Void> resetQueue() {
        Log.d(LC, "resetQueue, clearing queue");
        if (remoteMediaClient == null) {
            return actionResult("resetQueue(): remoteMediaClient is null");
        }
        return handleMediaClientRequest(
                "queueRemoveItems",
                "Could not reset queue by removing all items",
                remoteMediaClient.queueRemoveItems(mediaQueue.getItemIds(), null)
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
            return actionResult(null);
        }
        int startIndex = 0;
        int repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        Log.d(LC, "setQueue, creating a new queue");
        int numToDepreload = playbackEntries.size() - NUM_TO_PRELOAD;
        MediaQueueItem[] queueItems = getQueueItemsToQueue(
                numToDepreload,
                0,
                playbackEntries
        );
        List<PlaybackEntry> depreloadEntries = queueItems.length < playbackEntries.size() ?
                playbackEntries.subList(queueItems.length, playbackEntries.size())
                : new LinkedList<>();
        if (remoteMediaClient == null) {
            return actionResult("setQueue(): remoteMediaClient is null");
        }
        return handleMediaClientRequest(
                "queueLoad",
                "Could not load queue",
                remoteMediaClient.queueLoad(queueItems, startIndex, repeatMode, seekPosition, null)
        ).thenRun(() -> controller.dePreloadQueueEntries(depreloadEntries, 0));
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
    CompletableFuture<Void> next() {
        Log.d(LC, "next()");
        if (remoteMediaClient == null) {
            return actionResult("next(): remoteMediaClient is null");
        }
        return handleMediaClientRequest(
                "next",
                "Could not go to next",
                remoteMediaClient.queueNext(null))
                .thenCompose(r -> checkPreload())
                .thenRun(controller::onPreloadChanged);
    }

    @Override
    CompletableFuture<Void> skipItems(int offset) {
        Log.d(LC, "skipItems(" + offset + ")");
        if (offset == 0) {
            return actionResult(null);
        }
        if (offset == 1) {
            return next();
        }
        if (offset > 0) {
            // Skip forward
            int numCastQueueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE).size();
            int totalQueueEntries = numCastQueueEntries + controller.getNumQueueEntries();
            if (offset <= totalQueueEntries) {
                // Play queue item at offset now
                if (offset <= numCastQueueEntries) {
                    // Move the queue entry to after current index and skip to next
                    Log.d(LC, "skipItems short queue offset");
                    int currentIndex = getCurrentIndex();
                    int itemIndex = currentIndex + offset;
                    int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                    int beforeItemId = mediaQueue.itemIdAtIndex(currentIndex + 1);
                    Log.d(LC, "skipItems move [" + itemIndex + "] " + itemId + " to index " + (currentIndex + 1));
                    return handleMediaClientRequest(
                            "queueReorderItems(" + itemId + ", " + beforeItemId + ")",
                            "Could not move queue item to next up",
                            remoteMediaClient.queueReorderItems(
                                    new int[]{itemId},
                                    beforeItemId,
                                    null
                            )
                    ).thenCompose(r -> next());
                } else {
                    // Get the queue entry from PlaybackController, queue after current and play
                    Log.d(LC, "skipItems long queue offset");
                    PlaybackEntry playbackEntry = controller.consumeQueueEntry(offset);
                    return queue(
                            Collections.singletonList(playbackEntry),
                            0
                    ).thenCompose(r -> next());
                }
            } else {
                // Skip all playlist items until offset
                int numCastPlaylistEntries = getPreloadedPlaylistEntries(Integer.MAX_VALUE).size();
                if (offset <= totalQueueEntries + numCastPlaylistEntries) {
                    // Dequeue all playlist items up until offset, then move offset to after current
                    // index and skip to next
                    Log.d(LC, "skipItems short playlist offset");
                    int currentIndex = getCurrentIndex();
                    int removeStartIndex = currentIndex + totalQueueEntries + 1;
                    int itemIndex = currentIndex + offset;
                    int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                    int beforeItemId = mediaQueue.itemIdAtIndex(currentIndex + 1);
                    CompletableFuture<Void> result;
                    if (itemIndex > removeStartIndex) {
                        // We have playlist items to dequeue
                        result = removeItems(removeStartIndex, itemIndex - removeStartIndex);
                    } else {
                        result = CompletableFuture.completedFuture(null);
                    }
                    return result.thenCompose(r -> {
                        Log.d(LC, "skipItems move [" + itemIndex + "] " + itemId + " to index " + (currentIndex + 1));
                        return handleMediaClientRequest(
                                "queueReorderItems(" + itemId + ", " + beforeItemId + ")",
                                "Could not move playlist item to next up",
                                remoteMediaClient.queueReorderItems(
                                        new int[]{itemId},
                                        beforeItemId,
                                        null
                                )
                        );
                    }).thenCompose(r -> next());
                } else {
                    // Dequeue all playlist items. Consume and throw away all playlist items up
                    // until offset. Insert and play offset.
                    Log.d(LC, "skipItems long playlist offset");
                    int currentIndex = getCurrentIndex();
                    int removeStartIndex = currentIndex + totalQueueEntries + 1;
                    int consumeOffset = offset - totalQueueEntries - numCastPlaylistEntries;
                    Log.d(LC, "Consuming " + (consumeOffset - 1) + " playlist entries outside "
                            + " of player.");
                    for (int i = 0; i < consumeOffset - 1; i++) {
                        controller.consumePlaylistEntry();
                    }
                    PlaybackEntry playbackEntry = controller.consumePlaylistEntry();
                    return removeItems(removeStartIndex, numCastPlaylistEntries)
                            .thenCompose(r -> queue(Collections.singletonList(playbackEntry), 0))
                            .thenCompose(r -> next());
                }
            }
        } else {
            // Skip backward
            // TODO: implement
            return actionResult("Not implemented: skipItems backward");
        }
    }

    private CompletableFuture<Void> removeItems(int removeStartIndex, int num) {
        int[] itemsToRemove = new int[num];
        for (int i = 0; i < itemsToRemove.length; i++) {
            itemsToRemove[i] = mediaQueue.itemIdAtIndex(removeStartIndex + i);
        }
        Log.d(LC, "removing items with ids: "
                + Arrays.toString(itemsToRemove));
        return handleMediaClientRequest(
                "queueRemoveItems(" + Arrays.toString(itemsToRemove) + ")",
                "Could not remove items " + Arrays.toString(itemsToRemove),
                remoteMediaClient.queueRemoveItems(itemsToRemove, null)
        );
    }

    @Override
    CompletableFuture<Void> previous() {
        Log.d(LC, "previous()");
        CompletableFuture<Void> result = new CompletableFuture<>();
        // TODO: Implement
        Log.e(LC, "previous not implemented");
        return actionResult("Not implemented");
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

    private CompletableFuture<Void> playerAction(PlayerAction action) {
        if (remoteMediaClient == null) {
            return actionResult("playerAction(" + action.name()
                    + "): remoteMediaClient is null");
        }
        Log.d(LC, "playerAction: " + action.name() + " in state: "
                + getStateString(remoteMediaClient.getPlayerState()));
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
                return actionResult("Unknown player action: " + action.name());
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
            controller.onPreloadChanged();
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
                            controller.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
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
                    controller.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
                    break;
                case MediaStatus.PLAYER_STATE_PAUSED:
                    controller.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
                    break;
                case MediaStatus.PLAYER_STATE_PLAYING:
                    controller.onStateChanged(PlaybackStateCompat.STATE_PLAYING);
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
            controller.onMetaChanged(EntryID.from(castMeta));
        }

        @Override
        public void onQueueStatusUpdated() {
            Log.d(LC, "onQueueStatusUpdated");
            controller.onPreloadChanged();
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
