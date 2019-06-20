package se.splushii.dancingbunnies.audioplayer;

import android.content.ComponentName;
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
import java.util.stream.Collectors;

import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public abstract class AudioBrowserFragment extends Fragment {
    private static final String LC = Util.getLogContext(AudioBrowserFragment.class);
    protected MediaBrowserCompat mediaBrowser;
    protected MediaControllerCompat mediaController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LC, "onCreate");
        mediaBrowser = new MediaBrowserCompat(getActivity(), new ComponentName(requireContext(),
                AudioPlayerService.class), mediaBrowserConnectionCallback, null);
        mediaBrowser.connect();
    }

    @Override
    public void onDestroy() {
        Log.d(LC, "onDestroy");
        if (mediaController != null) {
            mediaController.unregisterCallback(mediaControllerCallback);
            mediaController = null;
        }
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
            mediaBrowser = null;
        }
        super.onDestroy();
    }

    private void connectMediaController(MediaSessionCompat.Token token) throws RemoteException {
        mediaController = new MediaControllerCompat(getActivity(), token);
        mediaController.registerCallback(mediaControllerCallback);
        Log.d(LC, "connecting mediacontroller. Session ready: " + mediaController.isSessionReady());
        if (mediaController.isSessionReady()) {
            onSessionReady();
        }
    }

    public void play() {
        mediaController.getTransportControls().play();
    }

    public void play(EntryID entryID) {
        mediaController.getTransportControls().playFromMediaId(entryID.id, entryID.toBundle());
    }

    public CompletableFuture<Boolean> play(List<EntryID> entryIDs) {
        return AudioPlayerService.play(
                mediaController,
                entryIDs
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "play entryIDs failed");
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

    public CompletableFuture<Boolean> queue(List<EntryID> entryIDs) {
        return AudioPlayerService.queue(
                mediaController,
                entryIDs
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "queue entryIDs failed");
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

    protected CompletableFuture<PlaylistID> getCurrentPlaylist() {
        return AudioPlayerService.getCurrentPlaylist(mediaController);
    }

    protected CompletableFuture<PlaylistEntry> getCurrentPlaylistEntry() {
        return AudioPlayerService.getCurrentPlaylistEntry(mediaController);
    }

    protected void pause() {
        mediaController.getTransportControls().pause();
    }

    protected void previous() {
        mediaController.getTransportControls().skipToPrevious();
    }

    protected void next() {
        mediaController.getTransportControls().skipToNext();
    }

    public void skipItems(long offset) {
        mediaController.getTransportControls().skipToQueueItem(offset);
    }

    protected void seekTo(long position) {
        mediaController.getTransportControls().seekTo(position);
    }

    private final MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    Log.d(LC, "MediaBrowser connected");
                    // TODO: Change to use MediaControllerCompat.setMediaController
                    // TODO: See https://developer.android.com/guide/topics/media-apps/audio-app/building-a-mediabrowser-client#customize-mediabrowser-connectioncallback
                    // TODO: Then put this in the Activity instead. Single controller for all fragments.
                    try {
                        connectMediaController(mediaBrowser.getSessionToken());
                    } catch (RemoteException e) {
                        Log.e(LC, "Failed to connect to media controller");
                    }
                    AudioBrowserFragment.this.onMediaBrowserConnected();
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

    private final MediaControllerCompat.Callback mediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    AudioBrowserFragment.this.onPlaybackStateChanged(state);
                }

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    AudioBrowserFragment.this.onMetadataChanged(EntryID.from(metadata));
                }

                @Override
                public void onSessionEvent(String event, Bundle extras) {
                    switch (event) {
                        case AudioPlayerService.SESSION_EVENT_PLAYLIST_SELECTION_CHANGED:
                            PlaylistID playlistID = extras.getParcelable(
                                    AudioPlayerService.BUNDLE_KEY_PLAYLIST_ID
                            );
                            long pos = extras.getLong(AudioPlayerService.BUNDLE_KEY_POS);
                            AudioBrowserFragment.this.onPlaylistSelectionChanged(playlistID, pos);
                            break;
                    }
                }

                @Override
                public void onSessionDestroyed() {
                    AudioBrowserFragment.this.onSessionDestroyed();
                }

                @Override
                public void onSessionReady() {
                    AudioBrowserFragment.this.onSessionReady();
                }

                @Override
                public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
                    AudioBrowserFragment.this.onQueueChanged(
                            sessionQueueToPlaybackEntries(queue)
                    );
                }
            };

    protected void onMediaBrowserConnected() {}
    protected void onPlaybackStateChanged(PlaybackStateCompat state) {}
    protected void onMetadataChanged(EntryID entryID) {}
    private void onSessionDestroyed() {}
    protected void onSessionReady() {}
    protected void onQueueChanged(List<PlaybackEntry> queue) {}
    protected void onPlaylistSelectionChanged(PlaylistID playlistID, long pos) {}
}
