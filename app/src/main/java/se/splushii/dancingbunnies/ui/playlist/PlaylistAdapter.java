package se.splushii.dancingbunnies.ui.playlist;

import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.QueryEntry;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_DELETE_MULTIPLE;

public class PlaylistAdapter extends
        SmartDiffSelectionRecyclerViewAdapter<QueryEntry, PlaylistAdapter.PlaylistHolder> {
    private static final String LC = Util.getLogContext(PlaylistAdapter.class);

    private final Fragment fragment;

    private Consumer<EntryID> onItemClickListener = p -> {};
    private LiveData<EntryID> currentPlaylistIDLiveData;

    public PlaylistAdapter(Fragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
    }

    @Override
    protected void onSelectionChanged() {}

    @Override
    public void onSelectionDrop(Collection<QueryEntry> selection,
                                int targetPos,
                                QueryEntry idAfterTargetPos) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistHolder dragViewHolder,
                                       Collection<QueryEntry> selection) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onResetDragViewHolder(PlaylistHolder dragViewHolder) {
        throw new RuntimeException("Not supported");
    }

    private void updateActionModeView(ActionModeCallback actionModeCallback,
                                      Selection<QueryEntry> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
        boolean showDelete = true;
        for (QueryEntry queryEntry: selection) {
            String src = queryEntry.entryID.src;
            if (!APIClient.getAPIClient(fragment.requireContext(), src)
                    .supports(PLAYLIST_DELETE, src)) {
                showDelete = false;
            }
        }
        int[] disabled = showDelete ? new int[0] :
                new int[] {ACTION_PLAYLIST_DELETE_MULTIPLE};
        actionModeCallback.setActions(
                new int[] {ACTION_PLAYLIST_DELETE_MULTIPLE},
                new int[] {ACTION_PLAYLIST_DELETE_MULTIPLE},
                disabled
        );
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<QueryEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<QueryEntry> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {}

    @Override
    public boolean validDrag(Selection<QueryEntry> selection) {
        return false;
    }

    @Override
    public boolean validSelect(QueryEntry key) {
        return true;
    }

    @Override
    public boolean validMove(PlaylistHolder current, PlaylistHolder target) {
        return false;
    }

    @Override
    public boolean validDrag(PlaylistHolder viewHolder) {
        return false;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    private void setPlaylists(List<QueryEntry> queryEntries) {
        Log.d(LC, "setPlaylists: " + queryEntries.size());
        setDataSet(queryEntries);
    }

    private boolean initialScrolled;
    public void setModel(PlaylistFragmentModel model,
                         QueryNode query,
                         Function<List<QueryEntry>, List<QueryEntry>> playlistFilter) {
        initialScrolled = false;
        Transformations.switchMap(model.getUserState(), userState ->
                MetaStorage.getInstance(fragment.requireContext()).getPlaylists(
                        // Use static query if supplied, otherwise get query from model state
                        query == null ? userState.getQuery() : query,
                        userState.sortByFields,
                        userState.sortOrderAscending
                )
        ).observe(fragment.getViewLifecycleOwner(), entries -> {
            setPlaylists(playlistFilter.apply(entries));
            updateScrollPos(model.getUserStateValue(), !entries.isEmpty());
        });
        currentPlaylistIDLiveData = model.getCurrentPlaylistID();
    }

    private void updateScrollPos(PlaylistUserState userState, boolean hasPlaylists) {
        if (!initialScrolled && hasPlaylists && fragment instanceof PlaylistFragment) {
            initialScrolled = true;
            ((PlaylistFragment) fragment).scrollPlaylistsTo(
                    userState.playlistPos,
                    userState.playlistPad
            );
        }
    }

    class PlaylistHolder extends ItemDetailsViewHolder<QueryEntry> {
        MutableLiveData<EntryID> playlistIDLiveData;
        LiveData<Integer> numPlaylistEntriesLiveData;
        LiveData<String> playlistNameLiveData;

        private final View entry;
        private final View highlightView;
        private final TextView nameTextView;
        private final ImageView srcImageView;
        private final TextView backendTextView;
        private final TextView typeTextView;
        private final TextView numEntriesTextView;

        PlaylistHolder(View v) {
            super(v);
            playlistIDLiveData = new MutableLiveData<>();
            playlistNameLiveData = Transformations.map(
                    MetaStorage.getInstance(v.getContext()).getPlaylistMeta(playlistIDLiveData),
                    meta -> meta.getAsString(Meta.FIELD_TITLE)
            );
            entry = v.findViewById(R.id.playlist);
            nameTextView = v.findViewById(R.id.playlist_name);
            srcImageView = v.findViewById(R.id.playlist_src);
            backendTextView = v.findViewById(R.id.playlist_backend);
            highlightView = v.findViewById(R.id.playlist_highlight);
            typeTextView = v.findViewById(R.id.playlist_type);
            numEntriesTextView = v.findViewById(R.id.playlist_num_entries);
        }

        @Override
        protected QueryEntry getSelectionKeyOf() {
            return getItem(getPos());
        }

        void setSourceResourceID(int srcResourceID) {
            srcImageView.setBackgroundResource(srcResourceID);
        }

        void updateHighlight(EntryID currentPlaylistID) {
            EntryID playlistID = playlistIDLiveData.getValue();
            boolean highlighted = playlistID != null
                    && playlistID.equals(currentPlaylistID);
            if (highlighted) {
                highlightView.setBackgroundResource(R.color.accent_active_accentdark);
            } else {
                setDefaultHighlightBackground();
            }
        }

        private void setDefaultHighlightBackground() {
            TypedValue value = new TypedValue();
            fragment.requireContext().getTheme().resolveAttribute(
                    android.R.color.transparent,
                    value,
                    true
            );
            highlightView.setBackgroundResource(value.resourceId);
        }

        void setPlaylistID(EntryID playlistID) {
            playlistIDLiveData.setValue(playlistID);
            backendTextView.setText(MusicLibraryService.getAPIInstanceIDFromSource(playlistID.src));
        }

        void setType(String type) {
            typeTextView.setText(type);
        }

        void setName(String name) {
            nameTextView.setText(name);
        }

        void setNumEntries(int numEntries) {
            numEntriesTextView.setText(String.valueOf(numEntries));
        }
    }

    @NonNull
    @Override
    public PlaylistHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        PlaylistHolder holder = new PlaylistHolder(
                layoutInflater.inflate(R.layout.playlist_item, parent, false)
        );
        currentPlaylistIDLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder::updateHighlight
        );
        holder.playlistNameLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder::setName
        );
        Transformations.switchMap(
                holder.playlistIDLiveData,
                playlistID -> MusicLibraryService.getNumPlaylistEntries(
                        fragment.requireContext(),
                        playlistID
                )
        ).observe(
                fragment.getViewLifecycleOwner(),
                holder::setNumEntries
        );
        Transformations.switchMap(
                holder.playlistIDLiveData,
                playlistID -> MusicLibraryService.isSmartPlaylist(
                        fragment.requireContext(),
                        playlistID
                )
        ).observe(
                fragment.getViewLifecycleOwner(),
                isSmartPlaylist -> holder.setType(isSmartPlaylist ? "Dynamic" : "Static")
        );
        return holder;
    }

    public void setOnItemClickListener(Consumer<EntryID> listener) {
        onItemClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistHolder holder, int position) {
        QueryEntry queryEntry = getItem(position);
        holder.setPlaylistID(queryEntry.entryID);
        holder.entry.setOnClickListener(view -> {
            if (hasSelection()) {
                return;
            }
            if (fragment instanceof PlaylistFragment) {
                ((PlaylistFragment) fragment).clearFocus();
            }
            onItemClickListener.accept(queryEntry.entryID);
        });
        holder.setSourceResourceID(MusicLibraryService.getAPIIconResourceFromSource(
                queryEntry.entryID.src
        ));
        holder.updateHighlight(currentPlaylistIDLiveData.getValue());
        holder.entry.setActivated(isSelected(queryEntry));
    }
}
