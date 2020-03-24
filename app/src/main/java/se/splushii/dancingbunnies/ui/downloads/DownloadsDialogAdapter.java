package se.splushii.dancingbunnies.ui.downloads;

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
import androidx.lifecycle.LiveData;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.DownloadEntry;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;

public class DownloadsDialogAdapter extends
        SelectionRecyclerViewAdapter<DownloadEntry, DownloadsDialogAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(DownloadsDialogAdapter.class);

    private final DownloadsDialogFragment fragment;
    private final List<DownloadEntry> dataset;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;

    DownloadsDialogAdapter(DownloadsDialogFragment fragment) {
        this.fragment = fragment;
        dataset = new ArrayList<>();
        cachedEntriesLiveData = MusicLibraryService.getCachedEntries(fragment.getContext());
        fetchStateLiveData = AudioStorage.getInstance(fragment.getContext()).getFetchState();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.downloads_dialog_item, parent, false)
        );
        holder.itemContent.initMetaObserver(fragment.requireContext());
        holder.itemContent.observeMeta(fragment.getViewLifecycleOwner());
        holder.itemContent.observeCachedLiveData(cachedEntriesLiveData, fragment.getViewLifecycleOwner());
        holder.itemContent.observeFetchStateLiveData(fetchStateLiveData, fragment.getViewLifecycleOwner());
        holder.actionsView.setAudioBrowser(AudioBrowser.getInstance(fragment.getActivity()));
        holder.actionsView.setFragmentManager(fragment.requireActivity().getSupportFragmentManager());
        holder.actionsView.setEntryIDSupplier(holder.itemContent::getEntryID);
        holder.actionsView.initialize();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadEntry downloadEntry = dataset.get(position);
        EntryID entryID = downloadEntry.entryID;
        holder.item.setBackgroundResource(
                position % 2 == 0
                        ? R.color.background_active_accent
                        : R.color.backgroundalternate_active_accent
        );
        holder.itemContent.setEntryID(entryID);
        holder.itemContent.setDragHandleListener(position == 0 ? null : () -> startDrag(holder));
        holder.item.setActivated(isSelected(holder.getKey()));
        holder.actionsView.setActions(
                new int[] {
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                new int[] {
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                new int[0]
        );
        if (isDragViewHolder(holder)) {
            onUseViewHolderForDrag(holder, getSelection());
        }
    }

    void hideTrackItemActions() {
        if (selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    void setDataset(List<DownloadEntry> downloads) {
        Util.Diff diff = Util.calculateDiff(dataset, downloads);
        if (diff.changed) {
            if (hasSelection()) {
                removeSelection(diff.deleted.stream().map(dataset::get).collect(Collectors.toList()));
            }
            dataset.clear();
            dataset.addAll(downloads);
            notifyDataSetChanged();
            recalculateSelection();
        }
    }

    void clearAll() {
        for (DownloadEntry downloadEntry: dataset) {
            AudioStorage.getInstance(fragment.requireContext())
                    .deleteAudioData(fragment.requireContext(), downloadEntry.entryID);
        }
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        dataset.add(to, dataset.remove(from));
    }

    @Override
    protected void addItemToDataset(int pos, DownloadEntry item) {
        dataset.add(pos, item);
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        dataset.remove(pos);
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection() && selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    @Override
    protected DownloadEntry getKey(int pos) {
        if (pos < 0 || pos >= dataset.size()) {
            return null;
        }
        return dataset.get(pos);
    }

    @Override
    protected int getPosition(@NonNull DownloadEntry key) {
        int index = dataset.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<DownloadEntry> selection,
                                int targetPos,
                                DownloadEntry idAfterTargetPos) {
        AudioStorage.getInstance(fragment.requireContext()).moveDownloads(
                fragment.requireContext(),
                new ArrayList<>(selection),
                idAfterTargetPos
        );
    }

    @Override
    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                       Collection<DownloadEntry> selection) {
        if (selection.size() == 1) {
            dragViewHolder.itemContent.resetFromDrag();
            return;
        }
        dragViewHolder.itemContent.useForDrag(selection.size() + " entries");
    }

    @Override
    public void onResetDragViewHolder(ViewHolder dragViewHolder) {
        dragViewHolder.itemContent.resetFromDrag();
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<DownloadEntry> selection) {
        fragment.onActionModeStarted(selection.size() + " entries");
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<DownloadEntry> selection) {
        fragment.onActionModeUpdated(selection.size() + " entries");
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {
        fragment.onActionModeEnding();
    }

    @Override
    public boolean validDrag(Selection<DownloadEntry> selection) {
        return true;
    }

    @Override
    public boolean validSelect(DownloadEntry key) {
        return getPosition(key) != 0;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return target.getPos() != 0;
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return viewHolder.getPos() != 0;
    }

    public class ViewHolder extends ItemDetailsViewHolder<DownloadEntry> {
        private final View item;
        final TrackItemView itemContent;
        final TrackItemActionsView actionsView;

        ViewHolder(@NonNull View v) {
            super(v);
            item = v.findViewById(R.id.downloads_entry);
            itemContent = v.findViewById(R.id.downloads_entry_content);
            actionsView = v.findViewById(R.id.downloads_entry_actions);
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
        protected DownloadEntry getSelectionKeyOf() {
            return dataset.get(getPos());
        }
    }

    @Override
    public long getItemId(int position) {
        return dataset.get(position).hashCode();
    }
}
