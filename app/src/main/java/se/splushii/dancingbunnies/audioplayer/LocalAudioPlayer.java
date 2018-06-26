package se.splushii.dancingbunnies.audioplayer;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.LinkedList;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

class LocalAudioPlayer extends AudioPlayer {
    private static final String LC = Util.getLogContext(LocalAudioPlayer.class);
    private static final int NUM_PRELOAD_ITEMS = 2;

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
    private final MusicLibraryService musicLibraryService;
    private MediaPlayerInstance player;
    private LinkedList<MediaPlayerInstance> nextPlayers;

    LocalAudioPlayer(MusicLibraryService musicLibraryService) {
        super();
        this.musicLibraryService = musicLibraryService;
        nextPlayers = new LinkedList<>();
    }

    private class MediaPlayerInstance {
        private MediaPlayer mediaPlayer;
        private MediaPlayerState state;
        private PlaybackEntry playbackEntry;
        private boolean buffering = false;

        MediaPlayerInstance(PlaybackEntry playbackEntry) {
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
                if (this.equals(player)) {
                    Log.d(LC, "onReady: " + title());
                    updatePlaybackState();
                    audioPlayerCallback.onReady();
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(LC, "MediaPlayer(" + title() + ") completed");
                state = MediaPlayerState.PLAYBACK_COMPLETED;
                if (this.equals(player)) {
                    audioPlayerCallback.onEnded();
                } else {
                    Log.e(LC, "This should never happen...");
                }
            });
            state = MediaPlayerState.IDLE;
            this.playbackEntry = playbackEntry;
        }

        void preload() {
            switch (state) {
                case IDLE:
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") preload in wrong state: " + state);
                    return;
            }
            buffering = true;
            if (this.equals(player)) {
                audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_BUFFERING);
            }
            musicLibraryService.getAudioData(playbackEntry.entryID, new AudioDataDownloadHandler() {
                @Override
                public void onStart() {
                    Log.d(LC, "MediaPlayer(" + title() + ") getting audio data");
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
                case PREPARED:
                case STARTED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") seekTo in wrong state: " + state);
                    return false;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") seeking");
            mediaPlayer.seekTo((int) pos);
            return true;
        }

        long getCurrentPosition() {
            switch (state) {
                case PREPARED:
                case STARTED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                case STOPPED:
                    break;
                default:
                    Log.w(LC, "MediaPlayer(" + title() + ") "
                            + "getCurrentPosition in wrong state: " + state);
                    return 0L;
            }
            Log.d(LC, "MediaPlayer(" + title() + ") getCurrentPosition");
            return mediaPlayer.getCurrentPosition();
        }

        boolean isReady() {
            switch (state) {
                case PREPARED:
                case STARTED:
                case PAUSED:
                case PLAYBACK_COMPLETED:
                    return true;
                default:
                    return false;
            }
        }

        boolean isStopped() {
            return MediaPlayerState.STOPPED.equals(state);
        }

        private int getPlaybackState() {
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

        private String title() {
            return playbackEntry.meta.getString(Meta.METADATA_KEY_TITLE);
        }
    }

    @Override
    public long getCurrentPosition() {
        if (player == null) {
            return 0;
        }
        return player.getCurrentPosition();
    }

    @Override
    public void play() {
        if (player != null) {
            if (player.isStopped()) {
                player.prepare();
            } else {
                player.play();
            }
        }
        for (MediaPlayerInstance mediaPlayerInstance: nextPlayers) {
            if (mediaPlayerInstance.isStopped()) {
                mediaPlayerInstance.prepare();
            }
        }
        updatePlaybackState();
    }

    @Override
    public void pause() {
        if (player != null) {
            player.pause();
        }
        updatePlaybackState();
    }

    @Override
    public void stop() {
        if (player != null) {
            player.stop();
        }
        for (MediaPlayerInstance mediaPlayerInstance: nextPlayers) {
            mediaPlayerInstance.stop();
        }
        updatePlaybackState();
    }

    @Override
    void seekTo(long pos) {
        if (player != null) {
            player.seekTo(pos);
        }
        updatePlaybackState();
    }

    @Override
    int getNumToPreload() {
        return NUM_PRELOAD_ITEMS;
    }

    @Override
    int getNumPreloadedNext() {
        int num = nextPlayers.size();
        if (player != null) {
            num++;
        }
        return num;
    }

    @Override
    void addPreloadNext(PlaybackEntry playbackEntry) {
        addPreloadNext(playbackEntry, nextPlayers.size() + 1);
    }

    @Override
    void addPreloadNext(PlaybackEntry playbackEntry, int index) {
        Log.d(LC, "addPreloadNext: " + playbackEntry.meta.getString(Meta.METADATA_KEY_TITLE));
        MediaPlayerInstance mediaPlayerInstance = new MediaPlayerInstance(playbackEntry);
        if (index == 0) {
            setCurrentPlayer(mediaPlayerInstance);
        } else {
            nextPlayers.add(--index, mediaPlayerInstance);
        }
        mediaPlayerInstance.preload();
    }

    @Override
    void clearPreload() {
        // TODO: implement
        Log.e(LC, "clearPreload not implemented");
    }

    @Override
    void next() {
        // TODO: Set error state if unsuccessful
        MediaPlayerInstance previousPlayer = player;
        setCurrentPlayer(nextPlayers.poll());
        // TODO: Add previousPlayer to previous preload? Nope
        // TODO: Remove one item from previous preload
        if (previousPlayer != null) {
            previousPlayer.release();
        }
        updatePlaybackState();
        if (player != null && player.isReady()) {
            audioPlayerCallback.onReady();
        }
        audioPlayerCallback.onPreloadNextConsumed();
    }

    @Override
    void previous() {
        // TODO: implement
        // TODO: Set error state if unsuccessful
        Log.e(LC, "previous not implemented");
    }

    private void updatePlaybackState() {
        if (player == null) {
            audioPlayerCallback.onStateChanged(PlaybackStateCompat.STATE_STOPPED);
            return;
        }
        audioPlayerCallback.onStateChanged(player.getPlaybackState());
    }

    private void setCurrentPlayer(MediaPlayerInstance mediaPlayerInstance) {
        player = mediaPlayerInstance;
        Log.d(LC, "setCurrentPlayer: " + (player == null ? "null" : player.title()));
        if (player == null) {
            audioPlayerCallback.onMetaChanged(EntryID.from(Meta.UNKNOWN_ENTRY));
        } else {
            audioPlayerCallback.onMetaChanged(mediaPlayerInstance.playbackEntry.entryID);
        }
    }
}
