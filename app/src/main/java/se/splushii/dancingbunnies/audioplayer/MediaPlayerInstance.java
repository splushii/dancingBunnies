package se.splushii.dancingbunnies.audioplayer;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.util.Util;

class MediaPlayerInstance {
    private static final String LC = Util.getLogContext(MediaPlayerInstance.class);

    private enum MediaPlayerState {
        NULL,
        IDLE,
        INITIALIZED,
        PREPARING,
        STARTED,
        PAUSED,
        STOPPED,
        PLAYBACK_COMPLETED,
        PREPARED
    }
    private final LocalAudioPlayer.MediaPlayerCallback callback;
    final PlaybackEntry playbackEntry;

    private MediaPlayer mediaPlayer;
    private MediaPlayerState state;
    private boolean buffering = false;
    private long lastSeek = -1;

    private MediaPlayerInstance nextPlayer;

    MediaPlayerInstance(PlaybackEntry playbackEntry,
                        LocalAudioPlayer.MediaPlayerCallback callback) {
        reconstruct();
        this.playbackEntry = playbackEntry;
        this.callback = callback;
    }

    private void reconstruct() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );
        mediaPlayer.setOnErrorListener(mediaPlayerErrorListener);
        mediaPlayer.setOnPreparedListener(mp -> {
            buffering = false;
            state = MediaPlayerState.PREPARED;
            Log.d(LC, "MediaPlayer(" + title() + ") prepared");
            if (lastSeek != -1) {
                seekTo(lastSeek);
                lastSeek = -1;
            }
            callback.onPrepared(this);
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            Log.d(LC, "MediaPlayer(" + title() + ") completed");
            state = MediaPlayerState.PLAYBACK_COMPLETED;
            callback.onPlaybackCompleted(this);
        });
        state = MediaPlayerState.IDLE;
    }

    private void preload() {
        switch (state) {
            case IDLE:
                break;
            case NULL:
                reconstruct();
                break;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") preload in wrong state: " + state);
                return;
        }
        if (buffering) {
            Log.w(LC, "MediaPlayer(" + title() + ") preload when already buffering");
            return;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") preload");
        buffering = true;
        callback.onBuffering(this);
        callback.getAudioData(playbackEntry.entryID, new AudioDataDownloadHandler() {
            @Override
            public void onDownloading() {
                Log.d(LC, "MediaPlayer(" + title() + ") downloading audio data");
            }

            @Override
            public void onSuccess(AudioDataSource audioDataSource) {
                Log.d(LC, "MediaPlayer(" + title() + ") successfully got audio data");
                initialize(audioDataSource);
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC, "MediaPlayer(" + title() + ") could not get audio data: "
                        + status +".\nRetrying...");
                // TODO: Restrict number of attempts and show user error when maxed out.
                preload();
            }
        });
    }

    private void initialize(AudioDataSource audioDataSource) {
        switch (state) {
            case IDLE:
                break;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") "
                        + "initialize in wrong state: " + state);
                return;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") initializing");
        mediaPlayer.setDataSource(audioDataSource);
        state = MediaPlayerState.INITIALIZED;
        if (nextPlayer != null && nextPlayer.isPrepared()) {
            mediaPlayer.setNextMediaPlayer(nextPlayer.mediaPlayer);
        }
        prepare();
    }

    private void prepare() {
        switch (state) {
            case INITIALIZED:
            case STOPPED:
                break;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") "
                        + "prepare in wrong state: " + state);
                return;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") preparing");
        mediaPlayer.prepareAsync();
        state = MediaPlayerState.PREPARING;
    }

    boolean play() {
        switch (state) {
            case PREPARED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                break;
            case STARTED:
                Log.d(LC, "MediaPlayer(" + title() + ") play in STARTED");
                return false;
            case IDLE:
                preload();
                return false;
            case STOPPED:
                prepare();
                return false;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") play in wrong state: " + state);
                return false;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") starting");
        mediaPlayer.start();
        state = MediaPlayerState.STARTED;
        return true;
    }

    boolean pause() {
        switch (state) {
            case PAUSED:
            case STARTED:
                break;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") pause in wrong state: " + state);
                return false;
        }
        if (mediaPlayer == null) {
            return false;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") pausing");
        mediaPlayer.pause();
        state = MediaPlayerState.PAUSED;
        return true;
    }

    boolean stop() {
        switch (state) {
            case PREPARED:
            case STARTED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                break;
            case STOPPED:
            case NULL:
                return true;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") stop in wrong state: " + state);
                return false;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") stopping");
        mediaPlayer.stop();
        state = MediaPlayerState.STOPPED;
        return true;
    }

    void release() {
        if (mediaPlayer != null) {
            Log.d(LC, "MediaPlayer(" + title() + ") releasing");
            mediaPlayer.release();
            mediaPlayer = null;
        }
        state = MediaPlayerState.NULL;
    }

    boolean seekTo(long pos) {
        switch (state) {
            case IDLE:
            case INITIALIZED:
            case PREPARING:
            case STOPPED:
            case NULL:
                lastSeek = pos;
                Log.d(LC, "MediaPlayer(" + title() + ") "
                        + "setting initial seek to: " + lastSeek);
                return true;
            case PREPARED:
            case STARTED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                lastSeek = pos;
                Log.d(LC, "MediaPlayer(" + title() + ") seeking to " + pos);
                mediaPlayer.seekTo((int) pos);
                return true;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") seekTo in wrong state: " + state);
                return false;
        }
    }

    void setNext(MediaPlayerInstance nextPlayer) {
        this.nextPlayer = nextPlayer;
        switch (state) {
            case INITIALIZED:
            case PREPARING:
            case PREPARED:
            case STARTED:
            case PAUSED:
            case STOPPED:
            case PLAYBACK_COMPLETED:
                break;
            case NULL:
            case IDLE:
                Log.d(LC, "setNext in state " + state + ". Setting when initialized");
                return;
            default:
                Log.w(LC, "setNext in wrong state: " + state);
                return;
        }
        if (nextPlayer == null) {
            mediaPlayer.setNextMediaPlayer(null);
            return;
        }
        if (!nextPlayer.isPrepared()) {
            Log.d(LC, "setNext to non-prepared player. nextMediaPlayer not set");
            return;
        }
        mediaPlayer.setNextMediaPlayer(nextPlayer.mediaPlayer);
    }

    long getCurrentPosition() {
        long pos = 0L;
        switch (state) {
            case STOPPED:
                break;
            case PREPARED:
                pos = lastSeek;
                break;
            case STARTED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                pos = mediaPlayer.getCurrentPosition();
                break;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") "
                        + "getPlayerSeekPosition in wrong state: " + state);
                return 0L;
        }
        Log.d(LC, "MediaPlayer(" + title() + ") getPlayerSeekPosition: " + pos);
        return pos;
    }

    private boolean isStopped() {
        return MediaPlayerState.STOPPED.equals(state);
    }

    private boolean isIdle() {
        return MediaPlayerState.IDLE.equals(state) && !buffering;
    }

    private boolean isPrepared() {
        switch (state) {
            case NULL:
            case IDLE:
            case INITIALIZED:
            case PREPARING:
                return false;
            case PREPARED:
            case STARTED:
            case PAUSED:
            case STOPPED:
            case PLAYBACK_COMPLETED:
                return true;
            default:
                Log.w(LC, "isPrepared in unknown state: " + state);
                return false;
        }
    }

    int getPlaybackState() {
        switch (state) {
            case STARTED:
                return PlaybackStateCompat.STATE_PLAYING;
            case PLAYBACK_COMPLETED:
            case PAUSED:
            case PREPARED:
                return PlaybackStateCompat.STATE_PAUSED;
            case IDLE:
                if (buffering) {
                    return PlaybackStateCompat.STATE_BUFFERING;
                }
            case NULL:
            case STOPPED:
            case INITIALIZED:
                return PlaybackStateCompat.STATE_STOPPED;
            case PREPARING:
                return PlaybackStateCompat.STATE_BUFFERING;
            default:
                Log.w(LC, "MediaPlayer(" + title() + ") unknown state");
                return PlaybackStateCompat.STATE_ERROR;
        }
    }

    private final MediaPlayer.OnErrorListener mediaPlayerErrorListener = (mp, what, extra) -> {
        String msg;
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                switch (extra) {
                    case MediaPlayer.MEDIA_ERROR_IO:
                        msg = "MEDIA_ERROR_IO";
                        break;
                    case MediaPlayer.MEDIA_ERROR_MALFORMED:
                        msg = "MEDIA_ERROR_MALFORMED";
                        break;
                    case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                        msg = "MEDIA_ERROR_UNSUPPORTED";
                        break;
                    case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                        msg = "MEDIA_ERROR_TIMED_OUT";
                        break;
                    case 0x80000000: // MediaPlayer.MEDIA_ERROR_SYSTEM
                        msg = "MEDIA_ERROR_SYSTEM (low level weird error)";
                        break;
                    default:
                        msg = "Unhandled MEDIA_ERROR_UNKNOWN error extra: " + extra;
                        break;
                }
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                msg = "MEDIA_ERROR_SERVER_DIED";
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                msg = "MEDIA_ERROR_UNSUPPORTED";
                break;
            default:
                msg = "Unhandled error: " + what;
                break;
        }
        Log.e(LC, "MediaPlayer(" + title() + ") error: " + msg);
        return false;
    };

    String title() {
        return playbackEntry.toString();
    }

    void getReady() {
        if (isIdle()) {
            preload();
        } else if (isStopped()) {
            prepare();
        }
    }
}