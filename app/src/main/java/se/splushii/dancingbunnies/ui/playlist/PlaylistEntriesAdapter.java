package se.splushii.dancingbunnies.ui.playlist;

import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_ENTRY_DELETE;
import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_ENTRY_MOVE;

public class PlaylistEntriesAdapter extends SelectionRecyclerViewAdapter<PlaylistEntry, PlaylistEntriesAdapter.PlaylistEntryHolder> {
    private static final String LC = Util.getLogContext(PlaylistEntriesAdapter.class);
    private final PlaylistFragment fragment;
    private final PlaylistStorage playlistStorage;

    private PlaylistID playlistID;
    private List<PlaylistEntry> playlistEntriesDataset;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;
    private LiveData<PlaybackEntry> currentPlaylistEntryLiveData;
    private LiveData<PlaylistID> currentPlaylistIDLiveData;
    private LiveData<PlaybackEntry> currentEntryLiveData;

    PlaylistEntriesAdapter(PlaylistFragment playlistFragment) {
        fragment = playlistFragment;
        playlistEntriesDataset = new LinkedList<>();
        playlistStorage = PlaylistStorage.getInstance(fragment.getContext());
        setHasStableIds(true);
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection() && selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    @Override
    protected PlaylistEntry getKey(int pos) {
        if (pos < 0 || pos >= playlistEntriesDataset.size()) {
            return null;
        }
        return playlistEntriesDataset.get(pos);
    }

    @Override
    protected int getPosition(@NonNull PlaylistEntry key) {
        int index = playlistEntriesDataset.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<PlaylistEntry> selection,
                                int targetPos,
                                PlaylistEntry idAfterTargetPos) {
        playlistStorage.movePlaylistEntries(playlistID, selection, targetPos);
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistEntryHolder dragViewHolder,
                                       Collection<PlaylistEntry> selection) {
        dragViewHolder.itemContent.setDragTitle(selection.size() + " entries");
    }

    @Override
    public void onResetDragViewHolder(PlaylistEntryHolder dragViewHolder) {
        dragViewHolder.itemContent.reset();
    }

    @Override
    public boolean onActionItemClicked(int menuItemID, List<PlaylistEntry> selectionList) {
        switch (menuItemID) {
            case R.id.playlist_entries_actionmode_action_delete:
                playlistStorage.removeFromPlaylist(playlistID, selectionList);
                return true;
            case R.id.playlist_entries_actionmode_action_play_now:
                fragment.play(selectionList.stream()
                        .map(EntryID::from)
                        .collect(Collectors.toList()));
                return true;
            case R.id.playlist_entries_actionmode_action_queue:
                fragment.queue(selectionList.stream()
                        .map(EntryID::from)
                        .collect(Collectors.toList())
                );
                return true;
            default:
                return false;
        }
    }

    private void updateActionModeView(ActionMode actionMode, Selection<PlaylistEntry> selection) {
        actionMode.setTitle(selection.size() + " entries");
        boolean showDelete = MusicLibraryService.checkAPISupport(
                playlistID.src,
                PLAYLIST_ENTRY_DELETE
        );
        actionMode.getMenu().findItem(R.id.playlist_entries_actionmode_action_delete)
                .setVisible(showDelete);
    }

    @Override
    public void onActionModeStarted(ActionMode actionMode, Selection<PlaylistEntry> selection) {
        updateActionModeView(actionMode, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionMode actionMode, Selection<PlaylistEntry> selection) {
        updateActionModeView(actionMode, selection);
    }

    @Override
    public void onActionModeEnding(ActionMode actionMode) {}

    @Override
    public boolean onDragInitiated(Selection<PlaylistEntry> selection) {
        return MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_MOVE);
    }

    @Override
    public boolean validMove(PlaylistEntryHolder current, PlaylistEntryHolder target) {
        return true;
    }

    @Override
    public boolean validDrag(PlaylistEntryHolder viewHolder) {
        return true;
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        playlistEntriesDataset.add(to, playlistEntriesDataset.remove(from));
    }

    @Override
    protected void addItemToDataset(int pos, PlaylistEntry item) {
        playlistEntriesDataset.add(pos, item);
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        playlistEntriesDataset.remove(pos);
    }

    @NonNull
    @Override
    public PlaylistEntryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        PlaylistEntryHolder holder = new PlaylistEntryHolder(
                layoutInflater.inflate(R.layout.playlist_entry_item, parent, false)
        );
        cachedEntriesLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder.itemContent::setCached
        );
        fetchStateLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder.itemContent::setFetchState
        );
        currentPlaylistEntryLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentPlaylistEntry -> holder.updateHighlight(
                        currentEntryLiveData.getValue(),
                        currentPlaylistEntry,
                        currentPlaylistIDLiveData.getValue()
                )
        );
        currentPlaylistIDLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentPlaylistID -> holder.updateHighlight(
                        currentEntryLiveData.getValue(),
                        currentPlaylistEntryLiveData.getValue(),
                        currentPlaylistID
                )
        );
        currentEntryLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentEntry -> holder.updateHighlight(
                        currentEntry,
                        currentPlaylistEntryLiveData.getValue(),
                        currentPlaylistIDLiveData.getValue()
                )
        );
        holder.initMetaObserver(fragment.requireContext());
        holder.observeMeta(fragment.getViewLifecycleOwner(), holder::setMeta);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistEntryHolder holder, int position) {
        holder.actionsView.initialize();
        holder.position = position;
        PlaylistEntry playlistEntry = playlistEntriesDataset.get(position);
        holder.playlistEntry = playlistEntry;
        EntryID entryID = EntryID.from(playlistEntry);
        holder.itemContent.setEntryID(entryID);
        holder.entry.setActivated(isSelected(holder.getKey()));
        holder.itemContent.setCached(cachedEntriesLiveData.getValue());
        holder.itemContent.setFetchState(fetchStateLiveData.getValue());
        holder.updateHighlight(
                currentEntryLiveData.getValue(),
                currentPlaylistEntryLiveData.getValue(),
                currentPlaylistIDLiveData.getValue()
        );
        holder.itemContent.setPos(playlistEntry.pos);
        holder.setEntryID(entryID);
        if (MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_DELETE)
                && PlaylistID.TYPE_STUPID.equals(playlistID.type)) {
            holder.actionsView.setOnRemoveListener(() ->
                    playlistStorage.removeFromPlaylist(
                            playlistID,
                            Collections.singletonList(playlistEntry)
                    )
            );
        } else {
            holder.actionsView.setOnRemoveListener(null);
        }
        holder.actionsView.setOnPlayListener(() -> fragment.play(entryID));
        holder.actionsView.setOnQueueListener(() -> fragment.queue(entryID));
        holder.actionsView.setOnPlayPlaylistListener(() -> fragment.setCurrentPlaylist(
                playlistID,
                playlistEntry.pos
        ));
    }

    @Override
    public int getItemCount() {
        return playlistEntriesDataset.size();
    }

    private void setPlaylistID(PlaylistID id) {
        playlistEntriesDataset = Collections.emptyList();
        playlistID = id;
        notifyDataSetChanged();
    }

    private void setDataSet(List<PlaylistEntry> entries) {
        playlistEntriesDataset = entries;
        notifyDataSetChanged();
    }

    void setModel(PlaylistFragmentModel model) {
        Transformations.switchMap(model.getUserState(), playlistUserState -> {
            if (playlistUserState.showPlaylists) {
                setPlaylistID(null);
                return new MutableLiveData<>();
            }
            PlaylistID playlistID = playlistUserState.browsedPlaylistID;
            Log.d(LC, "New playlistID: " + playlistID);
            setPlaylistID(playlistID);
            return model.getPlaylistEntries(playlistID, fragment.getContext());
        }).observe(fragment.getViewLifecycleOwner(), entries -> {
            setDataSet(entries);
            int pos = model.getUserStateValue().pos;
            int pad = model.getUserStateValue().pad;
            fragment.scrollPlaylistEntriesTo(pos, pad);
        });
        cachedEntriesLiveData = model.getCachedEntries(fragment.getContext());
        fetchStateLiveData = model.getFetchState(fragment.getContext());
        currentPlaylistEntryLiveData = model.getCurrentPlaylistEntry();
        currentPlaylistIDLiveData = model.getCurrentPlaylistID();
        currentEntryLiveData = model.getCurrentEntry();
    }

    @Override
    public long getItemId(int position) {
        return playlistEntriesDataset.get(position).rowId;
    }

    void hideTrackItemActions() {
        if (selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    public class PlaylistEntryHolder extends ItemDetailsViewHolder<PlaylistEntry> {
        private final View entry;
        private final TrackItemView itemContent;
        private final TrackItemActionsView actionsView;
        public int position;
        public PlaylistEntry playlistEntry;

        PlaylistEntryHolder(View v) {
            super(v);
            entry = v.findViewById(R.id.playlist_entry);
            itemContent = v.findViewById(R.id.playlist_entry_content);
            actionsView = v.findViewById(R.id.playlist_entry_actions);
            entry.setOnClickListener(view -> {
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
        protected PlaylistEntry getSelectionKeyOf() {
            return playlistEntriesDataset.get(getPositionOf());
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

        void updateHighlight(PlaybackEntry currentEntry,
                             PlaybackEntry currentPlaylistEntry,
                             PlaylistID currentPlaylistID) {
            boolean isCurrentPlaylist = playlistID != null
                    && playlistID.equals(currentPlaylistID);
            boolean isCurrentEntry = playlistEntry != null
                    && currentEntry != null
                    && EntryID.from(playlistEntry).equals(currentEntry.entryID);
            boolean isCurrentPlaylistEntry = playlistEntry != null
                    && currentPlaylistEntry != null
                    && playlistEntry.pos == currentPlaylistEntry.playlistPos;
            itemContent.setPosHighlight(isCurrentPlaylist && isCurrentPlaylistEntry);
            itemContent.setItemHighlight(isCurrentEntry);
        }
    }
}
