package se.splushii.dancingbunnies.ui.playlist;

import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_DELETE;

class PlaylistAdapter extends SelectionRecyclerViewAdapter<Playlist, PlaylistAdapter.PlaylistHolder> {
    private static final String LC = Util.getLogContext(PlaylistAdapter.class);

    private final PlaylistFragment fragment;
    private final PlaylistStorage playlistStorage;
    private PlaylistFragmentModel model;

    private List<Playlist> playlistDataset;

    PlaylistAdapter(PlaylistFragment playlistFragment) {
        playlistDataset = new LinkedList<>();
        fragment = playlistFragment;
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
    public void onSelectionDrop(List<Playlist> selection, int lastDragPos) {
        playlistStorage.movePlaylists(selection, lastDragPos);
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistHolder dragViewHolder, List<Playlist> selection) {
        dragViewHolder.nameTextView.setText(selection.size() + " selected");
        dragViewHolder.srcImageView.setBackgroundResource(0);
    }

    @Override
    public void onResetDragViewHolder(PlaylistHolder dragViewHolder) {
        dragViewHolder.nameTextView.setText(dragViewHolder.name);
        dragViewHolder.srcImageView.setBackgroundResource(dragViewHolder.srcResourceID);
    }

    @Override
    public boolean onActionItemClicked(int menuItemID, List<Playlist> selectionList) {
        switch (menuItemID) {
            // TODO: Add support in RecyclerViewActionModeSelectionTracker to specify when
            // TODO: certain actions are supported depending on currently selected items.
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
    public int getItemCount() {
        return playlistDataset.size();
    }

    private void setDataSet(List<Playlist> playlists) {
        Log.d(LC, "playlists: " + playlists);
        playlistDataset = playlists;
        notifyDataSetChanged();
    }

    void setModel(PlaylistFragmentModel model) {
        this.model = model;
        model.getPlaylists(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), this::setDataSet);
    }

    class PlaylistHolder extends ItemDetailsViewHolder<Playlist> {
        private final View entry;
        private String name = "";
        final TextView nameTextView;
        private int srcResourceID = 0;
        final ImageView srcImageView;

        PlaylistHolder(View v) {
            super(v);
            entry = v.findViewById(R.id.playlist);
            nameTextView = v.findViewById(R.id.playlist_name);
            srcImageView = v.findViewById(R.id.playlist_src);
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

        public void setName(String name) {
            this.name = name;
            nameTextView.setText(name);
        }
    }

    @NonNull
    @Override
    public PlaylistHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        return new PlaylistHolder(layoutInflater.inflate(R.layout.playlist_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistHolder holder, int position) {
        holder.entry.setOnClickListener(view -> {
            PlaylistID playlistID = new PlaylistID(playlistDataset.get(position));
            Log.d(LC, "browse playlist: " + playlistID);
            model.addBackStackHistory(getCurrentPosition());
            model.browsePlaylist(playlistID);
        });
        String name = playlistDataset.get(position).name;
        String src = playlistDataset.get(position).api;
        holder.setName(name);
        holder.setSourceResourceID(MusicLibraryService.getAPIIconResource(src));
        holder.entry.setActivated(isSelected(holder.getKey()));
    }

    private Pair<Integer, Integer> getCurrentPosition() {
        RecyclerView rv = fragment.getView().findViewById(R.id.playlist_recyclerview);
        return Util.getRecyclerViewPosition(rv);
    }
}
