package se.splushii.dancingbunnies.audioplayer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFutureTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.Query;
import se.splushii.dancingbunnies.musiclibrary.QueryEntry;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.musiclibrary.QueryTree;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

import static androidx.mediarouter.media.MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL;
import static androidx.mediarouter.media.MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE;

// TODO: Handle incoming call. PhoneStateListener.LISTEN_CALL_STATE.
public class AudioPlayerService extends MediaBrowserServiceCompat {
    private static final String LC = Util.getLogContext(AudioPlayerService.class);
    private static final String NOTIFICATION_CHANNEL_ID = "dancingbunnies.notification.channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "DancingBunnies";
    private static final int SERVICE_NOTIFICATION_ID = 1337;
    private static final float PLAYBACK_SPEED_PAUSED = 0f;
    private static final float PLAYBACK_SPEED_PLAYING = 1f;
    private static final String MEDIA_ID_ROOT = "dancingbunnies.media.id.root";

    public static final String SESSION_EVENT_PLAYLIST_SELECTION_CHANGED =
            "se.splushii.dancingbunnies.session_event.playlist_selection_changed";
    public static final String SESSION_EVENT_CURRENT_ENTRY_CHANGED =
            "se.splushii.dancingbunnies.session_event.current_entry_changed";
    public static final String SESSION_EVENT_RANDOM_CHANGED =
            "se.splushii.dancingbunnies.session_event.random_changed";

    private static final String COMMAND_SET_CURRENT_PLAYLIST = "SET_CURRENT_PLAYLIST";
    private static final String COMMAND_PLAY_ENTRYIDS = "PLAY_ENTRYIDS";
    private static final String COMMAND_QUEUE_ENTRYIDS = "QUEUE_ENTRYIDS";
    private static final String COMMAND_DEQUEUE = "DEQUEUE";
    private static final String COMMAND_CLEAR_QUEUE = "CLEAR_QUEUE";
    private static final String COMMAND_MOVE_QUEUE_ITEMS = "MOVE_QUEUE_ITEMS";
    private static final String COMMAND_SHUFFLE_QUEUE_ITEMS = "SHUFFLE_QUEUE_ITEMS";
    private static final String COMMAND_SORT_QUEUE_ITEMS = "SORT_QUEUE_ITEMS";
    private static final String COMMAND_PLAY_QUERY_BUNDLES = "PLAY_QUERY_BUNDLES";
    private static final String COMMAND_QUEUE_QUERY_BUNDLES = "QUEUE_QUERY_BUNDLES";
    private static final String COMMAND_TOGGLE_RANDOM = "TOGGLE_RANDOM";

    public static final String BUNDLE_KEY_CURRENT_PLAYBACK_ENTRY_BUNDLE =
            "dancingbunnies.bundle.key.audioplayerservice.playback_entry_bundle";
    public static final String BUNDLE_KEY_CURRENT_PLAYLIST_POS =
            "dancingbunnies.bundle.key.audioplayerservice.pos";
    public static final String BUNDLE_KEY_CURRENT_PLAYLIST_ID_BUNDLE =
            "dancingbunnies.bundle.key.audioplayerservice.playlist_id_bundle";
    public static final String BUNDLE_KEY_PLAYBACK_ENTRY =
            "dancingbunnies.bundle.key.audioplayerservice.playback_entry";
    static final String BUNDLE_KEY_PLAYLIST_ID =
            "dancingbunnies.bundle.key.audioplayerservice.playlist_id";
    public static final String BUNDLE_KEY_RANDOM =
            "dancingbunnies.bundle.key.audioplayerservice.random";

    private final Object mediaSessionExtrasLock = new Object();

    public static final int QUEUE_LAST = -1;

    public static final String STARTCMD_INTENT_CAST_ACTION = "dancingbunnies.intent.castaction";

    public static final String CAST_ACTION_TOGGLE_PLAYBACK = "TOGGLE_PLAYBACK";
    public static final String CAST_ACTION_NEXT = "NEXT";

    private PlaybackControllerStorage playbackControllerStorage;
    // TODO: Do null checks on playbackController (and let caller know)
    // TODO: Maybe wrap playbackController with getter (see AudioBrowser.isSessionReady())
    private PlaybackController playbackController;
    private final PlaybackController.Callback audioPlayerManagerCallback = new PlaybackCallback();

    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder playbackStateBuilder;
    private PlaybackStateCompat playbackState;
    private boolean casting = false;
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private RemoteVolumeProvider remoteVolumeProvider;

