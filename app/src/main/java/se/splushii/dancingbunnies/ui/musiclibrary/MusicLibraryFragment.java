package se.splushii.dancingbunnies.ui.musiclibrary;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
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
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
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
import se.splushii.dancingbunnies.ui.SortDialogFragment;
import se.splushii.dancingbunnies.ui.selection.LibraryEntryItemDetailsLookup;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE_QUERIES;

public class MusicLibraryFragment extends AudioBrowserFragment implements SortDialogFragment.ConfigHandler {
    private static final String LC = Util.getLogContext(MusicLibraryFragment.class);
    private static final int SORT_BY_GROUP_ID_HEADER = 0;
    private static final int SORT_BY_GROUP_ID_CUSTOM = 1;
    private static final int SORT_BY_GROUP_ID_SINGLE = 2;
    private static final int SORT_BY_GROUP_ORDER_HEADER = Menu.FIRST;
    private static final int SORT_BY_GROUP_ORDER_CUSTUM = Menu.FIRST + 1;
    private static final int SORT_BY_GROUP_ORDER_SINGLE = Menu.FIRST + 2;
    private static final int SHOW_GROUP_ID_HEADER = 0;
    private static final int SHOW_GROUP_ID_SINGLE = 1;
    private static final int SHOW_GROUP_ORDER_HEADER = Menu.FIRST;
    private static final int SHOW_GROUP_ORDER_SINGLE = Menu.FIRST + 1;

    private MusicLibraryAdapter recyclerViewAdapter;
    private LinearLayoutManager recViewLayoutManager;

    private View searchView;
    private TextView searchInfoText;

    private View filterView;
    private ChipGroup filterChips;
    private View filterEdit;
    private TextView filterEditType;
    private MutableLiveData<String> filterEditTypeValueLiveData;
    private AutoCompleteTextView filterEditInput;
    private View filterNew;
    private Spinner filterNewType;
    private AutoCompleteTextView filterNewInput;

    private ArrayList<String> metaKeys;
    private ArrayAdapter<String> metaKeyAdapter;

    private TextView headerShow;
    private Menu headerShowMenu;
    private View headerSortedByRoot;
    private TextView headerSortedBy;
    private Menu headerSortedByMenu;
    private ImageButton headerSortedByOrder;
    private TextView headerArtist;
    private View headerNum;
    private LinearLayout headerExtraSortedBy;
    private LinearLayout headerExtraSortedByKeys;
    private ImageButton headerExtraSortedByOrder;

