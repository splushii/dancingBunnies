package se.splushii.dancingbunnies.ui.musiclibrary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private final MusicLibraryFragment fragment;
    private final RecyclerView recyclerView;
    private SongViewHolder currentFastScrollerHolder;
    private List<LibraryEntry> dataset;
    private TrackItemActionsView selectedActionView;
    private SelectionTracker<LibraryEntry> selectionTracker;
    private boolean initialScrolled;

    MusicLibraryAdapter(MusicLibraryFragment fragment, RecyclerView recyclerView) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
        this.recyclerView = recyclerView;
    }

    public void setFastScrollerBubble(FastScrollerBubble fastScrollerBubble) {
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
        model.getDataSet().observe(fragment.getViewLifecycleOwner(), dataset -> {
            MusicLibraryUserState state = model.getUserState().getValue();
            if (state.query.isSearchQuery()) {
                setDataset(new ArrayList<>());
            } else {
                if (fragment.showAllEntriesRow()) {
                    dataset.add(
                            0,
                            new LibraryEntry(EntryID.UNKOWN, "All entries...", null)
                    );
                }
                setDataset(dataset);
            }
            updateScrollPos(state, dataset);
        });
    }

    private void updateScrollPos(MusicLibraryUserState userState, List<LibraryEntry> entries) {
        if (!initialScrolled && !entries.isEmpty()) {
            initialScrolled = true;
            fragment.scrollBrowseTo(userState.pos, userState.pad);
        }
    }

    void setSelectionTracker(SelectionTracker<LibraryEntry> selectionTracker) {
        this.selectionTracker = selectionTracker;
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection()) {
                    hideTrackItemActions();
                }
            }
        });
    }

    void hideTrackItemActions() {
        if (selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {
        private final View libraryEntry;
        private final TextView libraryEntryShow;
        private final LinearLayout libraryEntrySortedBy;
        private final LinearLayout libraryEntrySortedByKeys;
        private final TrackItemActionsView actionsView;
        private final TextView libraryEntryNum;
        private LiveData<Integer> numSubEntriesLiveData;
        private MutableLiveData<EntryID> entryIDLiveData;
        private LibraryEntry entry;
        private boolean browsable = false;

        private final ItemDetailsLookup.ItemDetails<LibraryEntry> itemDetails =
                new ItemDetailsLookup.ItemDetails<LibraryEntry>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Nullable
            @Override
            public LibraryEntry getSelectionKey() {
                return entry;
            }
        };

        SongViewHolder(View view) {
            super(view);
            entryIDLiveData = new MutableLiveData<>();
            libraryEntry = view.findViewById(R.id.library_entry);
            libraryEntryShow = view.findViewById(R.id.library_entry_show);
            libraryEntryNum = view.findViewById(R.id.library_entry_num);
            libraryEntrySortedBy = view.findViewById(R.id.library_entry_sortedby);
            libraryEntrySortedByKeys = view.findViewById(R.id.library_entry_sortedby_keys);
            actionsView = view.findViewById(R.id.library_entry_actions);
        }

        public ItemDetailsLookup.ItemDetails<LibraryEntry> getItemDetails() {
            return itemDetails;
        }

        void setShowValue(List<String> showMeta) {
            if (showMeta != null) {
                libraryEntryShow.setText(getShowDisplayValue(Meta.getAsString(showMeta)));
            }
        }

        void update(LibraryEntry libraryEntry, boolean browsable) {
            entry = libraryEntry;
            this.browsable = browsable;
            fragment.addSortedByColumns(libraryEntrySortedBy, libraryEntrySortedByKeys, entry.sortedByValues(), false);
            libraryEntryShow.setText(fragment.isSearchQuery() ? "" : getShowDisplayValue(entry.name()));
            libraryEntryNum.setVisibility(browsable ? View.VISIBLE : View.GONE);
            entryIDLiveData.setValue(libraryEntry.entryID);
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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.musiclibrary_item, parent, false);
        SongViewHolder holder = new SongViewHolder(v);
        LiveData<List<String>> titleMetaLiveData = Transformations.switchMap(holder.entryIDLiveData, entryID -> {
            if (!fragment.isSearchQuery()) {
                MutableLiveData<List<String>> nullMeta = new MutableLiveData<>();
                nullMeta.setValue(null);
                return nullMeta;
            }
            return MetaStorage.getInstance(fragment.requireContext())
                    .getMetaString(entryID, Meta.FIELD_TITLE);
        });
        titleMetaLiveData.observe(fragment.getViewLifecycleOwner(), holder::setShowValue);
        holder.numSubEntriesLiveData = Transformations.switchMap(holder.entryIDLiveData, e -> {
            if (!holder.isBrowsable()) {
                MutableLiveData<Integer> ret = new MutableLiveData<>();
                ret.setValue(-1);
                return ret;
            }
            MusicLibraryQuery query = fragment.getCurrentQuery();
            andSortedByToQuery(query, holder);
            return MetaStorage.getInstance(fragment.requireContext()).getNumSongEntries(
                    Collections.singletonList(e),
                    query.getQueryTree()
            );
        });
        holder.numSubEntriesLiveData.observe(fragment.getViewLifecycleOwner(), numSubEntries ->
                holder.libraryEntryNum.setText(String.valueOf(numSubEntries))
        );
        holder.actionsView.setAudioBrowser(fragment.getRemote());
        holder.actionsView.setFragmentManager(fragment.requireActivity().getSupportFragmentManager());
        holder.actionsView.setEntryIDSupplier(() -> holder.entry.entryID);
        holder.actionsView.setActions(
                new int[] {
                        ACTION_ADD_TO_QUEUE,
                        ACTION_ADD_TO_PLAYLIST,
                        ACTION_INFO
                },
                new int[] {
                        ACTION_PLAY,
                        ACTION_ADD_TO_QUEUE,
                        ACTION_ADD_TO_PLAYLIST,
                        ACTION_CACHE,
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                new int[] {}
        );
        holder.actionsView.initialize();
        return holder;
    }

    private void andSortedByToQuery(MusicLibraryQuery query, SongViewHolder holder) {
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
        holder.libraryEntry.setBackgroundResource(position % 2 == 0 ?
                R.color.background_active_accent : R.color.backgroundalternate_active_accent
        );
        LibraryEntry libraryEntry = dataset.get(position);
        final boolean browsable = libraryEntry.isBrowsable();
        holder.libraryEntryNum.setText("");
        holder.update(libraryEntry, browsable);
        boolean selected = selectionTracker != null
                && selectionTracker.isSelected(holder.getItemDetails().getSelectionKey());
        holder.libraryEntry.setActivated(selected);
        holder.libraryEntry.setOnClickListener(view -> {
            if (selectionTracker.hasSelection()) {
                return;
            }
            if (browsable) {
                fragment.addBackStackHistory();
                fragment.clearFocus();
                EntryID entryID = holder.entryIDLiveData.getValue();
                MusicLibraryQuery query = fragment.getCurrentQuery();
                andSortedByToQuery(query, holder);
                String showField = Meta.FIELD_ARTIST.equals(entryID.type) ?
                        Meta.FIELD_ALBUM : Meta.FIELD_SPECIAL_MEDIA_ID;
                query.setShowField(showField);
                ArrayList<String> sortFields = new ArrayList<>();
                if (Meta.FIELD_SPECIAL_MEDIA_ID.equals(showField)) {
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
                if (selectedActionView != holder.actionsView) {
                    hideTrackItemActions();
                }
                selectedActionView = holder.actionsView;
                boolean showActionsView = !selectionTracker.hasSelection()
                        && holder.actionsView.getVisibility() != View.VISIBLE;
                holder.actionsView.animateShow(showActionsView);
            }
        });
    }

    private void setDataset(List<LibraryEntry> items) {
        String showKey = fragment.getCurrentQuery().getShowField();
        List<LibraryEntry> newItems;
        if (Meta.FIELD_SPECIAL_MEDIA_ID.equals(showKey)) {
            HashMap<EntryID, ArrayList<String>> newSet = new HashMap<>();
            newItems = new ArrayList<>();
            for (LibraryEntry libraryEntry: items) {
                ArrayList<String> sortByValues = newSet.get(libraryEntry.entryID);
                if (sortByValues == null) {
                    sortByValues = new ArrayList<>(libraryEntry.sortedByValues());
                    LibraryEntry newLibraryEntry = new LibraryEntry(
                            libraryEntry.entryID,
                            libraryEntry.name(),
                            sortByValues
                    );
                    newSet.put(newLibraryEntry.entryID, sortByValues);
                    newItems.add(newLibraryEntry);
                } else {
                    List<String> newSortByValues = libraryEntry.sortedByValues();
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
        boolean changed = !dataset.equals(newItems);
        if (changed) {
            this.dataset = newItems;
            notifyDataSetChanged();
        }
    }

    LibraryEntry getEntryId(int position) {
        return dataset.get(position);
    }

    int getEntryIdPosition(@NonNull LibraryEntry libraryEntry) {
        int index = 0;
        for (LibraryEntry l: dataset) {
            if (libraryEntry.equals(l)) {
                return index;
            }
            index++;
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
