package se.splushii.dancingbunnies.audioplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.events.LibraryEvent;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.util.Util;

public class AudioPlayerService extends MediaBrowserServiceCompat {
    private static String LC = Util.getLogContext(AudioPlayerService.class);
    private static final String NOTIFICATION_CHANNEL_ID = "dancingbunnies.notification.channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "DancingBunnies";
    private static final int SERVICE_NOTIFICATION_ID = 1337;
    private static final float PLAYBACK_SPEED_PAUSED = 0f;
    private static final float PLAYBACK_SPEED_PLAYING = 1f;
    public static final String MEDIA_ID_ROOT = "dancingbunnies.media.id.root";
    public static final String MEDIA_ID_ARTIST_ROOT = "dancingbunnies.media.id.root.artist";
    public static final String MEDIA_ID_ALBUM_ROOT = "dancingbunnies.media.id.root.album";
    public static final String MEDIA_ID_SONG_ROOT = "dancingbunnies.media.id.root.song";

    private MusicLibrary musicLibrary;
    private AudioPlayer audioPlayer;
    private PlayQueue playQueue;
    private MediaSessionCompat mediaSession;
    private List<QueueItem> mediaSessionQueue;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private PlaybackStateCompat playbackState;

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

    public static String getMusicLibraryQueryOptionsString(Bundle options) {
        String api = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(options.getString(Meta.METADATA_KEY_TYPE));
        return "api: " + api + ", type: " + type.name();
    }

    private MusicLibraryQuery generateMusicLibraryQuery(@NonNull String parentId, @NonNull Bundle options) {
        String src = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(options.getString(Meta.METADATA_KEY_TYPE));
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
        Bundle b = EntryID.from(e).toBundle();
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
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        musicLibrary = new MusicLibrary(this);
        Log.d(LC, "onCreate");
        EventBus.getDefault().register(this);

        audioPlayer = new LocalAudioPlayer();

        playQueue = new PlayQueue(musicLibrary);

        mediaSession = new MediaSessionCompat(this, "AudioPlayerService");
        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)
                .setState(PlaybackStateCompat.STATE_NONE, 0, PLAYBACK_SPEED_PAUSED);
        playbackState = playbackStateBuilder.build();
        mediaSession.setPlaybackState(playbackState);
        mediaSession.setCallback(new AudioPlayerSessionCallback());

