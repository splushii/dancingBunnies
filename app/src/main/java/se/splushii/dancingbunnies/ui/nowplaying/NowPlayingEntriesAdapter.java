package se.splushii.dancingbunnies.ui.nowplaying;

import android.util.Log;
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
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_SET_CURRENT;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_SHUFFLE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_SORT_MULTIPLE;

public class NowPlayingEntriesAdapter extends
        SmartDiffSelectionRecyclerViewAdapter<PlaybackEntry, NowPlayingEntriesAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);

    // Use to debug. Maybe put in settings in the future.
    private static final boolean SHOW_PLAYLIST_ENTRIES = true;

    private final NowPlayingFragment fragment;

    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;
    private LiveData<NowPlayingState> nowPlayingStateLiveData;

    NowPlayingEntriesAdapter(NowPlayingFragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
    }

    void setModel(NowPlayingFragmentModel model) {
        cachedEntriesLiveData = MusicLibraryService.getCachedEntries(fragment.getContext());
        fetchStateLiveData = AudioStorage.getInstance(fragment.getContext()).getFetchState();
        nowPlayingStateLiveData = model.getState();
    }

    void setQueueEntries(List<PlaybackEntry> queueEntries) {
        Log.d(LC, "setQueueEntries: "
                + "curSize: " + getSize()
                + " newSize " + queueEntries.size());
        List<PlaybackEntry> entries = SHOW_PLAYLIST_ENTRIES ? queueEntries :
                queueEntries.stream()
                        .filter(p -> !PlaybackEntry.USER_TYPE_PLAYLIST.equals(p.playbackType))
                        .collect(Collectors.toList());
        setDataSet(entries, PlaybackEntry::equalsContent);
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
        long beforePlaybackID = idAfterTargetPos == null ?
                PlaybackEntry.PLAYBACK_ID_INVALID : idAfterTargetPos.playbackID;
        fragment.getRemote().moveQueueItems(new ArrayList<>(selection), beforePlaybackID);
    }

    @Override
    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                       Collection<PlaybackEntry> selection) {
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
                        ACTION_QUEUE_DELETE_MULTIPLE,
                        ACTION_QUEUE_SHUFFLE_MULTIPLE,
                        ACTION_QUEUE_SORT_MULTIPLE
                } : new int[0];
        actionModeCallback.setActions(
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE
                },
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_QUEUE_ADD_MULTIPLE,
                        ACTION_QUEUE_SHUFFLE_MULTIPLE,
                        ACTION_QUEUE_SORT_MULTIPLE,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE,
                        ACTION_QUEUE_DELETE_MULTIPLE,
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
    public boolean validDrag(Selection<PlaybackEntry> selection) {
        for (PlaybackEntry entry: selection) {
            if (!PlaybackEntry.USER_TYPE_QUEUE.equals(entry.playbackType)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean validSelect(PlaybackEntry key) {
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

        void updateHighlight(NowPlayingState state) {
            itemContent.setPosHighlight(state != null
                    && isFirstPlaylistEntry(getPos())
                    && playbackEntry != null
                    && PlaybackEntry.USER_TYPE_PLAYLIST.equals(playbackEntry.playbackType)
                    && state.currentPlaylistPos == playbackEntry.playlistPos);
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).playbackID;
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
        holder.itemContent.setDragHandleListener(() -> startDrag(holder));
        holder.actionsView.setAudioBrowser(fragment.getRemote());
        holder.actionsView.setFragmentManager(fragment.requireActivity().getSupportFragmentManager());
        holder.actionsView.setEntryIDSupplier(() -> holder.playbackEntry.entryID);
        holder.actionsView.setPlaybackEntrySupplier(() -> holder.playbackEntry);
        holder.actionsView.setPlaylistPositionSupplier(() -> holder.playbackEntry.playlistPos);
        holder.actionsView.setPlaylistIDSupplier(() -> fragment.getRemote().getCurrentPlaylist());
        holder.actionsView.initialize();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        PlaybackEntry entry = getItem(position);
        holder.playbackEntry = entry;
        boolean isQueueEntry = PlaybackEntry.USER_TYPE_QUEUE.equals(entry.playbackType);
        holder.item.setBackgroundResource(position % 2 == 0 ?
                R.color.background_active_accent : R.color.backgroundalternate_active_accent
        );
        int[] disabledActions;
        if (isQueueEntry) {
            holder.itemContent.setPos(-1);
            disabledActions = new int[] {ACTION_PLAYLIST_SET_CURRENT};
            holder.updateHighlight(null);
            holder.itemContent.setDragHandleListener(() -> startDrag(holder));
        } else {
            holder.itemContent.setPos(entry.playlistPos);
            disabledActions = new int[] {ACTION_QUEUE_DELETE};
            holder.updateHighlight(nowPlayingStateLiveData.getValue());
            holder.itemContent.setDragHandleListener(null);
        }
        holder.itemContent.setEntryID(entry.entryID);
        holder.itemContent.setPreloaded(entry.isPreloaded());
        holder.item.setActivated(isSelected(holder.getKey()));
        holder.actionsView.setActions(
                new int[] {
                        ACTION_PLAY,
                        ACTION_PLAYLIST_ENTRY_ADD,
                        ACTION_INFO
                },
                new int[] {
                        ACTION_PLAY,
                        ACTION_PLAYLIST_SET_CURRENT,
                        ACTION_QUEUE_ADD,
                        ACTION_PLAYLIST_ENTRY_ADD,
                        ACTION_QUEUE_DELETE,
                        ACTION_CACHE,
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                disabledActions
        );
        if (isDragViewHolder(holder)) {
            onUseViewHolderForDrag(holder, getSelection());
        }
    }

    private boolean isFirstPlaylistEntry(int position) {
        for (int i = 0; i < getSize() && i <= position; i++) {
            PlaybackEntry entry = getItem(i);
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
}
