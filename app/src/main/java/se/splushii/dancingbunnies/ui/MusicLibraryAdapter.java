package se.splushii.dancingbunnies.ui;

import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MusicLibraryFragment;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private List<MediaBrowserCompat.MediaItem> dataset;
    private MusicLibraryFragment fragment;
    private RecyclerView.ViewHolder contextMenuHolder;
    private boolean searchMode = false;

    public MusicLibraryAdapter(MusicLibraryFragment fragment) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
    }

    public MediaBrowserCompat.MediaItem getItemData(int childPosition) {
        return dataset.get(childPosition);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final View libraryEntry;
        private final TextView libraryEntryTitle;
        private final TextView libraryEntryArtist;
        private final View playAction;
        private final View queueAction;
        private final View addToPlaylistAction;
        private final ImageButton overflowMenu;
        private final View moreActions;

        SongViewHolder(View view) {
            super(view);
            libraryEntry = view.findViewById(R.id.library_entry);
            libraryEntryTitle = view.findViewById(R.id.library_entry_title);
            libraryEntryArtist = view.findViewById(R.id.library_entry_artist);
            playAction = view.findViewById(R.id.action_play);
            queueAction = view.findViewById(R.id.action_queue);
            addToPlaylistAction = view.findViewById(R.id.action_add_to_playlist);
            overflowMenu = view.findViewById(R.id.overflow_menu);
            moreActions = view.findViewById(R.id.more_actions);
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.musiclibrary_item_browsable, parent, false);
        return new SongViewHolder(v);
    }

    public void setDataset(List<MediaBrowserCompat.MediaItem> items, boolean searchMode) {
        this.dataset = items;
        this.searchMode = searchMode;
        notifyDataSetChanged();
    }

    public RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        final MediaBrowserCompat.MediaItem item = dataset.get(position);
        final String title = item.getDescription().getTitle() + "";
        EntryID entryID = EntryID.from(item);
        final boolean browsable = item.isBrowsable();
        holder.moreActions.setVisibility(View.GONE);
        holder.libraryEntryTitle.setText(title);
        if (searchMode) {
            String artist = item.getDescription().getExtras().getString(Meta.METADATA_KEY_ARTIST);
            holder.libraryEntryArtist.setText(artist);
        } else {
            holder.libraryEntryArtist.setText("");
        }
        if (position % 2 == 0) {
            holder.libraryEntry.setBackgroundColor(
                    fragment.requireContext().getColor(R.color.grey50)
            );
        } else {
            holder.libraryEntry.setBackgroundColor(
                    fragment.requireContext().getColor(R.color.grey100)
            );
        }
        holder.libraryEntry.setOnLongClickListener(view -> {
            Log.d(LC, "Long click on " + title);
            holder.moreActions.setVisibility(holder.moreActions.getVisibility() == View.VISIBLE ?
                    View.GONE : View.VISIBLE
            );
            return true;
        });
        holder.libraryEntry.setOnClickListener(view -> {
            if (browsable) {
                fragment.browse(entryID);
            } else {
                holder.moreActions.setVisibility(holder.moreActions.getVisibility() == View.VISIBLE ?
                        View.GONE : View.VISIBLE
                );
            }
        });
        holder.playAction.setOnClickListener(v -> fragment.play(entryID));
        holder.queueAction.setOnClickListener(v -> fragment.queue(entryID));
        holder.addToPlaylistAction.setOnClickListener(v -> fragment.addToPlaylist(entryID));
        holder.overflowMenu.setOnClickListener(v -> {
            v.setOnCreateContextMenuListener((menu, v1, menuInfo) -> menu.setHeaderTitle(title));
            contextMenuHolder = holder;
            v.showContextMenu();
        });
    }

    public Pair<Integer, Integer> getCurrentPosition() {
        RecyclerView rv = fragment.getView().findViewById(R.id.musiclibrary_recyclerview);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        int hPos = llm.findFirstCompletelyVisibleItemPosition();
        View v = llm.getChildAt(0);
        int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        if (hPad < 0 && hPos > 0) {
            hPos--;
        }
        return new Pair<>(hPos, hPad);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
