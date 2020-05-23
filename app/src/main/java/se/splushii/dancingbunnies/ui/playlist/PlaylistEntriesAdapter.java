package se.splushii.dancingbunnies.ui.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
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
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
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

public class PlaylistEntriesAdapter extends
        SmartDiffSelectionRecyclerViewAdapter
                <PlaylistEntry, PlaylistEntriesAdapter.PlaylistEntryHolder> {
    private static final String LC = Util.getLogContext(PlaylistEntriesAdapter.class);
    private final PlaylistFragment fragment;
    private final PlaylistStorage playlistStorage;

    private PlaylistID playlistID;
    private TrackItemActionsView selectedActionView;
    private LiveData<HashSet<EntryID>> cachedEntriesLiveData;
    private LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData;
    private LiveData<Long> currentPlaylistPosLiveData;
    private LiveData<PlaylistID> currentPlaylistIDLiveData;
    private LiveData<PlaybackEntry> currentEntryLiveData;
    private boolean initialScrolled;

    PlaylistEntriesAdapter(PlaylistFragment playlistFragment) {
        fragment = playlistFragment;
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
    public void onSelectionDrop(Collection<PlaylistEntry> selection,
                                int targetPos,
                                PlaylistEntry idAfterTargetPos) {
        playlistStorage.movePlaylistEntries(playlistID, selection, targetPos);
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistEntryHolder dragViewHolder,
                                       Collection<PlaylistEntry> selection) {
        if (selection.size() == 1) {
            dragViewHolder.itemContent.resetFromDrag();
            return;
        }
        dragViewHolder.itemContent.useForDrag(selection.size() + " entries");
    }

    @Override
    public void onResetDragViewHolder(PlaylistEntryHolder dragViewHolder) {
        dragViewHolder.itemContent.resetFromDrag();
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
    public boolean validDrag(Selection<PlaylistEntry> selection) {
        return dragSupported();
    }

    private boolean dragSupported() {
        return playlistID.type == PlaylistID.TYPE_STUPID
                && MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_MOVE);
    }

    @Override
    public boolean validSelect(PlaylistEntry key) {
        return true;
    }

    @Override
    public boolean validMove(PlaylistEntryHolder current, PlaylistEntryHolder target) {
        return true;
    }

    @Override
    public boolean validDrag(PlaylistEntryHolder viewHolder) {
        return true;
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
        holder.actionsView.setAudioBrowser(fragment.getRemote());
        holder.actionsView.setFragmentManager(fragment.requireActivity().getSupportFragmentManager());
        holder.actionsView.setEntryIDSupplier(() -> EntryID.from(holder.playlistEntry));
        holder.actionsView.setPlaylistIDSupplier(() -> playlistID);
        holder.actionsView.setPlaylistEntrySupplier(() -> holder.playlistEntry);
        holder.actionsView.setPlaylistPositionSupplier(() -> holder.playlistEntry.pos);
        holder.actionsView.initialize();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistEntryHolder holder, int position) {
        holder.position = position;
        holder.entry.setBackgroundResource(position % 2 == 0 ?
                R.color.background_active_accent : R.color.backgroundalternate_active_accent
        );
        PlaylistEntry playlistEntry = getItem(position);
        holder.playlistEntry = playlistEntry;
        EntryID entryID = EntryID.from(playlistEntry);
        holder.itemContent.setEntryID(entryID);
        holder.itemContent.setDragHandleListener(dragSupported() ? () -> startDrag(holder) : null);
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
        if (isDragViewHolder(holder)) {
            onUseViewHolderForDrag(holder, getSelection());
        }
    }

    private void setDataSet(PlaylistID playlistID, List<PlaylistEntry> entries) {
        this.playlistID = playlistID;
        setDataSet(entries);
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
        cachedEntriesLiveData = MusicLibraryService.getCachedEntries(fragment.getContext());
        fetchStateLiveData = AudioStorage.getInstance(fragment.getContext()).getFetchState();
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
        return getItem(position).rowId;
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
                actionsView.animateShow(actionsView.getVisibility() != View.VISIBLE);
            });
        }

        @Override
        protected PlaylistEntry getSelectionKeyOf() {
            return getItem(getPos());
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