    private final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private final MakeSomeNoiseReceiver makeSomeNoiseReceiver = new MakeSomeNoiseReceiver();
    private boolean isNoiseReceiverRegistered;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean playOnAudioFocusGain;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LC, "onBind");
        mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        );
        return super.onBind(intent);
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 @Nullable Bundle rootHints) {
        return new BrowserRoot(MEDIA_ID_ROOT, null);
    }

    private final HashSet<String> subscriptionIDs = new HashSet<>();
    private final HashMap<String, List<MediaBrowserCompat.MediaItem>> subscriptionResults = new HashMap<>();
    private final HashMap<String, Observer<List<QueryEntry>>> subscriptionObservers = new HashMap<>();
    private final HashMap<String, LiveData<List<QueryEntry>>> subscriptionLiveData = new HashMap<>();
    @Override
    public void onSubscribe(String id, Bundle options) {
        subscriptionIDs.add(id);
        subscriptionResults.put(id, new ArrayList<>());
        String showField = options.getString(Query.BUNDLE_KEY_SHOW);
        List<String> sortFields = options.getStringArrayList(Query.BUNDLE_KEY_SORT);
        boolean sortOrderAscending = options.getBoolean(Query.BUNDLE_KEY_SORT_ORDER);
        QueryNode queryNode = QueryNode.fromJSON(
                options.getString(Query.BUNDLE_KEY_QUERY_TREE)
        );
        LiveData<List<QueryEntry>> entries = MusicLibraryService.getSubscriptionEntries(
                this,
                showField,
                sortFields,
                sortOrderAscending,
                queryNode
        );
        subscriptionLiveData.put(id, entries);
        Observer<List<QueryEntry>> observer = queryEntries -> {
            List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
            Log.d(LC, "onSubscribe(" + id + ") entries: " + queryEntries.size());
            for (QueryEntry entry: queryEntries) {
                MediaBrowserCompat.MediaItem item = generateMediaItem(entry);
                mediaItems.add(item);
            }
            Log.d(LC, "onSubscribe(" + id + ") mediaItems: " + mediaItems.size());
            subscriptionResults.put(id, mediaItems);
            notifyChildrenChanged(id);
        };
        subscriptionObservers.put(id, observer);
        entries.observeForever(observer);
        Log.d(LC, "onSubscribe subscriptions: " + subscriptionIDs.size()
                + " id: " + id + " options: " + options.toString());
    }

    @Override
    public void onUnsubscribe(String id) {
        subscriptionIDs.remove(id);
        subscriptionResults.remove(id);
        LiveData<List<QueryEntry>> liveData = subscriptionLiveData.remove(id);
        Observer<List<QueryEntry>> observer = subscriptionObservers.remove(id);
        if (liveData != null && observer != null) {
            liveData.removeObserver(observer);
        }
        Log.d(LC, "onUnsubscribe subscriptions: " + subscriptionIDs.size() + " id: " + id);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        Bundle options = new Bundle();
        onLoadChildren(parentId, result, options);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result,
                               @NonNull Bundle options) {
        // TODO: Put EntryID.key() in parentId. Make a EntryID.from(key).
        // Make a EntryID.toMusicLibraryQueryOptions()
        // parentId is now usable!
        Log.d(LC, "onLoadChildren parentId: " + parentId);
        result.sendResult(subscriptionResults.get(parentId));
    }

    @Override
    public void onSearch(@NonNull String query, Bundle extras, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(MusicLibraryService.getSearchEntries(this, query).stream()
                .map(e -> generateMediaItem(new QueryEntry(e, e.id, null)))
                .collect(Collectors.toList())
        );
    }

    public static MediaBrowserCompat.MediaItem generateMediaItem(QueryEntry entry) {
        Bundle extras = entry.toBundle();
        MediaDescriptionCompat desc = new MediaDescriptionCompat.Builder()
                .setMediaId(entry.entryID.key())
                .setExtras(extras)
                .setTitle(entry.name())
                .build();
        int flags = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
        if (!Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(entry.entryID.type)) {
            flags |= MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
        }
        return new MediaBrowserCompat.MediaItem(desc, flags);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LC, "onStartCommand");
        mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
        );
        if (intent != null && intent.hasExtra(STARTCMD_INTENT_CAST_ACTION)) {
            handleCastIntent(intent);
        } else {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
        }
        return START_STICKY;
    }

    private void handleCastIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        String action = extras.getString(STARTCMD_INTENT_CAST_ACTION);
        if (action == null) {
            return;
        }
        Log.d(LC, "CAST_ACTION: " + action);
        CompletableFuture<Void> result;
        switch (action) {
            case CAST_ACTION_TOGGLE_PLAYBACK:
                result = playbackController.playPause();
                break;
            case CAST_ACTION_NEXT:
                result = playbackController.skipToNext();
                break;
            default:
                Log.e(LC, "Cast action " + action + " not supported");
                result = CompletableFuture.completedFuture(null);
                break;
        }
        result.handle(this::handleControllerResult);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LC, "onCreate");
        playbackControllerStorage = PlaybackControllerStorage.getInstance(this);
        setupAudioFocus();
        setupMediaSession();
        setupMediaRouter();
        setupPlaybackController();
        playbackController.initialize();
        setupNotification();
    }

    private final MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(@NonNull MediaRouter router,
                                    @NonNull MediaRouter.RouteInfo route,
                                    int reason) {
            Log.d(LC, "route selected: "
                    + route.getName()
                    + " (" + route.getDescription() + ")");
            setVolumeHandling(route, true);
            switch (reason) {
                case MediaRouter.UNSELECT_REASON_STOPPED:
                    Log.d(LC, "Previous route unselected because it was stopped");
                    if (playbackController != null) {
                        playbackController.pause()
                                .handle(AudioPlayerService.this::handleControllerResult);
                    }
                    break;
                case MediaRouter.UNSELECT_REASON_DISCONNECTED:
                    Log.d(LC, "Previous route unselected because it was disconnected");
                    if (playbackController != null) {
                        playbackController.pause()
                                .handle(AudioPlayerService.this::handleControllerResult);
                    }
                    break;
                case MediaRouter.UNSELECT_REASON_ROUTE_CHANGED:
                    Log.d(LC, "Previous route unselected because it was changed");
                    // Continue playback
                    break;
                default:
                case MediaRouter.UNSELECT_REASON_UNKNOWN:
                    Log.e(LC, "Previous route unselected with unknown reasons");
                    break;
            }
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            if (route.isSelected()) {
                Log.d(LC, "selected route changed: "
                        + route.getName()
                        + " (" + route.getDescription() + ")");
                setVolumeHandling(route, false);
            }
        }
    };

    private void setVolumeHandling(MediaRouter.RouteInfo route, boolean force) {
        switch (route.getPlaybackType()) {
            case PLAYBACK_TYPE_LOCAL:
                if (force || remoteVolumeProvider != null) {
                    remoteVolumeProvider = null;
                    mediaSession.setPlaybackToLocal(route.getPlaybackStream());
                }
                break;
            case PLAYBACK_TYPE_REMOTE:
                if (force || remoteVolumeProvider == null) {
                    remoteVolumeProvider = new RemoteVolumeProvider(route);
                    mediaSession.setPlaybackToRemote(remoteVolumeProvider);
                } else {
                    remoteVolumeProvider.updateVolumeFromRoute();
                }
                break;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(LC, "onDestroy");
        if (playbackController != null) {
            playbackController.onDestroy(!casting);
            playbackController = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (mediaRouter != null) {
            mediaRouter.removeCallback(mediaRouterCallback);
            mediaRouter = null;
        }
        super.onDestroy();
    }

    private boolean requestAudioFocus() {
        int res = audioManager.requestAudioFocus(audioFocusRequest);
        switch (res) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                return false;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                return true;
            case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                Log.w(LC, "Delayed audio focus not configured. This should never happen.");
                return false;
            default:
                Log.e(LC, "Unknown audio focus request result: " + res);
                return false;
        }
    }

    private void setupAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    playbackController.pause()
                            .handle(this::handleControllerResult);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Temporarily pause playback
                    if (isPlayingState()) {
                        playOnAudioFocusGain = true;
                    }
                    playbackController.pause()
                            .handle(this::handleControllerResult);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Lower the volume, keep playing. Let Android handle this automatically.
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (playOnAudioFocusGain) {
                        playbackController.play()
                                .handle(this::handleControllerResult);
                        playOnAudioFocusGain = false;
                    }
                    break;
                default:
                    Log.e(LC, "Unhandled audio focus change: " + focusChange);
                    break;
            }
        };
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build();
    }

    private void setupPlaybackController() {
        playbackController = new PlaybackController(
                this,
                playbackControllerStorage,
                audioPlayerManagerCallback
        );
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "AudioPlayerService");
        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                        | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                        | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
