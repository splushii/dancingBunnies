package se.splushii.dancingbunnies.ui.playlist;

import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_DELETE;

public class PlaylistAdapter extends SelectionRecyclerViewAdapter<Playlist, PlaylistAdapter.PlaylistHolder> {
    private static final String LC = Util.getLogContext(PlaylistAdapter.class);

    private final Fragment fragment;
    private final PlaylistStorage playlistStorage;

    private List<Playlist> playlistDataset;
    private Consumer<Playlist> onItemClickListener = p -> {};
    private LiveData<PlaylistID> currentPlaylistEntryLiveData;

    public PlaylistAdapter(Fragment fragment) {
        this.fragment = fragment;
        playlistDataset = new LinkedList<>();
        playlistStorage = PlaylistStorage.getInstance(fragment.getContext());
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        playlistDataset.add(to, playlistDataset.remove(from));
    }

    @Override
    protected void addItemToDataset(int pos, Playlist item) {
        playlistDataset.add(pos, item);
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        playlistDataset.remove(pos);
    }

    @Override
    protected void onSelectionChanged() {}

    @Override
    protected Playlist getKey(int pos) {
        return playlistDataset.get(pos);
    }

    @Override
    protected int getPosition(@NonNull Playlist playlist) {
        int index = playlistDataset.indexOf(playlist);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }

    @Override
    public void onSelectionDrop(Collection<Playlist> selection,
                                int targetPos,
                                Playlist idAfterTargetPos) {
        playlistStorage.movePlaylists(selection, targetPos);
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistHolder dragViewHolder,
                                       Collection<Playlist> selection) {
        dragViewHolder.nameTextView.setText(selection.size() + " selected");
        dragViewHolder.srcImageView.setBackgroundResource(0);
    }

    @Override
    public void onResetDragViewHolder(PlaylistHolder dragViewHolder) {
        dragViewHolder.update();
    }

    @Override
    public boolean onActionItemClicked(int menuItemID, List<Playlist> selectionList) {
        switch (menuItemID) {
            // TODO: Add support in RecyclerViewActionModeSelectionTracker to specify when
            // TODO: certain ActionMode actions are supported depending on currently selected items.
            case R.id.playlist_actionmode_action_delete:
                playlistStorage.deletePlaylists(selectionList);
                return true;
            default:
                return false;
        }
    }

    private void updateActionModeView(ActionMode actionMode, Selection<Playlist> selection) {
        actionMode.setTitle(selection.size() + " entries");
        boolean showDelete = true;
        for (Playlist playlist: selection) {
            if (!MusicLibraryService.checkAPISupport(playlist.api, PLAYLIST_DELETE)) {
                showDelete = false;
            }
        }
        actionMode.getMenu().findItem(R.id.playlist_actionmode_action_delete)
                .setVisible(showDelete);
    }

    @Override
    public void onActionModeStarted(ActionMode actionMode, Selection<Playlist> selection) {
        updateActionModeView(actionMode, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionMode actionMode, Selection<Playlist> selection) {
        updateActionModeView(actionMode, selection);
    }

    @Override
    public void onActionModeEnding(ActionMode actionMode) {}

    @Override
    public boolean onDragInitiated(Selection<Playlist> selection) {
        return true;
    }

    @Override
    public boolean validMove(PlaylistHolder current, PlaylistHolder target) {
        return true;
    }

    @Override
    public boolean validDrag(PlaylistHolder viewHolder) {
        return true;
    }

    @Override
    public int getItemCount() {
        return playlistDataset.size();
    }

    private void setDataSet(List<Playlist> playlists) {
        Log.d(LC, "playlists: " + playlists);
        playlistDataset = playlists;
        notifyDataSetChanged();
    }

    private boolean initialScrolled;
    public void setModel(PlaylistFragmentModel model,
                         Function<List<Playlist>, List<Playlist>> playlistFilter) {
        initialScrolled = false;
        model.getPlaylists(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), entries -> {
                    setDataSet(playlistFilter.apply(entries));
                    updateScrollPos(model.getUserStateValue(), entries);
                });
        model.getUserState().observe(fragment.getViewLifecycleOwner(), userState -> {
            updateScrollPos(userState, playlistDataset);
        });
        currentPlaylistEntryLiveData = model.getCurrentPlaylistID();
    }

