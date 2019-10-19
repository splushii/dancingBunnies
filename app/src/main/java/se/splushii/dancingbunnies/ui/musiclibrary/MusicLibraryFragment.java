package se.splushii.dancingbunnies.ui.musiclibrary;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.AddToNewPlaylistDialogFragment;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.selection.EntryIDItemDetailsLookup;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;

public class MusicLibraryFragment extends AudioBrowserFragment {
    private static final String LC = Util.getLogContext(MusicLibraryFragment.class);

    private MusicLibraryAdapter recyclerViewAdapter;
    private LinearLayoutManager recViewLayoutManager;

    private View searchView;
    private TextView searchInfoText;

    private View filterView;
    private ChipGroup filterChips;
    private View filterEdit;
    private TextView filterEditType;
    private String filterEditTypeValue = "";
    private EditText filterEditInput;
    private View filterNew;
    private Spinner filterNewType;
    private EditText filterNewInput;

    private ArrayList<String> metaKeys;
    private ArrayAdapter<String> metaKeyAdapter;

    private int showSelectionPos;
    private int sortSelectionPos;

    private TextView headerShow;
    private View headerSortedByRoot;
    private TextView headerSortedBy;
    private ImageButton headerSortedByOrder;
    private TextView headerArtist;
    private View headerNum;

    private SelectionTracker<EntryID> selectionTracker;
    private ActionMode actionMode;

    private FastScroller fastScroller;
    private FastScrollerBubble fastScrollerBubble;

