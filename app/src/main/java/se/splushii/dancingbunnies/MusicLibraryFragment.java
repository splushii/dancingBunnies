package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.events.LibraryChangedEvent;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
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
            MusicLibraryQuery query = new MusicLibraryQuery(
                    MusicLibrary.API_ID_ANY,
                    AudioPlayerService.MEDIA_ID_ROOT,
                    LibraryEntry.EntryType.ANY
            );
            refreshView(new LibraryView(query, 0, 0));
        }
        EventBus.getDefault().register(this);
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        currentLibraryView = new LibraryView(
                currentLibraryView.query,
                recViewAdapter.getCurrentPosition());
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe
    public void onMessageEvent(LibraryChangedEvent lce) {
        Log.d(LC, "got LibraryChangedEvent");
        refreshView(currentLibraryView);
    }

    public void refreshView(final LibraryView libView) {
        if (libView == null) {
            MusicLibraryQuery query = new MusicLibraryQuery(
                    MusicLibrary.API_ID_ANY,
                    AudioPlayerService.MEDIA_ID_ROOT,
                    LibraryEntry.EntryType.ANY
            );
            refreshView(new LibraryView(query, 0, 0));
            return;
        }
        if (!mediaBrowser.isConnected()) {
            Log.w(LC, "MediaBrowser not connected.");
            return;
        }
        if (currentLibraryView != null) {
            mediaBrowser.unsubscribe(currentLibraryView.query.id);
        }
        currentLibraryView = libView;
        Bundle options = libView.query.toBundle();
        MediaBrowserCompat.SubscriptionCallback subCb = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId,
                                         @NonNull List<MediaBrowserCompat.MediaItem> children,
                                         @NonNull Bundle options) {
                String optString = MusicLibraryQuery.getMusicLibraryQueryOptionsString(options);
                Log.d(LC, "subscription(" + parentId + ") (" + optString + "): "
                        + children.size());
                recViewAdapter.setDataset(children);
                currentLibraryView = libView;
                LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
                llm.scrollToPositionWithOffset(libView.pos, libView.pad);
            }

            @Override
            public void onError(@NonNull String parentId) {
                Log.d(LC, "MediaBrowser.subscribe(" + parentId + ") onError");
            }
        };
        Log.d(LC, libView.query.toString());
        mediaBrowser.subscribe(libView.query.id, options, subCb);
    }

    @Override
    public void onDestroyView() {
        fastScroller.onDestroy();
        fastScroller = null;
        fastScrollerBubble = null;
        super.onDestroyView();
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater menuInflater = getActivity().getMenuInflater();
        menuInflater.inflate(R.menu.song_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = recViewAdapter.getContextMenuHolder().getAdapterPosition();
        EntryID entryID = EntryID.from(recViewAdapter.getItemData(position));
        Log.d(LC, "info pos: " + position);
        switch (item.getItemId()) {
            case R.id.song_context_play:
                play(entryID);
                Log.d(LC, "song context play");
                return true;
            case R.id.song_context_queue:
                queue(entryID);
                Log.d(LC, "song context queue");
                return true;
            default:
                Log.d(LC, "song context unknown");
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(recView);
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
            refreshView(new LibraryView(
                    new MusicLibraryQuery(
                            MusicLibrary.API_ID_ANY,
                            AudioPlayerService.MEDIA_ID_ARTIST_ROOT,
                            LibraryEntry.EntryType.ANY
                    ), 0, 0));
            setFabVisibility(View.GONE);
        });

        fab_sort_song.setOnClickListener(view -> {
            Snackbar.make(view, "Song view", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            refreshView(new LibraryView(
                    new MusicLibraryQuery(
                            MusicLibrary.API_ID_ANY,
                            AudioPlayerService.MEDIA_ID_SONG_ROOT,
                            LibraryEntry.EntryType.ANY
                    ), 0, 0));
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

    public void browse(EntryID entryID) {
        addBackButtonHistory(
                new LibraryView(
                        currentLibraryView.query,
                        recViewAdapter.getCurrentPosition()
                )
        );
        MusicLibraryQuery query = new MusicLibraryQuery(entryID.src, entryID.id, entryID.type);
        refreshView(new LibraryView(query, 0, 0));
    }
}
