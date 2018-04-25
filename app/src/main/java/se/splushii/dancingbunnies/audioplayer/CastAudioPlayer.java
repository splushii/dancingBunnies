package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class CastAudioPlayer extends AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);
    private final SessionManager sessionManager;
    private final CastConnectionListener castConnectionListener;
    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListenerImpl();
    private CastSession castSession;
    private int playerState;
    private int idleReason;

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
        castSession = sessionManager.getCurrentCastSession();
        sessionManager.addSessionManagerListener(sessionManagerListener);
    }

    protected void onDestroy() {
        sessionManager.removeSessionManagerListener(sessionManagerListener);
        castSession = null;
    }

    @Override
    public long getCurrentPosition() {
        if (castSession == null || !castSession.isConnected()) {
            return 0;
        }
        return castSession.getRemoteMediaClient().getApproximateStreamPosition();
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

    private void playerAction(PlayerAction action) {
        if (castSession == null || !castSession.isConnected()) {
            return;
        }
        Log.d(LC, "playerAction: " + action.name() + " in state: "
                + castSession.getRemoteMediaClient().getPlayerState());
        switch (action) {
            case PLAY:
                castSession.getRemoteMediaClient().play();
                break;
            case PAUSE:
                castSession.getRemoteMediaClient().pause();
                break;
            case STOP:
                castSession.getRemoteMediaClient().stop();
                break;
            default:
                Log.d(LC, "Unknown PlayerAction: " + action.name());
                break;
        }
    }

    @Override
    public void setSource(AudioDataSource audioDataSource, MediaMetadataCompat meta) {
        MediaMetadata castMeta = Meta.from(meta);
        long duration = audioDataSource.getDuration();
        if (duration == 0L) {
            duration = meta.getLong(Meta.METADATA_KEY_DURATION);
        }
        Log.d(LC, "Media duration: " + duration);
        MediaInfo mediaInfo = new MediaInfo.Builder(audioDataSource.getURL())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(audioDataSource.getContentType())
                .setMetadata(castMeta)
//                .setStreamDuration(duration)
                .build();
        RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        MediaLoadOptions mediaLoadOptions = new MediaLoadOptions.Builder()
                .setAutoplay(false)
                .setPlayPosition(0) // TODO: Set start position. (e.g. when starting cast midsong)
                .build();
        remoteMediaClient.registerCallback(new RemoteMediaClient.Callback() {
            @Override
            public void onStatusUpdated() {
                String state;
                RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
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
            }

            @Override
            public void onMetadataUpdated() {
                Log.d(LC, "onMetadataUpdated");
                RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
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
                super.onQueueStatusUpdated();
            }

            @Override
            public void onPreloadStatusUpdated() {
                super.onPreloadStatusUpdated();
            }

            @Override
            public void onSendingRemoteMediaRequest() {
                super.onSendingRemoteMediaRequest();
            }

            @Override
            public void onAdBreakStatusUpdated() {
                super.onAdBreakStatusUpdated();
            }
        });
        remoteMediaClient.load(mediaInfo, mediaLoadOptions).setResultCallback(result -> {
            Log.d(LC, "load ready! Went smooth? " + result.getStatus().isSuccess());
            if (result.getStatus().isSuccess()) {
                audioPlayerCallback.onReady();
            }
        });
        audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
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
        }

        @Override
        public void onSessionEnded(Session session, int i) {
            Log.d(LC, "CastSession ended");
            onDisconnect();
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

        void onConnect(CastSession session) {
            castSession = session;
            castConnectionListener.onConnected();
        }

        void onDisconnect() {
            audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_PAUSED);
            castSession = null;
            castConnectionListener.onDisconnected();
        }
    }

    public abstract static class CastConnectionListener {
        abstract void onConnected();
        abstract void onDisconnected();
    }
}
