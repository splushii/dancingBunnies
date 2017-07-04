package se.splushii.dancingbunnies.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.events.PlaySongEvent;
import se.splushii.dancingbunnies.events.PlaybackEvent;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class AudioPlayerService extends MediaBrowserServiceCompat
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    private static final String MEDIA_ID_ROOT = "dancingbunnies.media.id.root";
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
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder mediaStateBuilder;
    private Song nowPlaying;

    @Override
    public IBinder onBind(Intent intent) {
        System.out.println("onBind in AudioPlayerService");
        return super.onBind(intent);
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 @Nullable Bundle rootHints) {
        return new BrowserRoot(MEDIA_ID_ROOT, null); // TODO: return something useful
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("onStartCommand in AudioPlayerService");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("onCreate in AudioPlayerService");
        EventBus.getDefault().register(this);

        // TODO: handle play queue
        mediaSession = new MediaSessionCompat(this, "AudioPlayerService");
        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE);
        mediaSession.setPlaybackState(mediaStateBuilder.build());
        mediaSession.setCallback(new AudioPlayerSessionCallback());

        Context context = getApplicationContext();
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setSessionActivity(pi);
    }

    @Override
    public void onDestroy() {
        System.out.println("onDestroy in AudioPlayerService");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mediaPlayerState = MediaPlayerState.PREPARED;
        mediaPlayer.start();
        mediaPlayerState = MediaPlayerState.STARTED;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mediaPlayerState = MediaPlayerState.PLAYBACK_COMPLETED;
    }

    private void onPlay() {
        initializeMediaPlayer();
        startService(new Intent(this, AudioPlayerService.class));
        switch (mediaPlayerState) {
            case PREPARED:
            case PAUSED:
            case PLAYBACK_COMPLETED:
                mediaPlayer.start();
                break;
            default:
                System.out.println("onPlay in wrong state: " + mediaPlayerState);
                break;
        }
/*        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat metadata = controller.getMetadata();
        MediaDescriptionCompat description = metadata.getDescription();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setContentTitle(description.getTitle())
                .setContentText(description.getSubtitle())
                .setSubText(description.getDescription())
                .setLargeIcon(description.getIconBitmap())
        // Enable launching the player by clicking the notification
                .setContentIntent(controller.getSessionActivity())
        // Stop the service when the notification is swiped away
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_STOP))
        // Make the transport controls visible on the lockscreen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        // Add an app icon and set its accent color
        // Be careful about the color
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(ContextCompat.getColor(this, R.color.primaryDark))
        // Add a pause button
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_play, getString(R.string.pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_PLAY_PAUSE)))
        // Take advantage of MediaStyle features
                .setStyle(new NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0)
        // Add a cancel button
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                PlaybackStateCompat.ACTION_STOP)));
        startForeground(AUDIO_PLAYER_SERVICE_NOTIFICATION_ID, builder.build());
        */
    }

    private void initializeMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayerState = MediaPlayerState.IDLE;
        }
    }

    private void onPause() {
        mediaPlayer.pause();
        mediaPlayerState = MediaPlayerState.PAUSED;
    }

    private void onStop() {
        mediaPlayer.stop();
        mediaPlayerState = MediaPlayerState.STOPPED;
        mediaPlayer.release();
        mediaPlayer = null;
        mediaPlayerState = MediaPlayerState.NULL;
        stopSelf();
        stopForeground(true);
    }

    @Subscribe
    public void onMessageEvent(PlaybackEvent pbe) {
        System.out.println("got playback event in audioplayerservice: " + pbe.action.name());
        switch (pbe.action) {
            case PAUSE:
                onPause();
                break;
            case PLAY:
                onPlay();
                break;
            case STOP:
                onStop();
                break;
            default:
                System.out.println("Unhandled PlaybackEvent: " + pbe.action.name());
                break;
        }
    }

    @Subscribe
    public void onMessageEvent(PlaySongEvent pse) {
        System.out.println("got play song event in audioplayerservice");
        resetMediaPlayer();
        onPlay();
        setCurrentSong(pse.song, pse.lib);
    }

    public void setCurrentSong(final Song song, MusicLibrary lib) {
        // TODO: MusicLibrary should probably be in AudioPlayerService
        nowPlaying = song;
        final AudioDataSource audioDataSource = lib.getAudioData(song);
        // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
        audioDataSource.download(new AudioDataDownloadHandler() {
            @Override
            public void onStart() {
                System.out.println ("Download of " + song.name() + " started.");
            }

            @Override
            public void onSuccess() {
                System.out.println("Download succeeded");
                System.out.println("size: " + audioDataSource.getSize());
                setNewSource(audioDataSource);
            }

            @Override
            public void onFailure(String status) {
                System.out.println("Download of " + song.name() + " failed: " + status);
            }
        });
    }

    private void resetMediaPlayer() {
        initializeMediaPlayer();
        mediaPlayer.reset();
        mediaPlayerState = MediaPlayerState.IDLE;
    }

    private void setNewSource(AudioDataSource audioDataSource) {
        mediaPlayer.setDataSource(audioDataSource);
        mediaPlayerState = MediaPlayerState.INITIALIZED;
        mediaPlayer.prepareAsync();
        mediaPlayerState = MediaPlayerState.PREPARING;
    }

    private class AudioPlayerSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            AudioPlayerService.this.onPlay();
        }

        @Override
        public void onPause() {
            AudioPlayerService.this.onPause();
        }
    }
}
