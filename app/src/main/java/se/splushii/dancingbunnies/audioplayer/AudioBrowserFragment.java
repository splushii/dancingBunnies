package se.splushii.dancingbunnies.audioplayer;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.ui.nowplaying.QueueEntry;
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

    protected List<QueueEntry> getQueue() {
        List<MediaSessionCompat.QueueItem> queueItems = mediaController.getQueue();
        List<QueueEntry> queueEntries = new ArrayList<>();
        for (int i = 0; i < queueItems.size(); i++) {
            MediaSessionCompat.QueueItem queueItem = queueItems.get(i);
            queueEntries.add(new QueueEntry(
                    new PlaybackEntry(queueItem.getDescription()),
                    queueItem.getQueueId(),
                    i
            ));
        }
        return queueEntries;
    }

    public void play() {
        mediaController.getTransportControls().play();
    }

    public void play(EntryID entryID) {
        mediaController.getTransportControls().playFromMediaId(entryID.id, entryID.toBundle());
    }

    public CompletableFuture<Void> play(List<EntryID> selectionList) {
        return queue(selectionList, 0)
                .thenAccept(success -> {
                    if (success) {
                        next();
                        play();
                    }
                });
    }

    public void queue(EntryID entryID) {
        mediaController.addQueueItem(entryID.toMediaDescriptionCompat());
    }

    public CompletableFuture<Boolean> queue(List<EntryID> entryIDs, int toPosition) {
        return AudioPlayerService.queue(
                mediaController,
                entryIDs,
                toPosition
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "queue entryIDs failed");
            }
            return success;
        });
    }

    public void dequeue(EntryID entryID, long pos) {
        MediaDescriptionCompat mediaDescription = entryID.toMediaDescriptionCompat();
        assert mediaDescription.getExtras() != null;
        mediaDescription.getExtras().putLong(AudioPlayerService.BUNDLE_KEY_DEQUEUE_QUEUE_POS, pos);
        mediaController.removeQueueItem(mediaDescription);
    }

    public void dequeue(List<Long> positionList) {
        AudioPlayerService.dequeue(
                mediaController,
                positionList
        ).thenAccept(success -> {
            if (!success) {
                Log.e(LC, "dequeue entries failed");
            }
        });
    }

    public CompletableFuture<Boolean> moveQueueItems(List<Long> positionList, int toPosition) {
        return AudioPlayerService.moveQueueItems(
                mediaController,
                positionList,
                toPosition
        ).thenApply(success -> {
            if (!success) {
                Log.e(LC, "moveQueueItems failed");
            }
            return success;
        });
    }

    protected CompletableFuture<Optional<PlaylistItem>> getCurrentPlaylist() {
        CompletableFuture<Optional<PlaylistItem>> future = new CompletableFuture<>();
        mediaController.sendCommand(AudioPlayerService.COMMAND_GET_CURRENT_PLAYLIST, null,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode != 0) {
                            Log.e(LC, "error on COMMAND_GET_CURRENT_PLAYLIST");
                            future.complete(Optional.empty());
                            return;
                        }
                        PlaylistItem playlistItem = AudioPlayerService.getPlaylist(resultData);
                        future.complete(playlistItem == null ?
                                Optional.empty() : Optional.of(playlistItem));
                    }
                });
        return future;
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
                    AudioBrowserFragment.this.onSessionEvent(event, extras);
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
                    AudioBrowserFragment.this.onQueueChanged(queue);
                }
            };

    protected void onMediaBrowserConnected() {}
    protected void onPlaybackStateChanged(PlaybackStateCompat state) {}
    protected void onMetadataChanged(EntryID entryID) {}
    protected void onSessionEvent(String event, Bundle extras) {}
    private void onSessionDestroyed() {}
    protected void onSessionReady() {}
    protected void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {}
}
