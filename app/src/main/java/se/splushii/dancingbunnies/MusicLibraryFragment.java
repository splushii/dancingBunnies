package se.splushii.dancingbunnies;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.services.AudioPlayerService;
import se.splushii.dancingbunnies.ui.MusicLibraryAdapter;

public class MusicLibraryFragment extends Fragment {
    MusicLibrary lib;
    private RecyclerView recView;
    private MusicLibraryAdapter recViewAdapter;
    private int numBackStack;
    private MediaBrowserCompat mediaBrowser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lib = new MusicLibrary(getContext());
        //
        mediaBrowser = new MediaBrowserCompat(getActivity(), new ComponentName(getActivity(),
                AudioPlayerService.class), mediaBrowserConnectionCallback, null);
    }

    private final MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    System.out.println("MediaBrowser connected");
                    try {
                        connectMediaController(mediaBrowser.getSessionToken());
                    } catch (RemoteException e) {
                        System.out.println("Failed to connect to media controller");
                    }
                }

                @Override
                public void onConnectionFailed() {
                    System.out.println("MediaBrowser onConnectFailed");
                }

                @Override
                public void onConnectionSuspended() {
                    System.out.println("MediaBrowser onConnectionSuspended");
                }
    };

    private void connectMediaController(MediaSessionCompat.Token token) throws RemoteException {
        MediaControllerCompat mediaController = new MediaControllerCompat(getActivity(), token);
        getActivity().setSupportMediaController(mediaController); // TODO: use setMediaController?
        mediaController.registerCallback(mediaControllerCallback);
    }

    private final MediaControllerCompat.Callback mediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    System.out.println("mediacontroller onplaybackstatechanged");
                }

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    System.out.println("mediacontroller onmetadatachanged");
                }

                @Override
                public void onSessionEvent(String event, Bundle extras) {
                    System.out.println("mediacontroller onsessionevent");
                }
            };

    @Override
    public void onStart() {
        super.onStart();
        mediaBrowser.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mediaBrowser.disconnect();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.musiclibrary_fragment_layout, container,
                false);
        final ProgressBar progressBar = (ProgressBar) rootView.findViewById(R.id.progress_bar);
        final TextView progressText = (TextView) rootView.findViewById(R.id.progress_bar_text);

        recView = (RecyclerView) rootView.findViewById(R.id.musiclibrary_recyclerview);
        recView.setHasFixedSize(true);
        RecyclerView.LayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new MusicLibraryAdapter(this, lib);
        recViewAdapter.setView(MusicLibraryAdapter.LibraryView.ARTIST);
        recView.setAdapter(recViewAdapter);

        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        final FloatingActionButton fab_sort_artist =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_artist);
        final FloatingActionButton fab_sort_song =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_song);
        final FloatingActionButton fab_refresh =
                (FloatingActionButton) rootView.findViewById(R.id.fab_sort_refresh);
        final TextView text_sort_artist =
                (TextView) rootView.findViewById(R.id.fab_sort_artist_label);
        final TextView text_sort_song =
                (TextView) rootView.findViewById(R.id.fab_sort_song_label);
        final TextView text_refresh =
                (TextView) rootView.findViewById(R.id.fab_sort_refresh_label);

        fab_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Getting all artists", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                recViewAdapter.getAllLibraryEntries(new MusicLibraryRequestHandler() {
                    @Override
                    public void onStart() {
                        recView.setVisibility(View.GONE);
                        progressText.setText("");
                        progressText.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onSuccess() {
                        progressBar.setVisibility(View.GONE);
                        progressText.setVisibility(View.GONE);
                        recView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(String status) {
                        progressBar.setVisibility(View.GONE);
                        progressText.setVisibility(View.GONE);
                        Toast.makeText(getContext(), status, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onProgress(String s) {
                        progressText.setText(s);
                    }
                });
                fab_sort_artist.setVisibility(View.GONE);
                fab_sort_song.setVisibility(View.GONE);
                fab_refresh.setVisibility(View.GONE);
                text_refresh.setVisibility(View.GONE);
                text_sort_artist.setVisibility(View.GONE);
                text_sort_song.setVisibility(View.GONE);
            }
        });

        fab_sort_artist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Artist view", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                recViewAdapter.setView(MusicLibraryAdapter.LibraryView.ARTIST);
                fab_sort_artist.setVisibility(View.GONE);
                fab_sort_song.setVisibility(View.GONE);
                fab_refresh.setVisibility(View.GONE);
                text_refresh.setVisibility(View.GONE);
                text_sort_artist.setVisibility(View.GONE);
                text_sort_song.setVisibility(View.GONE);
            }
        });

        fab_sort_song.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Song view", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                recViewAdapter.setView(MusicLibraryAdapter.LibraryView.SONG);
                fab_sort_artist.setVisibility(View.GONE);
                fab_sort_song.setVisibility(View.GONE);
                fab_refresh.setVisibility(View.GONE);
                text_refresh.setVisibility(View.GONE);
                text_sort_artist.setVisibility(View.GONE);
                text_sort_song.setVisibility(View.GONE);
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (fab_sort_artist.getVisibility() != View.VISIBLE) {
                    fab_sort_artist.setVisibility(View.VISIBLE);
                    fab_sort_song.setVisibility(View.VISIBLE);
                    fab_refresh.setVisibility(View.VISIBLE);
                    text_refresh.setVisibility(View.VISIBLE);
                    text_sort_artist.setVisibility(View.VISIBLE);
                    text_sort_song.setVisibility(View.VISIBLE);
                } else {
                    fab_sort_artist.setVisibility(View.GONE);
                    fab_sort_song.setVisibility(View.GONE);
                    fab_refresh.setVisibility(View.GONE);
                    text_refresh.setVisibility(View.GONE);
                    text_sort_artist.setVisibility(View.GONE);
                    text_sort_song.setVisibility(View.GONE);
                }
            }
        });
        return rootView;
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

    public void loadSettings(Context context) {
        recViewAdapter.loadSettings(context);
    }
}
