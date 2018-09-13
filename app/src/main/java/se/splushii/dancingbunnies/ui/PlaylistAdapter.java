package se.splushii.dancingbunnies.ui;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.PlaylistFragment;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String LC = Util.getLogContext(PlaylistAdapter.class);
    private static final int VIEWTYPE_PLAYLIST = 0;
    private static final int VIEWTYPE_PLAYLIST_ENTRIES = 1;

    private List<PlaylistItem> playlistDataset;
    private List<LibraryEntry> playlistEntriesDataset;

    private final PlaylistFragment fragment;
    private RecyclerView.ViewHolder contextMenuHolder;
    private boolean playlistMode = true;
    private PlaylistItem currentPlaylistItem;

    public PlaylistAdapter(PlaylistFragment playlistFragment) {
        playlistDataset = new LinkedList<>();
        playlistEntriesDataset = new LinkedList<>();
        fragment = playlistFragment;
    }

    static class PlaylistHolder extends RecyclerView.ViewHolder {
        private final View entry;
        private final TextView name;
        private final TextView numEntries;
        private final TextView src;

        PlaylistHolder(View v) {
            super(v);
            entry = v.findViewById(R.id.playlist);
            name = v.findViewById(R.id.playlist_name);
            src = v.findViewById(R.id.playlist_src);
            numEntries = v.findViewById(R.id.playlist_num_entries);
        }
    }

    static class PlaylistEntryHolder extends RecyclerView.ViewHolder {
        private final View entry;
        private final TextView name;
        private final TextView src;
        private final View moreActions;
        private final View deleteAction;

        PlaylistEntryHolder(View v) {
            super(v);
            entry = v.findViewById(R.id.playlist_entry);
            name = v.findViewById(R.id.playlist_entry_name);
            src = v.findViewById(R.id.playlist_entry_src);
            moreActions = v.findViewById(R.id.playlist_entry_more_actions);
            deleteAction = v.findViewById(R.id.playlist_entry_action_remove);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return playlistMode ? VIEWTYPE_PLAYLIST : VIEWTYPE_PLAYLIST_ENTRIES;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            default:
            case VIEWTYPE_PLAYLIST:
                return new PlaylistHolder(layoutInflater.inflate(
                        R.layout.playlist_item, parent, false
                ));
            case VIEWTYPE_PLAYLIST_ENTRIES:
                return new PlaylistEntryHolder(layoutInflater.inflate(
                        R.layout.playlist_entry_item, parent, false
                ));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            default:
            case VIEWTYPE_PLAYLIST:
                onBindPlaylistHolder((PlaylistHolder) holder, position);
                break;
            case VIEWTYPE_PLAYLIST_ENTRIES:
                onBindPlaylistEntryHolder((PlaylistEntryHolder) holder, position);
        }
    }

    private void onBindPlaylistEntryHolder(PlaylistEntryHolder holder, int position) {
        holder.entry.setOnClickListener(view -> {
            LibraryEntry item = playlistEntriesDataset.get(position);
            // TODO: Handle clicking entryID
            holder.moreActions.setVisibility(holder.moreActions.getVisibility() == View.VISIBLE ?
                    View.GONE : View.VISIBLE
            );
        });
        if (currentPlaylistItem.playlistID.src.equals(MusicLibraryService.API_ID_DANCINGBUNNIES)
                && currentPlaylistItem.playlistID.type.equals(PlaylistID.TYPE_STUPID)) {
            holder.deleteAction.setOnClickListener(v ->
                    fragment.removeFromPlaylist(currentPlaylistItem.playlistID, position)
            );
            holder.deleteAction.setVisibility(View.VISIBLE);
        } else {
             holder.deleteAction.setVisibility(View.GONE);
        }
        String name = playlistEntriesDataset.get(position).name();
        String src = playlistEntriesDataset.get(position).src();
        holder.entry.setOnLongClickListener(view -> {
            Log.d(LC, "onLongClick on " + name);
            return true;
        });
        holder.name.setText(name);
        holder.src.setText(src);
    }

    private void onBindPlaylistHolder(PlaylistHolder holder, int position) {
        holder.entry.setOnClickListener(view -> {
            currentPlaylistItem = playlistDataset.get(position);
            fragment.browsePlaylist(currentPlaylistItem.playlistID);
        });
        String name = playlistDataset.get(position).name;
        String src = playlistDataset.get(position).playlistID.src;
        holder.entry.setOnLongClickListener(view -> {
            Log.d(LC, "onLongClick on " + name);
            return true;
        });
        holder.name.setText(name);
        holder.src.setText(src);
    }

    public PlaylistItem getPlaylistItemData(int position) {
        return playlistDataset.get(position);
    }

    public LibraryEntry getPlaylistEntryItemData(int position) {
        return playlistEntriesDataset.get(position);
    }

    @Override
    public int getItemCount() {
        return playlistMode ? playlistDataset.size() : playlistEntriesDataset.size();
    }

    public RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    public void setPlaylistDataSet(List<PlaylistItem> playlists) {
        playlistMode = true;
        playlistDataset = playlists;
        notifyDataSetChanged();
    }

    public void setPlaylistEntriesDataSet(List<LibraryEntry> entries) {
        playlistMode = false;
        playlistEntriesDataset = entries;
        notifyDataSetChanged();
    }

    public Pair<Integer, Integer> getCurrentPosition() {
        RecyclerView rv = fragment.getView().findViewById(R.id.playlist_recyclerview);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        int hPos = llm.findFirstCompletelyVisibleItemPosition();
        View v = llm.getChildAt(0);
        int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        if (hPad < 0 && hPos > 0) {
            hPos--;
        }
        return new Pair<>(hPos, hPad);
    }
}
