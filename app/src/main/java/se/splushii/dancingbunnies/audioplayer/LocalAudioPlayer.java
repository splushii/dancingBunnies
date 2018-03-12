package se.splushii.dancingbunnies.audioplayer;

import android.media.MediaPlayer;
import android.util.Log;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer implements AudioPlayer, MediaPlayer.OnCompletionListener  {
    private static final String LC = Util.getLogContext(LocalAudioPlayer.class);
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
    private MediaPlayerState mediaPlayerState = MediaPlayerState.NULL;
    private MediaPlayer mediaPlayer;

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mediaPlayerState = MediaPlayerState.PLAYBACK_COMPLETED;
    }

    private void initializeMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayerState = MediaPlayerState.IDLE;
        }
    }

    private void resetMediaPlayer() {
        initializeMediaPlayer();
        mediaPlayer.reset();
        mediaPlayerState = MediaPlayerState.IDLE;
    }

    @Override
    public void setSource(AudioDataSource audioDataSource, Runnable runWhenReady) {
        // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
        audioDataSource.download(new AudioDataDownloadHandler() {
            @Override
            public void onStart() {
                Log.d(LC, "Download started.");
            }

            @Override
            public void onSuccess() {
                Log.d(LC, "Download succeeded\nsize: " + audioDataSource.getSize());
                LocalAudioPlayer.this.prepareMediaPlayer(audioDataSource, runWhenReady);
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC,  "Download failed: " + status);
            }
        });
    }

    private void prepareMediaPlayer(AudioDataSource audioDataSource, Runnable runWhenReady) {
        Log.d(LC, "prepareMediaPlayer");
        resetMediaPlayer();
        mediaPlayer.setDataSource(audioDataSource);
        mediaPlayerState = MediaPlayerState.INITIALIZED;
        mediaPlayer.setOnPreparedListener(mediaPlayer -> {
            mediaPlayerState = MediaPlayerState.PREPARED;
            runWhenReady.run();
        });
        mediaPlayer.prepareAsync();
        mediaPlayerState = MediaPlayerState.PREPARING;
    }

    @Override
    public long getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    @Override
    public void play() {
        switch (mediaPlayerState) {
            case PREPARED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                break;
            case STARTED:
                Log.d(LC, "onPlay in STARTED");
                return;
            default:
                Log.w(LC, "onPlay in wrong state: " + mediaPlayerState);
                return;
        }
        initializeMediaPlayer();
        mediaPlayer.start();
        mediaPlayerState = MediaPlayerState.STARTED;
    }

    @Override
    public void pause() {
        switch (mediaPlayerState) {
            case PAUSED:
            case STARTED:
                break;
            default:
                Log.w(LC, "onPause in wrong state: " + mediaPlayerState);
                return;
        }
        if (mediaPlayer == null) {
            return;
        }
        mediaPlayer.pause();
        mediaPlayerState = MediaPlayerState.PAUSED;
    }

    @Override
    public void stop() {
        switch (mediaPlayerState) {
            case PREPARED:
            case STARTED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
            case STOPPED:
                break;
            default:
                Log.w(LC, "onStop in wrong state: " + mediaPlayerState);
        }
        mediaPlayer.stop();
        mediaPlayerState = MediaPlayerState.STOPPED;
        mediaPlayer.release();
        mediaPlayer = null;
        mediaPlayerState = MediaPlayerState.NULL;
    }
}
