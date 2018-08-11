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
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.PendingResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class CastAudioPlayer extends AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);
    private static final int NUM_TO_PRELOAD = 3;

    private final SessionManager sessionManager;
    private final CastConnectionListener castConnectionListener;
    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListenerImpl();
    private final RemoteMediaClientCallback remoteMediaClientCallback = new RemoteMediaClientCallback();
    private final MediaQueueCallback mediaQueueCallback = new MediaQueueCallback();
    private final MusicLibraryService musicLibraryService;
    private RemoteMediaClient remoteMediaClient;
    private MediaQueue mediaQueue;
    private int playerState;
    private int idleReason;
    private long lastPos = 0;
    private SparseArray<MediaQueueItem> queueItemMap;
    private CompletableFuture<Optional<String>> lastPreloadNext;
    private final Object preloadLock = new Object();
    private List<PlaybackEntry> currentPlaybackEntries;
    private boolean playWhenReady = false;

    private enum PlayerAction {
        PLAY,
        PAUSE,
        STOP
    }

    CastAudioPlayer(MusicLibraryService musicLibraryService, CastContext castContext, CastConnectionListener castConnectionListener) {
        this.musicLibraryService = musicLibraryService;
        sessionManager = castContext.getSessionManager();
        this.castConnectionListener = castConnectionListener;
        queueItemMap = new SparseArray<>();
        lastPreloadNext = CompletableFuture.completedFuture(Optional.empty());
    }

    protected void onCreate() {
        sessionManager.addSessionManagerListener(sessionManagerListener);
        CastSession castSession = sessionManager.getCurrentCastSession();
        if (castSession != null && castSession.isConnected()) {
            onConnect(castSession);
        }
    }

    protected void onDestroy() {
        if (remoteMediaClient != null) {
            remoteMediaClient.unregisterCallback(remoteMediaClientCallback);
            remoteMediaClient = null;
        }
        sessionManager.removeSessionManagerListener(sessionManagerListener);
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
        if (remoteMediaClient.isPlaying()) {
            return actionResult(null);
        }
        return sequentialize(e -> {
            playWhenReady = true;
            return playerAction(PlayerAction.PLAY);
//            return playerAction(PlayerAction.PLAY).thenCompose(o -> {
//                if (o.isPresent()) {
//                    return CompletableFuture.completedFuture(o);
//                }
//                return preloadNext(currentPlaybackEntries);
//            });
        });
    }

    @Override
    public CompletableFuture<Optional<String>> pause() {
        if (remoteMediaClient.isPaused()) {
            return actionResult(null);
        }
        return sequentialize(e -> {
            playWhenReady = false;
            return playerAction(PlayerAction.PAUSE);
//            return playerAction(PlayerAction.PAUSE).thenCompose(o -> {
//                if (o.isPresent()) {
//                    return CompletableFuture.completedFuture(o);
//                }
//                return preloadNext(currentPlaybackEntries);
//            });
        });
    }

    @Override
    public CompletableFuture<Optional<String>> stop() {
        if (remoteMediaClient.isPaused()) {
            return actionResult(null);
        }
        return sequentialize(e -> {
            playWhenReady = false;
            return playerAction(PlayerAction.STOP);
//            return playerAction(PlayerAction.STOP).thenCompose(o -> {
//                if (o.isPresent()) {
//                    return CompletableFuture.completedFuture(o);
//                }
//                return preloadNext(currentPlaybackEntries);
//            });
        });
    }

    private CompletableFuture<Optional<String>> sequentialize(
            Function<? super Optional<String>, ? extends CompletionStage<Optional<String>>> func) {
        CompletableFuture<Optional<String>> future;
        synchronized (preloadLock) {
            future = lastPreloadNext.thenCompose(func);
            lastPreloadNext = future;
        }
        return future;
    }

    @Override
    public CompletableFuture<Optional<String>> seekTo(long pos) {
        return sequentialize(e -> {
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            if (remoteMediaClient == null) {
                return actionResult("remoteMediaClient is null");
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
            return result;
        });
    }

    @Override
    int getNumToPreload() {
        return NUM_TO_PRELOAD;
    }

    private CompletableFuture<Optional<String>> resetQueue() {
        final CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        Log.d(LC, "resetQueue, clearing queue");
        remoteMediaClient.queueRemoveItems(
                mediaQueue.getItemIds(),
                null
        ).setResultCallback(r -> {
            logResult("queueRemoveItems", r);
            if (r.getStatus().isSuccess()) {
                result.complete(Optional.empty());
                audioPlayerCallback.onReady();
            } else {
                result.complete(
                        Optional.of("Could not reset queue by removing all items: "
                                + getResultString(r))
                );
            }
        });
        return result;
    }

    private CompletableFuture<Optional<String>> preloadNext(List<PlaybackEntry> playbackEntries) {
        int[] mediaQueueItemIds = mediaQueue.getItemIds();
        MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
        MediaQueueItem currentItem = remoteMediaClient.getCurrentItem();
        // If current entry is correct:
        //   1. Remove all old entries except current
        //   2. Queue all new entries except the first
        // Else:
        //   1. queueLoad all new entries
        Log.d(LC, "preloadNext");
        logCurrentQueue();
        if (playbackEntries == null || playbackEntries.isEmpty()) {
            if (mediaQueueItemIds.length <= 0) {
                Log.d(LC, "Queue is empty and should be. No-op.");
                return actionResult(null);
            }
            Log.d(LC, "There should be no queue. Reset queue.");
            return resetQueue();
        }
        if (mediaStatus == null || mediaQueueItemIds.length <= 0) {
            Log.d(LC, "There is no queue. Initialize a new queue.");
            return setQueue(playbackEntries, 0, playWhenReady);
        }
        final CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        PlaybackEntry firstEntry = playbackEntries.get(0);
        EntryID currentEntryID = EntryID.from(currentItem.getMedia().getMetadata());
        if (!currentEntryID.equals(firstEntry.entryID)) {
            Log.d(LC, "The current track is incorrect. Initialize a new queue.");
            return setQueue(playbackEntries, 0, playWhenReady);
        }
        int numItemsToInsert = playbackEntries.size() > 0 ? playbackEntries.size() - 1: 0;
        MediaQueueItem[] itemsToInsert = new MediaQueueItem[numItemsToInsert];
        for (int i = 0; i < numItemsToInsert; i++) {
            itemsToInsert[i] = buildMediaQueueItem(playbackEntries.get(i + 1), playWhenReady);
        }
        Log.d(LC, "The current track is correct.");
        if (mediaQueueItemIds.length > 1) {
            Log.d(LC, "The current track is not alone.");
            int currentItemId = currentItem.getItemId();
            int[] idsToRemove = new int[mediaQueueItemIds.length - 1];
            int index = 0;
            for (int itemId: mediaQueueItemIds) {
                if (itemId != currentItemId) {
                    idsToRemove[index++] = itemId;
                }
            }
            Log.d(LC, "Remove all entries except the current." + Arrays.toString(idsToRemove));
            remoteMediaClient.queueRemoveItems(
                    idsToRemove,
                    null
            ).setResultCallback(r -> {
                logResult("queueRemoveItems", r);
                if (!r.getStatus().isSuccess()) {
                    // Try again
                    if (isCurrentPlaybackEntries(playbackEntries)) {
                        preloadNext(playbackEntries).thenAccept(result::complete);
                    } else {
                        result.complete(Optional.of("Outdated preloadNext canceled"));
                    }
                    return;
                }
                Log.d(LC, "The current track is now alone. "
                        + "Add the remaining entries last in the queue.");
                remoteMediaClient.queueInsertItems(
                        itemsToInsert,
                        MediaQueueItem.INVALID_ITEM_ID,
                        null
                ).setResultCallback(r2 -> {
                    logResult("queueInsertItems", r2);
                    if (!r2.getStatus().isSuccess()) {
                        // Try again
                        if (isCurrentPlaybackEntries(playbackEntries)) {
                            preloadNext(playbackEntries).thenAccept(result::complete);
                        } else {
                            result.complete(Optional.of("Outdated preloadNext canceled"));
                        }
                        return;
                    }
                    result.complete(Optional.empty());
                });
            });
            return result;
        }
        Log.d(LC, "The current track is alone. Add the remaining entries last in the queue.");
        remoteMediaClient.queueInsertItems(
                itemsToInsert,
                MediaQueueItem.INVALID_ITEM_ID,
                null
        ).setResultCallback(r -> {
            logResult("queueInsertItems", r);
            if (!r.getStatus().isSuccess()) {
                // Try again
                if (isCurrentPlaybackEntries(playbackEntries)) {
                    preloadNext(playbackEntries).thenAccept(result::complete);
                } else {
                    result.complete(Optional.of("Outdated preloadNext canceled"));
                }
                return;
            }
            result.complete(Optional.empty());
        });
        return result;
    }

    private boolean isCurrentPlaybackEntries(List<PlaybackEntry> playbackEntries) {
        return currentPlaybackEntries == playbackEntries;
    }

    private void logCurrentQueue() {
        StringBuilder sb = new StringBuilder("currentQueue:\n");
        MediaQueueItem currentItem = remoteMediaClient.getCurrentItem();
        int currentItemID = currentItem == null ? 0 : currentItem.getItemId();
        if (mediaQueue.getItemCount() <= 0) {
            Log.d(LC, sb.append("queue is empty").toString());
            return;
        }
        for (int itemId: mediaQueue.getItemIds()) {
            int itemIndex = mediaQueue.indexOfItemWithId(itemId);
            MediaQueueItem queueItem = queueItemMap.get(
                    itemId,
                    mediaQueue.getItemAtIndex(itemIndex)
            );
            String itemString = queueItem == null ? "null" :
                    queueItem.getMedia().getMetadata().getString(Meta.METADATA_KEY_TITLE);
            if (itemId == currentItemID) {
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
        return result;
    }

    @Override
    CompletableFuture<Optional<String>> setPreloadNext(List<PlaybackEntry> playbackEntries) {
        currentPlaybackEntries = playbackEntries;
        return sequentialize(e -> preloadNext(playbackEntries));
    }

    private MediaQueueItem[] buildMediaQueueItems(List<PlaybackEntry> playbackEntries, boolean playWhenReady) {
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
        MediaMetadata castMeta = Meta.from(meta);
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
        return sequentialize(e -> {
            CompletableFuture<Optional<String>> result = new CompletableFuture<>();
            remoteMediaClient.queueNext(
                    null
            ).setResultCallback(r -> {
                logResult("next", r);
                if (r.getStatus().isSuccess()) {
                    audioPlayerCallback.onReady();
                    result.complete(Optional.empty());
                } else {
                    result.complete(
                            Optional.of("Could not go to next: " + getResultString(r))
                    );
                }
            });
            return result;
        });
    }

    @Override
    CompletableFuture<Optional<String>> previous() {
        return sequentialize(e -> {
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
            return actionResult("remoteMediaClient is null");
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
        return result;
    }

    private class MediaQueueCallback extends MediaQueue.Callback {
        @Override
        public void itemsInsertedInRange(int index, int count) {
            Log.d(LC, "MediaQueue: itemsInsertedInRange(index: " + index
                    + ", count: " + count + ")");
            logCurrentQueue();
        }

        @Override
        public void itemsReloaded() {
            Log.d(LC, "MediaQueue: itemsReloaded()");
            logCurrentQueue();
        }

        @Override
        public void itemsRemovedAtIndexes(int[] ints) {
            Log.d(LC, "MediaQueue: itemsRemovedAtIndexes(" + Arrays.toString(ints) + ")");
            logCurrentQueue();
        }

        @Override
        public void itemsUpdatedAtIndexes(int[] ints) {
            Log.d(LC, "MediaQueue: itemsUpdatedAtIndexes(" + Arrays.toString(ints) + ")");
            logCurrentQueue();
        }

        @Override
        public void mediaQueueChanged() {
            Log.d(LC, "MediaQueue: mediaQueueChanged()");
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
                            audioPlayerCallback.onEnded();
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

    private class SessionManagerListenerImpl implements SessionManagerListener<Session> {
        @Override
        public void onSessionStarting(Session session) {
            Log.d(LC, "CastSession starting");
        }

        @Override
        public void onSessionStarted(Session session, String s) {
            Log.d(LC, "CastSession started");
            onConnect((CastSession) session);
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {
            Log.d(LC, "CastSession start failed");
            onDisconnect();
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(LC, "CastSession ending");
            CastSession castSession = (CastSession) session;
            if (remoteMediaClient == null) {
                remoteMediaClient = castSession.getRemoteMediaClient();
            }
            if (remoteMediaClient != null) {
                lastPos = castSession.getRemoteMediaClient().getApproximateStreamPosition();
            }
            onDisconnect();
        }

        @Override
        public void onSessionEnded(Session session, int i) {
            Log.d(LC, "CastSession ended");
            castConnectionListener.onDisconnected();
        }

        @Override
        public void onSessionResuming(Session session, String s) {
            Log.d(LC, "CastSession resuming");
        }

        @Override
        public void onSessionResumed(Session session, boolean b) {
            Log.d(LC, "CastSession resumed");
            onConnect((CastSession) session);
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {
            Log.d(LC, "CastSession resume failed");
            onDisconnect();
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
            Log.d(LC, "CastSession suspended");
        }

        void onDisconnect() {
            audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
            if (mediaQueue != null) {
                mediaQueue.unregisterCallback(mediaQueueCallback);
                mediaQueue = null;
            }
            if (remoteMediaClient != null) {
                remoteMediaClient.unregisterCallback(remoteMediaClientCallback);
                remoteMediaClient = null;
            }
        }
    }

    private void onConnect(CastSession session) {
        if (remoteMediaClient == null) {
            remoteMediaClient = session.getRemoteMediaClient();
            remoteMediaClient.registerCallback(remoteMediaClientCallback);
            mediaQueue = remoteMediaClient.getMediaQueue();
            mediaQueue.registerCallback(mediaQueueCallback);
        }
        castConnectionListener.onConnected();
    }

    abstract static class CastConnectionListener {
        abstract void onConnected();
        abstract void onDisconnected();
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
