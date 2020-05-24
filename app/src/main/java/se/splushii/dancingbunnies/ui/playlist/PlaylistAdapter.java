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
import java.util.Objects;
import java.util.function.Function;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.PLAYLIST_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_DELETE_MULTIPLE;

public class PlaylistAdapter extends
        SmartDiffSelectionRecyclerViewAdapter<Playlist, PlaylistAdapter.PlaylistHolder> {
    private static final String LC = Util.getLogContext(PlaylistAdapter.class);

    private final Fragment fragment;
    private final PlaylistStorage playlistStorage;

    private Consumer<Playlist> onItemClickListener = p -> {};
    private LiveData<PlaylistID> currentPlaylistEntryLiveData;

    public PlaylistAdapter(Fragment fragment) {
        this.fragment = fragment;
        playlistStorage = PlaylistStorage.getInstance(fragment.getContext());
        setHasStableIds(true);
    }

    @Override
    protected void onSelectionChanged() {}

    @Override
    public void onSelectionDrop(Collection<Playlist> selection,
                                int targetPos,
                                Playlist idAfterTargetPos) {
        playlistStorage.movePlaylists(selection, targetPos);
    }

    @Override
    public void onUseViewHolderForDrag(PlaylistHolder dragViewHolder,
                                       Collection<Playlist> selection) {
        if (selection.size() == 1) {
            onResetDragViewHolder(dragViewHolder);
            return;
        }
        dragViewHolder.useForDrag(selection.size() + " selected");
    }

    @Override
    public void onResetDragViewHolder(PlaylistHolder dragViewHolder) {
        dragViewHolder.resetFromDrag();
    }

    private void updateActionModeView(ActionModeCallback actionModeCallback,
                                      Selection<Playlist> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
        boolean showDelete = true;
        for (Playlist playlist: selection) {
            if (!MusicLibraryService.checkAPISupport(playlist.src, PLAYLIST_DELETE)) {
                showDelete = false;
            }
        }
        int[] disabled = showDelete ? new int[0] :
                new int[] { ACTION_PLAYLIST_DELETE_MULTIPLE };
        actionModeCallback.setActions(
                new int[] { ACTION_PLAYLIST_DELETE_MULTIPLE },
                new int[] { ACTION_PLAYLIST_DELETE_MULTIPLE },
                disabled
        );
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<Playlist> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<Playlist> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {}

    @Override
    public boolean validDrag(Selection<Playlist> selection) {
        return true;
    }

    @Override
    public boolean validSelect(Playlist key) {
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
    public long getItemId(int position) {
        Playlist playlist = getItem(position);
        return Objects.hash(playlist.src, playlist.id, playlist.type);
    }

    private void setPlaylists(List<Playlist> playlists) {
        Log.d(LC, "setPlaylists: " + playlists.size());
        setDataSet(playlists);
    }

    private boolean initialScrolled;
    public void setModel(PlaylistFragmentModel model,
                         Function<List<Playlist>, List<Playlist>> playlistFilter) {
        initialScrolled = false;
        model.getPlaylists(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), entries -> {
                    setPlaylists(playlistFilter.apply(entries));
                    updateScrollPos(model.getUserStateValue(), !entries.isEmpty());
                });
        model.getUserState().observe(
                fragment.getViewLifecycleOwner(),
                userState -> updateScrollPos(userState, !isEmpty())
        );
        currentPlaylistEntryLiveData = model.getCurrentPlaylistID();
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

    class PlaylistHolder extends ItemDetailsViewHolder<Playlist> {
        MutableLiveData<PlaylistID> playlistIDLiveData;
        LiveData<List<PlaylistEntry>> playlistEntriesLiveData;

        private final View entry;
        private final View highlightView;
        private final TextView nameTextView;
        private final ImageView srcImageView;
        private final TextView typeTextView;
        private final TextView numEntriesTextView;

        private Playlist playlist;
        private int srcResourceID = 0;
        private int numEntries;
        private PlaylistID currentPlaylist;
        private boolean isStatic;

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
        protected Playlist getSelectionKeyOf() {
            return getItem(getPos());
        }

        void setSourceResourceID(int srcResourceID) {
            this.srcResourceID = srcResourceID;
            if (isStatic) {
                return;
            }
            srcImageView.setBackgroundResource(srcResourceID);
        }

        void updateHighlight(PlaylistID currentPlaylistEntry) {
            this.currentPlaylist = currentPlaylistEntry;
            if (isStatic) {
                return;
            }
            boolean highlighted = playlist != null
                    && new PlaylistID(playlist).equals(currentPlaylistEntry);
            if (highlighted) {
                highlightView.setBackgroundColor(ContextCompat.getColor(
                        fragment.requireContext(),
                        R.color.colorAccent
                ));
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

        void setPlaylist(Playlist playlist) {
            this.playlist = playlist;
            if (isStatic) {
                return;
            }
            playlistIDLiveData.setValue(new PlaylistID(
                    playlist.src,
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

        void setNumEntries(int numEntries) {
            this.numEntries = numEntries;
            if (isStatic) {
                return;
            }
            numEntriesTextView.setText(String.valueOf(numEntries));
        }

        void useForDrag(String txt) {
            isStatic = true;
            nameTextView.setText(txt);
            srcImageView.setBackgroundResource(0);
            typeTextView.setText("");
            numEntriesTextView.setText("");
            setDefaultHighlightBackground();
        }

        void resetFromDrag() {
            isStatic = false;
            setPlaylist(playlist);
            setSourceResourceID(srcResourceID);
            setNumEntries(numEntries);
            updateHighlight(currentPlaylist);
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
                playlistEntries -> holder.setNumEntries(playlistEntries.size())
        );
        return holder;
    }

    public void setOnItemClickListener(Consumer<Playlist> listener) {
        onItemClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistHolder holder, int position) {
        holder.setPlaylist(getItem(position));
        holder.entry.setOnClickListener(view -> {
            if (hasSelection()) {
                return;
            }
            if (fragment instanceof PlaylistFragment) {
                ((PlaylistFragment) fragment).clearFocus();
            }
            onItemClickListener.accept(holder.playlist);
        });
        holder.setSourceResourceID(MusicLibraryService.getAPIIconResourceFromSource(holder.playlist.src));
        holder.updateHighlight(currentPlaylistEntryLiveData.getValue());
        holder.entry.setActivated(isSelected(holder.getKey()));
    }
}
