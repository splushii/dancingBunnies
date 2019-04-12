package se.splushii.dancingbunnies.ui.musiclibrary;

import android.support.v4.media.MediaBrowserCompat;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.ItemActionsView;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private final MusicLibraryFragment fragment;
    private final LinearLayoutManager layoutManager;
    private final FastScrollerBubble fastScrollerBubble;
    private SongViewHolder currentFastScrollerHolder;
    private List<MediaBrowserCompat.MediaItem> dataset;
    private ItemActionsView selectedActionView;
    private SelectionTracker<EntryID> selectionTracker;

    MusicLibraryAdapter(MusicLibraryFragment fragment,
                        LinearLayoutManager recViewLayoutManager,
                        RecyclerView recyclerView,
                        FastScrollerBubble fastScrollerBubble
    ) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
        this.layoutManager = recViewLayoutManager;
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
                    && !state.query.getShowType().equals(Meta.FIELD_SPECIAL_MEDIA_ID)) {
                dataset.add(0, AudioPlayerService.generateMediaItem(
                        new LibraryEntry(EntryID.UNKOWN, "All entries...")
                ));
            }
            setDataset(dataset);
            layoutManager.scrollToPositionWithOffset(state.pos, state.pad);
        });
    }

    void setSelectionTracker(SelectionTracker<EntryID> selectionTracker) {
        this.selectionTracker = selectionTracker;
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection() && selectedActionView != null) {
                    selectedActionView.animateShow(false);
                    selectedActionView = null;
                }
            }
        });
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        private final View libraryEntry;
        private final TextView libraryEntryTitle;
        private final TextView libraryEntryArtist;
        private final TextView libraryEntryAlbum;
        private final ItemActionsView actionsView;
        private EntryID entryId;
        public Meta meta;

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
            this.entryId = entryId;
        }

        EntryID getEntryId() {
            return entryId;
        }

        public String getTitle() {
            return libraryEntryTitle.getText() + "";
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.musiclibrary_item, parent, false);
        return new SongViewHolder(v);
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
        EntryID entryID = EntryID.from(item);
        holder.setEntryId(entryID);
        final boolean browsable = item.isBrowsable();
        holder.actionsView.initialize();
        holder.meta = null;
        if (Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
            MetaStorage.getInstance(fragment.requireContext()).getMeta(entryID)
                    .thenAcceptAsync(meta -> {
                        if (!entryID.equals(holder.entryId)) {
                            return;
                        }
                        holder.meta = meta;
                        String title = meta.getAsString(Meta.FIELD_TITLE);
                        String artist = meta.getAsString(Meta.FIELD_ARTIST);
                        String album = meta.getAsString(Meta.FIELD_ALBUM);
                        holder.libraryEntryTitle.setText(title);
                        holder.libraryEntryArtist.setText(artist);
                        holder.libraryEntryAlbum.setText(album);
                        holder.libraryEntryArtist.setVisibility(View.VISIBLE);
                        holder.libraryEntryAlbum.setVisibility(View.VISIBLE);
                        updateFastScrollerText(holder);
                    }, Util.getMainThreadExecutor());
        } else {
            final String name = item.getDescription().getTitle() + "";
            holder.libraryEntryTitle.setText(name);
            holder.libraryEntryArtist.setVisibility(View.GONE);
            holder.libraryEntryAlbum.setVisibility(View.GONE);
            updateFastScrollerText(holder);
        }
        if (position % 2 == 0) {
            holder.libraryEntry.setBackgroundResource(R.drawable.musiclibrary_item_drawable);
        } else {
            holder.libraryEntry.setBackgroundResource(R.drawable.musiclibrary_item_drawable_odd);
        }
        boolean selected = selectionTracker != null
                && selectionTracker.isSelected(holder.getItemDetails().getSelectionKey());
        holder.libraryEntry.setActivated(selected);
        holder.libraryEntry.setOnClickListener(view -> {
            if (browsable) {
                fragment.browse(entryID);
            } else {
                if (selectedActionView != null && selectedActionView != holder.actionsView) {
                    selectedActionView.animateShow(false);
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
                fragment.addToPlaylist(Collections.singletonList(entryID))
        );
        holder.actionsView.setOnInfoListener(() ->
                MetaDialogFragment.showMeta(fragment, holder.meta)
        );
    }

    private void updateFastScrollerText(SongViewHolder holder) {
        if (holder == currentFastScrollerHolder) {
            fastScrollerBubble.setText(getFastScrollerText(holder));
        }
    }

    Pair<Integer, Integer> getCurrentPosition() {
        RecyclerView rv = fragment.getView().findViewById(R.id.musiclibrary_recyclerview);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        int hPos = llm.findFirstCompletelyVisibleItemPosition();
        View v = llm.getChildAt(0);
        int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        if (hPad < 0 && hPos > 0) {
            hPos--;
        }
        return new Pair<>(hPos, hPad);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