        Context context = getApplicationContext();
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.PAGER_SELECTION, MainActivity.PAGER_NOWPLAYING);
        PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setSessionActivity(pi);
        mediaSession.setMetadata(Meta.UNKNOWN_ENTRY);

        mediaSessionQueue = new LinkedList<>();
        mediaSession.setQueue(mediaSessionQueue);

        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onDestroy() {
        Log.d(LC, "onDestroy");
        musicLibrary.onDestroy();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void onPlay() {
        Log.d(LC, "onPlay");
        if (!audioPlayer.play()) {
            Log.w(LC, "AudioPlayer (" + audioPlayer.getClass().getSimpleName()
                    + ") could not play. ");
            return;
        }
        startService(new Intent(this, AudioPlayerService.class));
        playbackState = playbackStateBuilder.setState(
                PlaybackStateCompat.STATE_PLAYING,
                audioPlayer.getCurrentPosition(),
                PLAYBACK_SPEED_PLAYING
        ).build();
        mediaSession.setPlaybackState(playbackState);
        setNotification();
    }

    private void setNotification() {
        String play_pause_string;
        int play_pause_drawable;
        switch (playbackState.getState()) {
            case PlaybackState.STATE_PLAYING:
                play_pause_string = getString(R.string.pause);
                play_pause_drawable = android.R.drawable.ic_media_pause;
                break;
            default:
                play_pause_string = getString(R.string.play);
                play_pause_drawable = android.R.drawable.ic_media_play;
                break;
        }
        NotificationCompat.Action action_play_pause = new NotificationCompat.Action.Builder(
                play_pause_drawable,
                play_pause_string,
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
        ).build();
        NotificationCompat.Action action_stop = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_delete,
                getString(R.string.stop),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                )
        ).build();
        NotificationCompat.Action action_next = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_next,
                getString(R.string.next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
        ).build();
        NotificationCompat.Action action_previous = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_previous,
                getString(R.string.previous),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
        ).build();
        MediaStyle style = new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(1, 3);
        MediaControllerCompat controller = mediaSession.getController();
        String description = Meta.getLongDescription(controller.getMetadata());
        Notification notification =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(description)
                        .setContentText(description)
                        .setLargeIcon(controller.getMetadata().getDescription().getIconBitmap())
                        .setSmallIcon(R.drawable.ic_play)
                        // Enable launching the player by clicking the notification
                        .setContentIntent(controller.getSessionActivity())
                        .addAction(action_previous)
                        .addAction(action_play_pause)
                        .addAction(action_next)
                        .addAction(action_stop)
                        .setStyle(style)
                        .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    private void onPause() {
        Log.d(LC, "onPause");
        if (!audioPlayer.pause()) {
            Log.w(LC, "AudioPlayer (" + audioPlayer.getClass().getSimpleName()
                    + ") could not pause. ");
            return;
        }
        playbackState = playbackStateBuilder.setState(
                PlaybackStateCompat.STATE_PAUSED,
                audioPlayer.getCurrentPosition(),
                PLAYBACK_SPEED_PAUSED).build();
        mediaSession.setPlaybackState(playbackState);
        setNotification();
    }

    private void onStop() {
        Log.d(LC, "onStop");
        if (!audioPlayer.stop()) {
            Log.w(LC, "AudioPlayer (" + audioPlayer.getClass().getSimpleName()
                    + ") could not stop. ");
            return;
        }
        playbackState = playbackStateBuilder.setState(
                PlaybackStateCompat.STATE_STOPPED,
                audioPlayer.getCurrentPosition(),
                PLAYBACK_SPEED_PAUSED).build();
        mediaSession.setPlaybackState(playbackState);
        mediaSession.setMetadata(Meta.UNKNOWN_ENTRY);
        stopSelf();
        stopForeground(true);
    }

    private void prepareMedia(EntryID entryID, boolean playWhenReady) {
        Log.d(LC, "prepareMedia");
        final AudioDataSource audioDataSource = musicLibrary.getAudioData(entryID);
        MediaMetadataCompat meta = musicLibrary.getSongMetaData(entryID);
        mediaSession.setMetadata(meta);
        audioPlayer.setSource(
                audioDataSource,
                () -> { if (playWhenReady) { onPlay(); } },
                this::onSkipToNext
        );
    }

    private void onSkipToNext() {
        Log.d(LC, "onSkipToNext");
        EntryID entryID = playQueue.next();
        if (entryID == null) {
            onStop();
        } else {
            prepareMedia(entryID, true);
        }
    }

    private void onSkipToPrevious() {
        Log.d(LC, "onSkipToPrevious");
        EntryID entryID = playQueue.previous();
        if (entryID == null) {
            onStop();
        } else {
            prepareMedia(entryID, true);
        }
    }

    private void onSkipToQueueItem(long queueItemId) {
        Log.d(LC, "onSkipToQueueItem: " + queueItemId);
        EntryID entryID = playQueue.skipTo(queueItemId);
        prepareMedia(entryID, true);
    }

    private void onAddQueueItem(EntryID entryID, PlayQueue.QueueOp op) {
        Log.d(LC, "onAddQueueItem");
        mediaSession.setQueue(playQueue.addToQueue(entryID, op));
    }

    @Subscribe
    public void onMessageEvent(LibraryEvent le) {
        Log.d(LC, "got library event");
        switch (le.action) {
            case FETCH_LIBRARY:
                musicLibrary.fetchAPILibrary(le.api, le.handler);
                Log.d(LC, "Library fetched from " + le.api + "!");
                break;
            case FETCH_PLAYLISTS:
                musicLibrary.fetchPlayLists(le.api, le.handler);
                break;
            default:
                Log.w(LC, "Unhandled LibraryEvent action: " + le.action);
                break;
        }
    }

    private class AudioPlayerSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            super.onPlay();
            AudioPlayerService.this.onPlay();
        }

        @Override
        public void onPause() {
            super.onPause();
            AudioPlayerService.this.onPause();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            super.onPlayFromMediaId(mediaId, extras);
            AudioPlayerService.this.onAddQueueItem(EntryID.from(extras), PlayQueue.QueueOp.CURRENT);
            AudioPlayerService.this.prepareMedia(EntryID.from(extras), true);
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            super.onPrepareFromMediaId(mediaId, extras);
            AudioPlayerService.this.onAddQueueItem(EntryID.from(extras), PlayQueue.QueueOp.CURRENT);
            AudioPlayerService.this.prepareMedia(EntryID.from(extras), false);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            super.onAddQueueItem(description);
            AudioPlayerService.this.onAddQueueItem(
                    EntryID.from(description),
                    PlayQueue.QueueOp.LAST
            );
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            AudioPlayerService.this.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            AudioPlayerService.this.onSkipToPrevious();
        }

        @Override
        public void onSkipToQueueItem(long queueItemId) {
            super.onSkipToQueueItem(queueItemId);
            AudioPlayerService.this.onSkipToQueueItem(queueItemId);
        }

        @Override
        public void onStop() {
            super.onStop();
            AudioPlayerService.this.onStop();
        }
    }
}
