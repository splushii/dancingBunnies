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
import android.os.ResultReceiver;
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

import com.google.android.gms.cast.framework.CastContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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
    private enum AudioPlayerType {
        LOCAL,
        CAST
    }
    private static String LC = Util.getLogContext(AudioPlayerService.class);
    private static final String NOTIFICATION_CHANNEL_ID = "dancingbunnies.notification.channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "DancingBunnies";
    private static final int SERVICE_NOTIFICATION_ID = 1337;
    private static final float PLAYBACK_SPEED_PAUSED = 0f;
    private static final float PLAYBACK_SPEED_PLAYING = 1f;
    public static final String MEDIA_ID_ROOT = "dancingbunnies.media.id.root";

    private AudioPlayer audioPlayer;
    private AudioPlayer.AudioPlayerCallback audioPlayerCallback;
    private LocalAudioPlayer localAudioPlayer;
    private CastAudioPlayer castAudioPlayer;
    private PlayQueue playQueue;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private PlaybackStateCompat playbackState;
    private boolean playWhenReady;
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
        Collections.sort(entries);
        for (LibraryEntry entry: entries) {
            MediaBrowserCompat.MediaItem item = generateMediaItem(entry);
            mediaItems.add(item);
        }
        result.sendResult(mediaItems);
    }

    private MediaBrowserCompat.MediaItem generateMediaItem(LibraryEntry entry) {
        EntryID entryID = entry.entryID;
        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                .setMediaId(entryID.key())
                .setExtras(entryID.toBundle())
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
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        bindService(
                new Intent(this, MusicLibraryService.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );

        Log.d(LC, "onCreate");

        localAudioPlayer = new LocalAudioPlayer();
        audioPlayer = localAudioPlayer;
        audioPlayerCallback = new AudioPlayerCallback();
        audioPlayer.setListener(audioPlayerCallback);

        CastContext castContext = CastContext.getSharedInstance(this);
        castAudioPlayer = new CastAudioPlayer(castContext, new CastAudioPlayer.CastConnectionListener() {
            @Override
            void onConnected() {
                setAudioPlayer(AudioPlayerType.CAST);
            }

            @Override
            void onDisconnected() {
                playWhenReady = false;
                setAudioPlayer(AudioPlayerType.LOCAL);
                setNotification();
            }
        });
        castAudioPlayer.onCreate();

        playQueue = new PlayQueue();

        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(notificationChannel);
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
        mediaSession.setCallback(new AudioPlayerSessionCallback());

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.PAGER_SELECTION, MainActivity.PAGER_NOWPLAYING);
        PendingIntent pi = PendingIntent.getActivity(this, 99 /*request code*/,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setSessionActivity(pi);
        mediaSession.setMetadata(Meta.UNKNOWN_ENTRY);

        mediaSession.setQueue(new LinkedList<>());
    }

    private void setAudioPlayer(AudioPlayerType audioPlayerType) {
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
        audioPlayer.removeListener();
        audioPlayer.stop();
        newAudioPlayer.setListener(audioPlayerCallback);
        audioPlayer = newAudioPlayer;
        EntryID entryID = playQueue.current();
        if (entryID == null) {
            return;
        }
        audioPlayer.setSource(
                musicLibraryService.getAudioData(entryID),
                musicLibraryService.getSongMetaData(entryID)
        );
        switch (playbackState.getState()) {
            case PlaybackState.STATE_PLAYING:
                // TODO: Seek to current position
                audioPlayer.play();
                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(LC, "onDestroy");
        castAudioPlayer.onDestroy();
        unbindService(serviceConnection);
        musicLibraryService = null;
        mediaSession.release();
        super.onDestroy();
    }

    private void onPlay() {
        Log.d(LC, "onPlay");
        playWhenReady = true;
        audioPlayer.play();
    }

    private void setNotification() {
        if (audioPlayer instanceof CastAudioPlayer) {
            stopForeground(true);
            return;
        }
        String play_pause_string;
        int play_pause_drawable;
        switch (playbackState.getState()) {
            case PlaybackState.STATE_PLAYING:
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

    private void onPause() {
        Log.d(LC, "onPause");
        playWhenReady = false;
        audioPlayer.pause();
    }

    private void onStop() {
        Log.d(LC, "onStop");
        playWhenReady = false;
        audioPlayer.stop();
    }

    private void prepareMedia(EntryID entryID, boolean forcePlayWhenReady) {
        if (forcePlayWhenReady) {
            this.playWhenReady = true;
        }
        Log.d(LC, "prepareMedia");
        final AudioDataSource audioDataSource = musicLibraryService.getAudioData(entryID);
        MediaMetadataCompat meta = musicLibraryService.getSongMetaData(entryID);
        audioPlayer.setSource(audioDataSource, meta);
    }

    private void onSkipToNext() {
        Log.d(LC, "onSkipToNext");
        EntryID entryID = playQueue.next();
        if (entryID == null) {
            onStop();
        } else {
            prepareMedia(entryID, false);
        }
    }

    private void onSkipToPrevious() {
        Log.d(LC, "onSkipToPrevious");
        EntryID entryID = playQueue.previous();
        if (entryID == null) {
            onStop();
        } else {
            prepareMedia(entryID, false);
        }
    }

    private void onSkipToQueueItem(long queueItemId) {
        Log.d(LC, "onSkipToQueueItem: " + queueItemId);
        EntryID entryID = playQueue.skipTo(queueItemId);
        if (entryID == null) {
            return;
        }
        prepareMedia(entryID, false);
    }

    private void onAddQueueItem(EntryID entryID, PlayQueue.QueueOp op) {
        Log.d(LC, "onAddQueueItem");
        MediaMetadataCompat meta = musicLibraryService.getSongMetaData(entryID);
        mediaSession.setQueue(playQueue.addToQueue(entryID, meta, op));
    }

    private void onRemoveQueueItem(EntryID entryID) {
        Log.d(LC, "onRemoveQueueItem");
        mediaSession.setQueue(playQueue.removeFromQueue(entryID));
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

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            AudioPlayerService.this.onAddQueueItem(EntryID.from(extras), PlayQueue.QueueOp.CURRENT);
            AudioPlayerService.this.prepareMedia(EntryID.from(extras), true);
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            AudioPlayerService.this.onAddQueueItem(EntryID.from(extras), PlayQueue.QueueOp.CURRENT);
            AudioPlayerService.this.prepareMedia(EntryID.from(extras), false);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            AudioPlayerService.this.onAddQueueItem(
                    EntryID.from(description),
                    PlayQueue.QueueOp.LAST
            );
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            AudioPlayerService.this.onRemoveQueueItem(EntryID.from(description));
        }

        @Override
        public void onSkipToNext() {
            AudioPlayerService.this.onSkipToNext();
        }

        @Override
        public void onSkipToPrevious() {
            AudioPlayerService.this.onSkipToPrevious();
        }

        @Override
        public void onSkipToQueueItem(long queueItemId) {
            AudioPlayerService.this.onSkipToQueueItem(queueItemId);
        }

        @Override
        public void onStop() {
            AudioPlayerService.this.onStop();
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            cb.send(0, null);
        }
    }

    private class AudioPlayerCallback implements AudioPlayer.AudioPlayerCallback {
        @Override
        public void onReady() {
            if (playWhenReady) {
                onPlay();
            }
        }

        @Override
        public void onEnded() {
            onSkipToNext();
        }

        @Override
        public void onStateChanged(int playBackState) {
            switch (playBackState) {
                case PlaybackStateCompat.STATE_NONE:
                    break;
                case PlaybackStateCompat.STATE_STOPPED:
                    playbackState = playbackStateBuilder.setState(
                            PlaybackStateCompat.STATE_STOPPED,
                            0,
                            PLAYBACK_SPEED_PAUSED).build();
                    mediaSession.setPlaybackState(playbackState);
                    mediaSession.setMetadata(Meta.UNKNOWN_ENTRY);
                    stopSelf();
                    stopForeground(true);
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    playbackState = playbackStateBuilder.setState(
                            PlaybackStateCompat.STATE_PAUSED,
                            audioPlayer.getCurrentPosition(),
                            PLAYBACK_SPEED_PAUSED).build();
                    mediaSession.setPlaybackState(playbackState);
                    setNotification();
                    break;
                case PlaybackStateCompat.STATE_PLAYING:
                    startService(new Intent(
                            AudioPlayerService.this,
                            AudioPlayerService.class));
                    playbackState = playbackStateBuilder.setState(
                            PlaybackStateCompat.STATE_PLAYING,
                            audioPlayer.getCurrentPosition(),
                            PLAYBACK_SPEED_PLAYING
                    ).build();
                    mediaSession.setPlaybackState(playbackState);
                    setNotification();
                    break;
                case PlaybackStateCompat.STATE_FAST_FORWARDING:
                    break;
                case PlaybackStateCompat.STATE_REWINDING:
                    break;
                case PlaybackStateCompat.STATE_BUFFERING:
                    break;
                case PlaybackStateCompat.STATE_ERROR:
                    break;
                case PlaybackStateCompat.STATE_CONNECTING:
                    break;
                case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                    break;
                case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                    break;
                case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                    break;
            }
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            MediaMetadataCompat meta = musicLibraryService.getSongMetaData(entryID);
            mediaSession.setMetadata(meta);
        }
    }
}
