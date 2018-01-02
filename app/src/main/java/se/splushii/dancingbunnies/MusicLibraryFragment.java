package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.events.LibraryChangedEvent;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.LibraryView;
import se.splushii.dancingbunnies.ui.MusicLibraryAdapter;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragment extends AudioBrowserFragment {
    private static String LC = Util.getLogContext(MusicLibraryFragment.class);
    private RecyclerView recView;
    private MusicLibraryAdapter recViewAdapter;
    private LibraryView currentLibraryView;
    private LinkedList<LibraryView> viewBackStack;

    FastScroller fastScroller;
    FastScrollerBubble fastScrollerBubble;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBackStack = new LinkedList<>();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (currentLibraryView != null) {
            refreshView(currentLibraryView);
        } else {
            refreshView(new LibraryView(MusicLibrary.API_ID_ANY,
                    AudioPlayerService.MEDIA_ID_ROOT,
                    LibraryEntry.EntryType.ANY, 0, 0));
        }
        EventBus.getDefault().register(this);
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        currentLibraryView = recViewAdapter.getCurrentView();
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe
    public void onMessageEvent(LibraryChangedEvent lce) {
        Log.d(LC, "got LibraryChangedEvent");
        refreshView(currentLibraryView);
    }

    public void refreshView(final LibraryView libView) {
        if (!mediaBrowser.isConnected()) {
            Log.w(LC, "MediaBrowser not connected.");
        }
        if (currentLibraryView != null) {
            mediaBrowser.unsubscribe(currentLibraryView.parentId);
        }
        currentLibraryView = libView;
        Bundle options = AudioPlayerService.generateMusicLibraryQueryOptions(libView.src, libView.type);
        mediaBrowser.subscribe(libView.parentId, options, new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children, @NonNull Bundle options) {
                String optString = AudioPlayerService.getMusicLibraryQueryOptionsString(options);
                Log.d(LC, "subscription(" + parentId + ") (" + optString + "): " + children.size());
                recViewAdapter.setDataset(children, libView.src, parentId, libView.type);
                LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
                llm.scrollToPositionWithOffset(libView.pos, libView.pad);
            }

            @Override
            public void onError(@NonNull String parentId) {
                Log.d(LC, "MediaBrowser.subscribe(" + parentId + ") onError");
            }
        });
    }

    @Override
    public void onDestroyView() {
        fastScroller.onDestroy();
        fastScroller = null;
        fastScrollerBubble = null;
        super.onDestroyView();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.musiclibrary_fragment_layout, container,
                false);

        recView = rootView.findViewById(R.id.musiclibrary_recyclerview);
        recView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new MusicLibraryAdapter(this);
        recView.setAdapter(recViewAdapter);

        fastScroller = rootView.findViewById(R.id.musiclibrary_fastscroller);
        fastScroller.setRecyclerView(recView);
        fastScrollerBubble = rootView.findViewById(R.id.musiclibrary_fastscroller_bubble);
        fastScroller.setBubble(fastScrollerBubble);

        FloatingActionButton fab = rootView.findViewById(R.id.fab);
        final FloatingActionButton fab_sort_artist = rootView.findViewById(R.id.fab_sort_artist);
        final FloatingActionButton fab_sort_song = rootView.findViewById(R.id.fab_sort_song);
        final FloatingActionButton fab_refresh = rootView.findViewById(R.id.fab_sort_refresh);

        fab_refresh.setOnClickListener(view -> {
            refreshView(currentLibraryView);
            setFabVisibility(View.GONE);
        });

        fab_sort_artist.setOnClickListener(view -> {
            Snackbar.make(view, "Artist view", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            refreshView(new LibraryView(MusicLibrary.API_ID_ANY,
                    AudioPlayerService.MEDIA_ID_ARTIST_ROOT,
                    LibraryEntry.EntryType.ANY, 0, 0));
            setFabVisibility(View.GONE);
        });

        fab_sort_song.setOnClickListener(view -> {
            Snackbar.make(view, "Song view", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            refreshView(new LibraryView(MusicLibrary.API_ID_ANY,
                    AudioPlayerService.MEDIA_ID_SONG_ROOT,
                    LibraryEntry.EntryType.ANY, 0, 0));
            setFabVisibility(View.GONE);
        });

        fab.setOnClickListener(view -> {
            if (fab_sort_artist.getVisibility() != View.VISIBLE) {
                setFabVisibility(View.VISIBLE);
            } else {
                setFabVisibility(View.GONE);
            }
        });
        return rootView;
    }

    private void setFabVisibility(int visibility) {
        View rootView = this.getView();
        FloatingActionButton fab_sort_artist = rootView.findViewById(R.id.fab_sort_artist);
        FloatingActionButton fab_sort_song = rootView.findViewById(R.id.fab_sort_song);
        FloatingActionButton fab_refresh = rootView.findViewById(R.id.fab_sort_refresh);
        TextView text_sort_artist = rootView.findViewById(R.id.fab_sort_artist_label);
        TextView text_sort_song = rootView.findViewById(R.id.fab_sort_song_label);
        TextView text_refresh = rootView.findViewById(R.id.fab_sort_refresh_label);
        fab_sort_artist.setVisibility(visibility);
        fab_sort_song.setVisibility(visibility);
        fab_refresh.setVisibility(visibility);
        text_refresh.setVisibility(visibility);
        text_sort_artist.setVisibility(visibility);
        text_sort_song.setVisibility(visibility);
    }

    public void addBackButtonHistory(LibraryView libView) {
        viewBackStack.push(libView);
    }

    public boolean onBackPressed() {
        if (viewBackStack.size() > 0) {
            refreshView(viewBackStack.pop());
            return true;
        }
        return false;
    }
}
