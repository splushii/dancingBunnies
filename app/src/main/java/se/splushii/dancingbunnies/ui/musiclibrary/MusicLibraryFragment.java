package se.splushii.dancingbunnies.ui.musiclibrary;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.AddToNewPlaylistDialogFragment;
import se.splushii.dancingbunnies.ui.AddToPlaylistDialogFragment;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.selection.EntryIDItemDetailsLookup;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class MusicLibraryFragment extends AudioBrowserFragment {
    private static final String LC = Util.getLogContext(MusicLibraryFragment.class);

    private MusicLibraryAdapter recyclerViewAdapter;
    private LinearLayoutManager recViewLayoutManager;
    private TextView headerShow;
    private TextView headerSortedBy;
    private TextView headerArtist;
    private View headerNum;
    private View headerFastScrollerPad;

    private SelectionTracker<EntryID> selectionTracker;
    private ActionMode actionMode;

    private FastScroller fastScroller;
    private FastScrollerBubble fastScrollerBubble;

    private View searchView;
    private View searchInfoView;
    private TextView searchInfoText;

    private View filterView;

    private ImageButton saveQueryBtn;
    private ChipGroup filterChips;

    private Spinner entryTypeSelectSpinner;
    private int entryTypeSelectionPos;
    private Spinner sortSelectSpinner;
    private int sortSelectionPos;

    private View filterEdit;
    private TextView filterEditType;
    private EditText filterEditInput;

    private View filterNew;
    private Spinner filterNewType;

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

        String showField = getCurrentQuery().getShowField();
        String sortField = getCurrentQuery().getSortByField();
        clearFilterView();
        boolean browsable = isBrowsable(showField);
        headerShow.setText(showField);
        headerArtist.setVisibility(showHeaderArtist(isBrowsable(showField)) ? VISIBLE : GONE);
        headerNum.setVisibility(showHeaderNum(browsable) ? VISIBLE : GONE);
        if (showHeaderSortedBy()) {
            headerSortedBy.setText(sortField);
            headerSortedBy.setVisibility(VISIBLE);
        } else {
            headerSortedBy.setVisibility(GONE);
        }
        if (isSearchQuery()) {
            Log.d(LC, "refreshView search");
            filterView.setVisibility(GONE);
            fastScroller.enableBubble(false);
            searchInfoText.setText(newUserState.query.getSearchQuery());
            searchView.setVisibility(VISIBLE);
        } else {
            Log.d(LC, "refreshView query");
            searchView.setVisibility(GONE);
            fastScroller.enableBubble(true);
            Chip chip = new Chip(requireContext());
            chip.setChipIconResource(R.drawable.ic_add_black_24dp);
            chip.setTextStartPadding(0.0f);
            chip.setTextEndPadding(0.0f);
            chip.setChipEndPadding(chip.getChipStartPadding());
            chip.setOnClickListener(v -> {
                filterEdit.setVisibility(GONE);
                filterNew.setVisibility(filterNew.getVisibility() == VISIBLE ? GONE : VISIBLE);
            });
            filterChips.addView(chip);
            setSpinnerSelection(entryTypeSelectSpinner, showField);
            entryTypeSelectionPos = entryTypeSelectSpinner.getSelectedItemPosition();
            setSpinnerSelection(sortSelectSpinner, sortField);
            sortSelectionPos = sortSelectSpinner.getSelectedItemPosition();
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
                    recyclerViewAdapter.hideTrackItemActions();
                }
            }
        });
        headerShow = rootView.findViewById(R.id.musiclibrary_header_show);
        headerSortedBy = rootView.findViewById(R.id.musiclibrary_header_sortedby);
        headerArtist = rootView.findViewById(R.id.musiclibrary_header_artist);
        headerNum = rootView.findViewById(R.id.musiclibrary_header_num);
        headerFastScrollerPad = rootView.findViewById(R.id.musiclibrary_header_fastscroller_padding);
        fastScroller.setOnHidden(hidden ->
                headerFastScrollerPad.setVisibility(hidden ? GONE : VISIBLE)
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
                    actionMode = getActivity().startActionMode(actionModeCallback);
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
        searchInfoView = rootView.findViewById(R.id.musiclibrary_search_info);
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

        entryTypeSelectSpinner = rootView.findViewById(R.id.musiclibrary_entry_type_spinner);
        sortSelectSpinner = rootView.findViewById(R.id.musiclibrary_sort_spinner);
        filterNew = rootView.findViewById(R.id.musiclibrary_filter_new);
        filterNewType = rootView.findViewById(R.id.musiclibrary_filter_new_type);
        MetaStorage.getInstance(requireContext())
                .getMetaFields()
                .observe(getViewLifecycleOwner(), fields -> {
                    String showField = getCurrentQuery().getShowField();
                    String sortField = getCurrentQuery().getSortByField();
                    fields.add(Meta.FIELD_SPECIAL_MEDIA_ID);
                    fields.add(Meta.FIELD_SPECIAL_MEDIA_SRC);
                    Collections.sort(fields, (f1, f2) -> {
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
                    for (int i = 0; i < fields.size(); i++) {
                        if (fields.get(i).equals(showField)) {
                            initialShowSelectionPos = i;
                        }
                        if (fields.get(i).equals(sortField)) {
                            initialSortSelectionPos = i;
                        }
                    }
                    ArrayAdapter<String> fieldsAdapter = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            fields
                    );
                    entryTypeSelectSpinner.setAdapter(fieldsAdapter);
                    entryTypeSelectSpinner.setSelection(initialShowSelectionPos);
                    entryTypeSelectionPos = initialShowSelectionPos;
                    entryTypeSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (entryTypeSelectionPos == position) {
                                return;
                            }
                            String field = fieldsAdapter.getItem(position);
                            Log.d(LC, "Showing entries of type: " + field);
                            Toast.makeText(
                                    requireContext(),
                                    "Showing entries of type: " + field,
                                    Toast.LENGTH_SHORT
                            ).show();
                            displayType(field);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                    sortSelectSpinner.setAdapter(fieldsAdapter);
                    sortSelectSpinner.setSelection(initialSortSelectionPos);
                    sortSelectionPos = initialSortSelectionPos;
                    sortSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (sortSelectionPos == position) {
                                return;
                            }
                            String field = fieldsAdapter.getItem(position);
                            Log.d(LC, "Sorting by: " + field);
                            Toast.makeText(
                                    requireContext(),
                                    "Sorting by: " + field,
                                    Toast.LENGTH_SHORT
                            ).show();
                            sortBy(field);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                    filterNewType.setAdapter(fieldsAdapter);
                    EditText filterNewInput = rootView.findViewById(R.id.musiclibrary_filter_new_text);
                    filterNewInput.setOnEditorActionListener((v, actionId, event) -> {
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            int pos = filterNewType.getSelectedItemPosition();
                            String field = fields.get(pos);
                            String filterString = filterNewInput.getText().toString();
                            Log.d(LC, "Applying filter: " + field + "(" + filterString + ")");
                            Toast.makeText(
                                    this.requireContext(),
                                    "Applying filter: " + field + "(" + filterString + ")",
                                    Toast.LENGTH_SHORT
                            ).show();
                            filter(field, filterString);
                            return true;
                        }
                        return false;
                    });
                });

        saveQueryBtn = rootView.findViewById(R.id.musiclibrary_filter_save);
        saveQueryBtn.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, getCurrentQueryBundle())
        );
        filterChips = rootView.findViewById(R.id.musiclibrary_filter_chips);

        filterEdit = rootView.findViewById(R.id.musiclibrary_filter_edit);
        filterEditType = rootView.findViewById(R.id.musiclibrary_filter_edit_type);
        filterEditInput = rootView.findViewById(R.id.musiclibrary_filter_edit_input);

        return rootView;
    }

    public boolean onBackPressed() {
        return model.popBackStack();
    }

    private void displayType(String displayType) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.displayType(displayType);
        model.sortBy(displayType);
    }

    private void sortBy(String field) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.sortBy(field);
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

    private void setSpinnerSelection(Spinner spinner, Object obj) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (obj.equals(spinner.getItemAtPosition(i))) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void clearFilterView() {
        filterChips.removeAllViews();
    }

    private void addFilterToView(String metaKey, String filter) {
        String text = String.format("%s: %s", metaKey, filter);
        Chip newChip = new Chip(requireContext());
        newChip.setEllipsize(TextUtils.TruncateAt.END);
        newChip.setChipBackgroundColorResource(R.color.colorAccent);
        newChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        newChip.setText(text);
        newChip.setOnClickListener(v -> {
            filterNew.setVisibility(GONE);
            String filterEditTypeText = metaKey + ':';
            if (chipHasSameFilter(text, filterEditType.getText().toString(),
                    filterEditInput.getText().toString())) {
                filterEdit.setVisibility(filterEdit.getVisibility() == VISIBLE ? GONE : VISIBLE);
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
                        return true;
                    }
                    return false;
                });
                filterEditType.setText(filterEditTypeText);
                filterEdit.setVisibility(VISIBLE);
            }
        });
        newChip.setOnCloseIconClickListener(v -> clearFilter(metaKey));
        newChip.setCloseIconVisible(true);
        int index = filterChips.getChildCount() <= 0 ? 0 : filterChips.getChildCount() - 1;
        filterChips.addView(newChip, index);
    }

    private boolean chipHasSameFilter(String chipText, String filterType, String filter) {
        return chipText.equals(filterType + " " + filter);
    }

    private void clearFilter(String filterType) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.clearFilter(filterType);
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.musiclibrary_actionmode_menu, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            MutableSelection<EntryID> selection = new MutableSelection<>();
            selectionTracker.copySelection(selection);
            List<EntryID> selectionList = new LinkedList<>();
            selection.forEach(selectionList::add);
            switch (item.getItemId()) {
                case R.id.musiclibrary_actionmode_action_play_now:
                    play(selectionList, getCurrentQueryBundle());
                    break;
                case R.id.musiclibrary_actionmode_action_queue:
                    queue(selectionList, getCurrentQueryBundle());
                    break;
                case R.id.musiclibrary_actionmode_action_add_to_playlist:
                    AddToPlaylistDialogFragment.showDialog(
                            MusicLibraryFragment.this,
                            new ArrayList<>(selectionList),
                            getCurrentQueryBundle()
                    );
                    break;
                default:
                    return false;
            }
            mode.finish();
            return true;
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionTracker.clearSelection();
            actionMode = null;
        }
    };

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

    boolean showHeaderSortedBy() {
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
}
