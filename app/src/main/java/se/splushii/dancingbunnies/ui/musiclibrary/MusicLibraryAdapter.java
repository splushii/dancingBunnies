package se.splushii.dancingbunnies.ui.musiclibrary;

import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
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
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private List<MediaBrowserCompat.MediaItem> dataset;
    private MusicLibraryFragment fragment;
    private RecyclerView.ViewHolder contextMenuHolder;

    MusicLibraryAdapter(MusicLibraryFragment fragment) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
    }

    MediaBrowserCompat.MediaItem getItemData(int childPosition) {
        return dataset.get(childPosition);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final View libraryEntry;
        private final TextView libraryEntryTitle;
        private final TextView libraryEntryArtist;
        private final TextView libraryEntryAlbum;
        private final View playAction;
        private final View queueAction;
        private final View addToPlaylistAction;
        private final ImageButton overflowMenu;
        private final View moreActions;

        SongViewHolder(View view) {
            super(view);
            libraryEntry = view.findViewById(R.id.library_entry);
            libraryEntryTitle = view.findViewById(R.id.library_entry_title);
            libraryEntryAlbum = view.findViewById(R.id.library_entry_album);
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
                .inflate(R.layout.musiclibrary_item, parent, false);
        return new SongViewHolder(v);
    }

    void setDataset(List<MediaBrowserCompat.MediaItem> items) {
        this.dataset = items;
        notifyDataSetChanged();
    }

    RecyclerView.ViewHolder getContextMenuHolder() {
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
        if (entryID.type.equals(Meta.METADATA_KEY_MEDIA_ID)) {
            fragment.getSongMeta(entryID).thenAccept(meta -> {
                String artist = meta.getString(Meta.METADATA_KEY_ARTIST);
                String album = meta.getString(Meta.METADATA_KEY_ALBUM);
                holder.libraryEntryArtist.setText(artist);
                holder.libraryEntryAlbum.setText(album);
                holder.libraryEntryArtist.setVisibility(View.VISIBLE);
                holder.libraryEntryAlbum.setVisibility(View.VISIBLE);
            });
        } else {
            holder.libraryEntryArtist.setVisibility(View.GONE);
            holder.libraryEntryAlbum.setVisibility(View.GONE);
//            holder.libraryEntryArtist.setText("");
//            holder.libraryEntryAlbum.setText("");
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
                if (holder.moreActions.getVisibility() == View.VISIBLE) {
                    holder.moreActions.setVisibility(View.GONE);
                    holder.libraryEntryTitle.setEllipsize(TextUtils.TruncateAt.END);
                    holder.libraryEntryArtist.setEllipsize(TextUtils.TruncateAt.END);
                    holder.libraryEntryAlbum.setEllipsize(TextUtils.TruncateAt.END);
                    holder.libraryEntryTitle.setSingleLine(true);
                    holder.libraryEntryArtist.setSingleLine(true);
                    holder.libraryEntryAlbum.setSingleLine(true);
                } else {
                    holder.moreActions.setVisibility(View.VISIBLE);
                    holder.libraryEntryTitle.setEllipsize(null);
                    holder.libraryEntryArtist.setEllipsize(null);
                    holder.libraryEntryAlbum.setEllipsize(null);
                    holder.libraryEntryTitle.setSingleLine(false);
                    holder.libraryEntryArtist.setSingleLine(false);
                    holder.libraryEntryAlbum.setSingleLine(false);
                }
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

    Pair<Integer, Integer> getCurrentPosition() {
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