    private void updateScrollPos(PlaylistUserState userState, List<Playlist> entries) {
        if (!initialScrolled && !entries.isEmpty() && fragment instanceof PlaylistFragment) {
            ((PlaylistFragment) fragment).scrollPlaylistsTo(
                    userState.playlistPos,
                    userState.playlistPad
            );
        }
    }

    class PlaylistHolder extends ItemDetailsViewHolder<Playlist> {
        private Playlist playlist;
        private int srcResourceID = 0;
        MutableLiveData<PlaylistID> playlistIDLiveData;
        LiveData<List<PlaylistEntry>> playlistEntriesLiveData;

        private final View entry;
        private final View highlightView;
        private final TextView nameTextView;
        private final ImageView srcImageView;
        private final TextView typeTextView;
        private final TextView numEntriesTextView;

        PlaylistHolder(View v) {
            super(v);
            playlistIDLiveData = new MutableLiveData<>();
            entry = v.findViewById(R.id.playlist);
            nameTextView = v.findViewById(R.id.playlist_name);
            srcImageView = v.findViewById(R.id.playlist_src);
            highlightView = v.findViewById(R.id.playlist_highlight);
            typeTextView = v.findViewById(R.id.playlist_type);
            numEntriesTextView = v.findViewById(R.id.playlist_num_entries);
        }

        @Override
        protected int getPositionOf() {
            return getAdapterPosition();
        }

        @Override
        protected Playlist getSelectionKeyOf() {
            return playlistDataset.get(getPositionOf());
        }

        void setSourceResourceID(int srcResourceID) {
            this.srcResourceID = srcResourceID;
            srcImageView.setBackgroundResource(srcResourceID);
        }

        void updateHighlight(PlaylistID currentPlaylistEntry) {
            boolean highlighted = playlist != null
                    && new PlaylistID(playlist).equals(currentPlaylistEntry);
            if (highlighted) {
                highlightView.setBackgroundColor(ContextCompat.getColor(
                        fragment.requireContext(),
                        R.color.colorAccentLight
                ));
            } else {
                TypedValue value = new TypedValue();
                fragment.requireContext().getTheme().resolveAttribute(
                        android.R.color.transparent,
                        value,
                        true
                );
                highlightView.setBackgroundResource(value.resourceId);
            }
        }

        public void setPlaylist(Playlist playlist) {
            this.playlist = playlist;
            playlistIDLiveData.setValue(new PlaylistID(
                    playlist.api,
                    playlist.id,
                    playlist.type
            ));
            nameTextView.setText(playlist.name);
            String type;
            switch (playlist.type) {
                case PlaylistID.TYPE_STUPID:
                    type = "Static";
                    break;
                case PlaylistID.TYPE_SMART:
                    type = "Dynamic";
                    break;
                case PlaylistID.TYPE_INVALID:
                default:
                    type = "Unkown playlist type";
                    break;
            }
            typeTextView.setText(type);
        }

        public void update() {
            nameTextView.setText(playlist.name);
            srcImageView.setBackgroundResource(srcResourceID);
        }
    }

    @NonNull
    @Override
    public PlaylistHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        PlaylistHolder holder = new PlaylistHolder(
                layoutInflater.inflate(R.layout.playlist_item, parent, false)
        );
        currentPlaylistEntryLiveData.observe(
                fragment.getViewLifecycleOwner(),
                holder::updateHighlight
        );
        holder.playlistEntriesLiveData = Transformations.switchMap(
                holder.playlistIDLiveData,
                playlistID -> MusicLibraryService.getPlaylistEntries(
                        fragment.requireContext(),
                        playlistID
                )
        );
        holder.playlistEntriesLiveData.observe(
                fragment.getViewLifecycleOwner(),
                playlistEntries ->
                        holder.numEntriesTextView.setText(String.valueOf(playlistEntries.size()))
        );
        return holder;
    }

    public void setOnItemClickListener(Consumer<Playlist> listener) {
        onItemClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistHolder holder, int position) {
        holder.setPlaylist(playlistDataset.get(position));
        holder.entry.setOnClickListener(view -> {
            if (hasSelection()) {
                return;
            }
            onItemClickListener.accept(holder.playlist);
        });
        holder.setSourceResourceID(MusicLibraryService.getAPIIconResource(holder.playlist.api));
        holder.updateHighlight(currentPlaylistEntryLiveData.getValue());
        holder.entry.setActivated(isSelected(holder.getKey()));
    }
}
