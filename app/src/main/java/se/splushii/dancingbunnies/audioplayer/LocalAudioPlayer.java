package se.splushii.dancingbunnies.audioplayer;

import android.media.MediaPlayer;
import android.util.Log;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer implements AudioPlayer {
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
    // TODO: Use multiple MediaPlayers to cache next track(s).
    private MediaPlayer mediaPlayer;
    // TODO: FIX: Bind datasource to mediaplayer. Cancel AudioDataSource when not needed anymore.
    // TODO: FIX: Bug when going next/previous fast multiple times.
    private AudioDataSource currentAudioDataSource;

    private void initializeMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayerState = MediaPlayerState.IDLE;
        }
    }

    private void resetMediaPlayer() {
        initializeMediaPlayer();
        mediaPlayer.reset();
        mediaPlayerState = MediaPlayerState.IDLE;
    }

    @Override
    public void setSource(AudioDataSource audioDataSource,
                          Runnable runWhenReady,
                          Runnable runWhenEnded) {
        currentAudioDataSource = audioDataSource;
        resetMediaPlayer();
        mediaPlayer.setOnPreparedListener(mediaPlayer -> {
            mediaPlayerState = MediaPlayerState.PREPARED;
            runWhenReady.run();
        });
        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            mediaPlayerState = MediaPlayerState.PLAYBACK_COMPLETED;
            runWhenEnded.run();
        });
        // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
        currentAudioDataSource.download(new AudioDataDownloadHandler() {
            @Override
            public void onStart() {
                Log.d(LC, "Download started.");
            }

            @Override
            public void onSuccess() {
                Log.d(LC, "Download succeeded\nsize: " + currentAudioDataSource.getSize());
                LocalAudioPlayer.this.prepareMediaPlayer();
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC,  "Download failed: " + status);
            }
        });
    }

    private void prepareMediaPlayer() {
        Log.d(LC, "prepareMediaPlayer");
        mediaPlayer.setDataSource(currentAudioDataSource);
        mediaPlayerState = MediaPlayerState.INITIALIZED;
        mediaPlayer.prepareAsync();
        mediaPlayerState = MediaPlayerState.PREPARING;
    }

    @Override
    public long getCurrentPosition() {
        if (mediaPlayer == null) {
            return 0;
        }
        return mediaPlayer.getCurrentPosition();
    }

    @Override
    public boolean play() {
        switch (mediaPlayerState) {
            case PREPARED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                break;
            case STARTED:
                Log.d(LC, "onPlay in STARTED");
                return false;
            default:
                Log.w(LC, "onPlay in wrong state: " + mediaPlayerState);
                return false;
        }
        initializeMediaPlayer();
        mediaPlayer.start();
        mediaPlayerState = MediaPlayerState.STARTED;
        return true;
    }

    @Override
    public boolean pause() {
        switch (mediaPlayerState) {
            case PAUSED:
            case STARTED:
                break;
            default:
                Log.w(LC, "onPause in wrong state: " + mediaPlayerState);
                return false;
        }
        if (mediaPlayer == null) {
            return false;
        }
        mediaPlayer.pause();
        mediaPlayerState = MediaPlayerState.PAUSED;
        return true;
    }

    @Override
    public boolean stop() {
        switch (mediaPlayerState) {
            case PREPARED:
            case STARTED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
            case STOPPED:
                break;
            default:
                Log.w(LC, "onStop in wrong state: " + mediaPlayerState);
                return false;
        }
        mediaPlayer.stop();
        mediaPlayerState = MediaPlayerState.STOPPED;
        mediaPlayer.release();
        mediaPlayer = null;
        mediaPlayerState = MediaPlayerState.NULL;
        return true;
    }
}
