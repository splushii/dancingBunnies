package se.splushii.dancingbunnies.ui.playlist;

import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.MetaStorage;
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
    private LiveData<List<PlaylistEntry>> playlistEntries;
    private List<PlaylistEntry> playlistEntriesDataset;

    private TrackItemActionsView selectedActionView;
    private PlaylistFragmentModel model;

    PlaylistEntriesAdapter(PlaylistFragment playlistFragment) {
        fragment = playlistFragment;
        playlistEntriesDataset = new LinkedList<>();
        playlistStorage = PlaylistStorage.getInstance(fragment.getContext());
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
        return playlistEntriesDataset.get(pos);
    }

    @Override
    protected int getPosition(@NonNull PlaylistEntry key) {
        int index = playlistEntriesDataset.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(List<PlaylistEntry> selection, int lastDragPos) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistEntryHolder dragViewHolder, List<PlaylistEntry> selection) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onResetDragViewHolder(PlaylistEntryHolder dragViewHolder) {
        throw new RuntimeException("Not implemented");
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
                fragment.queue(
                        selectionList.stream()
                                .map(EntryID::from)
                                .collect(Collectors.toList()),
                        AudioPlayerService.QUEUE_LAST
                );
                return true;
            default:
                return false;
        }
    }

    private void updateActionModeView(ActionMode actionMode, Selection<PlaylistEntry> selection) {
        actionMode.setTitle(selection.size() + " entries");
        boolean showDelete = true;
        for (PlaylistEntry entry: selection) {
            if (!MusicLibraryService.checkAPISupport(entry.api, PLAYLIST_ENTRY_DELETE)) {
                showDelete = false;
            }
        }
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
        for (PlaylistEntry entry: selection) {
            if (!MusicLibraryService.checkAPISupport(entry.api, PLAYLIST_ENTRY_MOVE)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    protected void addItemToDataset(int pos, PlaylistEntry item) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    @Override
    public PlaylistEntryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        return new PlaylistEntryHolder(layoutInflater.inflate(R.layout.playlist_entry_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistEntryHolder holder, int position) {
        holder.actionsView.initialize();
        holder.itemContent.initialize();
        PlaylistEntry playlistEntry = playlistEntriesDataset.get(position);
        holder.position = position;
        holder.entryID = EntryID.from(playlistEntry);
        holder.itemContent.setSource(holder.entryID.src);
        holder.entry.setActivated(isSelected(holder.getKey()));
        // TODO: Observer-leak? Is there a need to remove observers?
        // TODO: Register in onViewDetachedFromWindow and deregister in onViewDetachedFromWindow
        model.getCachedEntries(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), cachedEntries ->
                        holder.itemContent.setIsCached(cachedEntries.contains(holder.entryID))
                );
        model.getFetchState(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), fetchStateMap -> {
                    if (fetchStateMap.containsKey(holder.entryID)) {
                        holder.itemContent.setFetchState(fetchStateMap.get(holder.entryID));
                    }
                });
        MetaStorage.getInstance(fragment.requireContext()).getMeta(holder.entryID)
                .thenAcceptAsync(meta -> {
                    holder.actionsView.setOnInfoListener(() ->
                            MetaDialogFragment.showMeta(fragment, meta)
                    );
                    String title = meta.getAsString(Meta.FIELD_TITLE);
                    holder.itemContent.setTitle(title);
                    String artist = meta.getAsString(Meta.FIELD_ARTIST);
                    holder.itemContent.setArtist(artist);
                    String src = meta.entryID.src;
                    holder.itemContent.setSource(src);
                }, Util.getMainThreadExecutor());

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
        holder.actionsView.setOnPlayListener(() -> fragment.play(holder.entryID));
        holder.actionsView.setOnQueueListener(() -> fragment.queue(holder.entryID));
    }

    @Override
    public int getItemCount() {
        return playlistEntriesDataset.size();
    }

    private void setDataSet(PlaylistID id, List<PlaylistEntry> entries) {
        playlistID = id;
        playlistEntriesDataset = entries;
        notifyDataSetChanged();
    }

    void setModel(PlaylistFragmentModel model) {
        this.model = model;
        model.getUserState().observe(fragment.getViewLifecycleOwner(), playlistUserState -> {
            if (!playlistUserState.playlistMode) {
                PlaylistID playlistID = playlistUserState.playlistID;
                Log.d(LC, "New playlistID: " + playlistID);
                if (playlistEntries != null) {
                    // TODO: Needed?
//                    playlistEntries.removeObserver();
                }
                playlistEntries = model.getPlaylistEntries(playlistID, fragment.getContext());
                playlistEntries.observe(fragment.getViewLifecycleOwner(), entries -> {
                    Log.d(LC, "Playlist entries changed");
                    setDataSet(playlistID, entries);
                });
            } else {
                setDataSet(null, Collections.emptyList());
            }
        });
    }

    public class PlaylistEntryHolder extends ItemDetailsViewHolder<PlaylistEntry> {
        private final View entry;
        private final TrackItemView itemContent;
        private final TrackItemActionsView actionsView;
        public int position;
        EntryID entryID;

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
    }
}
