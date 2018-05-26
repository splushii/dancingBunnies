package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.media.session.PlaybackState;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import com.google.android.gms.cast.framework.CastContext;

import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);

    private AudioPlayer audioPlayer;
    private AudioPlayer.Callback audioPlayerCallback = new AudioPlayerCallback();
    private int lastPlaybackState = -1;
    private LocalAudioPlayer localAudioPlayer;
    private CastAudioPlayer castAudioPlayer;
    private PlayQueue playQueue;
    private Callback callback;
    private boolean playWhenReady;

    PlaybackController(Context context, Callback callback) {
        this.callback = callback;
        localAudioPlayer = new LocalAudioPlayer();
        audioPlayer = localAudioPlayer;
        audioPlayer.setListener(audioPlayerCallback);

        CastContext castContext = CastContext.getSharedInstance(context);
        castAudioPlayer = new CastAudioPlayer(castContext, new CastAudioPlayer.CastConnectionListener() {
            @Override
            void onConnected() {
                setAudioPlayer(AudioPlayer.Type.CAST);
            }

            @Override
            void onDisconnected() {
                playWhenReady = false;
                setAudioPlayer(AudioPlayer.Type.LOCAL);
            }
        });
        castAudioPlayer.onCreate();

        playQueue = new PlayQueue(castContext);
        playQueue.onCreate();
    }

    void onDestroy() {
        castAudioPlayer.onDestroy();
        playQueue.onDestroy();
    }

    public long getCurrentPosition() {
        return audioPlayer.getCurrentPosition();
    }

    public void play() {
        playWhenReady = true;
        audioPlayer.play();
    }

    public void pause() {
        playWhenReady = false;
        audioPlayer.pause();
    }

    public void stop() {
        playWhenReady = false;
        audioPlayer.stop();
    }

    public void skipToNext() {
        PlaybackEntry playbackEntry = playQueue.next();
        if (playbackEntry == null) {
            stop();
        } else {
            prepareMedia(playbackEntry, false);
        }
    }

    public void skipToPrevious() {
        PlaybackEntry playbackEntry = playQueue.previous();
        if (playbackEntry == null) {
            stop();
        } else {
            prepareMedia(playbackEntry, false);
        }
    }

    public void skipToQueueItem(long queueItemId) {
        PlaybackEntry playbackEntry = playQueue.skipTo(queueItemId);
        if (playbackEntry == null) {
            return;
        }
        prepareMedia(playbackEntry, false);
    }

    public List<MediaSessionCompat.QueueItem> addToQueue(PlaybackEntry playbackEntry,
                                                         PlayQueue.QueueOp op) {
        // TODO: If casting, add to castQueue. Also add to (local) playQueue.
        return playQueue.addToQueue(playbackEntry, op);
    }

    public List<MediaSessionCompat.QueueItem> removeFromQueue(PlaybackEntry playbackEntry) {
        return playQueue.removeFromQueue(playbackEntry);
    }

    public void seekTo(long pos) {
        audioPlayer.seekTo(pos);
    }

    public List<MediaSessionCompat.QueueItem> playNow(PlaybackEntry playbackEntry) {
        List<MediaSessionCompat.QueueItem> queue =
                addToQueue(playbackEntry, PlayQueue.QueueOp.NEXT);
        if (playQueue.size() > 1) {
            playbackEntry = playQueue.next();
        }
        prepareMedia(playbackEntry, true);
        return queue;
    }

    private void prepareMedia(PlaybackEntry playbackEntry, boolean forcePlayWhenReady) {
        Log.d(LC, "prepareMedia");
        if (forcePlayWhenReady) {
            playWhenReady = true;
        }
        audioPlayer.setSource(playbackEntry);
    }

    private void setAudioPlayer(AudioPlayer.Type audioPlayerType) {
        AudioPlayer newAudioPlayer;
        switch (audioPlayerType) {
            case LOCAL:
                if (audioPlayer instanceof LocalAudioPlayer) {
                    return;
                }
                newAudioPlayer = localAudioPlayer;
                break;
            case CAST:
                if (audioPlayer instanceof CastAudioPlayer) {
                    return;
                }
                newAudioPlayer = castAudioPlayer;
                break;
            default:
                return;
        }
        audioPlayer.pause();
        long lastPos = audioPlayer.getCurrentPosition();
        audioPlayer.removeListener();
        audioPlayer.stop();
        newAudioPlayer.setListener(audioPlayerCallback);
        audioPlayer = newAudioPlayer;
        callback.onPlayerChanged(audioPlayerType);
        PlaybackEntry playbackEntry = playQueue.current();
        if (playbackEntry == null) {
            return;
        }
        audioPlayer.setSource(playbackEntry, lastPos);
        switch (lastPlaybackState) {
            case PlaybackState.STATE_PLAYING:
                audioPlayer.play();
                audioPlayer.seekTo(lastPos);
                break;
            default:
                break;
        }
    }

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
    }

    private class AudioPlayerCallback implements AudioPlayer.Callback {
        @Override
        public void onReady() {
            if (playWhenReady) {
                play();
            }
        }

        @Override
        public void onEnded() {
            skipToNext();
        }

        @Override
        public void onStateChanged(int newPlaybackState) {
            lastPlaybackState = newPlaybackState;
            callback.onStateChanged(newPlaybackState);
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            callback.onMetaChanged(entryID);
        }
    }
}
