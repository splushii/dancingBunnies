package se.splushii.dancingbunnies.ui.nowplaying;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.selection.Selection;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_REMOVE_FROM_HISTORY;

public class NowPlayingHistoryEntriesAdapter extends
        SmartDiffSelectionRecyclerViewAdapter
                <PlaybackEntry, NowPlayingHistoryEntriesAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingHistoryEntriesAdapter.class);

    private final NowPlayingFragment fragment;

    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;

    NowPlayingHistoryEntriesAdapter(NowPlayingFragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
        cachedEntriesLiveData = MusicLibraryService.getCachedEntries(fragment.requireContext());
        fetchStateLiveData = AudioStorage.getInstance(fragment.requireContext()).getFetchState();
        PlaybackControllerStorage.getInstance(fragment.requireContext())
                .getHistoryEntries()
                .observe(fragment.getViewLifecycleOwner(), this::setHistoryEntries);
    }

    private void setHistoryEntries(List<PlaybackEntry> entries) {
        Log.d(LC, "setHistoryEntries: "
                + "curSize: " + getSize()
                + " newSize " + entries.size());
        setDataSet(entries);
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection() && selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
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

    private void updateActionModeView(ActionModeCallback actionModeCallback,
                                      Selection<PlaybackEntry> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<PlaybackEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback, Selection<PlaybackEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {}

    @Override
    public boolean validDrag(Selection<PlaybackEntry> selection) {
        return false;
    }

    @Override
    public boolean validSelect(PlaybackEntry key) {
        return true;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return false;
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return false;
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
                actionsView.animateShow(actionsView.getVisibility() != View.VISIBLE);
            });
        }

        @Override
        protected PlaybackEntry getSelectionKeyOf() {
            return getItem(getPos());
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).playbackID;
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
        holder.actionsView.setAudioBrowser(fragment.getRemote());
        holder.actionsView.setFragmentManager(fragment.requireActivity().getSupportFragmentManager());
        holder.actionsView.setEntryIDSupplier(() -> holder.playbackEntry.entryID);
        holder.actionsView.setPlaybackEntrySupplier(() -> holder.playbackEntry);
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
                        ACTION_REMOVE_FROM_HISTORY,
                        ACTION_CACHE,
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                new int[0]
        );
        holder.actionsView.initialize();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        PlaybackEntry entry = getItem(position);
        holder.playbackEntry = entry;
        holder.item.setBackgroundResource(position % 2 == 0 ?
                R.color.background_active_accent : R.color.backgroundalternate_active_accent
        );
        holder.itemContent.setEntryID(entry.entryID);
        holder.itemContent.setPreloaded(entry.isPreloaded());
        holder.itemContent.setPos(position);
        holder.item.setActivated(isSelected(holder.getKey()));
    }
}
