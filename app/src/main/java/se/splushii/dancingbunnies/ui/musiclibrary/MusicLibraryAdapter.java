package se.splushii.dancingbunnies.ui.musiclibrary;

import android.support.v4.media.MediaBrowserCompat;
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
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.AddToPlaylistDialogFragment;
import se.splushii.dancingbunnies.ui.FastScrollerBubble;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private final MusicLibraryFragment fragment;
    private final FastScrollerBubble fastScrollerBubble;
    private SongViewHolder currentFastScrollerHolder;
    private List<MediaBrowserCompat.MediaItem> dataset;
    private TrackItemActionsView selectedActionView;
    private SelectionTracker<EntryID> selectionTracker;

    MusicLibraryAdapter(MusicLibraryFragment fragment,
                        RecyclerView recyclerView,
                        FastScrollerBubble fastScrollerBubble
    ) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
        this.fastScrollerBubble = fastScrollerBubble;
        fastScrollerBubble.setUpdateCallback(pos -> {
            currentFastScrollerHolder =
                    (SongViewHolder) recyclerView.findViewHolderForLayoutPosition(pos);
            fastScrollerBubble.setText(getFastScrollerText(currentFastScrollerHolder));
        });
    }

    private String getFastScrollerText(SongViewHolder holder) {
        String title = holder == null ? "" : holder.getTitle();
        String firstChar = title.length() >= 1 ? title.substring(0, 1).toUpperCase() : "";
        String secondChar = title.length() >= 2 ? title.substring(1, 2).toLowerCase() : "";
        return firstChar + secondChar;
    }

    void setModel(MusicLibraryFragmentModel model) {
        model.getDataSet().observe(fragment.getViewLifecycleOwner(), dataset -> {
            MusicLibraryUserState state = model.getUserState().getValue();
            if(!state.query.isSearchQuery()
                    && !state.query.getShowField().equals(Meta.FIELD_SPECIAL_MEDIA_ID)) {
                dataset.add(0, AudioPlayerService.generateMediaItem(
                        new LibraryEntry(EntryID.UNKOWN, "All entries...")
                ));
            }
            setDataset(dataset);
            fragment.scrollTo(state.pos, state.pad);
        });
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
        private final TextView libraryEntryTitle;
        private final TextView libraryEntryArtist;
        private final TextView libraryEntryAlbum;
        private final TrackItemActionsView actionsView;
        private MutableLiveData<EntryID> entryIDLiveData;
        String itemTitle;
        LiveData<Meta> metaLiveData;

        private final ItemDetailsLookup.ItemDetails<EntryID> itemDetails = new ItemDetailsLookup.ItemDetails<EntryID>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Nullable
            @Override
            public EntryID getSelectionKey() {
                return getEntryId();
            }
        };

        SongViewHolder(View view) {
            super(view);
            entryIDLiveData = new MutableLiveData<>();
            libraryEntry = view.findViewById(R.id.library_entry);
            libraryEntryTitle = view.findViewById(R.id.library_entry_title);
            libraryEntryAlbum = view.findViewById(R.id.library_entry_album);
            libraryEntryArtist = view.findViewById(R.id.library_entry_artist);
            actionsView = view.findViewById(R.id.library_entry_actions);
        }

        public ItemDetailsLookup.ItemDetails<EntryID> getItemDetails() {
            return itemDetails;
        }

        void setEntryId(EntryID entryId) {
            entryIDLiveData.setValue(entryId);
        }

        EntryID getEntryId() {
            return entryIDLiveData.getValue();
        }

        public String getTitle() {
            return libraryEntryTitle.getText() + "";
        }

        public void setMeta(Meta meta) {
            if (meta == null) {
                libraryEntryTitle.setText(itemTitle);
                libraryEntryArtist.setVisibility(View.GONE);
                libraryEntryAlbum.setVisibility(View.GONE);
            } else {
                String title = meta.getAsString(Meta.FIELD_TITLE);
                String artist = meta.getAsString(Meta.FIELD_ARTIST);
                String album = meta.getAsString(Meta.FIELD_ALBUM);
                libraryEntryTitle.setText(title);
                libraryEntryArtist.setText(artist);
                libraryEntryAlbum.setText(album);
                libraryEntryArtist.setVisibility(View.VISIBLE);
                libraryEntryAlbum.setVisibility(View.VISIBLE);
            }
            updateFastScrollerText(this);
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.musiclibrary_item, parent, false);
        SongViewHolder holder = new SongViewHolder(v);
        holder.metaLiveData = Transformations.switchMap(holder.entryIDLiveData, entryID -> {
            if (!Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
                MutableLiveData<Meta> nullMeta = new MutableLiveData<>();
                nullMeta.setValue(null);
                return nullMeta;
            }
            return MetaStorage.getInstance(fragment.requireContext()).getMeta(entryID);
        });
        holder.metaLiveData.observe(fragment.getViewLifecycleOwner(), holder::setMeta);
        return holder;
    }

    private void setDataset(List<MediaBrowserCompat.MediaItem> items) {
        this.dataset = items;
        notifyDataSetChanged();
    }

    EntryID getEntryId(int position) {
        return EntryID.from(dataset.get(position));
    }

    int getEntryIdPosition(@NonNull EntryID entryID) {
        int index = 0;
        for (MediaBrowserCompat.MediaItem item: dataset) {
            if (entryID.equals(EntryID.from(item))) {
                return index;
            }
            index++;
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        final MediaBrowserCompat.MediaItem item = dataset.get(position);
        holder.itemTitle = item.getDescription().getTitle() + "";
        holder.actionsView.initialize();
        EntryID entryID = EntryID.from(item);
        holder.setEntryId(entryID);
        if (position % 2 == 0) {
            holder.libraryEntry.setBackgroundResource(R.color.white_active_accent);
        } else {
            holder.libraryEntry.setBackgroundResource(R.color.gray50_active_accent);
        }
        boolean selected = selectionTracker != null
                && selectionTracker.isSelected(holder.getItemDetails().getSelectionKey());
        holder.libraryEntry.setActivated(selected);
        final boolean browsable = item.isBrowsable();
        holder.libraryEntry.setOnClickListener(view -> {
            if (selectionTracker.hasSelection()) {
                return;
            }
            if (browsable) {
                fragment.browse(entryID);
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
        holder.actionsView.setOnPlayListener(() -> fragment.play(entryID));
        holder.actionsView.setOnQueueListener(() -> fragment.queue(entryID));
        holder.actionsView.setOnAddToPlaylistListener(() ->
                AddToPlaylistDialogFragment.showDialog(
                        fragment,
                        new ArrayList<>(Collections.singletonList(entryID))
                )
        );
        holder.actionsView.setOnInfoListener(() ->
                MetaDialogFragment.showMeta(fragment, holder.metaLiveData.getValue())
        );
    }

    private void updateFastScrollerText(SongViewHolder holder) {
        if (holder == currentFastScrollerHolder) {
            fastScrollerBubble.setText(getFastScrollerText(holder));
        }
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
