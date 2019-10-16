package se.splushii.dancingbunnies.ui.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.TrackItemView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_ENTRY_DELETE;
import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_ENTRY_MOVE;
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
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_REMOVE_FROM_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_SET_CURRENT_PLAYLIST;

public class PlaylistEntriesAdapter extends SelectionRecyclerViewAdapter<PlaylistEntry, PlaylistEntriesAdapter.PlaylistEntryHolder> {
    private static final String LC = Util.getLogContext(PlaylistEntriesAdapter.class);
    private final PlaylistFragment fragment;
    private final PlaylistStorage playlistStorage;

    private PlaylistID playlistID;
    private List<PlaylistEntry> playlistEntriesDataset;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;
    private LiveData<Long> currentPlaylistPosLiveData;
    private LiveData<PlaylistID> currentPlaylistIDLiveData;
    private LiveData<PlaybackEntry> currentEntryLiveData;
    private boolean initialScrolled;

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

    private void updateActionModeView(ActionModeCallback actionModeCallback,
                                      Selection<PlaylistEntry> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
        boolean showDelete = playlistID.type == PlaylistID.TYPE_STUPID
                && MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_DELETE);
        int[] disabled = showDelete ? new int[0] :
                new int[] { ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST };
        actionModeCallback.setActions(
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_QUEUE
                },
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_QUEUE,
                        ACTION_ADD_MULTIPLE_TO_PLAYLIST,
                        ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST,
                        ACTION_CACHE_MULTIPLE,
                        ACTION_CACHE_DELETE_MULTIPLE
                },
                disabled
        );
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<PlaylistEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<PlaylistEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {}

    @Override
    public boolean onDragInitiated(Selection<PlaylistEntry> selection) {
        return playlistID.type == PlaylistID.TYPE_STUPID
                && MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_MOVE);
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
        currentPlaylistPosLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentPlaylistPos -> holder.updateHighlight(
                        currentEntryLiveData.getValue(),
                        currentPlaylistPos,
                        currentPlaylistIDLiveData.getValue()
                )
        );
        currentPlaylistIDLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentPlaylistID -> holder.updateHighlight(
                        currentEntryLiveData.getValue(),
                        currentPlaylistPosLiveData.getValue(),
                        currentPlaylistID
                )
        );
        currentEntryLiveData.observe(
                fragment.getViewLifecycleOwner(),
                currentEntry -> holder.updateHighlight(
                        currentEntry,
                        currentPlaylistPosLiveData.getValue(),
                        currentPlaylistIDLiveData.getValue()
                )
        );
        holder.itemContent.initMetaObserver(fragment.requireContext());
        holder.itemContent.observeMeta(fragment.getViewLifecycleOwner());
        holder.itemContent.observeCachedLiveData(cachedEntriesLiveData, fragment.getViewLifecycleOwner());
        holder.itemContent.observeFetchStateLiveData(fetchStateLiveData, fragment.getViewLifecycleOwner());
        holder.actionsView.setAudioBrowserFragment(fragment);
        holder.actionsView.setEntryIDSupplier(() -> EntryID.from(holder.playlistEntry));
        holder.actionsView.setPlaylistIDSupplier(() -> playlistID);
        holder.actionsView.setPlaylistEntrySupplier(() -> holder.playlistEntry);
        holder.actionsView.setPlaylistPositionSupplier(() -> holder.playlistEntry.pos);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistEntryHolder holder, int position) {
        holder.actionsView.initialize();
        holder.position = position;
        holder.entry.setBackgroundResource(position % 2 == 0 ?
                R.color.white_active_accent : R.color.gray50_active_accent
        );
        PlaylistEntry playlistEntry = playlistEntriesDataset.get(position);
        holder.playlistEntry = playlistEntry;
        EntryID entryID = EntryID.from(playlistEntry);
        holder.itemContent.setEntryID(entryID);
        holder.itemContent.setPos(playlistEntry.pos);
        holder.updateHighlight(
                currentEntryLiveData.getValue(),
                currentPlaylistPosLiveData.getValue(),
                currentPlaylistIDLiveData.getValue()
        );
        holder.entry.setActivated(isSelected(holder.getKey()));
        int[] disabledActions;
        if (MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_DELETE)
                && playlistID.type == PlaylistID.TYPE_STUPID) {
            disabledActions = new int[0];
        } else {
            disabledActions = new int[] { ACTION_REMOVE_FROM_PLAYLIST };
        }
        holder.actionsView.setActions(
                new int[] {
                        ACTION_SET_CURRENT_PLAYLIST,
                        ACTION_ADD_TO_QUEUE,
                        ACTION_INFO
                },
                new int[] {
                        ACTION_PLAY,
                        ACTION_SET_CURRENT_PLAYLIST,
                        ACTION_ADD_TO_QUEUE,
                        ACTION_ADD_TO_PLAYLIST,
                        ACTION_REMOVE_FROM_PLAYLIST,
                        ACTION_CACHE,
                        ACTION_CACHE_DELETE,
                        ACTION_INFO
                },
                disabledActions
        );
    }

    @Override
    public int getItemCount() {
        return playlistEntriesDataset.size();
    }

    private void setDataSet(PlaylistID playlistID, List<PlaylistEntry> entries) {
        this.playlistID = playlistID;
        playlistEntriesDataset = entries;
        notifyDataSetChanged();
    }

    void setModel(PlaylistFragmentModel model) {
        initialScrolled = false;
        Transformations.switchMap(model.getUserState(), userState -> {
            if (userState.showPlaylists || userState.browsedPlaylistID == null) {
                setDataSet(null, Collections.emptyList());
                return new MutableLiveData<>();
            }
            return MusicLibraryService.getPlaylistEntries(
                    fragment.requireContext(),
                    userState.browsedPlaylistID
            );
        }).observe(fragment.getViewLifecycleOwner(), entries -> {
            PlaylistUserState userState = model.getUserStateValue();
            setDataSet(userState.browsedPlaylistID, entries);
            updateScrollPos(userState, entries);
        });
        cachedEntriesLiveData = model.getCachedEntries(fragment.getContext());
        fetchStateLiveData = model.getFetchState(fragment.getContext());
        currentPlaylistPosLiveData = model.getCurrentPlaylistPos();
        currentPlaylistIDLiveData = model.getCurrentPlaylistID();
        currentEntryLiveData = model.getCurrentEntry();
    }

    private void updateScrollPos(PlaylistUserState userState, List<PlaylistEntry> entries) {
        if (!initialScrolled && !entries.isEmpty()) {
            initialScrolled = true;
            fragment.scrollPlaylistEntriesTo(
                    userState.playlistEntriesPos,
                    userState.playlistEntriesPad
            );
        }
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
        PlaylistEntry playlistEntry;

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

        void updateHighlight(PlaybackEntry currentEntry,
                             Long currentPlaylistPos,
                             PlaylistID currentPlaylistID) {
            boolean isCurrentPlaylist = playlistID != null
                    && playlistID.equals(currentPlaylistID);
            boolean isCurrentEntry = playlistEntry != null
                    && currentEntry != null
                    && EntryID.from(playlistEntry).equals(currentEntry.entryID);
            boolean isCurrentPlaylistEntry = playlistEntry != null
                    && currentPlaylistPos != null
                    && playlistEntry.pos == currentPlaylistPos;
            itemContent.setPosHighlight(isCurrentPlaylist && isCurrentPlaylistEntry);
            itemContent.setItemHighlight(isCurrentEntry);
        }
    }
}
