package se.splushii.dancingbunnies.ui.musiclibrary;

import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserCallback;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Query;
import se.splushii.dancingbunnies.musiclibrary.QueryEntry;
import se.splushii.dancingbunnies.musiclibrary.QueryLeaf;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.musiclibrary.QueryTree;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.AddToNewPlaylistDialogFragment;
import se.splushii.dancingbunnies.ui.BrowseSortView;
import se.splushii.dancingbunnies.ui.EntryTypeSelectionDialogFragment;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE_QUERIES;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD_MULTIPLE_QUERIES;

public class MusicLibraryFragment
        extends Fragment
        implements AudioBrowserCallback, EntryTypeSelectionDialogFragment.ConfigHandler {
    private static final String LC = Util.getLogContext(MusicLibraryFragment.class);

    private static final int MENU_GROUP_ID_SHOW_HEADER = 0;
    private static final int MENU_GROUP_ID_SHOW_SINGLE = 1;
    private static final int MENU_GROUP_ORDER_SHOW_HEADER = Menu.FIRST;
    private static final int MENU_GROUP_ORDER_SHOW_SINGLE = Menu.FIRST + 1;

    private AudioBrowser remote;

    private View browseView;
    private View browseQueryBtn;
    private LinearLayout queryRootView;
    private ArrayAdapter<String> browseFilterTypeAdapter;
    private ArrayAdapter<String> browseFilterOperatorAdapter;
    private ArrayAdapter<String> browseFilterAutoCompleteValuesAdapter;
    private MutableLiveData<String> browseFilterSelectedKeyLiveData;

    private LinearLayout browseHeader;
    private TextView browseHeaderShow;
    private Menu browseHeaderShowMenu;
    private BrowseSortView browseSortView;
    private View browseHeaderNum;

    private View browseContentView;
    private RecyclerView browseRecyclerView;
    private MusicLibraryAdapter browseRecyclerViewAdapter;
    private LinearLayoutManager browseRecyclerViewLayoutManager;
    private RecyclerViewActionModeSelectionTracker
            <QueryEntry, MusicLibraryAdapter.SongViewHolder, MusicLibraryAdapter>
            browseSelectionTracker;
    private FastScroller browseFastScroller;
    private FastScrollerBubble browseFastScrollerBubble;

    private LinearLayout searchView;
    private EditText searchQueryEdit;
    private FloatingActionButton searchFAB;

    private View searchContentView;
    private MusicLibrarySearchAdapter searchRecyclerViewAdapter;
    private LinearLayoutManager searchRecyclerViewLayoutManager;
    private RecyclerViewActionModeSelectionTracker
            <EntryID, MusicLibrarySearchAdapter.SongViewHolder, MusicLibrarySearchAdapter>
            searchSelectionTracker;

    private ArrayList<String> metaKeys;
    private ArrayList<String> metaKeysForDisplay;

    private MusicLibraryFragmentModel model;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        remote = AudioBrowser.getInstance(requireActivity());

        ActionModeCallback searchActionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
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
                    public List<EntryID> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public QueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public EntryID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<QueryNode> getQueryNodes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Transaction> getTransactions() {
                        return null;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        searchSelectionTracker.clearSelection();
                    }
                }
        );
        searchActionModeCallback.setActions(
                new int[]{
                        ACTION_QUEUE_ADD_MULTIPLE,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE
                },
                new int[]{
                        ACTION_PLAY_MULTIPLE,
                        ACTION_QUEUE_ADD_MULTIPLE,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE,
                        ACTION_CACHE_MULTIPLE,
                        ACTION_CACHE_DELETE_MULTIPLE
                },
                new int[0]
        );
        searchSelectionTracker.setActionModeCallback(
                ActionMode.TYPE_PRIMARY,
                searchActionModeCallback
        );
        ActionModeCallback browseActionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
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
                    public List<EntryID> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public QueryNode getQueryNode() {
                        return getCurrentQueryTree();
                    }

                    @Override
                    public EntryID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<QueryNode> getQueryNodes() {
                        return getSelectedQueryTrees();
                    }

                    @Override
                    public List<Transaction> getTransactions() {
                        return null;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        browseSelectionTracker.clearSelection();
                    }
                }
        );
        browseActionModeCallback.setActions(
                new int[]{
                        ACTION_QUEUE_ADD_MULTIPLE_QUERIES,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE_QUERIES
                },
                new int[]{
                        ACTION_PLAY_MULTIPLE_QUERIES,
                        ACTION_QUEUE_ADD_MULTIPLE_QUERIES,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE_QUERIES,
                        ACTION_CACHE_MULTIPLE_QUERIES,
                        ACTION_CACHE_DELETE_MULTIPLE_QUERIES
                },
                new int[0]
        );
        browseSelectionTracker.setActionModeCallback(
                ActionMode.TYPE_PRIMARY,
                browseActionModeCallback
        );

        model = new ViewModelProvider(requireActivity()).get(MusicLibraryFragmentModel.class);
        model.getUserState().observe(getViewLifecycleOwner(), state -> {
            refreshView(state);
            model.query(remote);
        });
        browseRecyclerViewAdapter.setModel(model);
        searchRecyclerViewAdapter.setModel(model);
    }

    AudioBrowser getRemote() {
        return remote;
    }

    @Override
    public void onStart() {
        super.onStart();
        remote.registerCallback(this);
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        model.updateUserState(Util.getRecyclerViewPosition(browseRecyclerView));
        remote.unregisterCallback(this);
        // Otherwise afterTextChanged will get triggered on start which will trigger a search
        searchQueryEdit.setText("");
        super.onStop();
    }

    @Override
    public void onMediaBrowserConnected() {
        if (model != null) {
            refreshView(model.getUserState().getValue());
            model.query(remote);
        }
    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {}

    @Override
    public void onMetadataChanged(EntryID entryID) {}

    @Override
    public void onSessionReady() {
        if (model != null) {
            model.setCurrentEntry(remote.getCurrentEntry());
            refreshView(model.getUserState().getValue());
        }
    }

    @Override
    public void onQueueChanged(List<PlaybackEntry> queue) {}

    @Override
    public void onPlaylistSelectionChanged(EntryID playlistID, long pos) {}

    @Override
    public void onPlaylistPlaybackOrderChanged(boolean ordered) {}

    @Override
    public void onPlaylistPlaybackRandomChanged(boolean random) {}

    @Override
    public void onPlaylistPlaybackRepeatModeChanged(boolean repeat) {}

    @Override
    public void onCurrentEntryChanged(PlaybackEntry entry) {
        if (model != null) {
            model.setCurrentEntry(entry);
        }
    }

    @Override
    public void onSessionDestroyed() {}

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
            browseView.setVisibility(GONE);
            if (!searchQueryEdit.getText().toString().equals(newUserState.query.getSearchQuery())) {
                searchQueryEdit.setText(newUserState.query.getSearchQuery());
            }
            searchView.setVisibility(VISIBLE);
            searchContentView.setVisibility(VISIBLE);
        } else {
            searchContentView.setVisibility(GONE);
            searchView.setVisibility(GONE);
            browseFastScroller.enableBubble(true);
            browseHeaderShow.setText(showDisplayField);
            setShowMenuHeader(newUserState.query.getShowField());
            boolean browsable = isBrowsable(showField);
            browseHeaderNum.setVisibility(browsable ? VISIBLE : GONE);
            browseSortView.setSortedByColumns(newUserState.query.getSortByFields());
            browseSortView.onSortedBy(newUserState.query.getSortByFields());
            browseSortView.onSortedByAscending(newUserState.query.isSortOrderAscending());

            queryRootView.removeAllViews();
            QueryTree queryTree = newUserState.query.getQueryTree();
            addFilterGroupToView(queryRootView, queryTree, 1);

            browseView.setVisibility(VISIBLE);
            browseHeader.setVisibility(VISIBLE);
            browseContentView.setVisibility(VISIBLE);
        }
    }

    private void addFilterGroupToView(LinearLayout browseQueryView,
                                      QueryTree queryTree,
                                      int depth) {
        MusicLibraryFilterGroup filterGroup = new MusicLibraryFilterGroup(requireContext());
        filterGroup.setOperator(queryTree.getOperator(), queryTree.isNegated());
        filterGroup.setOnOperatorChanged((op, negated) -> {
            model.addBackStackHistory(
                    Util.getRecyclerViewPosition(browseRecyclerView),
                    getCurrentQueryTree()
            );
            queryTree.setOperator(op);
            queryTree.negate(negated);
            setQuery(getCurrentQuery());
        });
        filterGroup.setAdapters(
                browseFilterAutoCompleteValuesAdapter,
                browseFilterTypeAdapter,
                browseFilterOperatorAdapter
        );
        filterGroup.setNewListener(
                new MusicLibraryFilterGroup.NewListener() {
                    @Override
                    public void onActivated() {
                        trimQueryTreeSelection(depth - 1);
                        setQueryDepth(depth);
                    }

                    @Override
                    public String onTypeSelected(int typePos) {
                        String typeValue = metaKeys.get(typePos);
                        if (!typeValue.equals(browseFilterSelectedKeyLiveData.getValue())) {
                            browseFilterSelectedKeyLiveData.setValue(typeValue);
                        }
                        return typeValue;
                    }

                    @Override
                    public QueryLeaf.Op onOpSelected(int typePos, int pos) {
                        String typeValue = metaKeys.get(typePos);
                        return QueryLeaf.getOps(typeValue).get(pos);
                    }

                    @Override
                    public void onFilterSubmit(int typePos, int opPos, String input, boolean negate) {
                        if (typePos >= metaKeys.size() || typePos >= metaKeysForDisplay.size()) {
                            return;
                        }
                        String field = metaKeys.get(typePos);
                        String displayedField = metaKeysForDisplay.get(typePos);
                        QueryLeaf.Op op = QueryLeaf.getOps(field).get(opPos);
                        String displayedOp = QueryLeaf.getDisplayableOps(field).get(opPos);
                        Log.d(LC, "Applying filter: " + displayedField + " " + displayedOp + " " + input);
                        Toast.makeText(
                                getContext(),
                                "Applying filter: " + displayedField + " " + displayedOp + " " + input,
                                Toast.LENGTH_SHORT
                        ).show();
                        model.addBackStackHistory(
                                Util.getRecyclerViewPosition(browseRecyclerView),
                                getCurrentQueryTree()
                        );
                        queryTree.addChild(new QueryLeaf(field, op, input, negate));
                        setQuery(getCurrentQuery());
                    }

                    @Override
                    public void onSubQuerySubmit(QueryTree.Op op, boolean negate) {
                        model.addBackStackHistory(
                                Util.getRecyclerViewPosition(browseRecyclerView),
                                getCurrentQueryTree()
                        );
                        queryTree.addChild(new QueryTree(op, negate));
                        setQuery(getCurrentQuery());
                    }
                }
        );
        setQueryDepth(depth);
        browseQueryView.addView(filterGroup);
        List<Integer> queryTreeSelection = model.getQueryTreeSelection();
        int selectedIndex = depth > queryTreeSelection.size() ? -1 : queryTreeSelection.get(depth - 1);
        int index = 0;
        for (QueryNode node: queryTree) {
            final int nodeIndex = index;
            if (node instanceof QueryLeaf) {
                QueryLeaf leaf = (QueryLeaf) node;
                boolean negated = leaf.isNegated();
                String key = leaf.getKey();
                String value = leaf.getValue();
                QueryLeaf.Op op = leaf.getOperator();
                filterGroup.addLeafFilter(
                        key,
                        op,
                        value,
                        negated,
                        browseFilterAutoCompleteValuesAdapter,
                        new MusicLibraryFilterGroup.LeafFilterListener() {
                            @Override
                            public void onActivated() {
                                setQueryTreeSelection(nodeIndex, depth);
                                if (!key.equals(browseFilterSelectedKeyLiveData.getValue())) {
                                    browseFilterSelectedKeyLiveData.setValue(key);
                                }
                            }

                            @Override
                            public void onDeactivated() {}

                            @Override
                            public void onSubmit(int opPos, String input, boolean negated) {
                                QueryLeaf.Op op = QueryLeaf.getOps(key).get(opPos);
                                String displayedField = Meta.getDisplayKey(key);
                                String displayedOp = QueryLeaf.getDisplayableOps(key).get(opPos);
                                String msg = "Applying filter: "
                                        + (negated ? "! " : "")
                                        + displayedField + " " + displayedOp + " " + input;
                                Log.d(LC, msg);
                                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                                model.addBackStackHistory(
                                        Util.getRecyclerViewPosition(browseRecyclerView),
                                        getCurrentQueryTree()
                                );
                                leaf.setOperator(op);
                                leaf.setValue(input);
                                leaf.negate(negated);
                                setQuery(getCurrentQuery());
                            }

                            @Override
                            public void onClose() {
                                model.addBackStackHistory(
                                        Util.getRecyclerViewPosition(browseRecyclerView),
                                        getCurrentQueryTree()
                                );
                                queryTree.removeChild(node);
                                setQuery(getCurrentQuery());
                            }
                        }
                );
            } else if (node instanceof QueryTree) {
                QueryTree tree = (QueryTree) node;
                filterGroup.addTreeFilter(
                        tree.getOperator(),
                        tree.isNegated(),
                        new MusicLibraryFilterGroup.TreeFilterListener() {
                            @Override
                            public void onActivated() {
                                setQueryTreeSelection(nodeIndex, depth);
                                setQueryDepth(depth);
                                addFilterGroupToView(browseQueryView, tree, depth + 1);
                            }

                            @Override
                            public void onDeactivated() {
                                setQueryDepth(depth);
                            }

                            @Override
                            public void onClosed() {
                                model.addBackStackHistory(
                                        Util.getRecyclerViewPosition(browseRecyclerView),
                                        getCurrentQueryTree()
                                );
                                queryTree.removeChild(node);
                                setQuery(getCurrentQuery());
                            }
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
        if (queryRootView == null) {
            return false;
        }
        boolean removedViews = false;
        while (queryRootView.getChildCount() > depth) {
            int index = queryRootView.getChildCount() - 1;
            MusicLibraryFilterGroup filterGroup =
                    (MusicLibraryFilterGroup) queryRootView.getChildAt(index);
            filterGroup.deactivate();
            queryRootView.removeView(filterGroup);
            removedViews = true;
        }
        return removedViews;
    }

    private boolean isBrowsable(String field) {
        return !Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(field);
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
        if (searchSelectionTracker != null) {
            searchSelectionTracker.onSaveInstanceState(outState);
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
                model.search("", true);
            } else {
                searchQueryEdit.setText("");
            }
            searchView.setVisibility(VISIBLE);
            searchQueryEdit.requestFocus();
            Util.showSoftInput(requireActivity(), searchQueryEdit);
        });
        searchQueryEdit.addTextChangedListener(new TextWatcher() {
            private String lastQuery = "";
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                String query = editable.toString();
                if (!query.isEmpty() && !query.equals(lastQuery)) {
                    model.search(editable.toString(), false);
                    lastQuery = query;
                }
            }
        });
        searchQueryEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String query = searchQueryEdit.getText().toString();
                model.search(query, false);
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

        browseRecyclerViewAdapter = new MusicLibraryAdapter(this, browseRecyclerView);
        browseRecyclerView.setAdapter(browseRecyclerViewAdapter);
        browseSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_MUSICLIBRARY_BROWSE,
                browseRecyclerView,
                browseRecyclerViewAdapter,
                StorageStrategy.createParcelableStorage(QueryEntry.class),
                savedInstanceState
        );
        browseRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    clearFocus();
                }
            }
        });

        browseFastScroller = rootView.findViewById(R.id.musiclibrary_browse_fastscroller);
        browseFastScroller.setRecyclerView(browseRecyclerView);
        browseFastScrollerBubble = rootView.findViewById(R.id.musiclibrary_browse_fastscroller_bubble);
        browseFastScroller.setBubble(browseFastScrollerBubble);
        browseRecyclerViewAdapter.setFastScrollerBubble(browseFastScrollerBubble);

        metaKeys = new ArrayList<>();
        metaKeysForDisplay = new ArrayList<>();

        browseHeaderShow = rootView.findViewById(R.id.musiclibrary_browse_header_show);
        final PopupMenu browseHeaderShowPopup = new PopupMenu(requireContext(), browseHeaderShow);
        browseHeaderShowMenu = browseHeaderShowPopup.getMenu();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            browseHeaderShowMenu.setGroupDividerEnabled(true);
        }
        browseHeaderShowPopup.setForceShowIcon(true);
        browseHeaderShowPopup.setOnMenuItemClickListener(item -> {
            clearSelection();
            return onShowSelected(item, metaKeysForDisplay);
        });
        browseHeaderShow.setOnClickListener(view -> browseHeaderShowPopup.show());
        browseHeaderNum = rootView.findViewById(R.id.musiclibrary_browse_header_num);
        browseHeader = rootView.findViewById(R.id.musiclibrary_browse_header);
        setShowMenuHeader("");
        browseSortView = rootView.findViewById(R.id.musiclibrary_browse_sort);
        browseSortView.setCallback(new BrowseSortView.Callback() {
            @Override
            public void setSortOrder(boolean ascending) {
                MusicLibraryFragment.this.setSortOrder(ascending);
            }

            @Override
            public void sortBy(List<String> metaKeys) {
                MusicLibraryFragment.this.sortBy(metaKeys);
            }

            @Override
            public Fragment getTargetFragment() {
                return MusicLibraryFragment.this;
            }

            @Override
            public String getShowMetaKey() {
                return getCurrentQuery().getShowField();
            }

            @Override
            public List<String> getSortByMetaKeys() {
                return querySortedByKeys();
            }

            @Override
            public boolean isSortedAscending() {
                return MusicLibraryFragment.this.isSortedAscending();
            }

            @Override
            public String getMetaKey(int i) {
                return metaKeys.get(i);
            }

            @Override
            public String getMetaKeyForDisplay(int i) {
                return metaKeysForDisplay.get(i);
            }

            @Override
            public String getEntryType() {
                return EntryID.TYPE_TRACK;
            }
        });

        View browseHomeBtn = rootView.findViewById(R.id.musiclibrary_browse_home);
        browseHomeBtn.setOnClickListener(v -> {
            clearSelection();
            clearFocus();
            model.addBackStackHistory(Util.getRecyclerViewPosition(browseRecyclerView));
            model.reset();
        });

        browseFilterTypeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterOperatorAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterAutoCompleteValuesAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        browseFilterSelectedKeyLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                browseFilterSelectedKeyLiveData,
                key -> MetaStorage.getInstance(requireContext()).getTrackMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            browseFilterAutoCompleteValuesAdapter.clear();
            browseFilterAutoCompleteValuesAdapter.addAll(values);
            browseFilterAutoCompleteValuesAdapter.notifyDataSetChanged();
        });
        browseFilterSelectedKeyLiveData.observe(getViewLifecycleOwner(), key -> {
            browseFilterOperatorAdapter.clear();
            browseFilterOperatorAdapter.addAll(QueryLeaf.getDisplayableOps(key));
            browseFilterOperatorAdapter.notifyDataSetChanged();
        });

        MetaStorage.getInstance(requireContext())
                .getTrackMetaKeys()
                .observe(getViewLifecycleOwner(), metaKeys -> {
                    this.metaKeys.clear();
                    this.metaKeys.addAll(metaKeys);
                });
        MetaStorage.getInstance(requireContext())
                .getTrackMetaKeysForDisplay()
                .observe(getViewLifecycleOwner(), metaKeysForDisplay -> {
                    this.metaKeysForDisplay.clear();
                    this.metaKeysForDisplay.addAll(metaKeysForDisplay);
                    onMetaKeysForDisplayChanged();
                });

        browseView = rootView.findViewById(R.id.musiclibrary_browse);
        rootView.findViewById(R.id.musiclibrary_browse_save).setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, getCurrentQueryTree())
        );

        browseQueryBtn = rootView.findViewById(R.id.musiclibrary_browse_query);
        browseQueryBtn.setOnClickListener(
                view -> {
                    boolean enable = queryRootView.getVisibility() != VISIBLE;
                    queryRootView.setVisibility(enable ? VISIBLE : GONE);
                    browseQueryBtn.setActivated(enable);
                }
        );
        queryRootView = rootView.findViewById(R.id.musiclibrary_query_root);

        return rootView;
    }

    private void onMetaKeysForDisplayChanged() {
        browseFilterTypeAdapter.clear();
        browseFilterTypeAdapter.addAll(metaKeysForDisplay);
        browseHeaderShowMenu.removeGroup(MENU_GROUP_ID_SHOW_SINGLE);
        for (int i = 0; i < metaKeysForDisplay.size(); i++) {
            browseHeaderShowMenu.add(
                    MENU_GROUP_ID_SHOW_SINGLE,
                    i,
                    MENU_GROUP_ORDER_SHOW_SINGLE,
                    metaKeysForDisplay.get(i)
            );
        }
        browseSortView.onMetaKeyForDisplayChanged(metaKeysForDisplay);
        browseFilterTypeAdapter.notifyDataSetChanged();
    }

    private boolean onShowSelected(MenuItem item, List<String> displayedFields) {
        int position = item.getItemId();
        int groupId = item.getGroupId();
        if (groupId != MENU_GROUP_ID_SHOW_SINGLE) {
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
        browseHeaderShowMenu.removeGroup(MENU_GROUP_ID_SHOW_HEADER);
        browseHeaderShowMenu.add(
                MENU_GROUP_ID_SHOW_HEADER,
                Menu.NONE,
                MENU_GROUP_ORDER_SHOW_HEADER,
                "Showing entries of type:"
        ).setEnabled(false);
        browseHeaderShowMenu.add(
                MENU_GROUP_ID_SHOW_HEADER,
                Menu.NONE,
                MENU_GROUP_ORDER_SHOW_HEADER,
                Meta.getDisplayKey(header)
        ).setEnabled(false);
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
        clearSelection();
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

    void setQuery(Query query) {
        model.setQuery(query);
    }

    private ArrayList<QueryNode> getSelectedQueryTrees() {
        ArrayList<QueryNode> queryTrees = new ArrayList<>();
        List<String> sortedByKeys = querySortedByKeys();
        browseSelectionTracker.getSelection().forEach(queryEntry -> {
            Query query = getCurrentQuery();
            query.andEntryIDToQuery(queryEntry.entryID);
            query.andSortedByValuesToQuery(sortedByKeys, queryEntry.sortedByValues());
            queryTrees.add(query.getQueryTree());
        });
        return queryTrees;
    }

    Query getCurrentQuery() {
        MusicLibraryUserState state = model.getUserState().getValue();
        return state == null ? null : new Query(state.query);
    }
    
    private QueryTree getCurrentQueryTree() {
        Query query = getCurrentQuery();
        if (query == null) {
            return null;
        }
        QueryTree queryTree = query.getQueryTree();
        return queryTree.deepCopy();
    }

    boolean querySortedByShow() {
        Query query = getCurrentQuery();
        if (query == null) {
            return false;
        }
        return query.querySortedByShow();
    }

    private boolean isSortedAscending() {
        Query query = getCurrentQuery();
        if (query == null) {
            return false;
        }
        return query.isSortOrderAscending();
    }

    List<String> querySortedByKeys() {
        Query query = getCurrentQuery();
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
        Query query = getCurrentQuery();
        return !query.isSearchQuery()
                && !Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(query.getShowField());
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
        if (browseQueryBtn != null && queryRootView != null && queryRootView.getVisibility() == VISIBLE) {
            queryRootView.setVisibility(GONE);
            browseQueryBtn.setActivated(false);
        }
        boolean needRefresh = setQueryDepth(1);
        clearBrowseQueryFocus();
        needRefresh = trimQueryTreeSelection(0) || needRefresh;
        if (needRefresh && model != null) {
            refreshView(model.getUserState().getValue());
        }
    }

    private void clearBrowseQueryFocus() {
        if (queryRootView == null) {
            return;
        }
        for (int i = 0; i < queryRootView.getChildCount(); i++) {
            MusicLibraryFilterGroup filterGroup =
                    (MusicLibraryFilterGroup) queryRootView.getChildAt(i);
            filterGroup.deactivate();
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
