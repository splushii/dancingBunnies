package se.splushii.dancingbunnies.ui.musiclibrary;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
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
import se.splushii.dancingbunnies.ui.EntryTypeSelectionDialogFragment;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.selection.LibraryEntryItemDetailsLookup;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE_QUERIES;

public class MusicLibraryFragment extends AudioBrowserFragment implements EntryTypeSelectionDialogFragment.ConfigHandler {
    private static final String LC = Util.getLogContext(MusicLibraryFragment.class);
    private static final int SORT_BY_HEADER_ID_VALUE = 1;
    private static final int SORT_BY_GROUP_ID_HEADER = 0;
    private static final int SORT_BY_GROUP_ID_CUSTOM = 1;
    private static final int SORT_BY_GROUP_ID_SINGLE = 2;
    private static final int SORT_BY_GROUP_ORDER_HEADER = Menu.FIRST;
    private static final int SORT_BY_GROUP_ORDER_CUSTOM = Menu.FIRST + 1;
    private static final int SORT_BY_GROUP_ORDER_SINGLE = Menu.FIRST + 2;
    private static final int SHOW_GROUP_ID_HEADER = 0;
    private static final int SHOW_GROUP_ID_SINGLE = 1;
    private static final int SHOW_GROUP_ORDER_HEADER = Menu.FIRST;
    private static final int SHOW_GROUP_ORDER_SINGLE = Menu.FIRST + 1;

    private View browseFilterView;
    private ChipGroup browseFilterChips;
    private View browseFilterEdit;
    private TextView browseFilterEditType;
    private MutableLiveData<String> browseFilterEditTypeValueLiveData;
    private AutoCompleteTextView browseFilterEditInput;
    private View browseFilterNew;
    private Spinner browseFilterNewType;
    private AutoCompleteTextView browseFilterNewInput;

    private LinearLayout browseHeader;
    private TextView browseHeaderShow;
    private Menu browseHeaderShowMenu;
    private LinearLayout browseHeaderSortedBy;
    private Menu browseHeaderSortedByMenu;
    private LinearLayout browseHeaderSortedByKeys;
    private ImageView browseHeaderSortedByOrder;
    private View browseHeaderNum;

    private View browseContentView;
    private RecyclerView browseRecyclerView;
    private MusicLibraryAdapter browseRecyclerViewAdapter;
    private LinearLayoutManager browseRecyclerViewLayoutManager;
    private FastScroller browseFastScroller;
    private FastScrollerBubble browseFastScrollerBubble;
    private SelectionTracker<LibraryEntry> browseSelectionTracker;
    private ActionMode browseActionMode;

    private View searchView;
    private TextView searchInfoText;

    private View searchContentView;
    private MusicLibrarySearchAdapter searchRecyclerViewAdapter;
    private LinearLayoutManager searchRecyclerViewLayoutManager;
    private RecyclerViewActionModeSelectionTracker
            <EntryID, MusicLibrarySearchAdapter, MusicLibrarySearchAdapter.SongViewHolder>
            searchSelectionTracker;

