package se.splushii.dancingbunnies.audioplayer;

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
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class CastAudioPlayer extends AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);
    private final SessionManager sessionManager;
    private final CastConnectionListener castConnectionListener;
    private final SessionManagerListener<Session> sessionManagerListener =
            new SessionManagerListenerImpl();
    private final RemoteMediaClientCallback remoteMediaClientCallback =
            new RemoteMediaClientCallback();
    private RemoteMediaClient remoteMediaClient;
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

    CastAudioPlayer(CastContext castContext, CastConnectionListener castConnectionListener) {
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
    public long getCurrentPosition() {
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
                + remoteMediaClient.getPlayerState());
        remoteMediaClient.seek(pos).setResultCallback(result ->
                handleResult("seek", result)
        );
    }

    @Override
    int getNumToPreload() {
        return 0;
    }

    @Override
    int getNumPreloadedNext() {
        return 0;
    }

    @Override
    void addPreloadNext(PlaybackEntry playbackEntry) {

    }

    @Override
    void addPreloadNext(PlaybackEntry playbackEntry, int index) {

    }

    @Override
    void clearPreload() {

    }

    @Override
    void next() {

    }

    @Override
    void previous() {

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
                + remoteMediaClient.getPlayerState());
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

//    @Override
//    public void setSource(PlaybackEntry playbackEntry, long position) {
//        audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);
//        lastPos = position;
//        MediaMetadataCompat meta = playbackEntry.meta;
//        AudioDataSource audioDataSource = playbackEntry.audioDataSource;
//        MediaMetadata castMeta = Meta.from(meta);
//        long duration = audioDataSource.getDuration();
//        if (duration == 0L) {
//            duration = meta.getLong(Meta.METADATA_KEY_DURATION);
//        }
//        MediaInfo mediaInfo = new MediaInfo.Builder(audioDataSource.getURL())
//                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
//                .setContentType(audioDataSource.getContentType())
//                .setMetadata(castMeta)
//                .setStreamDuration(duration)
//                .build();
//        MediaLoadOptions mediaLoadOptions = new MediaLoadOptions.Builder()
//                .setAutoplay(false)
//                .setPlayPosition(position)
//                .build();
//        Log.d(LC, "Media duration: " + duration + ". Start: " + position);
//        remoteMediaClient.load(mediaInfo, mediaLoadOptions).setResultCallback(result -> {
//            handleResult("load", result);
//            if (result.getStatus().isSuccess()) {
//                audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
//                audioPlayerCallback.onReady();
//            }
//        });
//        audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
//    }

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
            MediaMetadata castMeta =item.getMedia().getMetadata();
            Log.d(LC, "queue title: " + castMeta.getString(Meta.METADATA_KEY_TITLE));
        }
    }

    private class RemoteMediaClientCallback extends RemoteMediaClient.Callback {
        @Override
        public void onStatusUpdated() {
            String state;
            if (remoteMediaClient == null) {
                return;
            }
            int newPlayerState = remoteMediaClient.getPlayerState();
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
                            state = "IDLE_FINISHED";
                            break;
                        case MediaStatus.IDLE_REASON_CANCELED:
                            audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
                            state = "IDLE_CANCELED";
                            break;
                        case MediaStatus.IDLE_REASON_ERROR:
                            state = "IDLE_ERROR";
                            break;
                        case MediaStatus.IDLE_REASON_INTERRUPTED:
                            state = "IDLE_INTERRUPTED";
                            break;
                        default:
                            state = "IDLE_NONE";
                            break;
                    }
                    break;
                case MediaStatus.PLAYER_STATE_BUFFERING:
                    audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
                    state = "BUFFERING";
                    break;
                case MediaStatus.PLAYER_STATE_PAUSED:
                    audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
                    state = "PAUSED";
                    break;
                case MediaStatus.PLAYER_STATE_PLAYING:
                    audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PLAYING);
                    state = "PLAYING";
                    break;
                default:
                    state = "UNKNOWN";
                    break;
            }
            Log.d(LC, "onStatusUpdated state:" + state);
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
        }
        syncQueue();
        castConnectionListener.onConnected();
    }

    abstract static class CastConnectionListener {
        abstract void onConnected();
        abstract void onDisconnected();
    }
}
