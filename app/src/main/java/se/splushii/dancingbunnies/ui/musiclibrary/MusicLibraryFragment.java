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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryLeaf;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryTree;
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

    private LinearLayout browseQueryView;
    private MutableLiveData<String> browseFilterEditTypeValueLiveData;
    private ArrayAdapter<String> browseFilterEditTagValuesAdapter;
    private ArrayAdapter<String> browseFilterNewTypeAdapter;
    private ArrayList<String> browseFilterNewTypeValues;
    private MutableLiveData<String> browseFilterNewTagValuesLiveData;
    private ArrayAdapter<String> browseFilterNewTagValuesAdapter;

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

    private LinearLayout searchView;
    private EditText searchQueryEdit;
    private FloatingActionButton searchFAB;

    private View searchContentView;
    private MusicLibrarySearchAdapter searchRecyclerViewAdapter;
    private LinearLayoutManager searchRecyclerViewLayoutManager;
    private RecyclerViewActionModeSelectionTracker
            <EntryID, MusicLibrarySearchAdapter, MusicLibrarySearchAdapter.SongViewHolder>
            searchSelectionTracker;

    private ArrayList<String> metaKeys;
    private ArrayList<String> metaKeysForDisplay;

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
        if (isSearchQuery()) {
            browseContentView.setVisibility(GONE);
            browseHeader.setVisibility(GONE);
            browseFastScroller.enableBubble(false);
            searchQueryEdit.setText(newUserState.query.getSearchQuery());
            searchView.setVisibility(VISIBLE);
            searchContentView.setVisibility(VISIBLE);
        } else {
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

            browseQueryView.removeAllViews();
            MusicLibraryQueryTree queryTree = newUserState.query.getQueryTree();
            addFilterGroupToView(browseQueryView, queryTree, 1);

            browseHeader.setVisibility(VISIBLE);
            browseContentView.setVisibility(VISIBLE);
        }
    }

    private void addFilterGroupToView(LinearLayout browseQueryView,
                                      MusicLibraryQueryTree queryTree,
                                      int depth) {
        MusicLibraryFilterGroup filterGroup = new MusicLibraryFilterGroup(requireContext());
        filterGroup.setOperator(queryTree.getOperator());
        filterGroup.setOnOperatorChanged(op -> {
            model.addBackStackHistory(
                    Util.getRecyclerViewPosition(browseRecyclerView),
                    getCurrentQueryTree()
            );
            queryTree.setOperator(op);
            setQuery(getCurrentQuery());
        });
        filterGroup.setNew(
                browseFilterNewTagValuesAdapter,
                browseFilterNewTypeAdapter,
                0,
                () -> {
                    trimQueryTreeSelection(depth - 1);
                    setQueryDepth(depth);
                },
                pos -> {
                    String filterNewKey = metaKeys.get(pos);
                    if (!filterNewKey.equals(browseFilterNewTagValuesLiveData.getValue())) {
                        browseFilterNewTagValuesLiveData.setValue(filterNewKey);
                    }
                },
                (pos, input) -> {
                    if (pos >= metaKeys.size() || pos >= metaKeysForDisplay.size()) {
                        return;
                    }
                    String field = metaKeys.get(pos);
                    String displayedField = metaKeysForDisplay.get(pos);
                    Log.d(LC, "Applying filter: " + displayedField + "(" + input + ")");
                    Toast.makeText(
                            getContext(),
                            "Applying filter: " + displayedField + "(" + input + ")",
                            Toast.LENGTH_SHORT
                    ).show();
                    model.addBackStackHistory(
                            Util.getRecyclerViewPosition(browseRecyclerView),
                            getCurrentQueryTree()
                    );
                    queryTree.addChild(new MusicLibraryQueryLeaf(field, input));
                    setQuery(getCurrentQuery());
                },
                op -> {
                    model.addBackStackHistory(
                            Util.getRecyclerViewPosition(browseRecyclerView),
                            getCurrentQueryTree()
                    );
                    queryTree.addChild(new MusicLibraryQueryTree(op));
                    setQuery(getCurrentQuery());
                }
        );
        setQueryDepth(depth);
        browseQueryView.addView(filterGroup);
        List<Integer> queryTreeSelection = model.getQueryTreeSelection();
        int selectedIndex = depth > queryTreeSelection.size() ? -1 : queryTreeSelection.get(depth - 1);
        int index = 0;
        for (MusicLibraryQueryNode node: queryTree) {
            final int nodeIndex = index;
            if (node instanceof MusicLibraryQueryLeaf) {
                MusicLibraryQueryLeaf leaf = (MusicLibraryQueryLeaf) node;
                String key = leaf.getKey();
                String value = leaf.getValue();
                filterGroup.addLeafFilter(
                        key,
                        value,
                        browseFilterEditTagValuesAdapter,
                        () -> {
                            setQueryTreeSelection(nodeIndex, depth);
                            if (!key.equals(browseFilterEditTypeValueLiveData.getValue())) {
                                browseFilterEditTypeValueLiveData.setValue(key);
                            }
                        },
                        () -> {},
                        filterString -> {
                            model.addBackStackHistory(
                                    Util.getRecyclerViewPosition(browseRecyclerView),
                                    getCurrentQueryTree()
                            );
                            leaf.setValue(filterString);
                            setQuery(getCurrentQuery());
                        },
                        () -> {
                            model.addBackStackHistory(
                                    Util.getRecyclerViewPosition(browseRecyclerView),
                                    getCurrentQueryTree()
                            );
                            queryTree.removeChild(node);
                            setQuery(getCurrentQuery());
                        }
                );
            } else if (node instanceof MusicLibraryQueryTree) {
                MusicLibraryQueryTree tree = (MusicLibraryQueryTree) node;
                filterGroup.addTreeFilter(
                        tree.getOperator(),
                        () -> {
                            setQueryTreeSelection(nodeIndex, depth);
                            setQueryDepth(depth);
                            addFilterGroupToView(browseQueryView, tree, depth + 1);
                        },
                        () -> {
                            setQueryDepth(depth);
                        },
                        () -> {
                            model.addBackStackHistory(
                                    Util.getRecyclerViewPosition(browseRecyclerView),
                                    getCurrentQueryTree()
                            );
                            queryTree.removeChild(node);
                            setQuery(getCurrentQuery());
                        }
                );
                if (selectedIndex >= 0 && selectedIndex == index) {
                    filterGroup.activate(index);
                    addFilterGroupToView(browseQueryView, tree, depth + 1);
                }
            }
            index++;
        }
    }

    private boolean trimQueryTreeSelection(int depth) {
        if (model == null) {
            return false;
        }
        if (model.getQueryTreeSelection().size() <= depth) {
            return false;
        }
        List<Integer> selection = model.getQueryTreeSelection().stream()
                .limit(depth)
                .collect(Collectors.toList());
        model.setQueryTreeSelection(selection);
        return true;
    }

    private void setQueryTreeSelection(int nodeIndex, int trimToDepth) {
        List<Integer> selection = model.getQueryTreeSelection().stream()
                .limit(trimToDepth - 1)
                .collect(Collectors.toList());
        selection.add(nodeIndex);
        setQueryDepth(trimToDepth);
        model.setQueryTreeSelection(selection);
    }

    private boolean setQueryDepth(int depth) {
        if (browseQueryView == null) {
            return false;
        }
        boolean removedViews = false;
        while (browseQueryView.getChildCount() > depth) {
            browseQueryView.removeViewAt(browseQueryView.getChildCount() - 1);
            removedViews = true;
        }
        return removedViews;
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
        searchQueryEdit = rootView.findViewById(R.id.musiclibrary_search_query);

        searchFAB = rootView.findViewById(R.id.musiclibrary_search_fab);
        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null) {
                searchFAB.show();
            } else {
                searchFAB.hide();
            }
        });
        searchFAB.setOnClickListener(v -> {
            if (!getCurrentQuery().isSearchQuery()) {
                model.search("");
            } else {
                searchQueryEdit.setText("");
            }
            searchView.setVisibility(VISIBLE);
            searchQueryEdit.requestFocus();
            Util.showSoftInput(requireActivity(), searchQueryEdit);
        });
        searchQueryEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String query = searchQueryEdit.getText().toString();
                model.search(query);
                clearFocus();
                return true;
            }
            return false;
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
                    public MusicLibraryQueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<MusicLibraryQueryNode> getQueryNodes() {
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
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    clearFocus();
                }
            }
        });

        View searchHomeBtn = rootView.findViewById(R.id.musiclibrary_search_home);
        searchHomeBtn.setOnClickListener(v -> {
            clearSelection();
            clearFocus();
            model.addBackStackHistory(Util.getRecyclerViewPosition(searchRecyclerView));
            model.reset();
        });

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
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    clearFocus();
                }
            }
        });

        metaKeys = new ArrayList<>();
        metaKeysForDisplay = new ArrayList<>();

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
            return onShowSelected(item, metaKeysForDisplay);
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
            return onSortedBySelected(item, metaKeysForDisplay);
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
                    public MusicLibraryQueryNode getQueryNode() {
                        return getCurrentQueryTree();
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<MusicLibraryQueryNode> getQueryNodes() {
                        return getSelectedQueryTrees();
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
        browseHomeBtn.setOnClickListener(v -> {
            clearSelection();
            clearFocus();
            model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
            model.reset();
        });

        browseFilterNewTypeValues = new ArrayList<>();
        browseFilterNewTypeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                browseFilterNewTypeValues
        );
        browseFilterNewTagValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterNewTagValuesLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                browseFilterNewTagValuesLiveData,
                key -> MetaStorage.getInstance(requireContext()).getMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            browseFilterNewTagValuesAdapter.clear();
            browseFilterNewTagValuesAdapter.addAll(values);
            browseFilterNewTagValuesAdapter.notifyDataSetChanged();
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
                    metaKeysForDisplay.clear();
                    metaKeysForDisplay.addAll(newFields.stream()
                            .map(Meta::getDisplayKey)
                            .collect(Collectors.toList())
                    );
                    browseFilterNewTypeValues.clear();
                    browseFilterNewTypeValues.addAll(metaKeysForDisplay);
                    browseHeaderShowMenu.removeGroup(SHOW_GROUP_ID_SINGLE);
                    for (int i = 0; i < metaKeysForDisplay.size(); i++) {
                        browseHeaderShowMenu.add(
                                SHOW_GROUP_ID_SINGLE,
                                i,
                                SHOW_GROUP_ORDER_SINGLE,
                                metaKeysForDisplay.get(i)
                        );
                    }
                    browseHeaderSortedByMenu.removeGroup(SORT_BY_GROUP_ID_SINGLE);
                    for (int i = 0; i < metaKeysForDisplay.size(); i++) {
                        browseHeaderSortedByMenu.add(
                                SORT_BY_GROUP_ID_SINGLE,
                                i,
                                SORT_BY_GROUP_ORDER_SINGLE,
                                metaKeysForDisplay.get(i)
                        );
                    }
                    browseFilterNewTypeAdapter.notifyDataSetChanged();
                });

        ImageButton browseSaveBtn = rootView.findViewById(R.id.musiclibrary_browse_save);
        browseSaveBtn.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, getCurrentQueryTree())
        );

        browseQueryView = rootView.findViewById(R.id.musiclibrary_browse_query);

        browseFilterEditTagValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterEditTypeValueLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                browseFilterEditTypeValueLiveData,
                key -> MetaStorage.getInstance(requireContext()).getMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            browseFilterEditTagValuesAdapter.clear();
            browseFilterEditTagValuesAdapter.addAll(values);
            browseFilterEditTagValuesAdapter.notifyDataSetChanged();
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

    void addBackStackHistory() {
        model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
    }

    void setQuery(MusicLibraryQuery query) {
        model.setQuery(query);
    }

    private ArrayList<MusicLibraryQueryNode> getSelectedQueryTrees() {
        MutableSelection<LibraryEntry> selection = new MutableSelection<>();
        browseSelectionTracker.copySelection(selection);
        ArrayList<MusicLibraryQueryNode> queryTrees = new ArrayList<>();
        List<String> sortedByKeys = querySortedByKeys();
        selection.forEach(libraryEntry -> {
            MusicLibraryQuery query = getCurrentQuery();
            query.andEntryIDToQuery(libraryEntry.entryID);
            query.andSortedByValuesToQuery(sortedByKeys, libraryEntry.sortedByValues());
            queryTrees.add(query.getQueryTree());
        });
        return queryTrees;
    }

    MusicLibraryQuery getCurrentQuery() {
        MusicLibraryUserState state = model.getUserState().getValue();
        return state == null ? null : new MusicLibraryQuery(state.query);
    }
    
    private MusicLibraryQueryTree getCurrentQueryTree() {
        MusicLibraryQuery query = getCurrentQuery();
        if (query == null) {
            return null;
        }
        MusicLibraryQueryTree queryTree = query.getQueryTree();
        return queryTree.deepCopy();
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
        if (searchQueryEdit != null && searchQueryEdit.hasFocus()) {
            searchQueryEdit.clearFocus();
            Util.hideSoftInput(requireActivity(), searchQueryEdit);
        }
        boolean needRefresh = setQueryDepth(1);
        needRefresh = trimQueryTreeSelection(0) || needRefresh;
        if (needRefresh && model != null) {
            refreshView(model.getUserState().getValue());
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
