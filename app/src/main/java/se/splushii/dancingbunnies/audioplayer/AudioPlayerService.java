package se.splushii.dancingbunnies.audioplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
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

    private PlaybackController playbackController;
    private final PlaybackController.Callback audioPlayerManagerCallback = new PlaybackCallback();
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private PlaybackStateCompat playbackState;
    private boolean notify = true;
    private String currentParentId = MEDIA_ID_ROOT;

    private MusicLibraryService musicLibraryService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(LC, "Connected MusicLibraryService");
            MusicLibraryService.MusicLibraryBinder binder = (MusicLibraryService.MusicLibraryBinder) service;
            musicLibraryService = binder.getService();
            setupMediaSession();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicLibraryService = null;
            Log.d(LC, "Disconnected from MusicLibraryService");
        }
    };

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
        onLoadChildren(parentId, result, options);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result, @NonNull Bundle options) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        // TODO: Put EntryID.key() in parentId. Make a EntryID.from(key).
        // Make a EntryID.toMusicLibraryQueryOptions()
        // parentId is now usable!
        Log.d(LC, "onLoadChildren parentId: " + parentId);
        currentParentId = parentId;
        List<LibraryEntry> entries = musicLibraryService.getSubscriptionEntries(new MusicLibraryQuery(options));
        Log.d(LC, "onLoadChildren entries: " + entries.size());
        Collections.sort(entries);
        for (LibraryEntry entry: entries) {
            MediaBrowserCompat.MediaItem item = generateMediaItem(entry);
            mediaItems.add(item);
        }
        Log.d(LC, "onLoadChildren mediaItems: " + mediaItems.size());
        result.sendResult(mediaItems);
    }

    @Override
    public void onSearch(@NonNull String query, Bundle extras, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        List<LibraryEntry> entries = musicLibraryService.getSearchEntries(query);
        for (LibraryEntry entry: entries) {
            MediaBrowserCompat.MediaItem item = generateMediaItem(entry);
            mediaItems.add(item);
        }
        result.sendResult(mediaItems);
    }

    private MediaBrowserCompat.MediaItem generateMediaItem(LibraryEntry entry) {
        EntryID entryID = entry.entryID;
        Bundle extras = entryID.toBundle();
        if (entryID.type.equals(Meta.METADATA_KEY_MEDIA_ID)) {
            MediaMetadataCompat meta = musicLibraryService.getSongMetaData(entryID);
            extras.putAll(meta.getBundle());
        }
        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                .setMediaId(entryID.key())
                .setExtras(extras)
                .setTitle(entry.name())
                .build();
        int flags = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
        switch(entryID.type) {
            case Meta.METADATA_KEY_MEDIA_ID:
                break;
            default:
                flags |= MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
                break;
        }
        return new MediaBrowserCompat.MediaItem(desc, flags);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LC, "onStartCommand");
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LC, "onCreate");

        bindService(
                new Intent(this, MusicLibraryService.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );

        playbackController = new PlaybackController(this, audioPlayerManagerCallback);

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
        playbackController.onDestroy();
        unbindService(serviceConnection);
        musicLibraryService = null;
        mediaSession.release();
        super.onDestroy();
    }

    private void setupMediaSession() {
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
        mediaSession.setCallback(new MediaSessionCallback());

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.PAGER_SELECTION, MainActivity.PAGER_NOWPLAYING);
        PendingIntent pi = PendingIntent.getActivity(this, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setSessionActivity(pi);
        mediaSession.setMetadata(Meta.UNKNOWN_ENTRY);

        mediaSession.setQueue(new LinkedList<>());
    }

    private void setNotification() {
        if (!notify) {
            stopSelf();
            stopForeground(true);
            return;
        }
        String play_pause_string;
        int play_pause_drawable;
        switch (playbackState.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
                play_pause_string = getString(R.string.pause);
                play_pause_drawable = R.drawable.ic_pause;
                break;
            default:
                play_pause_string = getString(R.string.play);
                play_pause_drawable = R.drawable.ic_play;
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
                R.drawable.ic_stop,
                getString(R.string.stop),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                )
        ).build();
        NotificationCompat.Action action_next = new NotificationCompat.Action.Builder(
                R.drawable.ic_next,
                getString(R.string.next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
        ).build();
        NotificationCompat.Action action_previous = new NotificationCompat.Action.Builder(
                R.drawable.ic_prev,
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
                        .setContentIntent(controller.getSessionActivity())
                        .addAction(action_previous)
                        .addAction(action_play_pause)
                        .addAction(action_next)
                        .addAction(action_stop)
                        .setStyle(style)
                        .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Log.d(LC, "onPlay");
            playbackController.play();
        }

        @Override
        public void onPause() {
            Log.d(LC, "onPause");
            playbackController.pause();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(LC, "onPlayFromMediaId");
            PlaybackEntry playbackEntry = getPlaybackEntry(EntryID.from(extras));
            mediaSession.setQueue(
                    playbackController.playNow(playbackEntry)
            );
            setToast(playbackEntry, "Playing %s \"%s\" now!");
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            Log.d(LC, "onAddQueueItem");
            PlaybackEntry playbackEntry = getPlaybackEntry(EntryID.from(description));
            List<MediaSessionCompat.QueueItem> queue = playbackController.addToQueue(
                    playbackEntry,
                    PlayQueue.QueueOp.LAST
            );
            mediaSession.setQueue(queue);
            setToast(playbackEntry, "Added %s \"%s\" to queue!");
        }

        private void setToast(PlaybackEntry playbackEntry, String format) {
            String entryType = playbackEntry.meta.getString(Meta.METADATA_KEY_TYPE);
            if (Meta.METADATA_KEY_MEDIA_ID.equals(entryType)) {
                entryType = Meta.METADATA_KEY_TITLE;
            }
            String title = playbackEntry.meta.getString(entryType);
            String type = Meta.getHumanReadable(entryType);
            String message = String.format(Locale.getDefault(), format, type, title);
            Toast.makeText(
                    AudioPlayerService.this,
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            Log.d(LC, "onRemoveQueueItem");
            mediaSession.setQueue(
                    playbackController.removeFromQueue(getPlaybackEntry(EntryID.from(description)))
            );
        }

        @Override
        public void onSkipToNext() {
            Log.d(LC, "onSkipToNext");
            playbackController.skipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(LC, "onSkipToPrevious");
            playbackController.skipToPrevious();
        }

        @Override
        public void onSkipToQueueItem(long queueItemId) {
            Log.d(LC, "onSkipToQueueItem: " + queueItemId);
            playbackController.skipToQueueItem(queueItemId);
        }

        @Override
        public void onStop() {
            Log.d(LC, "onStop");
            playbackController.stop();
        }

        @Override
        public void onSeekTo(long pos) {
            Log.d(LC, "onSeekTo(" + pos + ")");
            playbackController.seekTo(pos);
        }

        PlaybackEntry getPlaybackEntry(EntryID entryID) {
            if (!Meta.METADATA_KEY_MEDIA_ID.equals(entryID.type)) {
                Log.e(LC, "Non-track entry. Unhandled! Beware!");
            }
            MediaMetadataCompat meta = musicLibraryService.getSongMetaData(entryID);
            AudioDataSource audioDataSource = musicLibraryService.getAudioData(entryID);
            return new PlaybackEntry(entryID, meta, audioDataSource);
        }
    }

    private class PlaybackCallback implements PlaybackController.Callback {
        @Override
        public void onPlayerChanged(AudioPlayer.Type audioPlayerType) {
            switch (audioPlayerType) {
                case LOCAL:
                    notify = true;
                    break;
                case CAST:
                default:
                    notify = false;
                    setNotification();
                    break;
            }
        }

        @Override
        public void onStateChanged(int newPlaybackState) {
            switch (newPlaybackState) {
                case PlaybackStateCompat.STATE_STOPPED:
                    setPlaybackState(newPlaybackState, 0L, PLAYBACK_SPEED_PAUSED);
                    mediaSession.setMetadata(Meta.UNKNOWN_ENTRY);
                    stopSelf();
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getCurrentPosition(),
                            PLAYBACK_SPEED_PAUSED
                    );
                    break;
                case PlaybackStateCompat.STATE_PLAYING:
                    if (notify) {
                        Log.d(LC, "startService");
                        startService(new Intent(
                                AudioPlayerService.this,
                                AudioPlayerService.class));
                    }
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getCurrentPosition(),
                            PLAYBACK_SPEED_PLAYING
                    );
                    break;
                case PlaybackStateCompat.STATE_BUFFERING:
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getCurrentPosition(),
                            PLAYBACK_SPEED_PAUSED
                    );
                    break;
                case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                    setPlaybackState(newPlaybackState, 0L, PLAYBACK_SPEED_PAUSED);
                    break;
                case PlaybackStateCompat.STATE_NONE:
                    Log.w(LC, "Unhandled state: STATE_NONE");
                    break;
                case PlaybackStateCompat.STATE_FAST_FORWARDING:
                    Log.w(LC, "Unhandled state: STATE_FAST_FORWARDING");
                    break;
                case PlaybackStateCompat.STATE_REWINDING:
                    Log.w(LC, "Unhandled state: STATE_REWINDING");
                    break;
                case PlaybackStateCompat.STATE_ERROR:
                    Log.w(LC, "Unhandled state: STATE_ERROR");
                    break;
                case PlaybackStateCompat.STATE_CONNECTING:
                    Log.w(LC, "Unhandled state: STATE_CONNECTING");
                    break;
            }
            setNotification();
        }

        private void setPlaybackState(int newPlaybackState, long position, float playbackSpeed) {
            playbackState = playbackStateBuilder
                    .setState(newPlaybackState, position, playbackSpeed)
                    .build();
            mediaSession.setPlaybackState(playbackState);
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            MediaMetadataCompat meta = musicLibraryService.getSongMetaData(entryID);
            mediaSession.setMetadata(meta);
        }
    }
}
