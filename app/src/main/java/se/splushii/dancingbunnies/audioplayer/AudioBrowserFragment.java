package se.splushii.dancingbunnies.audioplayer;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public abstract class AudioBrowserFragment extends Fragment {
    private static final String LC = Util.getLogContext(AudioBrowserFragment.class);
    public MediaBrowserCompat mediaBrowser;
    public MediaControllerCompat mediaController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LC, "onCreate");
        mediaBrowser = new MediaBrowserCompat(getActivity(), new ComponentName(getActivity(),
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

    public void queue(EntryID entryID) {
        mediaController.addQueueItem(entryID.toMediaDescriptionCompat());
    }

    public void dequeue(EntryID entryID) {
        mediaController.removeQueueItem(entryID.toMediaDescriptionCompat());
    }

    public void pause() {
        mediaController.getTransportControls().pause();
    }

    public void previous() {
        mediaController.getTransportControls().skipToPrevious();
    }

    public void next() {
        mediaController.getTransportControls().skipToNext();
    }

    public void skipTo(long position) {
        mediaController.getTransportControls().skipToQueueItem(position);
    }

    public void seekTo(long position) {
        mediaController.getTransportControls().seekTo(position);
    }

    private final MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    Log.d(LC, "MediaBrowser connected");
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
                    AudioBrowserFragment.this.onMetadataChanged(metadata);
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

    protected void onMediaBrowserConnected() {
        Log.d(LC, "mediaBrowser connected");
    }

    protected void onPlaybackStateChanged(PlaybackStateCompat state) {
        Log.d(LC, "mediacontroller onplaybackstatechanged");
    }

    protected void onMetadataChanged(MediaMetadataCompat metadata) {
        Log.d(LC, "mediacontroller onmetadatachanged");
    }

    protected void onSessionEvent(String event, Bundle extras) {
        Log.d(LC, "mediacontroller onsessionevent: " + event);
    }

    protected void onSessionDestroyed() {
        Log.w(LC, "mediacontroller session destroyed");
    }

    protected void onSessionReady() {
        Log.d(LC, "mediacontroller session ready");
    }

    protected void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
        Log.d(LC, "mediacontroller onQueueChanged");
    }
}