    private ArrayList<String> metaKeys;
    private ArrayAdapter<String> metaKeyAdapter;

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
        browseRecyclerViewAdapter.setModel(model);
        searchRecyclerViewAdapter.setModel(model);
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        model.updateUserState(Util.getRecyclerViewPosition(browseRecyclerView));
        super.onStop();
    }

    @Override
    protected void onMediaBrowserConnected() {
        if (model != null) {
            refreshView(model.getUserState().getValue());
            model.query(mediaBrowser);
        }
    }

    @Override
    protected void onSessionReady() {
        if (model != null) {
            model.setCurrentEntry(getCurrentEntry());
            refreshView(model.getUserState().getValue());
        }
    }

    @Override
    protected void onCurrentEntryChanged(PlaybackEntry entry) {
        model.setCurrentEntry(entry);
    }

    private void refreshView(final MusicLibraryUserState newUserState) {
        if (newUserState == null) {
            return;
        }
        browseRecyclerViewAdapter.scrollWhenReady();
        searchRecyclerViewAdapter.scrollWhenReady();
        String showField = getCurrentQuery().getShowField();
        String showDisplayField = Meta.getDisplayKey(showField);
        clearFilterView();
        if (isSearchQuery()) {
            Log.d(LC, "refreshView search");
            browseContentView.setVisibility(GONE);
            browseHeader.setVisibility(GONE);
            browseFilterView.setVisibility(GONE);
            browseFastScroller.enableBubble(false);
            searchInfoText.setText(newUserState.query.getSearchQuery());
            searchView.setVisibility(VISIBLE);
            searchContentView.setVisibility(VISIBLE);
        } else {
            Log.d(LC, "refreshView query");
            searchContentView.setVisibility(GONE);
            searchView.setVisibility(GONE);
            browseFastScroller.enableBubble(true);
            browseHeaderShow.setText(showDisplayField);
            setShowMenuHeader(newUserState.query.getShowField());
            setSortByMenuHeader(getSortedByDisplayString(null, true, false));
            boolean browsable = isBrowsable(showField);
            browseHeaderNum.setVisibility(browsable ? VISIBLE : GONE);
            addSortedByColumns(browseHeaderSortedBy, browseHeaderSortedByKeys, null, true);
            int sortOrderResource = isSortedAscending() ?
                    R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp;
            browseHeaderSortedByOrder.setImageResource(sortOrderResource);
            Bundle queryBundle = newUserState.query.getQueryBundle();
            for (String metaKey: queryBundle.keySet()) {
                String filterValue = queryBundle.getString(metaKey);
                addFilterToView(metaKey, filterValue);
            }
            browseFilterView.setVisibility(VISIBLE);
            browseHeader.setVisibility(VISIBLE);
            browseContentView.setVisibility(VISIBLE);
        }
        if (browseFilterChips.getChildCount() > 0) {
            browseFilterChips.setVisibility(VISIBLE);
        } else {
            browseFilterChips.setVisibility(GONE);
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

    void addSortedByColumns(LinearLayout root,
                            LinearLayout columnRoot,
                            List<String> sortValues,
                            boolean isHeader) {
        columnRoot.removeAllViews();
        String showKey = getCurrentQuery().getShowField();
        List<String> sortKeys = getCurrentQuery().getSortByFields();
        TableLayout.LayoutParams textViewLayoutParams = new TableLayout.LayoutParams(
                0,
                TableLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        int columnCount = 0;
        for (int i = 0; i < sortKeys.size(); i++) {
            String key = sortKeys.get(i);
            if (showKey.equals(key)
                    || Meta.FIELD_SPECIAL_MEDIA_ID.equals(showKey) && Meta.FIELD_TITLE.equals(key)) {
                continue;
            }
            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(textViewLayoutParams);
            switch (Meta.getType(key)) {
                default:
                case STRING:
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    break;
                case LONG:
                case DOUBLE:
                    tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    break;
            }
            tv.setTextColor(ContextCompat.getColorStateList(
                    requireContext(),
                    R.color.primary_text_color
            ));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isHeader ? 14 : 12);
            tv.setSingleLine();
            tv.setEllipsize(TextUtils.TruncateAt.END);
            if (isHeader) {
                tv.setText(Meta.getDisplayKey(key));
            } else {
                String value = sortValues == null || i >= sortValues.size() ?
                        "" : sortValues.get(i);
                tv.setText(value == null ? "" : Meta.getDisplayValue(key, value));
            }
            int padding = Util.dpToPixels(requireContext(), 4);
            tv.setPadding(padding, 0, padding, 0);
            columnRoot.addView(tv);
            columnCount++;
        }
        LinearLayout.LayoutParams rootLayoutParams = (LinearLayout.LayoutParams) root.getLayoutParams();
        rootLayoutParams.weight = columnCount;
        rootLayoutParams.width = columnCount == 0 && isHeader ?
                Util.dpToPixels(requireContext(), 32) : 0;
        root.setLayoutParams(rootLayoutParams);
    }

    private boolean isBrowsable(String field) {
        return !Meta.FIELD_SPECIAL_MEDIA_ID.equals(field);
    }

    @Override
    public void onDestroyView() {
        Log.d(LC, "onDestroyView");
        browseFastScroller.onDestroy();
        browseFastScroller = null;
        browseFastScrollerBubble = null;
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (browseSelectionTracker != null) {
            browseSelectionTracker.onSaveInstanceState(outState);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.musiclibrary_fragment_layout, container,
                false);

        searchView = rootView.findViewById(R.id.musiclibrary_search);

        searchInfoText = rootView.findViewById(R.id.musiclibrary_search_info_query);
        View searchInfoView = rootView.findViewById(R.id.musiclibrary_search_info);
        searchInfoView.setOnClickListener(v -> {
            clearSelection();
            model.searchQueryClicked(searchInfoText.getText());
        });

        searchContentView = rootView.findViewById(R.id.musiclibrary_search_content);
        RecyclerView searchRecyclerView = rootView.findViewById(R.id.musiclibrary_search_recyclerview);
        searchRecyclerViewLayoutManager = new LinearLayoutManager(requireContext());
        searchRecyclerView.setLayoutManager(searchRecyclerViewLayoutManager);
        searchRecyclerViewAdapter = new MusicLibrarySearchAdapter(this);
        searchRecyclerView.setAdapter(searchRecyclerViewAdapter);
        searchSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_MUSICLIBRARY_SEARCH,
                searchRecyclerView,
                searchRecyclerViewAdapter,
                StorageStrategy.createParcelableStorage(EntryID.class),
                savedInstanceState
        );
        ActionModeCallback searchActionModeCallback = new ActionModeCallback(
                this,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return searchSelectionTracker.getSelection();
                    }

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
                        return null;
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<Bundle> getQueries() {
                        return Collections.emptyList();
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        searchSelectionTracker.clearSelection();
                    }
                }
        );
        searchSelectionTracker.setActionModeCallback(searchActionModeCallback);
        searchActionModeCallback.setActions(
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
        searchRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                clearFocus();
            }
        });

        browseFilterView = rootView.findViewById(R.id.musiclibrary_browse_filter);

        browseContentView = rootView.findViewById(R.id.musiclibrary_browse_content);
        browseRecyclerView = rootView.findViewById(R.id.musiclibrary_browse_recyclerview);
        browseRecyclerViewLayoutManager = new LinearLayoutManager(requireContext());
        browseRecyclerView.setLayoutManager(browseRecyclerViewLayoutManager);
        browseRecyclerView.getRecycledViewPool().setMaxRecycledViews(0, 50);

        browseFastScroller = rootView.findViewById(R.id.musiclibrary_browse_fastscroller);
        browseFastScroller.setRecyclerView(browseRecyclerView);
        browseFastScrollerBubble = rootView.findViewById(R.id.musiclibrary_browse_fastscroller_bubble);
        browseFastScroller.setBubble(browseFastScrollerBubble);

        browseRecyclerViewAdapter = new MusicLibraryAdapter(this, browseRecyclerView, browseFastScrollerBubble);
        browseRecyclerView.setAdapter(browseRecyclerViewAdapter);
        browseRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                clearFocus();
            }
        });

        metaKeys = new ArrayList<>();
        List<String> displayedFields = new ArrayList<>();

        browseHeaderShow = rootView.findViewById(R.id.musiclibrary_browse_header_show);
        final PopupMenu browseHeaderShowPopup = new PopupMenu(requireContext(), browseHeaderShow);
        browseHeaderShowMenu = browseHeaderShowPopup.getMenu();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            browseHeaderShowMenu.setGroupDividerEnabled(true);
        }
        MenuPopupHelper browseHeaderShowPopupHelper = new MenuPopupHelper(
                requireContext(),
                (MenuBuilder) browseHeaderShowMenu,
                browseHeaderShow
        );
        browseHeaderShowPopupHelper.setForceShowIcon(true);
        browseHeaderShowPopup.setOnMenuItemClickListener(item -> {
            clearSelection();
            return onShowSelected(item, displayedFields);
        });
        browseHeaderShow.setOnClickListener(view -> browseHeaderShowPopupHelper.show());
        browseHeaderNum = rootView.findViewById(R.id.musiclibrary_browse_header_num);

        browseHeaderSortedBy = rootView.findViewById(R.id.musiclibrary_browse_header_sortedby);
        browseHeader = rootView.findViewById(R.id.musiclibrary_browse_header);
        setShowMenuHeader("");
        final PopupMenu browseHeaderSortedByPopup = new PopupMenu(requireContext(), browseHeaderSortedBy);
        browseHeaderSortedByMenu = browseHeaderSortedByPopup.getMenu();
        browseHeaderSortedByMenu.add(
                SORT_BY_GROUP_ID_CUSTOM,
                Menu.NONE,
                SORT_BY_GROUP_ORDER_CUSTOM,
                "Custom sort"
        ).setIcon(R.drawable.ic_edit_black_24dp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            browseHeaderSortedByMenu.setGroupDividerEnabled(true);
        }
        MenuPopupHelper browseHeaderSortedByPopupHelper = new MenuPopupHelper(
                requireContext(),
                (MenuBuilder) browseHeaderSortedByMenu,
                browseHeaderSortedBy
        );
        browseHeaderSortedByPopupHelper.setForceShowIcon(true);
        browseHeaderSortedByPopup.setOnMenuItemClickListener(item -> {
            clearSelection();
            return onSortedBySelected(item, displayedFields);
        });
        browseHeaderSortedByKeys = rootView.findViewById(R.id.musiclibrary_browse_header_sortedby_keys);
        browseHeaderSortedByOrder = rootView.findViewById(R.id.musiclibrary_browse_header_sortedby_order);
        browseHeaderSortedBy.setOnClickListener(view -> browseHeaderSortedByPopupHelper.show());

        ActionModeCallback browseActionModeCallback = new ActionModeCallback(
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
                        browseSelectionTracker.clearSelection();
                        MusicLibraryFragment.this.browseActionMode = null;
                    }
                }
        );
        browseActionModeCallback.setActions(
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

        browseSelectionTracker = new SelectionTracker.Builder<>(
                MainActivity.SELECTION_ID_MUSICLIBRARY_BROWSE,
                browseRecyclerView,
                new MusicLibraryKeyProvider(browseRecyclerViewAdapter),
                new LibraryEntryItemDetailsLookup(browseRecyclerView),
                StorageStrategy.createParcelableStorage(LibraryEntry.class)
        ).withSelectionPredicate(
                SelectionPredicates.createSelectAnything()
        ).build();
        browseSelectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onItemStateChanged(@NonNull Object key, boolean selected) {}

            @Override
            public void onSelectionRefresh() {}

            @Override
            public void onSelectionChanged() {
                if (browseSelectionTracker.hasSelection() && browseActionMode == null) {
                    browseActionMode = requireActivity().startActionMode(browseActionModeCallback);
                }
                if (!browseSelectionTracker.hasSelection() && browseActionMode != null) {
                    browseActionMode.finish();
                }
                if (browseActionMode != null && browseSelectionTracker.hasSelection()) {
                    browseActionMode.setTitle(browseSelectionTracker.getSelection().size() + " sel.");
                }
            }

            @Override
            public void onSelectionRestored() {}
        });
        browseRecyclerViewAdapter.setSelectionTracker(browseSelectionTracker);
        if (savedInstanceState != null) {
            browseSelectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        View browseHomeBtn = rootView.findViewById(R.id.musiclibrary_browse_home);
        View searchHomeBtn = rootView.findViewById(R.id.musiclibrary_search_home);
        browseHomeBtn.setOnClickListener(v -> {
            clearSelection();
            model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
            model.reset();
        });
        searchHomeBtn.setOnClickListener(v -> {
            clearSelection();
            model.addBackStackHistory(Util.getRecyclerViewPosition(searchRecyclerView));
            model.reset();
        });

        browseFilterNew = rootView.findViewById(R.id.musiclibrary_browse_filter_new);
        browseFilterNewInput = rootView.findViewById(R.id.musiclibrary_browe_filter_new_text);
        browseFilterNewType = rootView.findViewById(R.id.musiclibrary_browse_filter_new_type);
        metaKeyAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                displayedFields
        );
        browseFilterNewType.setAdapter(metaKeyAdapter);
        browseFilterNewType.setSelection(0);
        browseFilterNewInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int pos = browseFilterNewType.getSelectedItemPosition();
                String field = metaKeys.get(pos);
                String displayedField = displayedFields.get(pos);
                String filterString = browseFilterNewInput.getText().toString();
                Log.d(LC, "Applying filter: " + displayedField + "(" + filterString + ")");
                Toast.makeText(
                        this.requireContext(),
                        "Applying filter: " + displayedField + "(" + filterString + ")",
                        Toast.LENGTH_SHORT
                ).show();
                filter(field, filterString);
                browseFilterNew.setVisibility(GONE);
                Util.hideSoftInput(requireActivity(), browseFilterNewInput);
                return true;
            }
            return false;
        });
        ArrayAdapter<String> browseFilterNewTagValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterNewInput.setAdapter(browseFilterNewTagValuesAdapter);
        MutableLiveData<String> browseFilterNewTagValuesLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                browseFilterNewTagValuesLiveData,
                key -> MetaStorage.getInstance(requireContext()).getMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            browseFilterNewTagValuesAdapter.clear();
            browseFilterNewTagValuesAdapter.addAll(values);
            browseFilterNewTagValuesAdapter.notifyDataSetChanged();
        });
        browseFilterNewType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String filterNewKey = metaKeys.get(browseFilterNewType.getSelectedItemPosition());
                if (!filterNewKey.equals(browseFilterNewTagValuesLiveData.getValue())) {
                    browseFilterNewTagValuesLiveData.setValue(filterNewKey);
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
                    browseHeaderShowMenu.removeGroup(SHOW_GROUP_ID_SINGLE);
                    for (int i = 0; i < displayedFields.size(); i++) {
                        browseHeaderShowMenu.add(
                                SHOW_GROUP_ID_SINGLE,
                                i,
                                SHOW_GROUP_ORDER_SINGLE,
                                displayedFields.get(i)
                        );
                    }
                    browseHeaderSortedByMenu.removeGroup(SORT_BY_GROUP_ID_SINGLE);
                    for (int i = 0; i < displayedFields.size(); i++) {
                        browseHeaderSortedByMenu.add(
                                SORT_BY_GROUP_ID_SINGLE,
                                i,
                                SORT_BY_GROUP_ORDER_SINGLE,
                                displayedFields.get(i)
                        );
                    }
                    metaKeyAdapter.notifyDataSetChanged();
                });

        ImageButton browseSaveBtn = rootView.findViewById(R.id.musiclibrary_browse_save);
        browseSaveBtn.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, getCurrentQueryBundle())
        );

        browseFilterChips = rootView.findViewById(R.id.musiclibrary_browse_filter_chips);

        browseFilterEdit = rootView.findViewById(R.id.musiclibrary_browse_filter_edit);
        browseFilterEditType = rootView.findViewById(R.id.musiclibrary_browse_filter_edit_type);
        browseFilterEditInput = rootView.findViewById(R.id.musiclibrary_browse_filter_edit_input);
        ArrayAdapter<String> browseFilterEditTagValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterEditInput.setAdapter(browseFilterEditTagValuesAdapter);
        browseFilterEditTypeValueLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                browseFilterEditTypeValueLiveData,
                key -> MetaStorage.getInstance(requireContext()).getMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            browseFilterEditTagValuesAdapter.clear();
            browseFilterEditTagValuesAdapter.addAll(values);
            browseFilterEditTagValuesAdapter.notifyDataSetChanged();
        });

        rootView.findViewById(R.id.musiclibrary_browse_filter_chip_new).setOnClickListener(view -> {
            browseFilterEdit.setVisibility(GONE);
            if (browseFilterNew.getVisibility() != VISIBLE) {
                browseFilterNew.setVisibility(VISIBLE);
                browseFilterNewInput.requestFocus();
                Util.showSoftInput(requireActivity(), browseFilterNewInput);
            } else {
                browseFilterNew.setVisibility(GONE);
                Util.hideSoftInput(requireActivity(), browseFilterNewInput);
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
        browseHeaderShowMenu.removeGroup(SHOW_GROUP_ID_HEADER);
        browseHeaderShowMenu.add(
                SHOW_GROUP_ID_HEADER,
                Menu.NONE,
                SHOW_GROUP_ORDER_HEADER,
                "Showing entries of type:"
        ).setEnabled(false);
        browseHeaderShowMenu.add(
                SHOW_GROUP_ID_HEADER,
                Menu.NONE,
                SHOW_GROUP_ORDER_HEADER,
                header
        ).setEnabled(false);
    }

    private boolean onSortedBySelected(MenuItem item, List<String> displayedFields) {
        int groupId = item.getGroupId();
        int itemId = item.getItemId();
        if (groupId == SORT_BY_GROUP_ID_HEADER && itemId == SORT_BY_HEADER_ID_VALUE) {
            item.setIcon(!isSortedAscending() ?
                    R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp
            );
            setSortOrder(!isSortedAscending());
            return true;
        }
        if (groupId == SORT_BY_GROUP_ID_CUSTOM) {
            MusicLibraryQuery query = getCurrentQuery();
            EntryTypeSelectionDialogFragment.showDialogForSortConfig(
                    this,
                    query.getSortByFields()
            );
            return true;
        }
        if (groupId != SORT_BY_GROUP_ID_SINGLE) {
            return false;
        }
        String field = metaKeys.get(itemId);
        String displayedField = displayedFields.get(itemId);
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
        browseHeaderSortedByMenu.removeGroup(SORT_BY_GROUP_ID_HEADER);
        browseHeaderSortedByMenu.add(
                SORT_BY_GROUP_ID_HEADER,
                Menu.NONE,
                SORT_BY_GROUP_ORDER_HEADER,
                "Sorting shown entries by:"
        ).setEnabled(false);
        browseHeaderSortedByMenu.add(
                SORT_BY_GROUP_ID_HEADER,
                SORT_BY_HEADER_ID_VALUE,
                SORT_BY_GROUP_ORDER_HEADER,
                header
        ).setIcon(isSortedAscending() ?
                R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp
        );

    }

    public boolean onBackPressed() {
        boolean actionPerformed = false;
        if (browseFilterEdit.getVisibility() == VISIBLE) {
            browseFilterEdit.setVisibility(GONE);
            actionPerformed = true;
        }
        if (browseFilterNew.getVisibility() == VISIBLE) {
            browseFilterNew.setVisibility(GONE);
            actionPerformed = true;
        }
        if (actionPerformed) {
            return true;
        }
        return model.popBackStack();
    }

    private void displayType(String displayType) {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
        model.displayType(displayType);
        model.sortBy(Collections.singletonList(displayType));
        model.setSortOrder(true);
    }

    private void sortBy(List<String> fields) {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
        model.sortBy(fields);
        model.setSortOrder(true);
    }

    private void setSortOrder(boolean ascending) {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
        model.setSortOrder(ascending);
    }

    private void filter(String filterType, String filter) {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
        model.filter(filterType, filter);
    }

    void addBackStackHistory() {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
    }

    void setQuery(MusicLibraryQuery query) {
        model.setQuery(query);
    }

    private void clearFilterView() {
        browseFilterChips.removeAllViews();
    }

    private void addFilterToView(String metaKey, String filter) {
        String text = String.format(
                "%s: %s",
                Meta.getDisplayKey(metaKey),
                Meta.getDisplayValue(metaKey,filter)
        );
        Chip newChip = new Chip(requireContext());
        newChip.setEllipsize(TextUtils.TruncateAt.END);
        newChip.setChipBackgroundColorResource(R.color.colorAccent);
        newChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        newChip.setText(text);
        newChip.setOnClickListener(v -> {
            browseFilterNew.setVisibility(GONE);
            if (metaKey.equals(browseFilterEditTypeValueLiveData.getValue())
                    && browseFilterEdit.getVisibility() == VISIBLE) {
                browseFilterEdit.setVisibility(GONE);
                Util.hideSoftInput(requireActivity(), browseFilterEditInput);
            } else {
                browseFilterEditInput.setText(filter);
                browseFilterEditInput.setOnEditorActionListener((v1, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        String filterString = browseFilterEditInput.getText().toString();
                        Log.d(LC, "Applying filter: " + metaKey + "(" + filterString + ")");
                        Toast.makeText(
                                this.requireContext(),
                                "Applying filter: " + metaKey + "(" + filterString + ")",
                                Toast.LENGTH_SHORT
                        ).show();
                        filter(metaKey, filterString);
                        browseFilterEdit.setVisibility(GONE);
                        Util.hideSoftInput(requireActivity(), browseFilterEditInput);
                        return true;
                    }
                    return false;
                });
                if (!metaKey.equals(browseFilterEditTypeValueLiveData.getValue())) {
                    browseFilterEditTypeValueLiveData.setValue(metaKey);
                }
                String filterEditTypeText = Meta.getDisplayKey(metaKey) + ':';
                browseFilterEditType.setText(filterEditTypeText);
                browseFilterEdit.setVisibility(VISIBLE);
                browseFilterEditInput.requestFocus();
                Util.showSoftInput(requireActivity(), browseFilterEditInput);
            }
        });
        newChip.setOnCloseIconClickListener(v -> clearFilter(metaKey));
        newChip.setCloseIconVisible(true);
        browseFilterChips.addView(newChip, browseFilterChips.getChildCount());
    }

    private void clearFilter(String filterType) {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
        model.clearFilter(filterType);
    }

    private ArrayList<Bundle> getSelectedBundleQueries() {
        MutableSelection<LibraryEntry> selection = new MutableSelection<>();
        browseSelectionTracker.copySelection(selection);
        ArrayList<Bundle> bundleQueries = new ArrayList<>();
        List<String> sortedByKeys = querySortedByKeys();
        selection.forEach(libraryEntry -> {
            MusicLibraryQuery query = getCurrentQuery();
            query.addEntryIDToQuery(libraryEntry.entryID);
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

    private Bundle getCurrentQueryBundle() {
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

    private boolean isSortedAscending() {
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
        if (browseSelectionTracker != null) {
            browseSelectionTracker.clearSelection();
        }
        if (searchSelectionTracker != null) {
            searchSelectionTracker.clearSelection();
        }
        if (browseRecyclerViewAdapter != null) {
            browseRecyclerViewAdapter.hideTrackItemActions();
        }
        if (searchRecyclerViewAdapter != null) {
            searchRecyclerViewAdapter.hideTrackItemActions();
        }
    }

    void scrollBrowseTo(int pos, int pad) {
        browseRecyclerViewLayoutManager.scrollToPositionWithOffset(pos, pad);
    }

    void scrollSearchTo(int pos, int pad) {
        searchRecyclerViewLayoutManager.scrollToPositionWithOffset(pos, pad);
    }

    boolean showAllEntriesRow() {
        MusicLibraryQuery query = getCurrentQuery();
        return !query.isSearchQuery() && !Meta.FIELD_SPECIAL_MEDIA_ID.equals(query.getShowField());
    }

    boolean isSearchQuery() {
        return getCurrentQuery().isSearchQuery();
    }

    public void clearFocus() {
        if (browseRecyclerViewAdapter != null) {
            browseRecyclerViewAdapter.hideTrackItemActions();
        }
        if (searchRecyclerViewAdapter!= null) {
            searchRecyclerViewAdapter.hideTrackItemActions();
        }
        if (browseFilterEdit != null && browseFilterEdit.getVisibility() == VISIBLE) {
            Util.hideSoftInput(requireActivity(), browseFilterEditInput);
            browseFilterEdit.setVisibility(GONE);
        }
        if (browseFilterNew != null && browseFilterNew.getVisibility() == VISIBLE) {
            Util.hideSoftInput(requireActivity(), browseFilterNewInput);
            browseFilterNew.setVisibility(GONE);
        }
    }

    @Override
    public void onEntryTypeSelection(List<String> keys) {
        Log.d(LC, "onEntryTypeSelection: " + keys);
        if (!keys.isEmpty()) {
            sortBy(keys);
        }
    }
}
