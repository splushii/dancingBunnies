package se.splushii.dancingbunnies.ui.musiclibrary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
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
    private SongViewHolder currentFastScrollerHolder;
    private List<LibraryEntry> dataset;
    private TrackItemActionsView selectedActionView;
    private SelectionTracker<EntryID> selectionTracker;
    private boolean initialScrolled;

    MusicLibraryAdapter(MusicLibraryFragment fragment,
                        RecyclerView recyclerView,
                        FastScrollerBubble fastScrollerBubble
    ) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
        fastScrollerBubble.setUpdateCallback(pos -> {
            currentFastScrollerHolder =
                    (SongViewHolder) recyclerView.findViewHolderForLayoutPosition(pos);
            fastScrollerBubble.setText(getFastScrollerText(currentFastScrollerHolder));
        });
    }

    private String getFastScrollerText(SongViewHolder holder) {
        String title = holder == null ? "" : holder.getSortedBy();
        if (title == null) {
            return "";
        }
        String firstChar = title.length() >= 1 ? title.substring(0, 1).toUpperCase() : "";
        String secondChar = title.length() >= 2 ? title.substring(1, 2).toLowerCase() : "";
        return firstChar + secondChar;
    }

    void setModel(MusicLibraryFragmentModel model) {
        initialScrolled = false;
        model.getDataSet().observe(fragment.getViewLifecycleOwner(), dataset -> {
            MusicLibraryUserState state = model.getUserState().getValue();
            if(fragment.showAllEntriesRow()) {
                dataset.add(
                        0,
                        new LibraryEntry(EntryID.UNKOWN, "All entries...", null)
                );
            }
            setDataset(dataset);
            updateScrollPos(state, dataset);
        });
    }

    private void updateScrollPos(MusicLibraryUserState userState, List<LibraryEntry> entries) {
        if (!initialScrolled && !entries.isEmpty()) {
            initialScrolled = true;
            fragment.scrollTo(userState.pos, userState.pad);
        }
    }

    void setSelectionTracker(SelectionTracker<EntryID> selectionTracker) {
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
        private final TextView libraryEntryArtist;
        private final TextView libraryEntrySortedBy;
        private final TrackItemActionsView actionsView;
        private final TextView libraryEntryNum;
        private LiveData<Integer> numSubEntriesLiveData;
        private MutableLiveData<EntryID> entryIDLiveData;
        private LibraryEntry entry;
        private boolean browsable = false;

        private final ItemDetailsLookup.ItemDetails<EntryID> itemDetails = new ItemDetailsLookup.ItemDetails<EntryID>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Nullable
            @Override
            public EntryID getSelectionKey() {
                return entryIDLiveData.getValue();
            }
        };

        SongViewHolder(View view) {
            super(view);
            entryIDLiveData = new MutableLiveData<>();
            libraryEntry = view.findViewById(R.id.library_entry);
            libraryEntryShow = view.findViewById(R.id.library_entry_show);
            libraryEntrySortedBy = view.findViewById(R.id.library_entry_sortedby);
            libraryEntryArtist = view.findViewById(R.id.library_entry_artist);
            libraryEntryNum = view.findViewById(R.id.library_entry_num);
            actionsView = view.findViewById(R.id.library_entry_actions);
        }

        public ItemDetailsLookup.ItemDetails<EntryID> getItemDetails() {
            return itemDetails;
        }

        void setShowValue(List<String> showMeta) {
            if (showMeta != null) {
                libraryEntryShow.setText(Meta.getAsString(showMeta));
            }
        }

        void setArtist(List<String> artistMeta) {
            if (artistMeta == null) {
                libraryEntryArtist.setText("");
            } else {
                String artist = Meta.getAsString(artistMeta);
                libraryEntryArtist.setText(artist);
            }
        }

        void update(LibraryEntry libraryEntry, boolean browsable) {
            entry = libraryEntry;
            this.browsable = browsable;
            if (fragment.showHeaderSortedBy()) {
                libraryEntrySortedBy.setText(entry.sortedBy());
                libraryEntrySortedBy.setVisibility(View.VISIBLE);
            } else {
                libraryEntrySortedBy.setVisibility(View.GONE);
            }
            libraryEntryShow.setText(fragment.isSearchQuery() ? "" : entry.name());
            libraryEntryArtist.setText("");
            libraryEntryNum.setVisibility(fragment.showHeaderNum(browsable) ?
                    View.VISIBLE : View.GONE
            );
            libraryEntryArtist.setVisibility(fragment.showHeaderArtist(browsable) ?
                    View.VISIBLE : View.GONE
            );
            entryIDLiveData.setValue(libraryEntry.entryID);
        }

        String getSortedBy() {
            return entry.sortedBy();
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
            return MetaStorage.getInstance(fragment.requireContext()).getMetaString(entryID, Meta.FIELD_TITLE);
        });
        titleMetaLiveData.observe(fragment.getViewLifecycleOwner(), holder::setShowValue);
        LiveData<List<String>> artistMetaLiveData = Transformations.switchMap(holder.entryIDLiveData, entryID -> {
            if (!Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
                MutableLiveData<List<String>> nullMeta = new MutableLiveData<>();
                nullMeta.setValue(null);
                return nullMeta;
            }
            return MetaStorage.getInstance(fragment.requireContext()).getMetaString(entryID, Meta.FIELD_ARTIST);
        });
        artistMetaLiveData.observe(fragment.getViewLifecycleOwner(), holder::setArtist);
        holder.numSubEntriesLiveData = Transformations.switchMap(holder.entryIDLiveData, e -> {
            if (!holder.isBrowsable()) {
                MutableLiveData<Integer> ret = new MutableLiveData<>();
                ret.setValue(-1);
                return ret;
            }
            MusicLibraryQuery query = fragment.getCurrentQuery();
            addSortedByToQuery(query, holder);
            return MetaStorage.getInstance(fragment.requireContext()).getNumSongEntries(
                    Collections.singletonList(e),
                    query.getQueryBundle()
            );
        });
        holder.numSubEntriesLiveData.observe(fragment.getViewLifecycleOwner(), numSubEntries ->
                holder.libraryEntryNum.setText(String.valueOf(numSubEntries))
        );
        holder.actionsView.setAudioBrowserFragment(fragment);
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
        return holder;
    }

    private void addSortedByToQuery(MusicLibraryQuery query, SongViewHolder holder) {
        if (holder.isBrowsable() && !fragment.querySortedByShow()) {
            // If the entries are sorted by a key (other than the entries' type),
            // then add it to the query
            String sortedByKey = fragment.querySortedByKey();
            String sortedByValue = holder.entry.sortedBy();
            if (sortedByValue != null) {
                query.addToQuery(sortedByKey, sortedByValue);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        holder.libraryEntry.setBackgroundResource(position % 2 == 0 ?
                R.color.white_active_accent : R.color.gray50_active_accent
        );
        LibraryEntry libraryEntry = dataset.get(position);
        final boolean browsable = libraryEntry.isBrowsable();
        holder.actionsView.initialize();
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
                EntryID entryID = holder.entryIDLiveData.getValue();
                MusicLibraryQuery query = fragment.getCurrentQuery();
                addSortedByToQuery(query, holder);
                String showField = Meta.FIELD_ARTIST.equals(entryID.type) ?
                        Meta.FIELD_ALBUM : Meta.FIELD_SPECIAL_MEDIA_ID;
                query.setShowField(showField);
                String sortField = Meta.FIELD_SPECIAL_MEDIA_ID.equals(showField) ?
                        Meta.FIELD_TITLE : showField;
                query.setSortByField(sortField);
                if (!entryID.isUnknown()) {
                    query.addToQuery(entryID.type, entryID.id);
                }
                fragment.setQuery(query);
            } else {
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
        boolean changed = !dataset.equals(items);
        if (changed) {
            this.dataset = items;
            notifyDataSetChanged();
        }
    }

    EntryID getEntryId(int position) {
        return dataset.get(position).entryID;
    }

    int getEntryIdPosition(@NonNull EntryID entryID) {
        int index = 0;
        for (LibraryEntry libraryEntry: dataset) {
            if (entryID.equals(libraryEntry.entryID)) {
                return index;
            }
            index++;
        }
        return RecyclerView.NO_POSITION;
    }

    Pair<Integer, Integer> getCurrentPosition() {
        RecyclerView rv = fragment.getView().findViewById(R.id.musiclibrary_recyclerview);
        return Util.getRecyclerViewPosition(rv);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