    private SelectionTracker<LibraryEntry> selectionTracker;
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
            headerExtraSortedBy.setVisibility(GONE);
            headerSortedBy.setText(getSortedByDisplayString(null, true, false));
            headerSortedByRoot.setVisibility(VISIBLE);
        } else {
            headerSortedByRoot.setVisibility(GONE);
            addSortedByColumns(headerExtraSortedByKeys, null, true);
            headerExtraSortedBy.setVisibility(VISIBLE);
        }
        int sortOrderResource = isSortedAscending() ?
                R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp;
        headerSortedByOrder.setImageResource(sortOrderResource);
        headerExtraSortedByOrder.setImageResource(sortOrderResource);
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
            setShowMenuHeader(newUserState.query.getShowField());
            setSortByMenuHeader(getSortedByDisplayString(null, true, false));
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

    String getSortedByDisplayString(List<String> sortValues,
                                    boolean showKeys,
                                    boolean excludeShowKey) {
        String showKey = getCurrentQuery().getShowField();
        List<String> sortKeys = getCurrentQuery().getSortByFields();
        if (sortKeys == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < sortKeys.size(); i++) {
            String key = sortKeys.get(i);
            if (excludeShowKey && showKey.equals(key)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                sb.append("; ");
            }
            if (showKeys) {
                sb.append(Meta.getDisplayKey(key));
            } else {
                String value = sortValues == null || i >= sortValues.size() ?
                        "" : sortValues.get(i);
                sb.append(value == null ? "" : Meta.getDisplayValue(key, value));
            }
        }
        return sb.toString();
    }

    void addSortedByColumns(LinearLayout root, List<String> sortValues, boolean showKeys) {
        root.removeAllViews();
        String showKey = getCurrentQuery().getShowField();
        List<String> sortKeys = getCurrentQuery().getSortByFields();
        TableLayout.LayoutParams layoutParams = new TableLayout.LayoutParams(
                0,
                TableLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        for (int i = 0; i < sortKeys.size(); i++) {
            String key = sortKeys.get(i);
            if (showKey.equals(key)) {
                continue;
            }
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(layoutParams);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setTextColor(ContextCompat.getColorStateList(
                    requireContext(),
                    R.color.primary_text_color
            ));
            tv.setSingleLine();
            tv.setEllipsize(TextUtils.TruncateAt.END);
            if (showKeys) {
                tv.setText(Meta.getDisplayKey(key));
            } else {
                String value = sortValues == null || i >= sortValues.size() ?
                        "" : sortValues.get(i);
                tv.setText(value == null ? "" : Meta.getDisplayValue(key, value));
            }
            int padding = Util.dpToPixels(requireContext(), 4);
            tv.setPadding(padding, padding, padding, padding);
            tv.setBackgroundResource(R.drawable.sorted_by_bg);
            root.addView(tv);
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
        final PopupMenu headerShowPopup = new PopupMenu(requireContext(), headerShow);
        headerShowMenu = headerShowPopup.getMenu();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            headerShowMenu.setGroupDividerEnabled(true);
        }
        MenuPopupHelper headerShowPopupHelper = new MenuPopupHelper(
                requireContext(),
                (MenuBuilder) headerShowMenu,
                headerShow
        );
        headerShowPopupHelper.setForceShowIcon(true);
        headerShowPopup.setOnMenuItemClickListener(item -> onShowSelected(item, displayedFields));
        headerShow.setOnClickListener(view -> headerShowPopupHelper.show());
        headerSortedByRoot = rootView.findViewById(R.id.musiclibrary_header_sortedby_root);
        headerSortedBy = rootView.findViewById(R.id.musiclibrary_header_sortedby);
        final PopupMenu headerSortedByPopup = new PopupMenu(requireContext(), headerSortedBy);
        headerSortedByMenu = headerSortedByPopup.getMenu();
        setShowMenuHeader("");
        headerSortedByMenu.add(SORT_BY_GROUP_ID_CUSTOM, Menu.NONE, SORT_BY_GROUP_ORDER_CUSTUM, "Custom sort")
                .setIcon(R.drawable.ic_edit_black_24dp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            headerSortedByMenu.setGroupDividerEnabled(true);
        }
        MenuPopupHelper headerSortedByPopupHelper = new MenuPopupHelper(
                requireContext(),
                (MenuBuilder) headerSortedByMenu,
                headerSortedBy
        );
        headerSortedByPopupHelper.setForceShowIcon(true);
        headerSortedByPopup.setOnMenuItemClickListener(item -> onSortedBySelected(item, displayedFields));
        headerSortedBy.setOnClickListener(view -> headerSortedByPopupHelper.show());
        headerSortedByOrder = rootView.findViewById(R.id.musiclibrary_header_sortedby_order);
        headerSortedByOrder.setOnClickListener(view -> setSortOrder(!isSortedAscending()));

        headerArtist = rootView.findViewById(R.id.musiclibrary_header_artist);
        headerNum = rootView.findViewById(R.id.musiclibrary_header_num);

        headerExtraSortedBy = rootView.findViewById(R.id.musiclibrary_header_extra_sortedby);
        final PopupMenu headerExtraSortedByPopup = new PopupMenu(requireContext(), headerExtraSortedBy);
        MenuPopupHelper headerExtraSortedByPopupHelper = new MenuPopupHelper(
                requireContext(),
                (MenuBuilder) headerSortedByMenu,
                headerExtraSortedBy
        );
        headerExtraSortedByPopupHelper.setForceShowIcon(true);
        headerExtraSortedByPopup.setOnMenuItemClickListener(item -> onSortedBySelected(item, displayedFields));
        headerExtraSortedByKeys = rootView.findViewById(R.id.musiclibrary_header_extra_sortedby_keys);
        headerExtraSortedByKeys.setOnClickListener(view -> headerExtraSortedByPopupHelper.show());
        headerExtraSortedByOrder = rootView.findViewById(R.id.musiclibrary_header_extra_sortedby_order);
        headerExtraSortedByOrder.setOnClickListener(view -> setSortOrder(!isSortedAscending()));

        ActionModeCallback actionModeCallback = new ActionModeCallback(
                this,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return Collections.emptyList();
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
                    public List<Bundle> getQueries() {
                        return getSelectedBundleQueries();
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
                        ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE,
                        ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST
                },
                new int[]{
                        ACTION_PLAY_MULTIPLE_QUERIES,
                        ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE,
                        ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST,
                        ACTION_CACHE_MULTIPLE_QUERIES,
                        ACTION_CACHE_DELETE_MULTIPLE_QUERIES
                },
                new int[0]
        );

        selectionTracker = new SelectionTracker.Builder<>(
                MainActivity.SELECTION_ID_MUSICLIBRARY,
                recyclerView,
                new MusicLibraryKeyProvider(recyclerViewAdapter),
                new LibraryEntryItemDetailsLookup(recyclerView),
                StorageStrategy.createParcelableStorage(LibraryEntry.class)
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
                    actionMode.setTitle(selectionTracker.getSelection().size() + " sel.");
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
        filterNewType.setSelection(0);
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
        ArrayAdapter<String> filterNewTagValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        filterNewInput.setAdapter(filterNewTagValuesAdapter);
        MutableLiveData<String> filterNewTagValuesLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                filterNewTagValuesLiveData,
                key -> MetaStorage.getInstance(requireContext()).getMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            filterNewTagValuesAdapter.clear();
            filterNewTagValuesAdapter.addAll(values);
            filterNewTagValuesAdapter.notifyDataSetChanged();
        });
        filterNewType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String filterNewKey = metaKeys.get(filterNewType.getSelectedItemPosition());
                if (!filterNewKey.equals(filterNewTagValuesLiveData.getValue())) {
                    filterNewTagValuesLiveData.setValue(filterNewKey);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        MetaStorage.getInstance(requireContext())
                .getMetaFields()
                .observe(getViewLifecycleOwner(), newFields -> {
                    newFields.add(Meta.FIELD_SPECIAL_MEDIA_ID);
                    newFields.add(Meta.FIELD_SPECIAL_MEDIA_SRC);
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
                    metaKeys.clear();
                    metaKeys.addAll(newFields);
                    displayedFields.clear();
                    displayedFields.addAll(newFields.stream()
                            .map(Meta::getDisplayKey)
                            .collect(Collectors.toList())
                    );
                    headerShowMenu.removeGroup(SHOW_GROUP_ID_SINGLE);
                    for (int i = 0; i < displayedFields.size(); i++) {
                        headerShowMenu.add(SHOW_GROUP_ID_SINGLE, i, SHOW_GROUP_ORDER_SINGLE, displayedFields.get(i));
                    }
                    headerSortedByMenu.removeGroup(SORT_BY_GROUP_ID_SINGLE);
                    for (int i = 0; i < displayedFields.size(); i++) {
                        headerSortedByMenu.add(SORT_BY_GROUP_ID_SINGLE, i, SORT_BY_GROUP_ORDER_SINGLE, displayedFields.get(i));
                    }
                    metaKeyAdapter.notifyDataSetChanged();
                });

        ImageButton saveQueryBtn = rootView.findViewById(R.id.musiclibrary_filter_save);
        saveQueryBtn.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, getCurrentQueryBundle())
        );

        filterChips = rootView.findViewById(R.id.musiclibrary_filter_chips);

        filterEdit = rootView.findViewById(R.id.musiclibrary_filter_edit);
        filterEditType = rootView.findViewById(R.id.musiclibrary_filter_edit_type);
        filterEditInput = rootView.findViewById(R.id.musiclibrary_filter_edit_input);
        ArrayAdapter<String> filterEditTagValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        filterEditInput.setAdapter(filterEditTagValuesAdapter);
        filterEditTypeValueLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                filterEditTypeValueLiveData,
                key -> MetaStorage.getInstance(requireContext()).getMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            filterEditTagValuesAdapter.clear();
            filterEditTagValuesAdapter.addAll(values);
            filterEditTagValuesAdapter.notifyDataSetChanged();
        });

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

    private boolean onShowSelected(MenuItem item, List<String> displayedFields) {
        int position = item.getItemId();
        int groupId = item.getGroupId();
        if (groupId != SHOW_GROUP_ID_SINGLE) {
            return false;
        }
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
    }

    private void setShowMenuHeader(String header) {
        headerShowMenu.removeGroup(SHOW_GROUP_ID_HEADER);
        headerShowMenu.add(SHOW_GROUP_ID_HEADER, Menu.NONE, SHOW_GROUP_ORDER_HEADER, "Showing entries of type:")
                .setEnabled(false);
        headerShowMenu.add(SHOW_GROUP_ID_HEADER, Menu.NONE, SHOW_GROUP_ORDER_HEADER, header)
                .setEnabled(false);
    }

    private boolean onSortedBySelected(MenuItem item, List<String> displayedFields) {
        int groupId = item.getGroupId();
        int position = item.getItemId();
        if (groupId == SORT_BY_GROUP_ID_CUSTOM) {
            SortDialogFragment.showDialogForSortConfig(this);
            return true;
        }
        if (groupId != SORT_BY_GROUP_ID_SINGLE) {
            return false;
        }
        String field = metaKeys.get(position);
        String displayedField = displayedFields.get(position);
        Log.d(LC, "Sorting by: " + displayedField);
        Toast.makeText(
                requireContext(),
                "Sorting by: " + displayedField,
                Toast.LENGTH_SHORT
        ).show();
        sortBy(Collections.singletonList(field));
        return true;
    }

    private void setSortByMenuHeader(String header) {
        headerSortedByMenu.removeGroup(SORT_BY_GROUP_ID_HEADER);
        headerSortedByMenu.add(SORT_BY_GROUP_ID_HEADER, Menu.NONE, SORT_BY_GROUP_ORDER_HEADER, "Sort shown entries by:")
                .setEnabled(false);
        headerSortedByMenu.add(SORT_BY_GROUP_ID_HEADER, Menu.NONE, SORT_BY_GROUP_ORDER_HEADER, header)
                .setEnabled(false);
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
        model.sortBy(Collections.singletonList(displayType));
        model.setSortOrder(true);
    }

    private void sortBy(List<String> fields) {
        model.addBackStackHistory(recyclerViewAdapter.getCurrentPosition());
        model.sortBy(fields);
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
            if (metaKey.equals(filterEditTypeValueLiveData.getValue()) && filterEdit.getVisibility() == VISIBLE) {
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
                if (!metaKey.equals(filterEditTypeValueLiveData.getValue())) {
                    filterEditTypeValueLiveData.setValue(metaKey);
                }
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

    private ArrayList<Bundle> getSelectedBundleQueries() {
        MutableSelection<LibraryEntry> selection = new MutableSelection<>();
        selectionTracker.copySelection(selection);
        ArrayList<Bundle> bundleQueries = new ArrayList<>();
        List<String> sortedByKeys = querySortedByKeys();
        selection.forEach(libraryEntry -> {
            MusicLibraryQuery query = getCurrentQuery();
            query.addToQuery(libraryEntry.entryID.type, libraryEntry.entryID.id);
            query.addSortedByValuesToQuery(
                    sortedByKeys,
                    libraryEntry.sortedByValues()
            );
            bundleQueries.add(query.getQueryBundle());
        });
        return bundleQueries;
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

    boolean sortedByMultipleFields() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return false;
        }
        return query.sortedByMultipleFields();
    }

    boolean isSortedAscending() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return false;
        }
        return query.isSortOrderAscending();
    }

    List<String> querySortedByKeys() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return null;
        }
        return query.getSortByFields();
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

    boolean showSortedByHeader() {
        return !isSearchQuery() && !showExtraSortedByHeader();
    }

    private boolean showExtraSortedByHeader() {
        boolean extraNotNeeded = querySortedByKeys().size() == 2
                && querySortedByKeys().contains(getCurrentQuery().getShowField());
        return !isSearchQuery() && sortedByMultipleFields() && !extraNotNeeded;
    }

    boolean isSearchQuery() {
        return getCurrentQuery().isSearchQuery();
    }

    boolean showHeaderArtist(boolean browsable) {
        MusicLibraryQuery query = getCurrentQuery();
        List<String> sortByFields = query.getSortByFields();
        if (sortByFields == null) {
            sortByFields = new ArrayList<>();
        }
        return query.isSearchQuery() || !browsable && !sortByFields.contains(Meta.FIELD_ARTIST);
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

    @Override
    public void onSortConfig(List<String> sortBy) {
        Log.d(LC, "onSortConfig: " + sortBy);
        if (!sortBy.isEmpty()) {
            sortBy(sortBy);
        }
    }
}