//                      | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS is intentionally not supported
//                        TODO: Implement
//                        | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
//                        | PlaybackStateCompat.ACTION_SET_RATING
//                        | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                )
                .setState(PlaybackStateCompat.STATE_NONE, 0, PLAYBACK_SPEED_PAUSED);
        playbackState = playbackStateBuilder.build();
        mediaSession.setPlaybackState(playbackState);
        mediaSession.setCallback(new MediaSessionCallback());

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.INTENT_EXTRA_PAGER_SELECTION, MainActivity.PAGER_NOWPLAYING);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                99 /*request code*/,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        mediaSession.setSessionActivity(pi);
        mediaSession.setMetadata(Meta.UNKNOWN_ENTRY.toMediaMetadataCompat());

        mediaSession.setQueueTitle("Queue");
        mediaSession.setQueue(new LinkedList<>());

        mediaSession.setActive(true);
    }

    private void setupMediaRouter() {
        mediaRouter = MediaRouter.getInstance(this);
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build();
        mediaRouter.setOnPrepareTransferListener((fromRoute, toRoute) -> {
            Log.d(LC, "prepare route transfer from "
                    + fromRoute.getName() + " (" + fromRoute.getDescription() + ")"
                    + " to "
                    + toRoute.getName() + " (" + toRoute.getDescription() + ")"
            );
            ListenableFutureTask<Void> listenableFutureTask = ListenableFutureTask.create(() -> {
                Log.d(LC, "route transfer preparing...");
                // TODO: Transfer state here instead of special case for Cast in PlaybackController?
                //       Not interesting until the Cast framework plays nice with MediaRouter
                Log.d(LC, "route transfer prepared!");
            }, null);
            listenableFutureTask.run();
            return listenableFutureTask;
        });
    }

    private void setupNotification() {
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(notificationChannel);
    }

    public static class RemoteVolumeProvider extends VolumeProviderCompat {
        private static final int MAX_STEPS = 50; // Cast devices generally have 50 steps
        private final float routeVolumePerStep;
        private final MediaRouter.RouteInfo routeInfo;

        public RemoteVolumeProvider(MediaRouter.RouteInfo routeInfo) {
            super(
                    VOLUME_CONTROL_ABSOLUTE,
                    getMaxSteps(
                            routeInfo.getVolumeMax()
                    ),
                    getSteps(
                            getMaxSteps(routeInfo.getVolumeMax()),
                            routeInfo.getVolume(),
                            routeInfo.getVolumeMax()
                    )
            );
            routeVolumePerStep = (float) routeInfo.getVolumeMax() / getMaxVolume();
            Log.d(LC, "New RemoteVolumeProvider for route: "
                    + routeInfo.getName() + "(" + routeInfo.getDescription() + ")"
                    + " with initial volume of " + getCurrentVolume() + "/" + getMaxVolume()
            );
            this.routeInfo = routeInfo;
        }

        public static int getMaxSteps(int maxVolume) {
            return Math.max(MAX_STEPS, maxVolume);
        }

        public static int getSteps(int maxSteps, int routeVolume, int routeMaxVolume) {
            return (int) (maxSteps * (float) routeVolume / routeMaxVolume);
        }

        @Override
        public void onSetVolumeTo(int volume) {
            setVolume(volume);
        }

        @Override
        public void onAdjustVolume(int direction) {
            int volume = getCurrentVolume() + direction;
            setVolume(volume);
        }

        private void setVolume(int volume) {
            routeInfo.requestSetVolume((int)(volume * routeVolumePerStep));
            setCurrentVolume(volume);
        }

        private void updateVolumeFromRoute() {
            int volume = getSteps(getMaxVolume(), routeInfo.getVolume(), routeInfo.getVolumeMax());
            setCurrentVolume(volume);
        }
    }

    private boolean isStoppedState() {
        return isStoppedState(playbackState.getState());
    }

    static boolean isStoppedState(int state) {
        return state == PlaybackStateCompat.STATE_ERROR
                || state == PlaybackStateCompat.STATE_NONE
                || state == PlaybackStateCompat.STATE_STOPPED;
    }

    private boolean isPlayingState() {
        return isPlayingState(playbackState.getState());
    }

    static boolean isPlayingState(int state) {
        return state == PlaybackStateCompat.STATE_PLAYING;
    }

    @SuppressLint("SwitchIntDef")
    private Notification setNotification() {
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
        NotificationCompat.Action action_next = new NotificationCompat.Action.Builder(
                R.drawable.ic_next,
                getString(R.string.next),
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
        ).build();
        MediaStyle style = new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                ))
                .setShowActionsInCompactView(0, 1);
        MediaControllerCompat controller = mediaSession.getController();
        MediaMetadataCompat mediaMetadataCompat = controller.getMetadata();
        Notification notification =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(Meta.getTitle(mediaMetadataCompat))
                        .setContentText(Meta.getArtistAlbum(mediaMetadataCompat))
                        .setLargeIcon(controller.getMetadata().getDescription().getIconBitmap())
                        .setSmallIcon(R.drawable.db_icon_96)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(controller.getSessionActivity())
                        .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this,
                                PlaybackStateCompat.ACTION_STOP
                        ))
                        .addAction(action_play_pause)
                        .addAction(action_next)
                        .setStyle(style)
                        .build();
        NotificationManagerCompat.from(this).notify(SERVICE_NOTIFICATION_ID, notification);
        return notification;
    }

    private Void handleControllerResult(Void result, Throwable t) {
        if (t != null) {
            Log.e(LC, Log.getStackTraceString(t));
            Toast.makeText(this, t.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return result;
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            Log.d(LC, "onPlay");
            if (requestAudioFocus()) {
                playbackController.play()
                        .handle(AudioPlayerService.this::handleControllerResult);
            } else {
                Toast.makeText(
                        AudioPlayerService.this,
                        "Could not get audio focus. Audio focus is held by another app.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        @Override
        public void onPause() {
            Log.d(LC, "onPause");
            playOnAudioFocusGain = false;
            playbackController.pause()
                    .handle(AudioPlayerService.this::handleControllerResult);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(LC, "onPlayFromMediaId");
            EntryID entryID = EntryID.from(extras);
            playbackController.playNow(Collections.singletonList(entryID))
                    .thenRun(() -> setToast(
                            entryID,
                            "Playing %s \"%s\" now!")
                    )
                    .handle(AudioPlayerService.this::handleControllerResult);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            onAddQueueItem(description, AudioPlayerService.QUEUE_LAST);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description, int index) {
            Log.d(LC, "onAddQueueItem(" +
                    (index == AudioPlayerService.QUEUE_LAST ? "last" : index) + ")");
            EntryID entryID = EntryID.from(description);
            playbackController.queueToPos(Collections.singletonList(entryID), index)
                    .thenRunAsync(() -> setToast(
                            entryID,
                            "Adding %s \"%s\" to queue!"),
                            Util.getMainThreadExecutor())
                    .handle(AudioPlayerService.this::handleControllerResult);
        }

        private void setToast(EntryID entryID, String format) {
            String type = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(entryID.type) ?
                    Meta.FIELD_TITLE : entryID.type;
            MetaStorage.getInstance(getApplicationContext())
                    .getTrackMetaOnce(entryID)
                    .thenAcceptAsync(meta -> {
                                String title = meta.getAsString(Meta.FIELD_TITLE);
                                String message = String.format(
                                        Locale.getDefault(),
                                        format,
                                        type,
                                        title
                                );
                                Toast.makeText(
                                        AudioPlayerService.this,
                                        message,
                                        Toast.LENGTH_SHORT
                                ).show();
                            },
                            Util.getMainThreadExecutor()
                    );
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            Log.d(LC, "onRemoveQueueItem");
            assert description.getExtras() != null;
            PlaybackEntry playbackEntry = description.getExtras().getParcelable(BUNDLE_KEY_PLAYBACK_ENTRY);
            playbackController.deQueue(Collections.singletonList(playbackEntry))
                    .handle(AudioPlayerService.this::handleControllerResult);
        }

        @Override
        public void onSkipToNext() {
            Log.d(LC, "onSkipToNext");
            playbackController.skipToNext()
                    .handle(AudioPlayerService.this::handleControllerResult);
        }

        @Override
        public void onSkipToPrevious() {
            Log.e(LC, "onSkipToPrevious not supported");
        }

        @Override
        public void onSkipToQueueItem(long queueItemId) {
            Log.e(LC, "onSkipToQueueItem not implemented");
        }

        @Override
        public void onStop() {
            Log.d(LC, "onStop");
            playOnAudioFocusGain = false;
            toggleNoiseReceiver(false);
            playbackController.stop()
                    .handle(AudioPlayerService.this::handleControllerResult);
            mediaRouter.removeCallback(mediaRouterCallback);
        }

        @Override
        public void onSeekTo(long pos) {
            Log.d(LC, "onSeekTo(" + pos + ")");
            playbackController.seekTo(pos)
                    .handle(AudioPlayerService.this::handleControllerResult);
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            Log.e(LC, "Unhandled MediaSession onCustomAction: " + action);
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            Log.d(LC, "onSetShuffleMode");
            switch (shuffleMode) {
                case PlaybackStateCompat.SHUFFLE_MODE_NONE:
                    playbackController.resetPlaybackOrder();
                    break;
                case PlaybackStateCompat.SHUFFLE_MODE_GROUP:
                case PlaybackStateCompat.SHUFFLE_MODE_ALL:
                    playbackController.shufflePlaybackOrder();
                    break;
                case PlaybackStateCompat.SHUFFLE_MODE_INVALID:
                default:
                    Log.e(LC, "onSetShuffleMode unhandled mode: " + shuffleMode);
                    break;
            }
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            Log.d(LC, "onSetRepeatMode: " + repeatMode);
            switch (repeatMode) {
                case PlaybackStateCompat.REPEAT_MODE_INVALID:
                case PlaybackStateCompat.REPEAT_MODE_NONE:
                    playbackController.setRepeat(false);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ALL:
                    playbackController.setRepeat(true);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ONE:
                case PlaybackStateCompat.REPEAT_MODE_GROUP:
                default:
                    Log.e(LC, "onSetRepeatMode unhandled mode: " + repeatMode);
                    break;
            }
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            switch (command) {
                case COMMAND_SET_CURRENT_PLAYLIST:
                    setPlaylist(cb, extras);
                    break;
                case COMMAND_PLAY_ENTRYIDS:
                    play(cb, extras);
                    break;
                case COMMAND_QUEUE_ENTRYIDS:
                    queue(cb, extras);
                    break;
                case COMMAND_DEQUEUE:
                    dequeue(cb, extras);
                    break;
                case COMMAND_CLEAR_QUEUE:
                    clearQueue(cb, extras);
                    break;
                case COMMAND_MOVE_QUEUE_ITEMS:
                    moveQueueItems(cb, extras);
                    break;
                case COMMAND_SHUFFLE_QUEUE_ITEMS:
                    shuffleQueueItems(cb, extras);
                    break;
                case COMMAND_SORT_QUEUE_ITEMS:
                    sortQueueItems(cb, extras);
                    break;
                case COMMAND_PLAY_QUERY_BUNDLES:
                    playQueryBundles(cb, extras);
                    break;
                case COMMAND_QUEUE_QUERY_BUNDLES:
                    queueQueryBundles(cb, extras);
                    break;
                case COMMAND_TOGGLE_RANDOM:
                    toggleRandom(cb, extras);
                default:
                    Log.e(LC, "Unhandled MediaSession onCommand: " + command);
                    break;
            }
        }
    }

    public static CompletableFuture<Boolean> play(MediaControllerCompat mediaController,
                                                  List<EntryID> entryIDs,
                                                  QueryNode queryNode) {
        Bundle params = new Bundle();
        params.putParcelableArrayList("entryids", new ArrayList<>(entryIDs));
        params.putString("queryJSON", queryNode.toJSON().toString());
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_PLAY_ENTRYIDS,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void play(ResultReceiver cb, Bundle b) {
        ArrayList<EntryID> entryIDs = b.getParcelableArrayList("entryids");
        QueryNode queryNode = QueryNode.fromJSON(b.getString("queryJSON"));
        if (entryIDs == null || entryIDs.isEmpty()) {
            cb.send(-1, null);
            return;
        }
        Log.d(LC, "play() " + entryIDs.size() + " entryIDs, query: " + queryNode);
        MusicLibraryService.getSongEntriesOnce(this, entryIDs, queryNode)
                .thenComposeAsync(songEntryIDs -> {
                    Log.d(LC, "play() total song entries: " + songEntryIDs.size());
                    return playbackController.playNow(songEntryIDs);
                }, Util.getMainThreadExecutor())
                .handle((r, t) -> {
                    handleControllerResult(r, t);
                    cb.send(t == null ? 0 : 1, null);
                    return r;
                });
    }

    public static CompletableFuture<Boolean> playQueryBundles(MediaControllerCompat mediaController,
                                                              List<QueryNode> queryNodes) {
        Bundle params = new Bundle();
        putQueryNodesToBundle(params, queryNodes);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_PLAY_QUERY_BUNDLES,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void playQueryBundles(ResultReceiver cb, Bundle b) {
        List<QueryNode> queryNodes = getQueryNodesFromBundle(b);
        if (queryNodes == null || queryNodes.isEmpty()) {
            cb.send(-1, null);
            return;
        }
        Log.d(LC, "play() " + queryNodes.size() + " queries");
        MusicLibraryService.getSongEntriesOnce(this, queryNodes)
                .thenComposeAsync(songEntryIDs -> {
                    Log.d(LC, "play() total song entries: " + songEntryIDs.size());
                    return playbackController.playNow(songEntryIDs);
                }, Util.getMainThreadExecutor())
                .handle((r, t) -> {
                    handleControllerResult(r, t);
                    cb.send(t == null ? 0 : 1, null);
                    return r;
                });
    }

    public static CompletableFuture<Boolean> queue(MediaControllerCompat mediaController,
                                                   List<EntryID> entryIDs,
                                                   QueryNode queryNode) {
        Bundle params = new Bundle();
        params.putParcelableArrayList("entryids", new ArrayList<>(entryIDs));
        if (queryNode == null) {
            queryNode = new QueryTree(QueryTree.Op.AND, false);
        }
        params.putString("queryJSON", queryNode.toJSON().toString());
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_QUEUE_ENTRYIDS,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void queue(ResultReceiver cb, Bundle b) {
        ArrayList<EntryID> entryIDs = b.getParcelableArrayList("entryids");
        QueryNode queryNode = QueryNode.fromJSON(b.getString("queryJSON"));
        if (entryIDs == null || entryIDs.isEmpty()) {
            cb.send(-1, null);
            return;
        }
        Log.d(LC, "queue() adding " + entryIDs.size() + " entryIDs");
        MusicLibraryService.getSongEntriesOnce(this, entryIDs, queryNode)
                .thenComposeAsync(songEntryIDs -> {
                    Log.d(LC, "queue() total song entries: " + songEntryIDs.size());
                    return playbackController.queue(songEntryIDs);
                }, Util.getMainThreadExecutor())
                .handle((r, t) -> {
                    handleControllerResult(r, t);
                    cb.send(t == null ? 0 : 1, null);
                    return r;
                });
    }

    public static CompletableFuture<Boolean> queueQueryBundles(
            MediaControllerCompat mediaController,
            List<QueryNode> queryNodes
    ) {
        Bundle params = new Bundle();
        putQueryNodesToBundle(params, queryNodes);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_QUEUE_QUERY_BUNDLES,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private static void putQueryNodesToBundle(Bundle bundle,
                                              List<QueryNode> queryNodes) {
        bundle.putStringArray(
                "queryNodeJSONs",
                QueryNode.toJSONStringArray(queryNodes)
        );
    }

    private List<QueryNode> getQueryNodesFromBundle(Bundle b) {
        return QueryNode.fromJSONStringArray(b.getStringArray("queryNodeJSONs"));
    }

    private void queueQueryBundles(ResultReceiver cb, Bundle b) {
        List<QueryNode> queryNodes = getQueryNodesFromBundle(b);
        if (queryNodes == null || queryNodes.isEmpty()) {
            cb.send(-1, null);
            return;
        }
        Log.d(LC, "queue() " + queryNodes.size() + " queries");
        MusicLibraryService.getSongEntriesOnce(this, queryNodes)
                .thenComposeAsync(songEntryIDs -> {
                    Log.d(LC, "queue() total song entries: " + songEntryIDs.size());
                    return playbackController.queue(songEntryIDs);
                }, Util.getMainThreadExecutor())
                .handle((r, t) -> {
                    handleControllerResult(r, t);
                    cb.send(t == null ? 0 : 1, null);
                    return r;
                });
    }

    public static CompletableFuture<Boolean> dequeue(MediaControllerCompat mediaController,
                                                     List<PlaybackEntry> playbackEntries) {
        Bundle params = new Bundle();
        params.putParcelableArrayList("playbackEntries", new ArrayList<>(playbackEntries));
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_DEQUEUE,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void dequeue(ResultReceiver cb, Bundle extras) {
        List<PlaybackEntry> playbackEntries = extras.getParcelableArrayList("playbackEntries");
        playbackController.deQueue(playbackEntries)
                .handle((r, t) -> {
                    cb.send(t == null ? 0 : 1, null);
                    return handleControllerResult(r, t);
                });
    }

    public static CompletableFuture<Boolean> clearQueue(MediaControllerCompat mediaController) {
        Bundle params = new Bundle();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_CLEAR_QUEUE,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void clearQueue(ResultReceiver cb, Bundle extras) {
        playbackController.clearQueue()
                .handle((r, t) -> {
                    cb.send(t == null ? 0 : 1, null);
                    return handleControllerResult(r, t);
                });
    }

    public static CompletableFuture<Boolean> moveQueueItems(MediaControllerCompat mediaController,
                                                            List<PlaybackEntry> playbackEntries,
                                                            long beforePlaybackID) {
        Bundle params = new Bundle();
        params.putParcelableArrayList("playbackEntries", new ArrayList<>(playbackEntries));
        params.putLong("beforePlaybackID", beforePlaybackID);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_MOVE_QUEUE_ITEMS,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void moveQueueItems(ResultReceiver cb, Bundle extras) {
        List<PlaybackEntry> playbackEntries = extras.getParcelableArrayList("playbackEntries");
        long beforePlaybackID = extras.getLong("beforePlaybackID");
        playbackController.moveQueueItems(playbackEntries, beforePlaybackID).handle((r, t) -> {
            cb.send(t == null ? 0 : 1, null);
            return handleControllerResult(r, t);
        });
    }

    public static CompletableFuture<Boolean> shuffleQueueItems(MediaControllerCompat mediaController,
                                                               List<PlaybackEntry> playbackEntries) {
        Bundle params = new Bundle();
        params.putParcelableArrayList("playbackEntries", new ArrayList<>(playbackEntries));
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_SHUFFLE_QUEUE_ITEMS,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void shuffleQueueItems(ResultReceiver cb, Bundle extras) {
        List<PlaybackEntry> playbackEntries = extras.getParcelableArrayList("playbackEntries");
        playbackController.shuffleQueueItems(playbackEntries).handle((r, t) -> {
            cb.send(t == null ? 0 : 1, null);
            return handleControllerResult(r, t);
        });
    }

    public static CompletableFuture<Boolean> sortQueueItems(MediaControllerCompat mediaController,
                                                            List<PlaybackEntry> playbackEntries,
                                                            List<String> sortBy) {
        Bundle params = new Bundle();
        params.putParcelableArrayList("playbackEntries", new ArrayList<>(playbackEntries));
        params.putStringArrayList("sortBy", new ArrayList<>(sortBy));
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_SORT_QUEUE_ITEMS,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void sortQueueItems(ResultReceiver cb, Bundle extras) {
        List<PlaybackEntry> playbackEntries = extras.getParcelableArrayList("playbackEntries");
        List<String> sortBy = extras.getStringArrayList("sortBy");
        playbackController.sortQueueItems(playbackEntries, sortBy).handle((r, t) -> {
            cb.send(t == null ? 0 : 1, null);
            return handleControllerResult(r, t);
        });
    }

    public static CompletableFuture<Boolean> setCurrentPlaylist(MediaControllerCompat mediaController,
                                                                EntryID playlistID,
                                                                long pos) {
        Bundle params = new Bundle();
        params.putParcelable("playlistID", playlistID);
        params.putLong("playlistPos", pos);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_SET_CURRENT_PLAYLIST,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void setPlaylist(ResultReceiver cb, Bundle extras) {
        EntryID playlistID = extras.getParcelable("playlistID");
        long pos = extras.getLong("playlistPos");
        playbackController.setPlaylist(playlistID, pos)
                .thenRunAsync(() -> cb.send(0, null), Util.getMainThreadExecutor());
    }

    public static CompletableFuture<Boolean> toggleRandom(MediaControllerCompat mediaController) {
        Bundle params = new Bundle();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mediaController.sendCommand(
                COMMAND_TOGGLE_RANDOM,
                params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        future.complete(resultCode == 0);
                    }
                }
        );
        return future;
    }

    private void toggleRandom(ResultReceiver cb, Bundle extras) {
        playbackController.toggleRandomPlayback().handle((r, t) -> {
            cb.send(t == null ? 0 : 1, null);
            return handleControllerResult(r, t);
        });
    }

    private class PlaybackCallback implements PlaybackController.Callback {
        @Override
        public void onPlayerChanged(AudioPlayer.Type audioPlayerType) {
            switch (audioPlayerType) {
                case CAST:
                    casting = true;
                    break;
                default:
                case LOCAL:
                    casting = false;
                    break;
            }
            onStateChanged(playbackState.getState());
        }

        private String getPlaybackStateString(int playbackState) {
            switch (playbackState) {
                case PlaybackStateCompat.STATE_ERROR:
                    return "STATE_ERROR";
                case PlaybackStateCompat.STATE_STOPPED:
                    return "STATE_STOPPED";
                case PlaybackStateCompat.STATE_NONE:
                    return "STATE_NONE";
                case PlaybackStateCompat.STATE_PAUSED:
                    return "STATE_PAUSED";
                case PlaybackStateCompat.STATE_PLAYING:
                    return "STATE_PLAYING";
                case PlaybackStateCompat.STATE_BUFFERING:
                    return "STATE_BUFFERING";
                case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                    return "STATE_SKIPPING_TO_PREVIOUS";
                case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                    return "STATE_SKIPPING_TO_NEXT";
                case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                    return "STATE_SKIPPING_TO_QUEUE_ITEM";
                case PlaybackStateCompat.STATE_FAST_FORWARDING:
                    return "STATE_FAST_FORWARDING";
                case PlaybackStateCompat.STATE_REWINDING:
                    return "STATE_REWINDING";
                case PlaybackStateCompat.STATE_CONNECTING:
                    return "STATE_CONNECTING";
                default:
                    return "UNKNOWN_STATE: " + playbackState;
            }
        }

        @Override
        public void onStateChanged(int newPlaybackState) {
            Log.d(LC, "PlaybackState: " + getPlaybackStateString(newPlaybackState));
            switch (newPlaybackState) {
                case PlaybackStateCompat.STATE_ERROR:
                case PlaybackStateCompat.STATE_NONE:
                case PlaybackStateCompat.STATE_STOPPED:
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getPlayerSeekPosition(),
                            PLAYBACK_SPEED_PAUSED
                    );
                    toggleNoiseReceiver(false);
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    stopForeground(true);
                    stopSelf();
                    break;
                case PlaybackStateCompat.STATE_PAUSED:
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getPlayerSeekPosition(),
                            PLAYBACK_SPEED_PAUSED
                    );
                    toggleNoiseReceiver(false);
                    stopForeground(false);
                    break;
                case PlaybackStateCompat.STATE_PLAYING:
                    Log.d(LC, "startService");
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getPlayerSeekPosition(),
                            PLAYBACK_SPEED_PLAYING
                    );
                    startService(new Intent(AudioPlayerService.this, AudioPlayerService.class));
                    toggleNoiseReceiver(true);
                    break;
                case PlaybackStateCompat.STATE_BUFFERING:
                    setPlaybackState(
                            newPlaybackState,
                            playbackController.getPlayerSeekPosition(),
                            PLAYBACK_SPEED_PAUSED
                    );
                    break;
                case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                    setPlaybackState(newPlaybackState, 0L, PLAYBACK_SPEED_PAUSED);
                    break;
                case PlaybackStateCompat.STATE_FAST_FORWARDING:
                    Log.w(LC, "Unhandled state: STATE_FAST_FORWARDING");
                    break;
                case PlaybackStateCompat.STATE_REWINDING:
                    Log.w(LC, "Unhandled state: STATE_REWINDING");
                    break;
                case PlaybackStateCompat.STATE_CONNECTING:
                    Log.w(LC, "Unhandled state: STATE_CONNECTING");
                    break;
                default:
                    Log.e(LC, "Unhandled state: " + newPlaybackState);
                    break;
            }
            if (isStoppedState()) {
                setMediaSessionActive(false);
            } else {
                setMediaSessionActive(true);
                if (isPlayingState()) {
                    startForeground(SERVICE_NOTIFICATION_ID, setNotification());
                } else {
                    setNotification();
                }
            }
        }

        private synchronized void setPlaybackState(int newPlaybackState, long position, float playbackSpeed) {
            Log.d(LC, "setPlaybackState: " + getPlaybackStateString(newPlaybackState)
                    + " pos: " + position + " speed: " + playbackSpeed);
            playbackState = playbackStateBuilder
                    .setState(newPlaybackState, position, playbackSpeed)
                    .build();
            mediaSession.setPlaybackState(playbackState);
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            MetaStorage.getInstance(getApplicationContext())
                    .getTrackMetaOnce(entryID)
                    .thenAcceptAsync(
                            meta -> mediaSession.setMetadata(meta.toMediaMetadataCompat()),
                            Util.getMainThreadExecutor()
                    );
        }

        @Override
        public void onCurrentEntryChanged(PlaybackEntry entry) {
            Bundle b = new Bundle();
            b.putParcelable(BUNDLE_KEY_PLAYBACK_ENTRY, entry);
            synchronized (mediaSessionExtrasLock) {
                Bundle extras = mediaSession.getController().getExtras();
                if (extras == null) {
                    extras = new Bundle();
                }
                extras.putBundle(BUNDLE_KEY_CURRENT_PLAYBACK_ENTRY_BUNDLE, b);
                mediaSession.setExtras(extras);
            }
            mediaSession.sendSessionEvent(SESSION_EVENT_CURRENT_ENTRY_CHANGED, b);
        }

        @Override
        public void onQueueChanged(List<PlaybackEntry> queue) {
            List<MediaSessionCompat.QueueItem> queueItems = new ArrayList<>();
            for (int i = 0; i < queue.size(); i++) {
                PlaybackEntry playbackEntry = queue.get(i);
                if (playbackEntry.playbackID == PlaybackEntry.PLAYBACK_ID_INVALID) {
                    playbackEntry = new PlaybackEntry(
                            playbackEntry.entryID,
                            playbackController.reservePlaybackIDs(1),
                            playbackEntry.playbackType,
                            playbackEntry.playlistPos,
                            playbackEntry.playlistSelectionID
                    );
                }
                queueItems.add(new MediaSessionCompat.QueueItem(
                        playbackEntry.toMediaDescriptionCompat(),
                        playbackEntry.playbackID
                ));
            }
            mediaSession.setQueue(queueItems);
        }

        @Override
        public void onPlaylistSelectionChanged(EntryID playlistID, long pos) {
            synchronized (mediaSessionExtrasLock) {
                Bundle extras = mediaSession.getController().getExtras();
                if (extras == null) {
                    extras = new Bundle();
                }
                Bundle playlistIDBundle = new Bundle();
                playlistIDBundle.putParcelable(BUNDLE_KEY_PLAYLIST_ID, playlistID);
                extras.putBundle(BUNDLE_KEY_CURRENT_PLAYLIST_ID_BUNDLE, playlistIDBundle);
                extras.putLong(BUNDLE_KEY_CURRENT_PLAYLIST_POS, pos);
                mediaSession.setExtras(extras);
            }
            Bundle eventBundle = new Bundle();
            eventBundle.putLong(BUNDLE_KEY_CURRENT_PLAYLIST_POS, pos);
            eventBundle.putParcelable(BUNDLE_KEY_PLAYLIST_ID, playlistID);
            mediaSession.sendSessionEvent(SESSION_EVENT_PLAYLIST_SELECTION_CHANGED, eventBundle);
        }

        @Override
        public void onPlayerSeekPositionChanged(long pos) {
            setPlaybackState(playbackState.getState(), pos, playbackState.getPlaybackSpeed());
        }

        @Override
        public void onPlaybackOrderChanged(boolean ordered) {
            Log.d(LC, "onPlaybackOrderChanged, ordered: " + ordered);
            mediaSession.setShuffleMode(ordered
                    ? PlaybackStateCompat.SHUFFLE_MODE_NONE
                    : PlaybackStateCompat.SHUFFLE_MODE_ALL
            );
        }

        @Override
        public void onPlaybackRandomChanged(boolean random) {
            Log.d(LC, "onPlaybackRandomChanged, random: " + random);
            synchronized (mediaSessionExtrasLock) {
                Bundle extras = mediaSession.getController().getExtras();
                if (extras == null) {
                    extras = new Bundle();
                }
                extras.putBoolean(BUNDLE_KEY_RANDOM, random);
                mediaSession.setExtras(extras);
            }
            Bundle eventBundle = new Bundle();
            eventBundle.putBoolean(BUNDLE_KEY_RANDOM, random);
            mediaSession.sendSessionEvent(SESSION_EVENT_RANDOM_CHANGED, eventBundle);
        }

        @Override
        public void onRepeatModeChanged(boolean repeat) {
            Log.d(LC, "onRepeatModeChanged: " + repeat);
            mediaSession.setRepeatMode(repeat ?
                    PlaybackStateCompat.REPEAT_MODE_ALL : PlaybackStateCompat.REPEAT_MODE_NONE
            );
        }
    }

    private void setMediaSessionActive(boolean active) {
        if (active != mediaSession.isActive()) {
            mediaSession.setActive(active);
        }
    }

    private void toggleNoiseReceiver(boolean register) {
        synchronized (makeSomeNoiseReceiver) {
            if (register) {
                if (!isNoiseReceiverRegistered) {
                    registerReceiver(makeSomeNoiseReceiver, intentFilter);
                    isNoiseReceiverRegistered = true;
                }
            } else {
                if (isNoiseReceiverRegistered) {
                    unregisterReceiver(makeSomeNoiseReceiver);
                    isNoiseReceiverRegistered = false;
                }
            }
        }
    }

    private class MakeSomeNoiseReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (!casting) {
                    playbackController.pause()
                            .handle(AudioPlayerService.this::handleControllerResult);
                }
            }
        }
    }
}
