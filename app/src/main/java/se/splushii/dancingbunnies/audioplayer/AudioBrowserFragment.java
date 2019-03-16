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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.ui.nowplaying.PlaybackEntryMeta;
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

    protected List<PlaybackEntryMeta> getQueue() {
        List<PlaybackEntryMeta> playbackEntries = new LinkedList<>();
        for (MediaSessionCompat.QueueItem queueItem: mediaController.getQueue()) {
            Meta meta = new Meta(queueItem.getDescription());
            PlaybackEntry playbackEntry = new PlaybackEntry(meta, PlaybackEntry.USER_TYPE_QUEUE);
            PlaybackEntryMeta playbackEntryMeta = new PlaybackEntryMeta(playbackEntry, meta);
            playbackEntries.add(playbackEntryMeta);
        }
        return playbackEntries;
    }

    public CompletableFuture<Meta> getSongMeta(EntryID entryID) {
        return AudioPlayerService.getSongMeta(mediaController, entryID);
    }

    public void play() {
        mediaController.getTransportControls().play();
    }

    public void play(EntryID entryID) {
        mediaController.getTransportControls().playFromMediaId(entryID.id, entryID.toBundle());
    }

    public void queue(EntryID entryID) {
        mediaController.addQueueItem(entryID.toMediaDescriptionCompat());
    }

    protected CompletableFuture<Boolean> queue(List<EntryID> entryIDs, int toPosition) {
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
        mediaDescription.getExtras().putLong(Meta.METADATA_KEY_QUEUE_POS, pos);
        mediaController.removeQueueItem(mediaDescription);
    }

    protected void dequeue(List<Long> positionList) {
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

    public void addToPlaylist(List<EntryID> entryIDs) {
        AudioPlayerService.addToPlaylist(
                mediaController,
                entryIDs
        ).thenAccept(success -> {
            if (!success) {
                Log.e(LC, "addToPlaylist failed");
            }
        });
    }

    // TODO: Rewrite other commands in the same spirit as removeFromPlaylist
    public void removeFromPlaylist(PlaylistID playlistID, int position) {
        AudioPlayerService.removeFromPlaylist(mediaController, playlistID, position).thenAccept(
                success -> {
                    if (!success) {
                        Log.e(LC, "removeFromPlaylist failed");
                    }
                }
        );
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
                        future.complete(Optional.of(playlistItem));
                    }
                });
        return future;
    }

    protected CompletableFuture<Optional<List<PlaylistItem>>> getPlaylists() {
        CompletableFuture<Optional<List<PlaylistItem>>> future = new CompletableFuture<>();
        mediaController.sendCommand(AudioPlayerService.COMMAND_GET_PLAYLISTS, null,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode != 0) {
                            Log.e(LC, "error on COMMAND_GET_PLAYLISTS");
                            future.complete(Optional.empty());
                            return;
                        }
                        ArrayList<PlaylistItem> playlists =
                                AudioPlayerService.getPlaylistItems(resultData);
                        if (playlists == null) {
                            Log.w(LC, "playlists is null");
                            future.complete(Optional.empty());
                            return;
                        }
                        future.complete(Optional.of(playlists));
                    }
                });
        return future;
    }

    protected CompletableFuture<Optional<List<LibraryEntry>>> getPlaylistEntries(PlaylistID playlistID) {
        CompletableFuture<Optional<List<LibraryEntry>>> future = new CompletableFuture<>();
        mediaController.sendCommand(
                AudioPlayerService.COMMAND_GET_PLAYLIST_ENTRIES,
                playlistID.toBundle(),
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode != 0) {
                            Log.e(LC, "error on COMMAND_GET_PLAYLIST_ENTRIES");
                            future.complete(Optional.empty());
                            return;
                        }
                        ArrayList<LibraryEntry> entries =
                                AudioPlayerService.getPlaylistEntries(resultData);
                        future.complete(Optional.of(entries));
                    }
                }
        );
        return future;
    }

    protected CompletableFuture<Optional<List<PlaybackEntryMeta>>> getPlaylistNext(int maxEntries) {
        CompletableFuture<Optional<List<PlaybackEntryMeta>>> future = new CompletableFuture<>();
        Bundle params = new Bundle();
        params.putInt("MAX_ENTRIES", maxEntries);
        mediaController.sendCommand(
                AudioPlayerService.COMMAND_GET_PLAYLIST_NEXT, params,
                new ResultReceiver(null) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        if (resultCode != 0) {
                            future.complete(Optional.empty());
                            return;
                        }
                        List<PlaybackEntryMeta> playbackEntries =
                                AudioPlayerService.getPlaybackEntryMetaList(resultData);
                        future.complete(Optional.of(playbackEntries));
                    }
                }
        );
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
                    AudioBrowserFragment.this.onMetadataChanged(new Meta(metadata));
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
    protected void onMetadataChanged(Meta metadata) {}
    protected void onSessionEvent(String event, Bundle extras) {}
    private void onSessionDestroyed() {}
    protected void onSessionReady() {}
    protected void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {}
}
