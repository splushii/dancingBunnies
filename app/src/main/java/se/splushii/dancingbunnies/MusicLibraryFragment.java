package se.splushii.dancingbunnies;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.services.AudioPlayerService;
import se.splushii.dancingbunnies.ui.MusicLibraryAdapter;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragment extends Fragment {
    private static String LC = Util.getLogContext(MusicLibraryFragment.class);
    private RecyclerView recView;
    private MusicLibraryAdapter recViewAdapter;
    private int numBackStack;
    private MediaBrowserCompat mediaBrowser;

    // TODO: Add a fastscroller

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
        mediaBrowser.disconnect();
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshView(MusicLibrary.API_ID_ANY, AudioPlayerService.MEDIA_ID_ROOT, LibraryEntry.EntryType.ANY, 0, 0);
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        super.onStop();
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

    private void connectMediaController(MediaSessionCompat.Token token) throws RemoteException {
        MediaControllerCompat mediaController = new MediaControllerCompat(getActivity(), token);
        MediaControllerCompat.setMediaController(getActivity(), mediaController);
        mediaController.registerCallback(mediaControllerCallback);
    }

    private final MediaControllerCompat.Callback mediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    Log.d(LC, "mediacontroller onplaybackstatechanged");
                }

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    Log.d(LC, "mediacontroller onmetadatachanged");
                }

                @Override
                public void onSessionEvent(String event, Bundle extras) {
                    Log.d(LC, "mediacontroller onsessionevent: " + event);
                }
            };

    public void refreshView(final String src, final String parentId, final LibraryEntry.EntryType type, final int pos, final int pad) {
        if (!mediaBrowser.isConnected()) {
            Log.d(LC, "MediaBrowser not connected.");
        }
        Bundle options = AudioPlayerService.generateMusicLibraryQueryOptions(src, type);
        mediaBrowser.subscribe(parentId, options, new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children, @NonNull Bundle options) {
                Log.d(LC, "MediaBrowser.subscribe(" + parentId + ", options) onChildrenLoaded");
                recViewAdapter.setDataset(children, src, parentId, type);
                LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
                llm.scrollToPositionWithOffset(pos, pad);
            }

            @Override
            public void onError(@NonNull String parentId) {
                Log.d(LC, "MediaBrowser.subscribe(" + parentId + ") onError");
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.musiclibrary_fragment_layout, container,
                false);

        recView = (RecyclerView) rootView.findViewById(R.id.musiclibrary_recyclerview);
        recView.setHasFixedSize(true);
        RecyclerView.LayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new MusicLibraryAdapter(this);
        recView.setAdapter(recViewAdapter);

        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        final FloatingActionButton fab_sort_artist =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_artist);
        final FloatingActionButton fab_sort_song =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_song);
        final FloatingActionButton fab_refresh =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_refresh);

        fab_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshView(MusicLibrary.API_ID_ANY, AudioPlayerService.MEDIA_ID_ROOT, LibraryEntry.EntryType.ANY, 0, 0);
                setFabVisibility(View.GONE);
            }
        });

        fab_sort_artist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Artist view", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                refreshView(MusicLibrary.API_ID_ANY, AudioPlayerService.MEDIA_ID_ARTIST_ROOT, LibraryEntry.EntryType.ANY, 0, 0);
                setFabVisibility(View.GONE);
            }
        });

        fab_sort_song.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Song view", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                refreshView(MusicLibrary.API_ID_ANY, AudioPlayerService.MEDIA_ID_SONG_ROOT, LibraryEntry.EntryType.ANY, 0, 0);
                setFabVisibility(View.GONE);
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (fab_sort_artist.getVisibility() != View.VISIBLE) {
                    setFabVisibility(View.VISIBLE);
                } else {
                    setFabVisibility(View.GONE);
                }
            }
        });
        return rootView;
    }

    private void setFabVisibility(int visibility) {
        View rootView = this.getView();
        FloatingActionButton fab_sort_artist =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_artist);
        FloatingActionButton fab_sort_song =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_song);
        FloatingActionButton fab_refresh =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_refresh);
        TextView text_sort_artist =
                (TextView) rootView.findViewById(R.id.fab_sort_artist_label);
        TextView text_sort_song =
                (TextView) rootView.findViewById(R.id.fab_sort_song_label);
        TextView text_refresh =
                (TextView) rootView.findViewById(R.id.fab_sort_refresh_label);
        fab_sort_artist.setVisibility(visibility);
        fab_sort_song.setVisibility(visibility);
        fab_refresh.setVisibility(visibility);
        text_refresh.setVisibility(visibility);
        text_sort_artist.setVisibility(visibility);
        text_sort_song.setVisibility(visibility);
    }

    public void pushBackStack() {
        numBackStack++;
    }

    public boolean onBackPressed() {
        if (numBackStack-- > 0) {
            recViewAdapter.onBackPressed();
            return true;
        }
        return false;
    }
}
