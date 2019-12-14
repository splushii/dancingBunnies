package se.splushii.dancingbunnies.ui.nowplaying;

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
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_REMOVE_FROM_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_REMOVE_MULTIPLE_FROM_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_SET_CURRENT_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_SHUFFLE_MULTIPLE_IN_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_SORT_MULTIPLE_IN_QUEUE;

public class NowPlayingEntriesAdapter extends
        SelectionRecyclerViewAdapter<PlaybackEntry, NowPlayingEntriesAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);

    // Use to debug. Maybe put in settings in the future.
    private static final boolean SHOW_PLAYLIST_ENTRIES = true;

    private final NowPlayingFragment fragment;

    private List<PlaybackEntry> queueEntries;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;
    private LiveData<NowPlayingState> nowPlayingStateLiveData;

    NowPlayingEntriesAdapter(NowPlayingFragment fragment) {
        this.fragment = fragment;
        queueEntries = new ArrayList<>();
        setHasStableIds(true);
    }

    void setModel(NowPlayingFragmentModel model) {
        cachedEntriesLiveData = MusicLibraryService.getCachedEntries(fragment.getContext());
        fetchStateLiveData = AudioStorage.getInstance(fragment.getContext()).getFetchState();
        nowPlayingStateLiveData = model.getState();
    }

    void setQueueEntries(List<PlaybackEntry> queueEntries) {
        List<PlaybackEntry> entries = SHOW_PLAYLIST_ENTRIES ? queueEntries :
                queueEntries.stream()
                        .filter(p -> !PlaybackEntry.USER_TYPE_PLAYLIST.equals(p.playbackType))
                        .collect(Collectors.toList());
        boolean changed = !this.queueEntries.equals(entries);
        this.queueEntries = entries;
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
        if (pos < 0 || pos >= queueEntries.size()) {
            return null;
        }
        return queueEntries.get(pos);
    }

    @Override
    protected int getPosition(@NonNull PlaybackEntry key) {
        int index = queueEntries.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<PlaybackEntry> selection,
                                int targetPos,
                                PlaybackEntry idAfterTargetPos) {
        long beforePlaybackID = idAfterTargetPos == null ?
                PlaybackEntry.PLAYBACK_ID_INVALID : idAfterTargetPos.playbackID;
        fragment.moveQueueItems(new ArrayList<>(selection), beforePlaybackID);
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

    private void updateActionModeView(ActionModeCallback actionModeCallback, Selection<PlaybackEntry> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
        boolean containsPlaylistEntries = false;
        for (PlaybackEntry entry: selection) {
            if (PlaybackEntry.USER_TYPE_PLAYLIST.equals(entry.playbackType)) {
                containsPlaylistEntries = true;
                break;
            }
        }
        int[] disabled = containsPlaylistEntries ?
                new int[] {
                        ACTION_REMOVE_MULTIPLE_FROM_QUEUE,
                        ACTION_SHUFFLE_MULTIPLE_IN_QUEUE,
                        ACTION_SORT_MULTIPLE_IN_QUEUE
                } : new int[0];
        actionModeCallback.setActions(
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_PLAYLIST
                },
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_QUEUE,
                        ACTION_SHUFFLE_MULTIPLE_IN_QUEUE,
                        ACTION_SORT_MULTIPLE_IN_QUEUE,
                        ACTION_ADD_MULTIPLE_TO_PLAYLIST,
                        ACTION_REMOVE_MULTIPLE_FROM_QUEUE,
                        ACTION_CACHE_MULTIPLE,
                        ACTION_CACHE_DELETE_MULTIPLE
                },
                disabled
        );
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<PlaybackEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<PlaybackEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {}

    @Override
    public boolean onDragInitiated(Selection<PlaybackEntry> selection) {
        for (PlaybackEntry entry: selection) {
            if (!PlaybackEntry.USER_TYPE_QUEUE.equals(entry.playbackType)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return PlaybackEntry.USER_TYPE_QUEUE.equals(target.playbackEntry.playbackType);
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return PlaybackEntry.USER_TYPE_QUEUE.equals(viewHolder.playbackEntry.playbackType);
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
            return queueEntries.get(getPositionOf());
        }

        void updateHighlight(NowPlayingState state) {
            itemContent.setPosHighlight(state != null
                    && isFirstPlaylistEntry(getPositionOf())
                    && playbackEntry != null
                    && PlaybackEntry.USER_TYPE_PLAYLIST.equals(playbackEntry.playbackType)
                    && state.currentPlaylistPos == playbackEntry.playlistPos);
        }
    }

    @Override
    public long getItemId(int position) {
        return queueEntries.get(position).playbackID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.nowplaying_queue_item, parent, false)
        );
        nowPlayingStateLiveData.observe(fragment.getViewLifecycleOwner(), holder::updateHighlight);
        holder.itemContent.initMetaObserver(fragment.requireContext());
        holder.itemContent.observeMeta(fragment.getViewLifecycleOwner());
        holder.itemContent.observeCachedLiveData(cachedEntriesLiveData, fragment.getViewLifecycleOwner());
        holder.itemContent.observeFetchStateLiveData(fetchStateLiveData, fragment.getViewLifecycleOwner());
        holder.actionsView.setAudioBrowserFragment(fragment);
        holder.actionsView.setEntryIDSupplier(() -> holder.playbackEntry.entryID);
        holder.actionsView.setPlaybackEntrySupplier(() -> holder.playbackEntry);
        holder.actionsView.setPlaylistPositionSupplier(() -> holder.playbackEntry.playlistPos);
        holder.actionsView.setPlaylistIDSupplier(fragment::getCurrentPlaylist);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        holder.actionsView.initialize();
        PlaybackEntry entry = queueEntries.get(position);
        holder.playbackEntry = entry;
        boolean isQueueEntry = PlaybackEntry.USER_TYPE_QUEUE.equals(entry.playbackType);
        holder.item.setBackgroundResource(position % 2 == 1 ?
                R.color.white_active_accent : R.color.gray50_active_accent
        );
        int[] disabledActions;
        if (isQueueEntry) {
            holder.itemContent.resetPos();
            disabledActions = new int[] { ACTION_SET_CURRENT_PLAYLIST };
            holder.updateHighlight(null);
        } else {
            holder.itemContent.setPos(entry.playlistPos);
            disabledActions = new int[] { ACTION_REMOVE_FROM_QUEUE };
            holder.updateHighlight(nowPlayingStateLiveData.getValue());
        }
        holder.itemContent.setEntryID(entry.entryID);
        holder.itemContent.setPreloaded(entry.isPreloaded());
        holder.item.setActivated(isSelected(holder.getKey()));
        holder.actionsView.setActions(
                new int[] {
                        ACTION_PLAY,
                        ACTION_ADD_TO_PLAYLIST,
                        ACTION_INFO
                },
                new int[] {
                        ACTION_PLAY,
                        ACTION_SET_CURRENT_PLAYLIST,
                        ACTION_ADD_TO_QUEUE,
                        ACTION_ADD_TO_PLAYLIST,
                        ACTION_REMOVE_FROM_QUEUE,
                        ACTION_CACHE,
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                disabledActions
        );
    }

    private boolean isFirstPlaylistEntry(int position) {
        for (int i = 0; i < queueEntries.size() && i <= position; i++) {
            PlaybackEntry entry = queueEntries.get(i);
            if (PlaybackEntry.USER_TYPE_PLAYLIST.equals(entry.playbackType)) {
                if (i == position) {
                    return true;
                } else {
                    break;
                }
            }
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return queueEntries.size();
    }
}
