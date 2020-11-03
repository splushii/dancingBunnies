package se.splushii.dancingbunnies.ui.musiclibrary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Query;
import se.splushii.dancingbunnies.musiclibrary.QueryEntry;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD;

public class MusicLibraryAdapter extends
        SmartDiffSelectionRecyclerViewAdapter<QueryEntry, MusicLibraryAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(MusicLibraryAdapter.class);

    private final MusicLibraryFragment fragment;
    private final RecyclerView recyclerView;
    private SongViewHolder currentFastScrollerHolder;
    private TrackItemActionsView selectedActionView;
    private boolean initialScrolled;

    MusicLibraryAdapter(MusicLibraryFragment fragment, RecyclerView recyclerView) {
        this.fragment = fragment;
        this.recyclerView = recyclerView;
    }

    void setFastScrollerBubble(FastScrollerBubble fastScrollerBubble) {
        fastScrollerBubble.setUpdateCallback(pos -> {
            currentFastScrollerHolder =
                    (SongViewHolder) recyclerView.findViewHolderForLayoutPosition(pos);
            fastScrollerBubble.setText(getFastScrollerText(currentFastScrollerHolder));
        });
    }

    private String getFastScrollerText(SongViewHolder holder) {
        String title = holder == null ? "" : fragment.getSortedByDisplayString(
                holder.entry.sortedByValues(),
                false,
                false
        );
        if (title == null) {
            return "";
        }
        String firstChar = title.length() >= 1 ? title.substring(0, 1).toUpperCase() : "";
        String secondChar = title.length() >= 2 ? title.substring(1, 2).toLowerCase() : "";
        return firstChar + secondChar;
    }

    void scrollWhenReady() {
        initialScrolled = false;
    }

    void setModel(MusicLibraryFragmentModel model) {
        initialScrolled = false;
        model.getQueryEntries().observe(fragment.getViewLifecycleOwner(), queryEntries -> {
            MusicLibraryUserState state = model.getUserState().getValue();
            if (state.query.isSearchQuery() || queryEntries.isEmpty()) {
                setQueryEntries(new ArrayList<>());
            } else {
                if (fragment.showAllEntriesRow()) {
                    queryEntries.add(
                            0,
                            new QueryEntry(EntryID.UNKOWN, "All entries...", null)
                    );
                }
                setQueryEntries(queryEntries);
            }
            updateScrollPos(state, queryEntries);
        });
    }

    private void updateScrollPos(MusicLibraryUserState userState, List<QueryEntry> entries) {
        if (!initialScrolled && !entries.isEmpty()) {
            initialScrolled = true;
            fragment.scrollBrowseTo(userState.pos, userState.pad);
        }
    }

    void hideTrackItemActions() {
        if (selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection() && selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    @Override
    public void onSelectionDrop(Collection<QueryEntry> selection, int targetPos, QueryEntry idAfterTargetPos) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onUseViewHolderForDrag(SongViewHolder dragViewHolder, Collection<QueryEntry> selection) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onResetDragViewHolder(SongViewHolder dragViewHolder) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback, Selection<QueryEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback, Selection<QueryEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    private void updateActionModeView(ActionModeCallback actionModeCallback,
                                      Selection<QueryEntry> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {}

    @Override
    public boolean validSelect(QueryEntry key) {
        return true;
    }

    @Override
    public boolean validMove(SongViewHolder current, SongViewHolder target) {
        return false;
    }

    @Override
    public boolean validDrag(SongViewHolder viewHolder) {
        return false;
    }

    @Override
    public boolean validDrag(Selection<QueryEntry> selection) {
        return false;
    }

    public class SongViewHolder extends ItemDetailsViewHolder<QueryEntry> {
        private final View queryEntry;
        private final TextView queryEntryShow;
        private final LinearLayout queryEntrySortedBy;
        private final LinearLayout queryEntrySortedByKeys;
        private final TrackItemActionsView actionsView;
        private final TextView queryEntryNum;
        private LiveData<Integer> numSubEntriesLiveData;
        private MutableLiveData<EntryID> entryIDLiveData;
        private QueryEntry entry;
        private boolean browsable = false;

        SongViewHolder(View view) {
            super(view);
            entryIDLiveData = new MutableLiveData<>();
            queryEntry = view.findViewById(R.id.library_entry);
            queryEntryShow = view.findViewById(R.id.library_entry_show);
            queryEntryNum = view.findViewById(R.id.library_entry_num);
            queryEntrySortedBy = view.findViewById(R.id.library_entry_sortedby);
            queryEntrySortedByKeys = view.findViewById(R.id.library_entry_sortedby_keys);
            actionsView = view.findViewById(R.id.library_entry_actions);
        }

        @Override
        protected QueryEntry getSelectionKeyOf() {
            return entry;
        }

        void update(QueryEntry queryEntry, boolean browsable) {
            entry = queryEntry;
            this.browsable = browsable;
            fragment.addSortedByColumns(queryEntrySortedBy, queryEntrySortedByKeys, entry.sortedByValues(), false);
            queryEntryShow.setText(getShowDisplayValue(entry.name()));
            queryEntryNum.setVisibility(browsable ? View.VISIBLE : View.GONE);
            entryIDLiveData.setValue(queryEntry.entryID);
        }

        String getShowDisplayValue(String value) {
            String showField = fragment.getCurrentQuery().getShowField();
            return Meta.getDisplayValue(showField, value);
        }

        boolean isBrowsable() {
            return browsable;
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {
        SongViewHolder holder = new SongViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.musiclibrary_item, parent, false)
        );

        holder.numSubEntriesLiveData = Transformations.switchMap(holder.entryIDLiveData, e -> {
            if (!holder.isBrowsable()) {
                MutableLiveData<Integer> ret = new MutableLiveData<>();
                ret.setValue(-1);
                return ret;
            }
            Query query = fragment.getCurrentQuery();
            andSortedByToQuery(query, holder);
            return MetaStorage.getInstance(fragment.requireContext()).getNumTracksSum(
                    Collections.singletonList(e),
                    query.getQueryTree()
            );
        });
        holder.numSubEntriesLiveData.observe(fragment.getViewLifecycleOwner(), numSubEntries ->
                holder.queryEntryNum.setText(String.valueOf(numSubEntries))
        );

        holder.actionsView.setAudioBrowser(fragment.getRemote());
        holder.actionsView.setFragmentManager(fragment.requireActivity().getSupportFragmentManager());
        holder.actionsView.setEntryIDSupplier(() -> holder.entryIDLiveData.getValue());
        holder.actionsView.setActions(
                new int[] {
                        ACTION_QUEUE_ADD,
                        ACTION_PLAYLIST_ENTRY_ADD,
                        ACTION_INFO
                },
                new int[] {
                        ACTION_PLAY,
                        ACTION_QUEUE_ADD,
                        ACTION_PLAYLIST_ENTRY_ADD,
                        ACTION_CACHE,
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                new int[] {}
        );
        holder.actionsView.initialize();
        return holder;
    }

    private void andSortedByToQuery(Query query, SongViewHolder holder) {
        if (holder.isBrowsable() && !fragment.querySortedByShow()) {
            // If the entries are sorted by a key (other than the entries' type),
            // then add it to the query
            query.andSortedByValuesToQuery(
                    fragment.querySortedByKeys(),
                    holder.entry.sortedByValues()
            );
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        holder.queryEntry.setBackgroundResource(position % 2 == 0 ?
                R.color.background_active_accent : R.color.backgroundalternate_active_accent
        );
        QueryEntry queryEntry = getItem(position);
        final boolean browsable = queryEntry.isBrowsable();
        holder.queryEntryNum.setText("");
        holder.update(queryEntry, browsable);
        holder.queryEntry.setActivated(isSelected(holder.getKey()));
        holder.queryEntry.setOnClickListener(view -> {
            if (hasSelection()) {
                return;
            }
            if (browsable) {
                fragment.addBackStackHistory();
                fragment.clearFocus();
                EntryID entryID = holder.entryIDLiveData.getValue();
                Query query = fragment.getCurrentQuery();
                andSortedByToQuery(query, holder);
                String showField = Meta.FIELD_ARTIST.equals(entryID.type) ?
                        Meta.FIELD_ALBUM : Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
                query.setShowField(showField);
                ArrayList<String> sortFields = new ArrayList<>();
                if (Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(showField)) {
                    sortFields.add(Meta.FIELD_TITLE);
                    sortFields.add(Meta.FIELD_ARTIST);
                } else {
                    sortFields.add(showField);
                }
                query.setSortByFields(sortFields);
                query.setSortOrder(true);
                query.andEntryIDToQuery(entryID);
                fragment.setQuery(query);
            } else {
                fragment.clearFocus();
                selectedActionView = holder.actionsView;
                holder.actionsView.animateShow(holder.actionsView.getVisibility() != View.VISIBLE);
            }
        });
    }

    private void setQueryEntries(List<QueryEntry> items) {
        String showKey = fragment.getCurrentQuery().getShowField();
        List<QueryEntry> newItems;
        if (Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(showKey)) {
            HashMap<EntryID, ArrayList<String>> newSet = new HashMap<>();
            newItems = new ArrayList<>();
            for (QueryEntry queryEntry : items) {
                ArrayList<String> sortByValues = newSet.get(queryEntry.entryID);
                if (sortByValues == null) {
                    sortByValues = new ArrayList<>(queryEntry.sortedByValues());
                    QueryEntry newQueryEntry = new QueryEntry(
                            queryEntry.entryID,
                            queryEntry.name(),
                            sortByValues
                    );
                    newSet.put(newQueryEntry.entryID, sortByValues);
                    newItems.add(newQueryEntry);
                } else {
                    List<String> newSortByValues = queryEntry.sortedByValues();
                    for (int i = 0; i < sortByValues.size() && i < newSortByValues.size(); i++) {
                        sortByValues.set(i, String.format(
                                Locale.getDefault(),
                                "%s, %s",
                                sortByValues.get(i),
                                newSortByValues.get(i)
                        ));
                    }
                }
            }
        } else {
            newItems = items;
        }
        setDataSet(newItems);
    }
}
