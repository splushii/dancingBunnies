package se.splushii.dancingbunnies.ui.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.ui.EntryIDDetailsLookup;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragment extends AudioBrowserFragment {
    private static String LC = Util.getLogContext(MusicLibraryFragment.class);

    private static final String INITIAL_DISPLAY_TYPE = Meta.METADATA_KEY_ARTIST;

    private RecyclerView recyclerView;
    private MusicLibraryAdapter recyclerViewAdapter;
    private String currentSubscriptionID;
    private MusicLibraryUserState userState;
    private LinkedList<MusicLibraryUserState> viewBackStack;
    private SelectionTracker<EntryID> selectionTracker;
    private ActionMode actionMode;

    private FastScroller fastScroller;
    private FastScrollerBubble fastScrollerBubble;

    private ChipGroup filterChips;

    private View entryTypeSelect;
    private Spinner entryTypeSelectSpinner;
    private int entryTypeSelectionPos;

    private View filterEdit;
    private TextView filterEditType;
    private EditText filterEditInput;

    private View filterNew;
    private Spinner filterNewType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBackStack = new LinkedList<>();
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshView(userState);
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(LC, "onStop");
        if (userState != null) {
            userState = new MusicLibraryUserState(
                    userState.query,
                    recyclerViewAdapter.getCurrentPosition());
        }
    }

    @Override
    protected void onMediaBrowserConnected() {
        refreshView(userState);
    }

    public void refreshView(final MusicLibraryUserState newUserState) {
        if (newUserState == null) {
            MusicLibraryQuery query = new MusicLibraryQuery();
            setDisplayType(query, INITIAL_DISPLAY_TYPE);
            refreshView(new MusicLibraryUserState(query, 0, 0));
            return;
        }
        entryTypeSelect.setVisibility(View.GONE);
        filterEdit.setVisibility(View.GONE);
        filterNew.setVisibility(View.GONE);
        clearFilterView();
        if (!newUserState.query.isSearchQuery()) {
            Chip chip = new Chip(requireContext());
            chip.setChipIconResource(R.drawable.ic_add_black_24dp);
            chip.setTextStartPadding(0.0f);
            chip.setTextEndPadding(0.0f);
            chip.setChipEndPadding(chip.getChipStartPadding());
            chip.setOnClickListener(v -> {
                entryTypeSelect.setVisibility(View.GONE);
                filterEdit.setVisibility(View.GONE);
                filterNew.setVisibility(filterNew.getVisibility() == View.VISIBLE ?
                        View.GONE : View.VISIBLE
                );
            });
            filterChips.addView(chip);
            Bundle b = newUserState.query.toBundle();
            for (String metaKey: b.keySet()) {
                String filterValue = b.getString(metaKey);
                addFilterToView(metaKey, filterValue);
            }
        }
        if (filterChips.getChildCount() > 0) {
            filterChips.setVisibility(View.VISIBLE);
        } else {
            filterChips.setVisibility(View.GONE);
        }
        unsubscribe();
        currentSubscriptionID = newUserState.query.query(mediaBrowser, new MusicLibraryQuery.MusicLibraryQueryCallback() {
            @Override
            public void onQueryResult(@NonNull List<MediaBrowserCompat.MediaItem> items) {
                recyclerViewAdapter.setDataset(items);
                userState = newUserState;
                LinearLayoutManager llm = (LinearLayoutManager) recyclerView.getLayoutManager();
                llm.scrollToPositionWithOffset(newUserState.pos, newUserState.pad);
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
        menuInflater.inflate(R.menu.musiclibrary_item_contextmenu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();
        if (menuItemId != R.id.musiclibrary_item_contextmenu_play
                && menuItemId != R.id.musiclibrary_item_contextmenu_queue) {
            return false;
        }
        int position = recyclerViewAdapter.getContextMenuHolder().getAdapterPosition();
        EntryID entryID = EntryID.from(recyclerViewAdapter.getItemData(position));
        Log.d(LC, "info pos: " + position);
        switch (menuItemId) {
            case R.id.musiclibrary_item_contextmenu_play:
                play(entryID);
                Log.d(LC, "song context play");
            case R.id.musiclibrary_item_contextmenu_queue:
                queue(entryID);
                Log.d(LC, "song context queue");
        }
        return true;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(recyclerView);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        selectionTracker.onSaveInstanceState(outState);
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

        selectionTracker = new SelectionTracker.Builder<>(
                MainActivity.SELECTION_ID_MUSICLIBRARY,
                recyclerView,
                new MusicLibraryKeyProvider(recyclerViewAdapter),
                new EntryIDDetailsLookup(recyclerView),
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

        fastScroller = rootView.findViewById(R.id.musiclibrary_fastscroller);
        fastScroller.setRecyclerView(recyclerView);
        fastScrollerBubble = rootView.findViewById(R.id.musiclibrary_fastscroller_bubble);
        fastScroller.setBubble(fastScrollerBubble);

        entryTypeSelect = rootView.findViewById(R.id.musiclibrary_entry_type);
        entryTypeSelectSpinner = rootView.findViewById(R.id.musiclibrary_entry_type_spinner);
        List<String> filterTypes = new ArrayList<>();
        List<String> metaKeys = new ArrayList<>();
        List<Map.Entry<String, String>> metaValuesMap = new ArrayList<>(Meta.humanMap.entrySet());
        metaValuesMap.sort(Comparator.comparing(Map.Entry::getValue));
        int initialSelectionPos = 0;
        int index = 0;
        for (Map.Entry<String, String> entry: metaValuesMap) {
            String key = entry.getKey();
            if (!Meta.DBKeysSet.contains(key)) {
                continue;
            }
            filterTypes.add(entry.getValue());
            metaKeys.add(key);
            if (key.equals(INITIAL_DISPLAY_TYPE)) {
                initialSelectionPos = index;
            }
            index++;
        }
        ArrayAdapter<String> filterInputTypeAdapter = new ArrayAdapter<>(
                this.requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                filterTypes
        );
        entryTypeSelectSpinner.setAdapter(filterInputTypeAdapter);
        entryTypeSelectSpinner.setSelection(initialSelectionPos);
        entryTypeSelectionPos = initialSelectionPos;
        entryTypeSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (entryTypeSelectionPos == position) {
                    return;
                }
                String filterType = filterInputTypeAdapter.getItem(position);
                String metaKey = metaKeys.get(position);
                Log.d(LC, "Showing entries of type: " + filterType);
                Toast.makeText(
                        requireContext(),
                        "Showing entries of type: " + filterType,
                        Toast.LENGTH_SHORT
                ).show();
                addBackButtonHistory(
                        new MusicLibraryUserState(
                                userState.query,
                                recyclerViewAdapter.getCurrentPosition()
                        )
                );
                MusicLibraryQuery query = new MusicLibraryQuery(userState.query);
                query.addToQuery(Meta.METADATA_KEY_TYPE, metaKey);
                refreshView(new MusicLibraryUserState(query, 0, 0));
                entryTypeSelect.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        filterChips = rootView.findViewById(R.id.musiclibrary_filter_chips);

        filterEdit = rootView.findViewById(R.id.musiclibrary_filter_edit);
        filterEditType = rootView.findViewById(R.id.musiclibrary_filter_edit_type);
        filterEditInput = rootView.findViewById(R.id.musiclibrary_filter_edit_input);

        filterNew = rootView.findViewById(R.id.musiclibrary_filter_new);
        filterNewType = rootView.findViewById(R.id.musiclibrary_filter_new_type);
        filterNewType.setAdapter(filterInputTypeAdapter);
        EditText filterNewInput = rootView.findViewById(R.id.musiclibrary_filter_new_text);
        filterNewInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int pos = filterNewType.getSelectedItemPosition();
                String metaKey = metaKeys.get(pos);
                String filterType = filterTypes.get(pos);
                String filterString = filterNewInput.getText().toString();
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

        return rootView;
    }

    private void addBackButtonHistory(MusicLibraryUserState userState) {
        viewBackStack.push(userState);
    }

    public boolean onBackPressed() {
        if (viewBackStack.size() > 0) {
            refreshView(viewBackStack.pop());
            return true;
        }
        refreshView(null);
        return false;
    }

    private void filter(String filterType, String filter) {
        addBackButtonHistory(
                new MusicLibraryUserState(
                        userState.query,
                        recyclerViewAdapter.getCurrentPosition()
                )
        );
        MusicLibraryQuery query = new MusicLibraryQuery(userState.query);
        query.addToQuery(filterType, filter);
        refreshView(new MusicLibraryUserState(query, 0, 0));
    }

    void browse(EntryID entryID) {
        addBackButtonHistory(
                new MusicLibraryUserState(
                        userState.query,
                        recyclerViewAdapter.getCurrentPosition()
                )
        );
        MusicLibraryQuery query = new MusicLibraryQuery(userState.query);
        String displayType = entryID.type.equals(Meta.METADATA_KEY_ARTIST) ?
                Meta.METADATA_KEY_ALBUM : Meta.METADATA_KEY_MEDIA_ID;
        setDisplayType(query, displayType);
        if (entryID.id != null) {
            query.addToQuery(entryID.type, entryID.id);
        }
        refreshView(new MusicLibraryUserState(query, 0, 0));
    }

    private void setDisplayType(MusicLibraryQuery query, String metaKey) {
        query.addToQuery(Meta.METADATA_KEY_TYPE, metaKey);
        setEntryTypeSelectSpinnerSelectionFromMetaKey(metaKey);
    }

    private void setEntryTypeSelectSpinnerSelectionFromMetaKey(String metaKey) {
        String filterType = Meta.humanMap.get(metaKey);
        setEntryTypeSelectSpinnerSelection(filterType);
    }

    private void setEntryTypeSelectSpinnerSelection(String filterType) {
        for (int i = 0; i < entryTypeSelectSpinner.getCount(); i++) {
            if (filterType.equals(entryTypeSelectSpinner.getItemAtPosition(i))) {
                entryTypeSelectSpinner.setSelection(i);
                entryTypeSelectionPos = i;
                break;
            }
        }
    }

    private void clearFilterView() {
        filterChips.removeAllViews();
    }

    private void addFilterToView(String metaKey, String filter) {
        String filterType = Meta.humanMap.get(metaKey);
        filter = Meta.METADATA_KEY_TYPE.equals(metaKey) ?
                Meta.humanMap.get(filter) : filter;
        String text = String.format("%s: %s", filterType, filter);
        Chip newChip = new Chip(requireContext());
        newChip.setChipBackgroundColorResource(R.color.colorAccent);
        newChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        newChip.setText(text);
        if (metaKey.equals(Meta.METADATA_KEY_TYPE)) {
            newChip.setOnClickListener(v -> {
                filterEdit.setVisibility(View.GONE);
                filterNew.setVisibility(View.GONE);
                entryTypeSelect.setVisibility(entryTypeSelect.getVisibility() == View.VISIBLE ?
                        View.GONE : View.VISIBLE
                );
            });
            filterChips.addView(newChip, 0);
            setEntryTypeSelectSpinnerSelection(filter);
        } else {
            String finalFilter = filter;
            newChip.setOnClickListener(v -> {
                entryTypeSelect.setVisibility(View.GONE);
                filterNew.setVisibility(View.GONE);
                String filterEditTypeText = filterType + ':';
                if (chipHasSameFilter(newChip, filterEditType.getText().toString(),
                        filterEditInput.getText().toString())) {
                    filterEdit.setVisibility(filterEdit.getVisibility() == View.VISIBLE ?
                            View.GONE : View.VISIBLE
                    );
                } else {
                    filterEditInput.setText(finalFilter);
                    filterEditInput.setOnEditorActionListener((v1, actionId, event) -> {
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            String filterString = filterEditInput.getText().toString();
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
                    filterEditType.setText(filterEditTypeText);
                    filterEdit.setVisibility(View.VISIBLE);
                }
            });
            newChip.setOnCloseIconClickListener(v -> clearFilter(metaKey));
            newChip.setCloseIconVisible(true);
            int index = filterChips.getChildCount() <= 0 ? 0 : filterChips.getChildCount() - 1;
            filterChips.addView(newChip, index);
        }
    }

    private boolean chipHasSameFilter(Chip chip, String filterType, String filter) {
        return chip.getText().toString().equals(filterType + " " + filter);
    }

    private void clearFilter(String filterType) {
        addBackButtonHistory(
                new MusicLibraryUserState(
                        userState.query,
                        recyclerViewAdapter.getCurrentPosition()
                )
        );
        MusicLibraryQuery query = new MusicLibraryQuery(userState.query);
        query.removeFromQuery(filterType);
        refreshView(new MusicLibraryUserState(query, 0, 0));
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
                    queue(
                            selectionList,
                            0
                    ).thenAccept(success -> {
                        if (success) {
                            next();
                            play();
                        }
                    });
                    break;
                case R.id.musiclibrary_actionmode_action_queue:
                    queue(selectionList, AudioPlayerService.QUEUE_LAST);
                    break;
                case R.id.musiclibrary_actionmode_action_add_to_playlist:
                    addToPlaylist(selectionList);
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

    public void clearSelection() {
        if (selectionTracker != null) {
            selectionTracker.clearSelection();
        }
    }
}
