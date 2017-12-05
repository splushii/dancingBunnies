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
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.events.LibraryEvent;
import se.splushii.dancingbunnies.events.PlaySongEvent;
import se.splushii.dancingbunnies.events.PlaybackEvent;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.util.Util;

public class AudioPlayerService extends MediaBrowserServiceCompat
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {
    private static String LC = Util.getLogContext(AudioPlayerService.class);
    private MusicLibrary musicLibrary;
    public static final String MEDIA_ID_ROOT = "dancingbunnies.media.id.root";
    public static final String MEDIA_ID_ARTIST_ROOT = "dancingbunnies.media.id.root.artist";
    public static final String MEDIA_ID_ALBUM_ROOT = "dancingbunnies.media.id.root.album";
    public static final String MEDIA_ID_SONG_ROOT = "dancingbunnies.media.id.root.song";
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
    private AudioDataSource nowPlaying;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LC, "onBind");
        return super.onBind(intent);
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 @Nullable Bundle rootHints) {
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        Bundle options = new Bundle();
        options.putString(Meta.METADATA_KEY_API, MusicLibrary.API_ID_ANY);
        onLoadChildren(parentId, result, options);
    }

    public static Bundle generateMusicLibraryQueryOptions(String src, LibraryEntry.EntryType type) {
        Bundle options = new Bundle();
        options.putString(Meta.METADATA_KEY_API, src);
        options.putSerializable(Meta.METADATA_KEY_TYPE, type);
        return options;
    }

    private MusicLibraryQuery generateMusicLibraryQuery(@NonNull String parentId, @NonNull Bundle options) {
        String src = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                (LibraryEntry.EntryType) options.getSerializable(Meta.METADATA_KEY_TYPE);
        return new MusicLibraryQuery(src, parentId, type);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result, @NonNull Bundle options) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        ArrayList<? extends LibraryEntry> libraryEntries;
        Log.d(LC, "Total songs: " + musicLibrary.songs().size()
                + ", albums: " + musicLibrary.albums().size()
                + ", artists: " + musicLibrary.artists().size());
        Log.d(LC, "onLoadChildren parentId: " + parentId);
        switch(parentId) {
            case MEDIA_ID_SONG_ROOT:
                libraryEntries = musicLibrary.songs();
                break;
            case MEDIA_ID_ALBUM_ROOT:
                libraryEntries = musicLibrary.albums();
                break;
            case MEDIA_ID_ARTIST_ROOT:
            case MEDIA_ID_ROOT:
                libraryEntries = musicLibrary.artists();
                break;
            default:
                MusicLibraryQuery query = generateMusicLibraryQuery(parentId, options);
                libraryEntries = musicLibrary.getEntries(query);
                break;
        }
        Log.d(LC, "onLoadChildren entries: " + libraryEntries.size());
        for (LibraryEntry e: libraryEntries) {
            MediaBrowserCompat.MediaItem item = generateMediaItem(e);
            mediaItems.add(item);
        }
        Log.d(LC, "onLoadChildren mediaItems: " + mediaItems.size());
        result.sendResult(mediaItems);
    }

    private MediaBrowserCompat.MediaItem generateMediaItem(LibraryEntry e) {
        Bundle b = new Bundle();
        b.putString(Meta.METADATA_KEY_API, e.src());
        b.putSerializable(Meta.METADATA_KEY_TYPE, e.type());
        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                .setMediaId(e.id())
                .setExtras(b)
                .setTitle(e.name())
                .build();
        int flags = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
        switch(e.type()) {
            case ALBUM:
            case ARTIST:
                flags |= MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
                break;
            case SONG:
                break;
            default:
                Log.w(LC, "onLoadChildren: unhandled LibraryEntry type: " + e.type());
                break;
        }
        return new MediaBrowserCompat.MediaItem(desc, flags);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LC, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        musicLibrary = new MusicLibrary(this);
        Log.d(LC, "onCreate");
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
        Log.d(LC, "onDestroy");
        musicLibrary.onDestroy();
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
                Log.w(LC, "onPlay in wrong state: " + mediaPlayerState);
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
        Log.d(LC, "got playback event: " + pbe.action.name());
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
                Log.w(LC, "Unhandled PlaybackEvent: " + pbe.action.name());
                break;
        }
    }

    @Subscribe
    public void onMessageEvent(PlaySongEvent pse) {
        Log.d(LC, "got play song event with src: " + pse.src + " id: " + pse.id);
        resetMediaPlayer();
        onPlay();
        setCurrentSong(pse.src, pse.id);
    }

    @Subscribe
    public void onMessageEvent(LibraryEvent le) {
        Log.d(LC, "got library event");
        switch (le.action) {
            case FETCH_LIBRARY:
                musicLibrary.fetchAPILibrary(le.api, le.handler);
                break;
            case FETCH_PLAYLISTS:
                musicLibrary.fetchPlayLists(le.api, le.handler);
                break;
            default:
                Log.w(LC, "Unhandled LibraryEvent action: " + le.action);
                break;
        }
    }

    private void setCurrentSong(final String src, final String id) {
        final AudioDataSource audioDataSource = musicLibrary.getAudioData(src, id);
        nowPlaying = audioDataSource;
        // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
        audioDataSource.download(new AudioDataDownloadHandler() {
            @Override
            public void onStart() {
                Log.d(LC, "Download of src: " + src + ", id: " + id + " started.");
            }

            @Override
            public void onSuccess() {
                Log.d(LC, "Download succeeded\nsize: " + audioDataSource.getSize());
                setNewSource(audioDataSource);
            }

            @Override
            public void onFailure(String status) {
                Log.e(LC,  "Download of src: " + src + ", id: " + id + " failed: " + status);
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
