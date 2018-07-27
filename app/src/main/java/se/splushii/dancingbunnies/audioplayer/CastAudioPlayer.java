package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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

    public List<EntryID> getQueueEntryIDs() {
        Log.d(LC, "getQueue");
        List<EntryID> queue = new LinkedList<>();
        if (remoteMediaClient == null) {
            Log.w(LC, "remoteMediaClient null");
            return queue;
        }
        MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
        if (mediaStatus == null) {
            Log.w(LC, "mediaStatus null");
            return queue;
        }
        List<MediaQueueItem> castQueue = mediaStatus.getQueueItems();
        for (MediaQueueItem castQueueItem: castQueue) {
            MediaMetadata meta = castQueueItem.getMedia().getMetadata();
            EntryID entryID = EntryID.from(meta);
            if (entryID.id == null) {
                continue;
            }
            queue.add(entryID);
        }
        Log.d(LC, "queue size: " + queue.size());
        return queue;
    }

    public List<MediaSessionCompat.QueueItem> getQueue() {
        Log.d(LC, "getQueue");
        List<MediaSessionCompat.QueueItem> queue = new LinkedList<>();
        if (remoteMediaClient == null) {
            Log.w(LC, "remoteMediaClient null");
            return queue;
        }
        MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
        if (mediaStatus == null) {
            Log.w(LC, "mediaStatus null");
            return queue;
        }
        List<MediaQueueItem> castQueue = mediaStatus.getQueueItems();
        for (MediaQueueItem castQueueItem: castQueue) {
            MediaMetadata meta = castQueueItem.getMedia().getMetadata();
            queue.add(new MediaSessionCompat.QueueItem(
                    Meta.meta2desc(meta),
                    castQueueItem.getItemId()
            ));
        }
        Log.d(LC, "queue size: " + queue.size());
        return queue;
    }

    private enum PlayerAction {
        PLAY,
        PAUSE,
        STOP
    }

    CastAudioPlayer(MusicLibraryService musicLibraryService, CastContext castContext, CastConnectionListener castConnectionListener) {
        this.musicLibraryService = musicLibraryService;
        sessionManager = castContext.getSessionManager();
        this.castConnectionListener = castConnectionListener;
    }

    protected void onCreate() {
        sessionManager.addSessionManagerListener(sessionManagerListener);
        CastSession castSession = sessionManager.getCurrentCastSession();
        if (castSession != null && castSession.isConnected()) {
            onConnect(castSession);
        }
        syncQueue();
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
    public void play() {
        playerAction(PlayerAction.PLAY);
    }

    @Override
    public void pause() {
        playerAction(PlayerAction.PAUSE);
    }

    @Override
    public void stop() {
        playerAction(PlayerAction.STOP);
    }

    @Override
    void seekTo(long pos) {
        if (remoteMediaClient == null) {
            return;
        }
        Log.d(LC, "playerAction: seekTo(" + pos + ") in state: "
                + getStateString(remoteMediaClient.getPlayerState()));
        remoteMediaClient.seek(pos).setResultCallback(result ->
                handleResult("seek", result)
        );
    }

    @Override
    int getNumToPreload() {
        return NUM_TO_PRELOAD;
    }

    @Override
    void setPreloadNext(List<PlaybackEntry> playbackEntries) {
        MediaQueueItem currentItem = remoteMediaClient.getCurrentItem();
        Log.d(LC, "queue count: " + mediaQueue.getItemCount());
        MediaQueueItem firstQueueItem = mediaQueue.getItemAtIndex(0);
        if (currentItem != null)
            Log.e(LC, "current: " + currentItem.getMedia().getMetadata().getString(Meta.METADATA_KEY_TITLE));
        else
            Log.e(LC, "current: null");
        if (firstQueueItem != null)
            Log.e(LC, "queue(0): " + firstQueueItem.getMedia().getMetadata().getString(Meta.METADATA_KEY_TITLE));
        else
            Log.e(LC, "queue(0): null");

        int index = 0;
        MediaQueueItem[] queueItems = new MediaQueueItem[playbackEntries.size()];
        for (PlaybackEntry playbackEntry: playbackEntries) {
            MediaMetadataCompat meta = playbackEntry.meta;
            String URL = musicLibraryService.getAudioURL(playbackEntry.entryID);
            if (URL == null) {
                Log.e(LC, "Could not get URL for " + meta.getString(Meta.METADATA_KEY_TITLE));
                continue;
            }
            MediaMetadata castMeta = Meta.from(meta);
            long duration = meta.getLong(Meta.METADATA_KEY_DURATION);
            String contentType = meta.getString(Meta.METADATA_KEY_CONTENT_TYPE);
            long position = 0;
            MediaInfo mediaInfo = new MediaInfo.Builder(URL)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(contentType)
                    .setMetadata(castMeta)
                    .setStreamDuration(duration)
                    .build();
            MediaQueueItem queueItem = new MediaQueueItem.Builder(mediaInfo)
                    .setAutoplay(false)
                    .setStartTime(position)
                    .build();
            queueItems[index++] = queueItem;
        }
        int startIndex = 0;
        int repeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
        remoteMediaClient.queueLoad(queueItems, startIndex, repeatMode, null)
                .setResultCallback(result -> {
                    handleResult("queueLoad", result);
                    if (result.getStatus().isSuccess()) {
                        audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
                        audioPlayerCallback.onReady();
                    }
                });
        audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
    }

    @Override
    void next() {
        remoteMediaClient.queueNext(null).setResultCallback(result ->
                handleResult("next", result)
        );
    }

    @Override
    void previous() {
        // TODO: Implement
        Log.e(LC, "previous not implemented");
    }

    private void handleResult(String action, RemoteMediaClient.MediaChannelResult result) {
        Log.d(LC, action + " smooth? " + result.getStatus().isSuccess());
        if (!result.getStatus().isSuccess()) {
            String msg = result.getStatus().toString() + ": " + result.getStatus().getStatusMessage();
            Log.e(LC, msg);
        }
    }

    private void playerAction(PlayerAction action) {
        if (remoteMediaClient == null) {
            return;
        }
        Log.d(LC, "playerAction: " + action.name() + " in state: "
                + getStateString(remoteMediaClient.getPlayerState()));
        switch (action) {
            case PLAY:
                remoteMediaClient.play().setResultCallback(result ->
                        handleResult("play", result)
                );
                break;
            case PAUSE:
                remoteMediaClient.pause().setResultCallback(result ->
                        handleResult("pause", result)
                );
                break;
            case STOP:
                remoteMediaClient.stop().setResultCallback(result ->
                        handleResult("stop", result)
                );
                break;
            default:
                Log.d(LC, "Unknown PlayerAction: " + action.name());
                break;
        }
    }

    private void syncQueue() {
        if (remoteMediaClient == null) {
            return;
        }
        remoteMediaClient.registerCallback(remoteMediaClientCallback);
        MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
        if (mediaStatus == null) {
            Log.d(LC, "mediaStatus is null");
            return;
        }
        Log.d(LC, "cast queue size: " + mediaStatus.getQueueItemCount());
        for (MediaQueueItem item: mediaStatus.getQueueItems()) {
            MediaMetadata castMeta = item.getMedia().getMetadata();
            Log.d(LC, "queue title: " + castMeta.getString(Meta.METADATA_KEY_TITLE));
        }
    }

    private class MediaQueueCallback extends MediaQueue.Callback {
        @Override
        public void itemsInsertedInRange(int index, int count) {
            Log.d(LC, "MediaQueue: itemsInsertedInRange(index: " + index
                    + ", count: " + count + ")");
        }

        @Override
        public void itemsReloaded() {
            Log.d(LC, "MediaQueue: itemsReloaded()");
        }

        @Override
        public void itemsRemovedAtIndexes(int[] ints) {
            Log.d(LC, "MediaQueue: itemsRemovedAtIndexes(" + Arrays.toString(ints) + ")");
        }

        @Override
        public void itemsUpdatedAtIndexes(int[] ints) {
            Log.d(LC, "MediaQueue: itemsUpdatedAtIndexes(" + Arrays.toString(ints) + ")");
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
            audioPlayerCallback.onQueueChanged();
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
            audioPlayerCallback.onQueueChanged();
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
        syncQueue();
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
