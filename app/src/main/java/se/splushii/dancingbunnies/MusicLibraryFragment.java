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
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.flags.impl.DataUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.LibraryView;
import se.splushii.dancingbunnies.ui.MusicLibraryAdapter;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragment extends AudioBrowserFragment {
    private static String LC = Util.getLogContext(MusicLibraryFragment.class);
    private RecyclerView recyclerView;
    private MusicLibraryAdapter recyclerViewAdapter;
    private LibraryView currentLibraryView;
    private String currentSubscriptionID;
    private LinkedList<LibraryView> viewBackStack;

    private TextView activeFiltersView;
    FastScroller fastScroller;
    FastScrollerBubble fastScrollerBubble;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBackStack = new LinkedList<>();
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshView(currentLibraryView);
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(LC, "onStop");
        if (currentLibraryView != null) {
            currentLibraryView = new LibraryView(
                    currentLibraryView.query,
                    recyclerViewAdapter.getCurrentPosition());
        }
    }

    @Override
    protected void onMediaBrowserConnected() {
        refreshView(currentLibraryView);
    }

    public void refreshView(final LibraryView libView) {
        if (libView == null) {
            MusicLibraryQuery query = new MusicLibraryQuery();
            query.addToQuery(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_ARTIST);
            refreshView(new LibraryView(query, 0, 0));
            return;
        }
        clearFilterView();
        if (!libView.query.isSearchQuery()) {
            Bundle b = libView.query.toBundle();
            for (String filterType: b.keySet()) {
                String filterValue = b.getString(filterType);
                addFilterToView(filterType, filterValue);
            }
        }
        if (activeFiltersView.getText().toString().isEmpty()) {
            activeFiltersView.setVisibility(View.GONE);
        } else {
            activeFiltersView.setVisibility(View.VISIBLE);
        }
        unsubscribe();
        currentSubscriptionID = libView.query.query(mediaBrowser, new MusicLibraryQuery.MusicLibraryQueryCallback() {
            @Override
            public void onQueryResult(@NonNull List<MediaBrowserCompat.MediaItem> items) {
                recyclerViewAdapter.setDataset(items, libView.query.isSearchQuery());
                currentLibraryView = libView;
                LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
                llm.scrollToPositionWithOffset(libView.pos, libView.pad);
            }
        });
    }

    private void unsubscribe() {
        if (currentSubscriptionID != null && mediaBrowser.isConnected()) {
            mediaBrowser.unsubscribe(currentSubscriptionID);
        }
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
        int position = recyclerViewAdapter.getContextMenuHolder().getAdapterPosition();
        EntryID entryID = EntryID.from(recyclerViewAdapter.getItemData(position));
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
        registerForContextMenu(recyclerView);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.musiclibrary_fragment_layout, container,
                false);

        recyclerView = rootView.findViewById(R.id.musiclibrary_recyclerview);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recyclerView.setLayoutManager(recViewLayoutManager);
        recyclerViewAdapter = new MusicLibraryAdapter(this);
        recyclerView.setAdapter(recyclerViewAdapter);

        fastScroller = rootView.findViewById(R.id.musiclibrary_fastscroller);
        fastScroller.setRecyclerView(recyclerView);
        fastScrollerBubble = rootView.findViewById(R.id.musiclibrary_fastscroller_bubble);
        fastScroller.setBubble(fastScrollerBubble);

        activeFiltersView = rootView.findViewById(R.id.musiclibrary_active_filters);

        View filterInput = rootView.findViewById(R.id.musiclibrary_filterinput);
        FloatingActionButton filterFab = rootView.findViewById(R.id.musiclibrary_filter_fab);
        filterFab.setOnClickListener(view -> filterInput.setVisibility(
                filterInput.getVisibility() == View.GONE ? View.VISIBLE : View.GONE
        ));
        Spinner filterInputType = rootView.findViewById(R.id.musiclibrary_filterinput_type);

        List<String> filterTypes = new ArrayList<>();
        List<String> metaKeys = new ArrayList<>();
        List<Map.Entry<String, String>> derp = new ArrayList<>(Meta.humanMap.entrySet());
        derp.sort(Comparator.comparing(Map.Entry::getValue));
        for (Map.Entry<String, String> entry: derp) {
            filterTypes.add(entry.getValue());
            metaKeys.add(entry.getKey());
        }
        ArrayAdapter<String> filterInputTypeAdapter = new ArrayAdapter<>(
                this.requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                filterTypes
        );
        filterInputType.setAdapter(filterInputTypeAdapter);
        EditText filterInputText = rootView.findViewById(R.id.musiclibrary_filterinput_text);
        filterInputText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int pos = filterInputType.getSelectedItemPosition();
                String filterType = filterTypes.get(pos);
                String metaKey = metaKeys.get(pos);
                String filterString = filterInputText.getText().toString();
                Log.d(LC, "Applying filter: " + filterType + "(" + filterString + ")");
                Toast.makeText(
                        this.requireContext(),
                        "Applying filter: " + filterType + "(" + filterString + ")",
                        Toast.LENGTH_SHORT
                ).show();
                filter(metaKey, filterString);
                return true;
            }
            return false;
        });

        FloatingActionButton fab = rootView.findViewById(R.id.musiclibrary_fab);
        final FloatingActionButton sortByArtistFab = rootView.findViewById(R.id.fab_sort_artist);
        final FloatingActionButton sortBySongFab = rootView.findViewById(R.id.fab_sort_song);

        sortByArtistFab.setOnClickListener(view -> {
            Snackbar.make(view, "Artist view", Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
            MusicLibraryQuery query = new MusicLibraryQuery();
            query.addToQuery(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_ARTIST);
            refreshView(new LibraryView(query, 0, 0));
            setFabVisibility(View.GONE);
        });

        sortBySongFab.setOnClickListener(view -> {
            Snackbar.make(view, "Song view", Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show();
            MusicLibraryQuery query = new MusicLibraryQuery();
            query.addToQuery(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_MEDIA_ID);
            refreshView(new LibraryView(query, 0, 0));
            setFabVisibility(View.GONE);
        });

        fab.setOnClickListener(view -> {
            if (sortByArtistFab.getVisibility() != View.VISIBLE) {
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
        TextView text_sort_artist = rootView.findViewById(R.id.fab_sort_artist_label);
        TextView text_sort_song = rootView.findViewById(R.id.fab_sort_song_label);
        fab_sort_artist.setVisibility(visibility);
        fab_sort_song.setVisibility(visibility);
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
        refreshView(null);
        return false;
    }

    public void filter(String filterType, String filter) {
        addBackButtonHistory(
                new LibraryView(
                        currentLibraryView.query,
                        recyclerViewAdapter.getCurrentPosition()
                )
        );
        MusicLibraryQuery query = new MusicLibraryQuery(currentLibraryView.query);
        query.addToQuery(filterType, filter);
        refreshView(new LibraryView(query, 0, 0));
    }

    public void browse(EntryID entryID) {
        addBackButtonHistory(
                new LibraryView(
                        currentLibraryView.query,
                        recyclerViewAdapter.getCurrentPosition()
                )
        );
        MusicLibraryQuery query;
        switch (entryID.type) {
            case Meta.METADATA_KEY_ARTIST:
                query = new MusicLibraryQuery();
                query.addToQuery(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_ALBUM);
                query.addToQuery(Meta.METADATA_KEY_ARTIST, entryID.id);
                break;
            case Meta.METADATA_KEY_ALBUM:
                query = new MusicLibraryQuery(currentLibraryView.query);
                query.addToQuery(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_MEDIA_ID);
                if (entryID.id != null) {
                    query.addToQuery(Meta.METADATA_KEY_ALBUM, entryID.id);
                }
                break;
            default:
                query = new MusicLibraryQuery();
                break;
        }
        refreshView(new LibraryView(query, 0, 0));
    }

    private void clearFilterView() {
        activeFiltersView.setText("");
    }

    private void addFilterToView(String filterType, String filter) {
        String s = activeFiltersView.getText().toString();
        s += s.isEmpty() ? "" : ", ";
        String humanFilterType = Meta.humanMap.get(filterType);
        String humanFilter = Meta.METADATA_KEY_TYPE.equals(filterType) ?
                Meta.humanMap.get(filter) : filter;
        activeFiltersView.setText(
                String.format("%s%s=%s", s, humanFilterType, humanFilter)
        );
    }
}
