package se.splushii.dancingbunnies.ui.nowplaying;

import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingEntriesAdapter extends
        SelectionRecyclerViewAdapter<PlaybackEntry, NowPlayingEntriesAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);
    private final NowPlayingFragment fragment;

    private List<PlaybackEntry> queueEntries;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;

    NowPlayingEntriesAdapter(NowPlayingFragment fragment) {
        this.fragment = fragment;
        queueEntries = new ArrayList<>();
        setHasStableIds(true);
    }

    void setModel(NowPlayingFragmentModel model) {
        cachedEntriesLiveData = model.getCachedEntries(fragment.getContext());
        fetchStateLiveData = model.getFetchState(fragment.getContext());
    }

    void setQueueEntries(List<PlaybackEntry> queueEntries) {
        boolean changed = !this.queueEntries.equals(queueEntries);
        this.queueEntries = queueEntries;
        if (changed) {
            notifyDataSetChanged();
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
    protected PlaybackEntry getKey(int pos) {
        return queueEntries.get(pos);
    }

    @Override
    protected int getPosition(@NonNull PlaybackEntry key) {
        int index = queueEntries.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<PlaybackEntry> selection, int lastDragPos) {
        fragment.moveQueueItems(
                new ArrayList<>(selection),
                lastDragPos
        );
    }

    @Override
    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                       Collection<PlaybackEntry> selection) {
        dragViewHolder.itemContent.setDragTitle(selection.size() + " entries");
    }

    @Override
    public void onResetDragViewHolder(ViewHolder dragViewHolder) {
        dragViewHolder.itemContent.reset();
    }

    @Override
    public boolean onActionItemClicked(int menuItemID, List<PlaybackEntry> selectionList) {
        switch (menuItemID) {
            case R.id.nowplaying_actionmode_action_queue:
                fragment.queue(
                        selectionList.stream()
                                .map(playbackEntry -> playbackEntry.entryID)
                                .collect(Collectors.toList()),
                        AudioPlayerService.QUEUE_LAST
                );
                return true;
            case R.id.nowplaying_actionmode_action_dequeue:
                fragment.dequeue(selectionList);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onActionModeStarted(ActionMode actionMode, Selection<PlaybackEntry> selection) {
        actionMode.setTitle(selection.size() + " entries");
    }

    @Override
    public void onActionModeSelectionChanged(ActionMode actionMode, Selection<PlaybackEntry> selection) {
        actionMode.setTitle(selection.size() + " entries");
    }

    @Override
    public void onActionModeEnding(ActionMode actionMode) {}

    @Override
    public boolean onDragInitiated(Selection<PlaybackEntry> selection) {
        return true;
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        queueEntries.add(to, queueEntries.remove(from));
    }

    @Override
    protected void addItemToDataset(int pos, PlaybackEntry item) {
        queueEntries.add(pos, item);
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        queueEntries.remove(pos);
    }

    public class ViewHolder extends ItemDetailsViewHolder<PlaybackEntry> {
        private final View item;
        final TrackItemView itemContent;
        private final TrackItemActionsView actionsView;

        ViewHolder(View v) {
            super(v);
            item = v.findViewById(R.id.nowplaying_item);
            itemContent = v.findViewById(R.id.nowplaying_item_content);
            actionsView = v.findViewById(R.id.nowplaying_item_actions);
            item.setOnClickListener(view -> {
                if (hasSelection()) {
                    return;
                }
                if (selectedActionView != null && selectedActionView != actionsView) {
                    selectedActionView.animateShow(false);
                }
                selectedActionView = actionsView;
                boolean showActionsView = !hasSelection()
                        && actionsView.getVisibility() != View.VISIBLE;
                actionsView.animateShow(showActionsView);
            });
        }

        @Override
        protected int getPositionOf() {
            return getAdapterPosition();
        }

        @Override
        protected PlaybackEntry getSelectionKeyOf() {
            return queueEntries.get(getPositionOf());
        }

        public void setMeta(Meta meta) {
            actionsView.setOnInfoListener(() -> MetaDialogFragment.showMeta(fragment, meta));
            String title = meta.getAsString(Meta.FIELD_TITLE);
            itemContent.setTitle(title);
            String artist = meta.getAsString(Meta.FIELD_ARTIST);
            itemContent.setArtist(artist);
            String src = meta.entryID.src;
            itemContent.setSource(src);
        }
    }

    @Override
    public long getItemId(int position) {
        return queueEntries.get(position).playbackID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ViewHolder holder = new ViewHolder(layoutInflater.inflate(R.layout.nowplaying_queue_item, parent, false));
        cachedEntriesLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder.itemContent::setCached
        );
        fetchStateLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder.itemContent::setFetchState
        );
        holder.initMetaObserver(fragment.requireContext());
        holder.observeMeta(fragment.getViewLifecycleOwner(), holder::setMeta);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        holder.actionsView.initialize();
        PlaybackEntry entry = queueEntries.get(position);
        if (PlaybackEntry.USER_TYPE_QUEUE.equals(entry.playbackType)) {
            holder.item.setBackground(ContextCompat.getDrawable(
                    fragment.requireContext(),
                    R.drawable.nowplaying_queue_item_drawable
            ));
        } else {
            Log.e(LC, "NOT A QUEUE ENTRY WOO! " + entry);
            holder.item.setBackground(ContextCompat.getDrawable(
                    fragment.requireContext(),
                    R.drawable.nowplaying_playlist_item_drawable
            ));
        }
        holder.itemContent.setPreloaded(entry.isPreloaded());
        holder.itemContent.setEntryID(entry.entryID);
        // TODO: Add the selection logic again. Not possible to select queue AND playlist entries.
        holder.item.setActivated(isSelected(holder.getKey()));
        holder.actionsView.setOnPlayListener(() -> {
            // TODO: probably change for playlistEntry.
            fragment.skipItems(position + 1);
            fragment.play();
        });
        holder.actionsView.setOnRemoveListener(() ->
                // TODO: Not supported for playlist.
                fragment.dequeue(entry)
        );
        holder.itemContent.setFetchState(fetchStateLiveData.getValue());
        holder.itemContent.setCached(cachedEntriesLiveData.getValue());
        holder.setEntryID(entry.entryID);
    }

    @Override
    public int getItemCount() {
        return queueEntries.size();
    }
}
