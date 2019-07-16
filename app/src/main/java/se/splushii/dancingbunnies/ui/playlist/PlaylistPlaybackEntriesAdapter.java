package se.splushii.dancingbunnies.ui.playlist;

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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistPlaybackEntriesAdapter extends
        SelectionRecyclerViewAdapter<PlaybackEntry, PlaylistPlaybackEntriesAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(PlaylistPlaybackEntriesAdapter.class);

    private final PlaylistFragment fragment;

    private List<PlaybackEntry> entries;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;
    private LiveData<Long> currentPlaylistPosLiveData;
    private MutableLiveData<PlaylistID> browsedPlaylistIDLiveData;
    private LiveData<PlaylistID> currentPlaylistIDLiveData;
    private LiveData<PlaybackEntry> currentEntryLiveData;

    private boolean initialScrolled;

    PlaylistPlaybackEntriesAdapter(PlaylistFragment fragment) {
        this.fragment = fragment;
        entries = new ArrayList<>();
        setHasStableIds(true);
    }

    void setModel(PlaylistFragmentModel model) {
        cachedEntriesLiveData = model.getCachedEntries(fragment.getContext());
        fetchStateLiveData = model.getFetchState(fragment.getContext());
        currentPlaylistPosLiveData = model.getCurrentPlaylistPos();
        currentPlaylistIDLiveData = model.getCurrentPlaylistID();
        currentEntryLiveData = model.getCurrentEntry();
        initialScrolled = false;
        PlaybackControllerStorage.getInstance(fragment.getContext())
                .getCurrentPlaylistPlaybackEntries()
                .observe(fragment.getViewLifecycleOwner(), entries -> {
                    setEntries(entries);
                    updateScrollPos(model);
                });
        browsedPlaylistIDLiveData = new MutableLiveData<>();
        model.getUserState().observe(fragment.getViewLifecycleOwner(), state -> {
            browsedPlaylistIDLiveData.setValue(state.browsedPlaylistID);
            updateScrollPos(model);
        });
    }

    void setEntries(List<PlaybackEntry> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    private void updateScrollPos(PlaylistFragmentModel model) {
        PlaylistUserState userState = model.getUserStateValue();
        if (userState.scrollPlaylistPlaybackToPlaylistPos) {
            model.unsetScrollPlaylistPlaybackToPlaylistPos();
            for (int i = 0; i < entries.size(); i++) {
                PlaybackEntry entry = entries.get(i);
                if (entry.playlistPos == userState.playlistPlaybackEntriesPos) {
                    fragment.scrollPlaylistPlaybackEntriesTo(i, 0);
                    break;
                }
            }
        } else if (!initialScrolled && !entries.isEmpty()) {
            initialScrolled = true;
            fragment.scrollPlaylistPlaybackEntriesTo(
                    userState.playlistPlaybackEntriesPos,
                    userState.playlistPlaybackEntriesPad
            );
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
        if (pos < 0 || pos >= entries.size()) {
            return null;
        }
        return entries.get(pos);
    }

    @Override
    protected int getPosition(@NonNull PlaybackEntry key) {
        int index = entries.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<PlaybackEntry> selection,
                                int targetPos,
                                PlaybackEntry idAfterTargetPos) {
        long beforePlaybackID = idAfterTargetPos == null ?
                PlaybackEntry.PLAYBACK_ID_INVALID : idAfterTargetPos.playbackID;
        PlaybackControllerStorage.getInstance(fragment.getContext()).move(
                PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK,
                beforePlaybackID,
                new ArrayList<>(selection)
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
            case R.id.playlist_playback_entries_actionmode_action_queue:
                fragment.queue(selectionList.stream()
                        .map(playbackEntry -> playbackEntry.entryID)
                        .collect(Collectors.toList())
                );
                return true;
            case R.id.playlist_playback_entries_actionmode_action_play_now:
                fragment.play(selectionList.stream()
                        .map(playbackEntry -> playbackEntry.entryID)
                        .collect(Collectors.toList())
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
        return true;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return true;
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return true;
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        entries.add(to, entries.remove(from));
    }

    @Override
    protected void addItemToDataset(int pos, PlaybackEntry item) {
        entries.add(pos, item);
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        entries.remove(pos);
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
            item = v.findViewById(R.id.playlist_entry);
            itemContent = v.findViewById(R.id.playlist_entry_content);
            actionsView = v.findViewById(R.id.playlist_entry_actions);
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
            return entries.get(getPositionOf());
        }

        public void setMeta(Meta meta) {
            if (meta == null) {
                actionsView.setOnInfoListener(null);
                itemContent.setTitle("");
                itemContent.setArtist("");
                itemContent.setSource("");
                return;
            }
            actionsView.setOnInfoListener(() -> MetaDialogFragment.showMeta(fragment, meta));
            String title = meta.getAsString(Meta.FIELD_TITLE);
            itemContent.setTitle(title);
            String artist = meta.getAsString(Meta.FIELD_ARTIST);
            itemContent.setArtist(artist);
            String src = meta.entryID.src;
            itemContent.setSource(src);
        }

        void updateHighlight(PlaylistID browsedPlaylistID,
                             PlaylistID currentPlaylistID,
                             PlaybackEntry currentEntry,
                             Long currentPlaylistPos) {
            boolean isCurrentPlaylist = browsedPlaylistID != null
                    && browsedPlaylistID.equals(currentPlaylistID);
            boolean isCurrentEntry = playbackEntry != null
                    && currentEntry != null
                    && playbackEntry.entryID.equals(currentEntry.entryID);
            boolean isCurrentPlaylistEntry = playbackEntry != null
                    && currentPlaylistPos != null
                    && playbackEntry.playlistPos == currentPlaylistPos;
            itemContent.setPosHighlight(isCurrentPlaylist && isCurrentPlaylistEntry);
            itemContent.setItemHighlight(isCurrentEntry);
        }
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).playbackID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ViewHolder holder = new ViewHolder(layoutInflater.inflate(
                R.layout.playlist_entry_item,
                parent,
                false
        ));
        cachedEntriesLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder.itemContent::setCached
        );
        fetchStateLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder.itemContent::setFetchState
        );
        currentPlaylistPosLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentPlaylistPos -> holder.updateHighlight(
                        browsedPlaylistIDLiveData.getValue(),
                        currentPlaylistIDLiveData.getValue(),
                        currentEntryLiveData.getValue(),
                        currentPlaylistPos
                )
        );
        currentEntryLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentEntry -> holder.updateHighlight(
                        browsedPlaylistIDLiveData.getValue(),
                        currentPlaylistIDLiveData.getValue(),
                        currentEntry,
                        currentPlaylistPosLiveData.getValue()
                )
        );
        currentPlaylistIDLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentPlaylistID -> holder.updateHighlight(
                        browsedPlaylistIDLiveData.getValue(),
                        currentPlaylistID,
                        currentEntryLiveData.getValue(),
                        currentPlaylistPosLiveData.getValue()
                )
        );
        browsedPlaylistIDLiveData.observe(
                fragment.getViewLifecycleOwner(),
                browsedPlaylistID -> holder.updateHighlight(
                        browsedPlaylistID,
                        currentPlaylistIDLiveData.getValue(),
                        currentEntryLiveData.getValue(),
                        currentPlaylistPosLiveData.getValue()
                )
        );

        holder.initMetaObserver(fragment.requireContext());
        holder.observeMeta(fragment.getViewLifecycleOwner(), holder::setMeta);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        holder.actionsView.initialize();
        PlaybackEntry entry = entries.get(position);
        holder.playbackEntry = entry;
        holder.itemContent.setPos(entry.playlistPos);
        holder.actionsView.setOnPlayListener(() -> fragment.play(entry.entryID));
        holder.actionsView.setOnQueueListener(() -> fragment.queue(entry.entryID));
        holder.actionsView.setOnPlayPlaylistListener(() -> fragment.setCurrentPlaylist(
                currentPlaylistIDLiveData.getValue(),
                entry.playlistPos
        ));
        holder.updateHighlight(
                browsedPlaylistIDLiveData.getValue(),
                currentPlaylistIDLiveData.getValue(),
                currentEntryLiveData.getValue(),
                currentPlaylistPosLiveData.getValue()
        );
        holder.itemContent.setPreloaded(entry.isPreloaded());
        holder.itemContent.setEntryID(entry.entryID);
        holder.item.setActivated(isSelected(holder.getKey()));
        holder.itemContent.setFetchState(fetchStateLiveData.getValue());
        holder.itemContent.setCached(cachedEntriesLiveData.getValue());
        holder.setEntryID(entry.entryID);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }
}
