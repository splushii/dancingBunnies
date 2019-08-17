package se.splushii.dancingbunnies.ui.nowplaying;

import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingHistoryEntriesAdapter extends
        SelectionRecyclerViewAdapter<PlaybackEntry, NowPlayingHistoryEntriesAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingHistoryEntriesAdapter.class);

    private final NowPlayingFragment fragment;

    private List<PlaybackEntry> historyEntries;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;

    NowPlayingHistoryEntriesAdapter(NowPlayingFragment fragment) {
        this.fragment = fragment;
        historyEntries = new ArrayList<>();
        setHasStableIds(true);
    }

    void setModel(NowPlayingFragmentModel model) {
        cachedEntriesLiveData = model.getCachedEntries(fragment.getContext());
        fetchStateLiveData = model.getFetchState(fragment.getContext());
        PlaybackControllerStorage.getInstance(fragment.getContext())
                .getHistoryEntries()
                .observe(fragment.getViewLifecycleOwner(), this::setHistoryEntries);
    }

    void setHistoryEntries(List<PlaybackEntry> entries) {
        Log.d(LC, "setHistoryEntries: "
                + "curSize: " + historyEntries.size()
                + " newSize " + entries.size());
        boolean changed = !historyEntries.equals(entries);
        if (changed) {
            historyEntries = entries;
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
        if (pos < 0 || pos >= historyEntries.size()) {
            return null;
        }
        return historyEntries.get(pos);
    }

    @Override
    protected int getPosition(@NonNull PlaybackEntry key) {
        int index = historyEntries.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<PlaybackEntry> selection,
                                int targetPos,
                                PlaybackEntry idAfterTargetPos) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                       Collection<PlaybackEntry> selection) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onResetDragViewHolder(ViewHolder dragViewHolder) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean onActionItemClicked(int menuItemID, List<PlaybackEntry> selectionList) {
        switch (menuItemID) {
            case R.id.nowplaying_history_actionmode_action_queue:
                fragment.queue(selectionList.stream()
                        .map(playbackEntry -> playbackEntry.entryID)
                        .collect(Collectors.toList()),
                        null
                );
                return true;
            case R.id.nowplaying_history_actionmode_action_play:
                fragment.play(selectionList.stream()
                                .map(playbackEntry -> playbackEntry.entryID)
                                .collect(Collectors.toList()),
                        null
                );
                return true;
            case R.id.nowplaying_history_actionmode_action_delete:
                PlaybackControllerStorage.getInstance(fragment.getContext()).removeEntries(
                        PlaybackControllerStorage.QUEUE_ID_HISTORY,
                        selectionList
                );
                return true;
            default:
                return false;
        }
    }

    private void updateActionModeView(ActionMode actionMode, Selection<PlaybackEntry> selection) {
        actionMode.setTitle(selection.size() + " entries");
    }

    @Override
    public void onActionModeStarted(ActionMode actionMode, Selection<PlaybackEntry> selection) {
        updateActionModeView(actionMode, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionMode actionMode, Selection<PlaybackEntry> selection) {
        updateActionModeView(actionMode, selection);
    }

    @Override
    public void onActionModeEnding(ActionMode actionMode) {}

    @Override
    public boolean onDragInitiated(Selection<PlaybackEntry> selection) {
        return false;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return false;
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return false;
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        throw new RuntimeException("Not supported");
    }

    @Override
    protected void addItemToDataset(int pos, PlaybackEntry item) {
        throw new RuntimeException("Not supported");
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        throw new RuntimeException("Not supported");
    }

    void hideTrackItemActions() {
        if (selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    public class ViewHolder extends ItemDetailsViewHolder<PlaybackEntry> {
        private final View item;
        final TrackItemView itemContent;
        private final TrackItemActionsView actionsView;
        public PlaybackEntry playbackEntry;

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
            return historyEntries.get(getPositionOf());
        }
    }

    @Override
    public long getItemId(int position) {
        return historyEntries.get(position).playbackID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ViewHolder holder = new ViewHolder(layoutInflater.inflate(R.layout.nowplaying_queue_item, parent, false));
        holder.itemContent.initMetaObserver(fragment.requireContext());
        holder.itemContent.observeMeta(fragment.getViewLifecycleOwner());
        holder.itemContent.observeCachedLiveData(cachedEntriesLiveData, fragment.getViewLifecycleOwner());
        holder.itemContent.observeFetchStateLiveData(fetchStateLiveData, fragment.getViewLifecycleOwner());
        holder.actionsView.setOnInfoListener(() ->
                MetaDialogFragment.showMeta(fragment, holder.itemContent.getMeta())
        );
        holder.actionsView.setOnQueueListener(() -> fragment.queue(holder.playbackEntry.entryID));
        holder.actionsView.setOnPlayListener(() -> fragment.play(holder.playbackEntry.entryID));
        holder.actionsView.setOnRemoveListener(() ->
                PlaybackControllerStorage.getInstance(fragment.getContext()).removeEntries(
                        PlaybackControllerStorage.QUEUE_ID_HISTORY,
                        Collections.singletonList(holder.playbackEntry)
                )
        );
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        holder.actionsView.initialize();
        PlaybackEntry entry = historyEntries.get(position);
        holder.playbackEntry = entry;
        holder.item.setBackgroundResource(position % 2 == 1 ?
                R.color.white_active_accent : R.color.gray50_active_accent
        );
        holder.itemContent.setEntryID(entry.entryID);
        holder.itemContent.setPreloaded(entry.isPreloaded());
        holder.itemContent.setPos(position);
        holder.item.setActivated(isSelected(holder.getKey()));
    }

    @Override
    public int getItemCount() {
        return historyEntries.size();
    }
}
