package se.splushii.dancingbunnies.audioplayer;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

public class AudioBrowser {
    private static final String LC = Util.getLogContext(AudioBrowser.class);

    private static volatile AudioBrowser instance;

    private final MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaController;
    private final ConcurrentHashMap<AudioBrowserCallback, MediaControllerCallback> callbackMap;

    public static synchronized AudioBrowser getInstance(FragmentActivity activity) {
        if (instance == null) {
            instance = new AudioBrowser(activity);
        }
        return instance;
    }

    private AudioBrowser(FragmentActivity activity) {
        callbackMap = new ConcurrentHashMap<>();
        MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback =
                new MediaBrowserCompat.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        Log.d(LC, "MediaBrowser connected");
                        callbackMap.forEach((callback, mediaControllerCallback) ->
                                callback.onMediaBrowserConnected()
                        );
                        try {
                            registerMediaController(activity, mediaBrowser.getSessionToken());
                        } catch (RemoteException e) {
                            Log.e(LC, "Failed to connect to media controller");
                        }
                    }

                    @Override
                    public void onConnectionFailed() {
                        Log.e(LC, "MediaBrowser onConnectFailed");
                    }

                    @Override
                    public void onConnectionSuspended() {
                        Log.w(LC, "MediaBrowser onConnectionSuspended");
                    }
                };
        mediaBrowser = new MediaBrowserCompat(
                activity,
                new ComponentName(activity, AudioPlayerService.class),
                mediaBrowserConnectionCallback,
                null
        );
    }

    public synchronized void connect() {
        if (!mediaBrowser.isConnected()) {
            mediaBrowser.connect();
        }
    }

    public synchronized void disconnect() {
        if (mediaBrowser.isConnected()) {
            if (mediaController != null) {
                callbackMap.forEach((callback, mediaControllerCallback) ->
                        mediaController.unregisterCallback(mediaControllerCallback)
                );
                mediaController = null;
            }
            mediaBrowser.disconnect();
        }
    }

    private synchronized void registerMediaController(FragmentActivity activity,
                                                      MediaSessionCompat.Token token
    ) throws RemoteException {
        if (mediaController != null) {
            Log.w(LC, "MediaController already registered: Session ready: "
                    + mediaController.isSessionReady());
            return;
        }
        mediaController = new MediaControllerCompat(activity, token);
        MediaControllerCompat.setMediaController(activity, mediaController);
        callbackMap.forEach((callback, mediaControllerCallback) ->
                mediaController.registerCallback(mediaControllerCallback)
        );
        Log.d(LC, "MediaController registered. Session ready: "
                + mediaController.isSessionReady());
        if (mediaController.isSessionReady()) {
            callbackMap.forEach((callback, mediaControllerCallback) -> callback.onSessionReady());
        }
    }

    public boolean isSessionReady() {
        return mediaController != null && mediaController.isSessionReady();
    }

    public void registerCallback(FragmentActivity activity,
                                                    AudioBrowserCallback callback) {
        MediaControllerCallback mediaControllerCallback = new MediaControllerCallback(callback);
        if (callbackMap.putIfAbsent(callback, mediaControllerCallback) != null) {
            Log.w(LC, "registerCallback: callback already registered");
            return;
        }
        if (mediaController != null) {
            mediaController.registerCallback(mediaControllerCallback);
        }
    }

    public void unregisterCallback(FragmentActivity activity, AudioBrowserCallback callback) {
        MediaControllerCallback mediaControllerCallback = callbackMap.remove(callback);
        if (mediaControllerCallback != null && mediaController != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
        }
    }

    public void play() {
        mediaController.getTransportControls().play();
    }

    public void play(EntryID entryID) {
        mediaController.getTransportControls().playFromMediaId(entryID.id, entryID.toBundle());
    }

    public CompletableFuture<Boolean> play(List<EntryID> entryIDs, MusicLibraryQueryNode queryNode) {
        return AudioPlayerService.play(
                mediaController,
                entryIDs,
                queryNode
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "play entryIDs failed");
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> playQueries(List<MusicLibraryQueryNode> queryNodes) {
        return AudioPlayerService.playQueryBundles(
                mediaController,
                queryNodes
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "play queries failed");
            }
            return success;
        });
    }

    public List<PlaybackEntry> getQueue() {
        return sessionQueueToPlaybackEntries(mediaController.getQueue());
    }

    private List<PlaybackEntry> sessionQueueToPlaybackEntries(
            List<MediaSessionCompat.QueueItem> queue
    ) {
        return queue.stream()
                .map(queueItem -> new PlaybackEntry(queueItem.getDescription()))
                .collect(Collectors.toList());
    }

    public void queue(EntryID entryID) {
        mediaController.addQueueItem(entryID.toMediaDescriptionCompat());
    }

    public CompletableFuture<Boolean> queue(List<EntryID> entryIDs,
                                            MusicLibraryQueryNode queryNode) {
        return AudioPlayerService.queue(
                mediaController,
                entryIDs,
                queryNode
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "queue entryIDs failed");
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> queueQueryBundles(List<MusicLibraryQueryNode> queryNodes) {
        return AudioPlayerService.queueQueryBundles(
                mediaController,
                queryNodes
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "queue queries failed");
            }
            return success;
        });
    }

    public void dequeue(PlaybackEntry playbackEntry) {
        MediaDescriptionCompat mediaDescription = playbackEntry.entryID.toMediaDescriptionCompat();
        assert mediaDescription.getExtras() != null;
        mediaDescription.getExtras().putParcelable(AudioPlayerService.BUNDLE_KEY_PLAYBACK_ENTRY, playbackEntry);
        mediaController.removeQueueItem(mediaDescription);
    }

    public void dequeue(List<PlaybackEntry> playbackEntries) {
        AudioPlayerService.dequeue(
                mediaController,
                playbackEntries
        ).thenAccept(success -> {
            if (!success) {
                Log.e(LC, "dequeue entries failed");
            }
        });
    }

    public void clearQueue() {
        AudioPlayerService.clearQueue(mediaController).thenAccept(success -> {
            if (!success) {
                Log.e(LC, "dequeue entries failed");
            }
        });
    }

    public CompletableFuture<Boolean> moveQueueItems(List<PlaybackEntry> playbackEntries,
                                                     long beforePlaybackID) {
        return AudioPlayerService.moveQueueItems(
                mediaController,
                playbackEntries,
                beforePlaybackID
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "moveQueueItems failed");
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> shuffleQueueItems(List<PlaybackEntry> playbackEntries) {
        return AudioPlayerService.shuffleQueueItems(
                mediaController,
                playbackEntries
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "shuffleQueueItems failed");
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> sortQueueItems(List<PlaybackEntry> playbackEntries,
                                                     List<String> sortBy) {
        return AudioPlayerService.sortQueueItems(
                mediaController,
                playbackEntries,
                sortBy
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "sortQueueItems failed");
            }
            return success;
        });
    }

    public PlaybackStateCompat getPlaybackState() {
        return mediaController.getPlaybackState();
    }

    public MediaMetadataCompat getCurrentMediaMetadata() {
        return mediaController.getMetadata();
    }

    // TODO: Why not do like this with all event/fetch methods? (Put data in extras and send events)
    public PlaybackEntry getCurrentEntry() {
        Bundle extras = mediaController.getExtras();
        if (extras == null) {
            return null;
        }
        Bundle playbackEntryBundle = extras.getBundle(
                AudioPlayerService.BUNDLE_KEY_CURRENT_PLAYBACK_ENTRY_BUNDLE
        );
        if (playbackEntryBundle == null) {
            return null;
        }
        playbackEntryBundle.setClassLoader(PlaybackEntry.class.getClassLoader());
        return playbackEntryBundle.getParcelable(AudioPlayerService.BUNDLE_KEY_PLAYBACK_ENTRY);
    }

    public CompletableFuture<Boolean> setCurrentPlaylist(PlaylistID playlistID, long pos) {
        return AudioPlayerService.setCurrentPlaylist(
                mediaController,
                playlistID,
                pos
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "setCurrentPlaylist (" + playlistID + ") failed");
            }
            return success;
        });
    }

    public PlaylistID getCurrentPlaylist() {
        Bundle extras = mediaController.getExtras();
        if (extras == null) {
            return null;
        }
        Bundle playlistIDBundle = extras.getBundle(
                AudioPlayerService.BUNDLE_KEY_CURRENT_PLAYLIST_ID_BUNDLE
        );
        if (playlistIDBundle == null) {
            return null;
        }
        playlistIDBundle.setClassLoader(PlaylistID.class.getClassLoader());
        return playlistIDBundle.getParcelable(AudioPlayerService.BUNDLE_KEY_PLAYLIST_ID);
    }

    public long getCurrentPlaylistPos() {
        Bundle extras = mediaController.getExtras();
        if (extras == null) {
            return PlaybackEntry.PLAYLIST_POS_NONE;
        }
        return extras.getLong(AudioPlayerService.BUNDLE_KEY_CURRENT_PLAYLIST_POS);
    }

    public void pause() {
        mediaController.getTransportControls().pause();
    }

    public void next() {
        mediaController.getTransportControls().skipToNext();
    }

    public void seekTo(long position) {
        mediaController.getTransportControls().seekTo(position);
    }

    public void toggleRepeat() {
        mediaController.getTransportControls().setRepeatMode(isRepeat() ?
                PlaybackStateCompat.REPEAT_MODE_NONE : PlaybackStateCompat.REPEAT_MODE_ALL
        );
    }

    public boolean isRepeat() {
        return isRepeat(mediaController.getRepeatMode());
    }

    private boolean isRepeat(int repeatMode) {
        return repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL;
    }

    public void setPlaylistPlaybackOrderMode(int playbackOrderMode) {
        int shuffleMode;
        switch (playbackOrderMode) {
            default:
            case PlaybackController.PLAYBACK_ORDER_SEQUENTIAL:
                shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
                break;
            case PlaybackController.PLAYBACK_ORDER_SHUFFLE:
                shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_ALL;
                break;
            case PlaybackController.PLAYBACK_ORDER_RANDOM:
                shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_GROUP;
                break;
        }
        mediaController.getTransportControls().setShuffleMode(shuffleMode);
    }

    public int getPlaylistPlaybackOrderMode() {
        if (mediaController == null) {
            return PlaybackStateCompat.SHUFFLE_MODE_NONE;
        }
        return getPlaylistPlaybackOrderMode(mediaController.getShuffleMode());
    }

    private int getPlaylistPlaybackOrderMode(int shuffleMode) {
        switch (shuffleMode) {
            default:
            case PlaybackStateCompat.SHUFFLE_MODE_INVALID:
            case PlaybackStateCompat.SHUFFLE_MODE_NONE:
                return PlaybackController.PLAYBACK_ORDER_SEQUENTIAL;
            case PlaybackStateCompat.SHUFFLE_MODE_ALL:
                return PlaybackController.PLAYBACK_ORDER_SHUFFLE;
            case PlaybackStateCompat.SHUFFLE_MODE_GROUP:
                return PlaybackController.PLAYBACK_ORDER_RANDOM;
        }
    }

    public void downloadAudioData(Context context,
                                  List<MusicLibraryQueryNode> queryNodes,
                                  int priority) {
        MusicLibraryService.downloadAudioData(context, queryNodes, priority)
                .handle(Util::printFutureError);
    }

    public String query(String currentSubscriptionID,
                        MusicLibraryQuery musicLibraryQuery,
                        Consumer<List<LibraryEntry>> onResult) {
        if (currentSubscriptionID != null && mediaBrowser.isConnected()) {
            mediaBrowser.unsubscribe(currentSubscriptionID);
        }
        currentSubscriptionID = musicLibraryQuery.query(
                mediaBrowser,
                new MusicLibraryQuery.MusicLibraryQueryCallback() {
                    @Override
                    public void onQueryResult(@NonNull List<MediaBrowserCompat.MediaItem> items) {
                        onResult.accept(
                                items.stream().map(LibraryEntry::from).collect(Collectors.toList())
                        );
                    }
                }
        );
        return currentSubscriptionID;
    }

    public class MediaControllerCallback extends MediaControllerCompat.Callback {
        private final AudioBrowserCallback callback;

        MediaControllerCallback(AudioBrowserCallback callback) {
            super();
            this.callback = callback;
        }

        @Override
        public void onShuffleModeChanged(int shuffleMode) {
            callback.onPlaylistPlaybackOrderModeChanged(
                    getPlaylistPlaybackOrderMode(shuffleMode)
            );
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            callback.onRepeatModeChanged(isRepeat(repeatMode));
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            callback.onPlaybackStateChanged(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            callback.onMetadataChanged(EntryID.from(metadata));
        }

        @Override
        public void onSessionEvent(String event, Bundle extras) {
            switch (event) {
                case AudioPlayerService.SESSION_EVENT_PLAYLIST_SELECTION_CHANGED:
                    PlaylistID playlistID = extras.getParcelable(
                            AudioPlayerService.BUNDLE_KEY_PLAYLIST_ID
                    );
                    long pos = extras.getLong(AudioPlayerService.BUNDLE_KEY_CURRENT_PLAYLIST_POS);
                    callback.onPlaylistSelectionChanged(playlistID, pos);
                    break;
                case AudioPlayerService.SESSION_EVENT_CURRENT_ENTRY_CHANGED:
                    PlaybackEntry entry = extras.getParcelable(
                            AudioPlayerService.BUNDLE_KEY_PLAYBACK_ENTRY
                    );
                    callback.onCurrentEntryChanged(entry);
                    break;
            }
        }

        @Override
        public void onSessionDestroyed() {
            callback.onSessionDestroyed();
        }

        @Override
        public void onSessionReady() {
            callback.onSessionReady();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            callback.onQueueChanged(sessionQueueToPlaybackEntries(queue));
        }
    }
}
