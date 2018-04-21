package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaMetadataCompat;
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
import com.google.android.gms.common.api.PendingResult;

import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

// TODO: Use RemoteMediaClient.Callback() to get updates from cast receiver.

public class CastAudioPlayer implements AudioPlayer {
    private static final String LC = Util.getLogContext(CastAudioPlayer.class);
    private final SessionManager sessionManager;
    private final CastConnectionListener castConnectionListener;
    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListenerImpl();
    private CastSession castSession;
    private enum PlayerAction {
        PLAY,
        PAUSE,
        STOP
    }

    public CastAudioPlayer(CastContext castContext, CastConnectionListener castConnectionListener) {
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
    public CompletableFuture<Boolean> play() {
        return playerAction(PlayerAction.PLAY);
    }

    @Override
    public CompletableFuture<Boolean> pause() {
        return playerAction(PlayerAction.PAUSE);
    }

    @Override
    public CompletableFuture<Boolean> stop() {
        return playerAction(PlayerAction.STOP);
    }

    private CompletableFuture<Boolean> playerAction(PlayerAction action) {
        CompletableFuture<Boolean> ret = new CompletableFuture<>();
        if (castSession == null || !castSession.isConnected()) {
            ret.complete(false);
            return ret;
        }
        PendingResult pendingResult;
        switch (action) {
            case PLAY:
                pendingResult = castSession.getRemoteMediaClient().play();
                break;
            case PAUSE:
                pendingResult = castSession.getRemoteMediaClient().pause();
                break;
            case STOP:
                pendingResult = castSession.getRemoteMediaClient().stop();
                break;
            default:
                Log.d(LC, "Unknown PlayerAction: " + action.name());
                ret.complete(false);
                return ret;
        }
        return CompletableFuture.supplyAsync(() ->
                pendingResult.await().getStatus().isSuccess()
        );
    }

    @Override
    public void setSource(AudioDataSource audioDataSource, MediaMetadataCompat meta, Runnable runWhenReady, Runnable runWhenEnded) {
        MediaMetadata castMeta = new MediaMetadata();
        castMeta.putString(MediaMetadata.KEY_TITLE, meta.getString(Meta.METADATA_KEY_TITLE));
        castMeta.putString(MediaMetadata.KEY_ALBUM_TITLE, meta.getString(Meta.METADATA_KEY_ALBUM));
        castMeta.putString(MediaMetadata.KEY_ARTIST, meta.getString(Meta.METADATA_KEY_ARTIST));
        long duration = audioDataSource.getDuration();
        if (duration == 0L) {
            duration = meta.getLong(Meta.METADATA_KEY_DURATION);
        }
        MediaInfo mediaInfo = new MediaInfo.Builder(audioDataSource.getURL())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(audioDataSource.getContentType())
                .setMetadata(castMeta)
                .setStreamDuration(duration)
                .build();
        RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        MediaLoadOptions mediaLoadOptions = new MediaLoadOptions.Builder()
                .setAutoplay(false)
                .setPlayPosition(0) // TODO: Set start position. (e.g. when starting cast midsong)
                .build();
        remoteMediaClient.addListener(new RemoteMediaClient.Listener() {
            @Override
            public void onStatusUpdated() {
                String state;
                // TODO: Probably need to have some callback from AudioPlayer
                // to AudioPlayerService. To set player state and player metadata.
                // Also want to get play queue from cast.
                switch (remoteMediaClient.getMediaStatus().getPlayerState()) {
                    case MediaStatus.PLAYER_STATE_IDLE:
                        state = "IDLE";
                        break;
                    case MediaStatus.PLAYER_STATE_BUFFERING:
                        state = "BUFFERING";
                        break;
                    case MediaStatus.PLAYER_STATE_PAUSED:
                        state = "PAUSED";
                        break;
                    case MediaStatus.PLAYER_STATE_PLAYING:
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
            }

            @Override
            public void onQueueStatusUpdated() {

            }

            @Override
            public void onPreloadStatusUpdated() {

            }

            @Override
            public void onSendingRemoteMediaRequest() {

            }

            @Override
            public void onAdBreakStatusUpdated() {

            }
        });
        remoteMediaClient.load(mediaInfo, mediaLoadOptions).setResultCallback(result -> {
            if (result.getStatus().isSuccess()) {
                runWhenReady.run();
            }
        });
        // TODO: Set some listener for when track ended, or add all playqueue items to cast-queue
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
            castSession = null;
            castConnectionListener.onDisconnected();
        }
    }

    public abstract static class CastConnectionListener {
        abstract void onConnected();
        abstract void onDisconnected();
    }
}
