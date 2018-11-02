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
import java.util.Optional;
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
        playWhenReady = true;
        return playerAction(
                PlayerAction.PLAY
        ).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                setAutoPlay(playWhenReady)
        );
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
        playWhenReady = false;
        return playerAction(
                PlayerAction.PAUSE
        ).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                setAutoPlay(playWhenReady)
        );
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
        playWhenReady = false;
        return playerAction(
                PlayerAction.STOP
        ).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                setAutoPlay(playWhenReady)
        );
    }

    @Override
    public CompletableFuture<Optional<String>> seekTo(long pos) {
        Log.d(LC, "seekTo()");
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
    CompletableFuture<Optional<String>> queue(List<PlaybackEntry> playbackEntries, PlaybackQueue.QueueOp op) {
        List<PlaybackEntry> entriesToQueue = new LinkedList<>();
        List<PlaybackEntry> entriesToDePreload = new LinkedList<>();
        int numPreloaded = getNumPreloaded();
        int currentIndex = getCurrentIndex();

        List<PlaybackEntry> playlistEntries = getPreloadedPlaylistEntries(Integer.MAX_VALUE);
        List<PlaybackEntry> queueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE);
        int totalEntries = numPreloaded + playbackEntries.size();
        int numPlaylistEntriesToDepreload = 0;
        if (totalEntries > NUM_TO_PRELOAD) {
            // Depreload player playlist entries if needed.
            numPlaylistEntriesToDepreload = Integer.min(
                    totalEntries - NUM_TO_PRELOAD,
                    playlistEntries.size()
            );
        }
        List<PlaybackEntry> playlistEntriesToDepreload = new LinkedList<>();
        int playlistEntriesToDepreloadStartIndex = currentIndex + 1 + queueEntries.size();
        int playlistEntriesToDepreloadItemIds[] = new int[numPlaylistEntriesToDepreload];
        for (int i = 0; i < numPlaylistEntriesToDepreload; i++) {
            int itemId = mediaQueue.itemIdAtIndex(
                    playlistEntriesToDepreloadStartIndex + playlistEntries.size() - i - 1
            );
            playlistEntriesToDepreloadItemIds[i] = itemId;
            playlistEntriesToDepreload.add(mediaQueueItem2PlaybackEntry(queueItemMap.get(itemId)));
        }
        totalEntries -= numPlaylistEntriesToDepreload;
        int numQueueEntriesToDepreload = 0;
        if (op.equals(PlaybackQueue.QueueOp.NEXT) && totalEntries > NUM_TO_PRELOAD) {
            // Depreload player queue entries if needed.
            numQueueEntriesToDepreload = Integer.min(
                    totalEntries - NUM_TO_PRELOAD,
                    queueEntries.size()
            );
        }
        List<PlaybackEntry> queueEntriesToDepreload = new LinkedList<>();
        int queueEntriesToDepreloadStartIndex = currentIndex + 1;
        int queueEntriesToDepreloadItemIds[] = new int[numQueueEntriesToDepreload];
        for (int i = 0; i < numQueueEntriesToDepreload; i++) {
            int itemId = mediaQueue.itemIdAtIndex(
                    queueEntriesToDepreloadStartIndex + queueEntries.size() - i - 1
            );
            queueEntriesToDepreloadItemIds[i] = itemId;
            queueEntriesToDepreload.add(mediaQueueItem2PlaybackEntry(queueItemMap.get(itemId)));
        }

        totalEntries -= numQueueEntriesToDepreload;
        totalEntries -= playbackEntries.size();
        int numToQueue = 0;
        if (NUM_TO_PRELOAD > totalEntries) {
            numToQueue = Integer.min(NUM_TO_PRELOAD - totalEntries, playbackEntries.size());
        }
        if (numToQueue > 0) {
            entriesToQueue = playbackEntries.subList(0, numToQueue);
        }
        MediaQueueItem[] queueItemsToQueue = buildMediaQueueItems(entriesToQueue, playWhenReady);
        int queueInsertBeforeIndex = op.equals(PlaybackQueue.QueueOp.NEXT) ?
                currentIndex + 1 : currentIndex + queueEntries.size() + 1;
        int queueInsertBeforeItemId = mediaQueue.itemIdAtIndex(queueInsertBeforeIndex);

        entriesToDePreload.addAll(playbackEntries.subList(numToQueue, playbackEntries.size()));

        logCurrentQueue();
        Log.d(LC, "queue()"
                + "\nqueueEntriesToDepreload: "
                + numQueueEntriesToDepreload + " items from "
                + "[" + queueEntriesToDepreloadStartIndex + "]: "
                + Arrays.toString(queueEntriesToDepreloadItemIds)
                + "\nplaylistEntriesToDepreload: "
                + numPlaylistEntriesToDepreload + " items from "
                + "[" + playlistEntriesToDepreloadStartIndex + "]: "
                + Arrays.toString(playlistEntriesToDepreloadItemIds)
                + "\nentriesToQueue: " + entriesToQueue.size() + " before "
                + "[" + queueInsertBeforeIndex + "] " + queueInsertBeforeItemId
                + "\nentriesToDepreload: " + entriesToDePreload.size());


        CompletableFuture<Optional<String>> result = CompletableFuture.completedFuture(Optional.empty());
        if (entriesToQueue.size() > 0) {
            result = result.thenCompose(e -> {
                CompletableFuture<Optional<String>> req = new CompletableFuture<>();
                remoteMediaClient.queueInsertItems(
                        queueItemsToQueue,
                        queueInsertBeforeItemId,
                        null
                ).setResultCallback(r -> {
                    logResult("queue() queueInsertItems", r);
                    if (r.getStatus().isSuccess()) {
                        req.complete(Optional.empty());
                    } else {
                        req.complete(Optional.of("Could not insert queue items."));
                    }
                });
                remoteMediaClientResultCallbackTimeout(req);
                return req;
            });
        }

        if (queueEntriesToDepreloadItemIds.length > 0 || playlistEntriesToDepreloadItemIds.length > 0) {
            int[] itemIdsToRemove = IntStream.concat(
                    Arrays.stream(queueEntriesToDepreloadItemIds),
                    Arrays.stream(playlistEntriesToDepreloadItemIds)
            ).toArray();
            result = result.thenCompose(e -> {
                if (e.isPresent()) {
                    return CompletableFuture.completedFuture(e);
                }
                CompletableFuture<Optional<String>> req = new CompletableFuture<>();
                remoteMediaClient.queueRemoveItems(
                        itemIdsToRemove,
                        null
                ).setResultCallback(r -> {
                    logResult("queue() queueRemoveItems superfluous entries", r);
                    if (r.getStatus().isSuccess()) {
                        audioPlayerCallback.dePreloadQueueEntries(queueEntriesToDepreload, op);
                        audioPlayerCallback.dePreloadPlaylistEntries(playlistEntriesToDepreload);
                        req.complete(Optional.empty());
                    } else {
                        req.complete(Optional.of("Could not depreload superfluous items"));
                    }
                });
                remoteMediaClientResultCallbackTimeout(req);
                return req;

            });
        }

        if (entriesToDePreload.size() > 0) {
            result.thenCompose(e -> {
                if (e.isPresent()) { return CompletableFuture.completedFuture(e); }
                audioPlayerCallback.dePreloadQueueEntries(entriesToDePreload, op);
                return actionResult(null);
            });
        }
        return result;
    }

    @Override
    CompletableFuture<Optional<String>> dequeue(long[] queuePosition) {
        // TODO: implement
        return actionResult("dequeue not implemented");
//        if (queuePosition < 0) {
//            return actionResult("Can not dequeue negative index: " + queuePosition);
//        }
//        List<PlaybackEntry> queueEntries = getPreloadedQueueEntries(Integer.MAX_VALUE);
//        if (queuePosition < queueEntries.size()) {
//            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
//            PlaybackEntry playbackEntry = queueEntries.get(queuePosition);
//            int mediaQueueItemIndex = getCurrentIndex() + queuePosition + 1;
//            int mediaQueueItemId = mediaQueue.itemIdAtIndex(mediaQueueItemIndex);
//            logCurrentQueue();
//            Log.d(LC, "dequeue() removing " + playbackEntry.toString()
//                    + " at position " + queuePosition
//                    + " mediaQueue(index: " + mediaQueueItemIndex
//                    + ", id: " + mediaQueueItemId + ")");
//            remoteMediaClient.queueRemoveItems(
//                    new int[]{mediaQueueItemId},
//                    null
//            ).setResultCallback(r -> {
//                logResult("dequeue() queueRemoveItem", r);
//                if (r.getStatus().isSuccess()) {
//                    result.complete(Optional.empty());
//                } else {
//                    result.complete(
//                            Optional.of("Could not remove queue item " + playbackEntry.toString()
//                                    + ": " + getResultString(r))
//                    );
//                }
//            });
//            remoteMediaClientResultCallbackTimeout(result);
//            return result;
//        }
//        audioPlayerCallback.consumeQueueEntry(queuePosition - queueEntries.size());
//        return actionResult(null);
    }

    private void remoteMediaClientResultCallbackTimeout(
            CompletableFuture<Optional<String>> result) {
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
                result.complete(Optional.of("Timeout while waiting for Cast action result"));
            }
        });
    }

    public CompletableFuture<Optional<String>> checkPreload() {
        Log.d(LC, "checkPreload()");
        synchronized (preloadLock) {
            Log.d(LC, "checkPreload() start");
            logCurrentQueue();
            int numPreloaded = getNumPreloaded();
            if (numPreloaded < NUM_TO_PRELOAD) {
                List<PlaybackEntry> entries =
                        audioPlayerCallback.requestPreload(NUM_TO_PRELOAD - numPreloaded);
                return preload(entries);
            }
        }
        return actionResult(null);
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
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            remoteMediaClient.queueRemoveItems(
                    itemIndexesToRemove.stream().mapToInt(i -> i).toArray(),
                    null
            ).setResultCallback(r -> {
                logResult("dePreload queueRemoveItems", r);
                if (r.getStatus().isSuccess()) {
                    audioPlayerCallback.dePreloadQueueEntries(
                            queueEntriesToRemove,
                            PlaybackQueue.QueueOp.NEXT
                    );
                    audioPlayerCallback.dePreloadPlaylistEntries(playlistEntriesToRemove);
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
        }
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
        return CompletableFuture.<Optional<String>>completedFuture(Optional.empty()).thenCompose(e -> {
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
        }).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                checkPreload()
        ).thenApply(e -> {
            audioPlayerCallback.onPreloadChanged();
            return e;
        });
    }

    @Override
    CompletableFuture<Optional<String>> skipItems(int offset) {
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
            int totalQueueEntries = numCastQueueEntries + audioPlayerCallback.getNumQueueEntries();
            if (offset <= totalQueueEntries) {
                // Play queue item at offset now
                if (offset <= numCastQueueEntries) {
                    // Move the queue entry to after current index and skip to next
                    Log.d(LC, "skipItems short queue offset");
                    int currentIndex = getCurrentIndex();
                    int itemIndex = currentIndex + offset;
                    int itemId = mediaQueue.itemIdAtIndex(itemIndex);
                    int beforeItemId = mediaQueue.itemIdAtIndex(currentIndex + 1);
                    return CompletableFuture.<Optional<String>>completedFuture(Optional.empty()).thenCompose(e -> {
                        Log.d(LC, "skipItems move [" + itemIndex + "] " + itemId + " to index " + (currentIndex + 1));
                        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
                        remoteMediaClient.queueReorderItems(
                                new int[]{itemId},
                                beforeItemId,
                                null
                        ).setResultCallback(r -> {
                            logResult("queueReorderItems(" + itemId + ", "
                                    + beforeItemId + ")", r);
                            if (r.getStatus().isSuccess()) {
                                result.complete(Optional.empty());
                            } else {
                                result.complete(
                                        Optional.of("Could not move queue item to next up: " + getResultString(r))
                                );
                            }
                        });
                        remoteMediaClientResultCallbackTimeout(result);
                        return result;
                    }).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                            next()
                    );
                } else {
                    // Get the queue entry from PlaybackController, queue after current and play
                    Log.d(LC, "skipItems long queue offset");
                    PlaybackEntry playbackEntry = audioPlayerCallback.consumeQueueEntry(offset);
                    return queue(
                            Collections.singletonList(playbackEntry),
                            PlaybackQueue.QueueOp.NEXT
                    ).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                            next()
                    );
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
                    CompletableFuture<Optional<String>> result;
                    if (itemIndex > removeStartIndex) {
                        // We have playlist items to dequeue
                        result = removeItems(removeStartIndex, itemIndex - removeStartIndex);
                    } else {
                        result = CompletableFuture.completedFuture(Optional.empty());
                    }
                    return result.thenCompose(e -> {
                        Log.d(LC, "skipItems move [" + itemIndex + "] " + itemId + " to index " + (currentIndex + 1));
                        CompletableFuture<Optional<String>> result2 = new CompletableFuture<>();
                        remoteMediaClient.queueReorderItems(
                                new int[]{itemId},
                                beforeItemId,
                                null
                        ).setResultCallback(r -> {
                            logResult("queueReorderItems(" + itemId + ", "
                                    + beforeItemId + ")", r);
                            if (r.getStatus().isSuccess()) {
                                result2.complete(Optional.empty());
                            } else {
                                result2.complete(
                                        Optional.of("Could not move playlist item to next up: " + getResultString(r))
                                );
                            }
                        });
                        remoteMediaClientResultCallbackTimeout(result2);
                        return result2;
                    }).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                            next()
                    );
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
                        audioPlayerCallback.consumePlaylistEntry();
                    }
                    PlaybackEntry playbackEntry = audioPlayerCallback.consumePlaylistEntry();
                    return removeItems(
                            removeStartIndex,
                            numCastPlaylistEntries
                    ).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                            queue(Collections.singletonList(playbackEntry), PlaybackQueue.QueueOp.NEXT)
                    ).thenCompose(e -> e.isPresent() ? CompletableFuture.completedFuture(e) :
                            next()
                    );
                }
            }
        } else {
            // Skip backward
            // TODO: implement
            return actionResult("Not implemented: skipItems backward");
        }
    }

    private CompletableFuture<Optional<String>> removeItems(int removeStartIndex, int num) {
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        int[] itemsToRemove = new int[num];
        for (int i = 0; i < itemsToRemove.length; i++) {
            itemsToRemove[i] = mediaQueue.itemIdAtIndex(removeStartIndex + i);
        }
        Log.d(LC, "removing items with ids: "
                + Arrays.toString(itemsToRemove));
        remoteMediaClient.queueRemoveItems(
                itemsToRemove,
                null
        ).setResultCallback(r -> {
            logResult("queueRemoveItems("
                    + Arrays.toString(itemsToRemove) + ")", r);
            if (r.getStatus().isSuccess()) {
                result.complete(Optional.empty());
            } else {
                result.complete(Optional.of(
                        "Could not remove items " + Arrays.toString(itemsToRemove) + ": "
                                + getResultString(r)
                ));
            }
        });
        remoteMediaClientResultCallbackTimeout(result);
        return result;
    }

    @Override
    CompletableFuture<Optional<String>> previous() {
        Log.d(LC, "previous()");
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
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
            dePreload();
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
            dePreload();
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
            audioPlayerCallback.onPreloadChanged();
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