    private MusicLibraryFragmentModel model;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        model = ViewModelProviders.of(requireActivity()).get(MusicLibraryFragmentModel.class);
        model.getUserState().observe(getViewLifecycleOwner(), state -> {
            refreshView(state);
            model.query(mediaBrowser);
        });
        recyclerViewAdapter.setModel(model);
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        model.updateUserState(recyclerViewAdapter.getCurrentPosition());
        super.onStop();
    }

    @Override
    protected void onMediaBrowserConnected() {
        if (model != null) {
            refreshView(model.getUserState().getValue());
            model.query(mediaBrowser);
        }
    }

    private void refreshView(final MusicLibraryUserState newUserState) {
        if (newUserState == null) {
            return;
        }
        recyclerViewAdapter.scrollWhenReady();
        String showField = getCurrentQuery().getShowField();
        String showDisplayField = Meta.getDisplayKey(showField);
        String sortField = getCurrentQuery().getSortByField();
        String sortDisplayField = Meta.getDisplayKey(sortField);
        clearFilterView();
        boolean browsable = isBrowsable(showField);
        headerShow.setText(showDisplayField);
        headerArtist.setVisibility(showHeaderArtist(isBrowsable(showField)) ? VISIBLE : GONE);
        headerArtist.setLayoutParams(new TableRow.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                querySortedByShow() ? 1.5f : 1f)
        );

        headerNum.setVisibility(showHeaderNum(browsable) ? VISIBLE : GONE);
        if (showSortedByHeader()) {
            headerSortedBy.setText(sortDisplayField);
            headerSortedByRoot.setVisibility(VISIBLE);
        } else {
            headerSortedByRoot.setVisibility(GONE);
        }
        headerSortedByOrder.setImageResource(isSortedAscending() ?
                R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp
        );
        if (isSearchQuery()) {
            Log.d(LC, "refreshView search");
            headerShow.setEnabled(false);
            filterView.setVisibility(GONE);
            fastScroller.enableBubble(false);
            searchInfoText.setText(newUserState.query.getSearchQuery());
            searchView.setVisibility(VISIBLE);
        } else {
            Log.d(LC, "refreshView query");
            headerShow.setEnabled(true);
            searchView.setVisibility(GONE);
            fastScroller.enableBubble(true);
            showSelectionPos = getMetaKeyPosition(showField);
            sortSelectionPos = getMetaKeyPosition(sortField);
            Bundle b = newUserState.query.getQueryBundle();
            for (String metaKey: b.keySet()) {
                String filterValue = b.getString(metaKey);
                addFilterToView(metaKey, filterValue);
            }
            filterView.setVisibility(VISIBLE);
        }
        if (filterChips.getChildCount() > 0) {
            filterChips.setVisibility(VISIBLE);
        } else {
            filterChips.setVisibility(GONE);
        }
    }

    private boolean isBrowsable(String field) {
        return !Meta.FIELD_SPECIAL_MEDIA_ID.equals(field);
    }

    @Override
    public void onDestroyView() {
        Log.d(LC, "onDestroyView");
        fastScroller.onDestroy();
        fastScroller = null;
        fastScrollerBubble = null;
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectionTracker != null) {
            selectionTracker.onSaveInstanceState(outState);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.musiclibrary_fragment_layout, container,
                false);

        searchView = rootView.findViewById(R.id.musiclibrary_search);
        filterView = rootView.findViewById(R.id.musiclibrary_filter);
        RecyclerView recyclerView = rootView.findViewById(R.id.musiclibrary_recyclerview);
        recViewLayoutManager = new LinearLayoutManager(this.getContext());
        recyclerView.setLayoutManager(recViewLayoutManager);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(0, 50);

        fastScroller = rootView.findViewById(R.id.musiclibrary_fastscroller);
        fastScroller.setRecyclerView(recyclerView);
        fastScrollerBubble = rootView.findViewById(R.id.musiclibrary_fastscroller_bubble);
        fastScroller.setBubble(fastScrollerBubble);

        recyclerViewAdapter = new MusicLibraryAdapter(this, recyclerView, fastScrollerBubble);
        recyclerView.setAdapter(recyclerViewAdapter);
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    clearFocus();
                }
            }
        });

        metaKeys = new ArrayList<>();
        List<String> displayedFields = new ArrayList<>();

        headerShow = rootView.findViewById(R.id.musiclibrary_header_show);
        final PopupMenu headerShowDropdownMenu = new PopupMenu(requireContext(), headerShow);
        final Menu headerShowMenu = headerShowDropdownMenu.getMenu();
        headerShowDropdownMenu.setOnMenuItemClickListener(item -> {
            int position = item.getItemId();
            if (showSelectionPos == position) {
                return false;
            }
            showSelectionPos = position;
            String field = metaKeys.get(position);
            String displayedField = displayedFields.get(position);
            Log.d(LC, "Showing entries of type: " + displayedField);
            Toast.makeText(
                    requireContext(),
                    "Showing entries of type: " + displayedField,
                    Toast.LENGTH_SHORT
            ).show();
            displayType(field);
            return true;
        });
        headerShow.setOnClickListener(view -> headerShowDropdownMenu.show());
        headerSortedByRoot = rootView.findViewById(R.id.musiclibrary_header_sortedby_root);
        headerSortedBy = rootView.findViewById(R.id.musiclibrary_header_sortedby);
        final PopupMenu headerSortedByDropdownMenu = new PopupMenu(requireContext(), headerSortedBy);
        final Menu headerSortedByMenu = headerSortedByDropdownMenu.getMenu();
        headerSortedByDropdownMenu.setOnMenuItemClickListener(item -> {
            int position = item.getItemId();
            if (sortSelectionPos == position) {
                return false;
            }
            sortSelectionPos = position;
            String field = metaKeys.get(position);
            String displayedField = displayedFields.get(position);
            Log.d(LC, "Sorting by: " + displayedField);
            Toast.makeText(
                    requireContext(),
                    "Sorting by: " + displayedField,
                    Toast.LENGTH_SHORT
            ).show();
            sortBy(field);
            return true;
        });
        headerSortedBy.setOnClickListener(view -> headerSortedByDropdownMenu.show());
        headerSortedByOrder = rootView.findViewById(R.id.musiclibrary_header_sortedby_order);
        headerSortedByOrder.setOnClickListener(view -> setSortOrder(!isSortedAscending()));
        headerArtist = rootView.findViewById(R.id.musiclibrary_header_artist);
        headerNum = rootView.findViewById(R.id.musiclibrary_header_num);

        ActionModeCallback actionModeCallback = new ActionModeCallback(
                this,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        MutableSelection<EntryID> selection = new MutableSelection<>();
                        selectionTracker.copySelection(selection);
                        List<EntryID> selectionList = new LinkedList<>();
                        selection.forEach(selectionList::add);
                        return selectionList;
                    }

                    @Override
                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Playlist> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public Bundle getQueryBundle() {
                        return getCurrentQueryBundle();
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        selectionTracker.clearSelection();
                        MusicLibraryFragment.this.actionMode = null;
                    }
                }
        );
        actionModeCallback.setActions(
                new int[]{
                        ACTION_ADD_MULTIPLE_TO_QUEUE,
                        ACTION_ADD_MULTIPLE_TO_PLAYLIST
                },
                new int[]{
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_QUEUE,
                        ACTION_ADD_MULTIPLE_TO_PLAYLIST,
                        ACTION_CACHE_MULTIPLE,
                        ACTION_CACHE_DELETE_MULTIPLE
                },
                new int[0]
        );

        selectionTracker = new SelectionTracker.Builder<>(
                MainActivity.SELECTION_ID_MUSICLIBRARY,
                recyclerView,
                new MusicLibraryKeyProvider(recyclerViewAdapter),
                new EntryIDItemDetailsLookup(recyclerView),
                StorageStrategy.createParcelableStorage(EntryID.class)
        ).withSelectionPredicate(
                SelectionPredicates.createSelectAnything()
        ).build();
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onItemStateChanged(@NonNull Object key, boolean selected) {}

            @Override
            public void onSelectionRefresh() {}

            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection() && actionMode == null) {
                    actionMode = requireActivity().startActionMode(actionModeCallback);
                }
                if (!selectionTracker.hasSelection() && actionMode != null) {
                    actionMode.finish();
                }
                if (actionMode != null && selectionTracker.hasSelection()) {
                    actionMode.setTitle(selectionTracker.getSelection().size() + " entries.");
                }
            }

            @Override
            public void onSelectionRestored() {}
        });
        recyclerViewAdapter.setSelectionTracker(selectionTracker);
        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        searchInfoText = rootView.findViewById(R.id.musiclibrary_search_info_query);
        View searchInfoView = rootView.findViewById(R.id.musiclibrary_search_info);
        searchInfoView.setOnClickListener(v -> model.searchQueryClicked(searchInfoText.getText()));

        View filterHomeBtn = rootView.findViewById(R.id.musiclibrary_filter_home);
        View searchHomeBtn = rootView.findViewById(R.id.musiclibrary_search_home);
        filterHomeBtn.setOnClickListener(v -> {
            model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
            model.reset();
        });
        searchHomeBtn.setOnClickListener(v -> {
            model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
            model.reset();
        });

        filterNew = rootView.findViewById(R.id.musiclibrary_filter_new);
        filterNewInput = rootView.findViewById(R.id.musiclibrary_filter_new_text);
        filterNewType = rootView.findViewById(R.id.musiclibrary_filter_new_type);
        metaKeyAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                displayedFields
        );
        filterNewType.setAdapter(metaKeyAdapter);
        filterNewInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int pos = filterNewType.getSelectedItemPosition();
                String field = metaKeys.get(pos);
                String displayedField = displayedFields.get(pos);
                String filterString = filterNewInput.getText().toString();
                Log.d(LC, "Applying filter: " + displayedField + "(" + filterString + ")");
                Toast.makeText(
                        this.requireContext(),
                        "Applying filter: " + displayedField + "(" + filterString + ")",
                        Toast.LENGTH_SHORT
                ).show();
                filter(field, filterString);
                filterNew.setVisibility(GONE);
                Util.hideSoftInput(requireActivity(), filterNewInput);
                return true;
            }
            return false;
        });
        MetaStorage.getInstance(requireContext())
                .getMetaFields()
                .observe(getViewLifecycleOwner(), newFields -> {
                    newFields.add(Meta.FIELD_SPECIAL_MEDIA_ID);
                    newFields.add(Meta.FIELD_SPECIAL_MEDIA_SRC);
                    String showField = getCurrentQuery().getShowField();
                    String sortField = getCurrentQuery().getSortByField();
                    Collections.sort(newFields, (f1, f2) -> {
                        if (f1.equals(f2)) {
                            return 0;
                        }
                        for (String field: Meta.FIELD_ORDER) {
                            if (f1.equals(field)) {
                                return -1;
                            }
                            if (f2.equals(field)) {
                                return 1;
                            }
                        }
                        return f1.compareTo(f2);
                    });
                    int initialShowSelectionPos = 0;
                    int initialSortSelectionPos = 0;
                    for (int i = 0; i < newFields.size(); i++) {
                        if (newFields.get(i).equals(showField)) {
                            initialShowSelectionPos = i;
                        }
                        if (newFields.get(i).equals(sortField)) {
                            initialSortSelectionPos = i;
                        }
                    }
                    metaKeys.clear();
                    metaKeys.addAll(newFields);
                    displayedFields.clear();
                    displayedFields.addAll(newFields.stream()
                            .map(Meta::getDisplayKey)
                            .collect(Collectors.toList())
                    );
                    headerShowMenu.clear();
                    for (int i = 0; i < displayedFields.size(); i++) {
                        headerShowMenu.add(0, i, 0, displayedFields.get(i));
                    }
                    headerSortedByMenu.clear();
                    for (int i = 0; i < displayedFields.size(); i++) {
                        headerSortedByMenu.add(0, i, 0, displayedFields.get(i));
                    }
                    metaKeyAdapter.notifyDataSetChanged();
                    showSelectionPos = initialShowSelectionPos;
                    sortSelectionPos = initialSortSelectionPos;
                });

        ImageButton saveQueryBtn = rootView.findViewById(R.id.musiclibrary_filter_save);
        saveQueryBtn.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, getCurrentQueryBundle())
        );

        filterChips = rootView.findViewById(R.id.musiclibrary_filter_chips);

        filterEdit = rootView.findViewById(R.id.musiclibrary_filter_edit);
        filterEditType = rootView.findViewById(R.id.musiclibrary_filter_edit_type);
        filterEditInput = rootView.findViewById(R.id.musiclibrary_filter_edit_input);

        rootView.findViewById(R.id.musiclibrary_filter_chip_new).setOnClickListener(view -> {
            filterEdit.setVisibility(GONE);
            if (filterNew.getVisibility() != VISIBLE) {
                filterNew.setVisibility(VISIBLE);
                filterNewInput.requestFocus();
                Util.showSoftInput(requireActivity(), filterNewInput);
            } else {
                filterNew.setVisibility(GONE);
                Util.hideSoftInput(requireActivity(), filterNewInput);
            }
        });

        return rootView;
    }

    public boolean onBackPressed() {
        boolean actionPerformed = false;
        if (filterEdit.getVisibility() == VISIBLE) {
            filterEdit.setVisibility(GONE);
            actionPerformed = true;
        }
        if (filterNew.getVisibility() == VISIBLE) {
            filterNew.setVisibility(GONE);
            actionPerformed = true;
        }
        if (actionPerformed) {
            return true;
        }
        return model.popBackStack();
    }

    private void displayType(String displayType) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.displayType(displayType);
        model.sortBy(displayType);
        model.setSortOrder(true);
    }

    private void sortBy(String field) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.sortBy(field);
        model.setSortOrder(true);
    }

    private void setSortOrder(boolean ascending) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.setSortOrder(ascending);
    }

    private void filter(String filterType, String filter) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.filter(filterType, filter);
    }

    void addBackStackHistory() {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
    }

    void setQuery(MusicLibraryQuery query) {
        model.setQuery(query);
    }

    private int getMetaKeyPosition(String key) {
        return metaKeys == null ? 0 : metaKeys.indexOf(key);
    }

    private void clearFilterView() {
        filterChips.removeAllViews();
    }

    private void addFilterToView(String metaKey, String filter) {
        String text = String.format("%s: %s", Meta.getDisplayKey(metaKey), Meta.getDisplayValue(metaKey, filter));
        Chip newChip = new Chip(requireContext());
        newChip.setEllipsize(TextUtils.TruncateAt.END);
        newChip.setChipBackgroundColorResource(R.color.colorAccent);
        newChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        newChip.setText(text);
        newChip.setOnClickListener(v -> {
            filterNew.setVisibility(GONE);
            if (metaKey.equals(filterEditTypeValue) && filterEdit.getVisibility() == VISIBLE) {
                filterEdit.setVisibility(GONE);
                Util.hideSoftInput(requireActivity(), filterEditInput);
            } else {
                filterEditInput.setText(filter);
                filterEditInput.setOnEditorActionListener((v1, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        String filterString = filterEditInput.getText().toString();
                        Log.d(LC, "Applying filter: " + metaKey + "(" + filterString + ")");
                        Toast.makeText(
                                this.requireContext(),
                                "Applying filter: " + metaKey + "(" + filterString + ")",
                                Toast.LENGTH_SHORT
                        ).show();
                        filter(metaKey, filterString);
                        filterEdit.setVisibility(GONE);
                        Util.hideSoftInput(requireActivity(), filterEditInput);
                        return true;
                    }
                    return false;
                });
                filterEditTypeValue = metaKey;
                String filterEditTypeText = Meta.getDisplayKey(metaKey) + ':';
                filterEditType.setText(filterEditTypeText);
                filterEdit.setVisibility(VISIBLE);
                filterEditInput.requestFocus();
                Util.showSoftInput(requireActivity(), filterEditInput);
            }
        });
        newChip.setOnCloseIconClickListener(v -> clearFilter(metaKey));
        newChip.setCloseIconVisible(true);
        filterChips.addView(newChip, filterChips.getChildCount());
    }

    private void clearFilter(String filterType) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.clearFilter(filterType);
    }

    MusicLibraryQuery getCurrentQuery() {
        MusicLibraryUserState state = model.getUserState().getValue();
        return state == null ? null : new MusicLibraryQuery(state.query);
    }

    Bundle getCurrentQueryBundle() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return new Bundle();
        }
        Bundle b = query.getQueryBundle();
        if (b == null) {
            return new Bundle();
        }
        return new Bundle(query.getQueryBundle());
    }

    boolean querySortedByShow() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return false;
        }
        return query.querySortedByShow();
    }

    boolean isSortedAscending() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return false;
        }
        return query.isSortOrderAscending();
    }

    String querySortedByKey() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return null;
        }
        return query.getSortByField();
    }

    public void clearSelection() {
        if (selectionTracker != null) {
            selectionTracker.clearSelection();
        }
        if (recyclerViewAdapter != null) {
            recyclerViewAdapter.hideTrackItemActions();
        }
    }

    void scrollTo(int pos, int pad) {
        recViewLayoutManager.scrollToPositionWithOffset(pos, pad);
    }

    boolean showAllEntriesRow() {
        MusicLibraryQuery query = getCurrentQuery();
        return !query.isSearchQuery() && !Meta.FIELD_SPECIAL_MEDIA_ID.equals(query.getShowField());
    }

    private boolean showSortedByHeader() {
        return !isSearchQuery(); // && !querySortedByShow();
    }

    boolean showSortedByValues() {
        return !isSearchQuery() && !querySortedByShow();
    }

    boolean isSearchQuery() {
        return getCurrentQuery().isSearchQuery();
    }

    boolean showHeaderArtist(boolean browsable) {
        MusicLibraryQuery query = getCurrentQuery();
        return query.isSearchQuery()
                || !browsable && !Meta.FIELD_ARTIST.equals(query.getSortByField());
    }

    boolean showHeaderNum(boolean browsable) {
        return !isSearchQuery() && browsable;
    }

    public void clearFocus() {
        if (recyclerViewAdapter != null) {
            recyclerViewAdapter.hideTrackItemActions();
        }
        if (filterEdit != null && filterEdit.getVisibility() == VISIBLE) {
            Util.hideSoftInput(requireActivity(), filterEditInput);
            filterEdit.setVisibility(GONE);
        }
        if (filterNew != null && filterNew.getVisibility() == VISIBLE) {
            Util.hideSoftInput(requireActivity(), filterNewInput);
            filterNew.setVisibility(GONE);
        }
    }
}
