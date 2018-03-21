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
    private MediaPlayerInstance player;
    private MediaPlayerInstance nextPlayer;

    private class MediaPlayerInstance {
        MediaPlayer mediaPlayer;
        MediaPlayerState state;
        AudioDataSource audioDataSource;
        MediaPlayerInstance(AudioDataSource audioDataSource) {
            this.mediaPlayer = new MediaPlayer();
            this.state = MediaPlayerState.IDLE;
            this.audioDataSource = audioDataSource;
        }

        void prepareMediaPlayer() {
            Log.d(LC, "prepareMediaPlayer");
            mediaPlayer.setDataSource(audioDataSource);
            state = MediaPlayerState.INITIALIZED;
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
                    Log.d(LC, "onPlay in STARTED");
                    return false;
                default:
                    Log.w(LC, "onPlay in wrong state: " + state);
                    return false;
            }
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
                    Log.w(LC, "onPause in wrong state: " + state);
                    return false;
            }
            if (mediaPlayer == null) {
                return false;
            }
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
                case STOPPED:
                    break;
                default:
                    Log.w(LC, "onStop in wrong state: " + state);
                    return false;
            }
            mediaPlayer.stop();
            state = MediaPlayerState.STOPPED;
            mediaPlayer.release();
            audioDataSource = null;
            mediaPlayer = null;
            state = MediaPlayerState.NULL;
            return true;
        }
    }

    @Override
    public void setSource(AudioDataSource audioDataSource,
                          Runnable runWhenReady,
                          Runnable runWhenEnded) {
        if (nextPlayer != null) {
            nextPlayer.audioDataSource.close();
            nextPlayer.mediaPlayer.setOnPreparedListener(null);
            nextPlayer.mediaPlayer.setOnCompletionListener(null);
            nextPlayer.mediaPlayer.release();
            nextPlayer = null;
        }
        nextPlayer = new MediaPlayerInstance(audioDataSource);
        nextPlayer.mediaPlayer.setOnPreparedListener(mediaPlayer -> {
            nextPlayer.state = MediaPlayerState.PREPARED;
            if (player != null) {
                player.audioDataSource.close();
                player.mediaPlayer.setOnPreparedListener(null);
                player.mediaPlayer.setOnCompletionListener(null);
                player.mediaPlayer.release();
                player = null;
            }
            player = nextPlayer;
            runWhenReady.run();
        });
        nextPlayer.mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            nextPlayer.state = MediaPlayerState.PLAYBACK_COMPLETED;
            runWhenEnded.run();
        });
        // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
        nextPlayer.audioDataSource.download(new AudioDataDownloadHandler() {
            @Override
            public void onStart() {
                Log.d(LC, "Download started");
            }

            @Override
            public void onSuccess() {
                Log.d(LC, "Download succeeded\nsize: " + nextPlayer.audioDataSource.getSize());
                nextPlayer.prepareMediaPlayer();
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC, "nextPlayer download error: " + status);
            }
        });
    }

    @Override
    public long getCurrentPosition() {
        if (player == null) {
            return 0;
        }
        return player.mediaPlayer.getCurrentPosition();
    }

    @Override
    public boolean play() {
        return player != null && player.play();
    }

    @Override
    public boolean pause() {
        return player != null && player.pause();
    }

    @Override
    public boolean stop() {
        return player != null && player.stop();
    }
}
