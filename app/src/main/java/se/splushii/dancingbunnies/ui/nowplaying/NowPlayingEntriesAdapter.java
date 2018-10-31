package se.splushii.dancingbunnies.ui.nowplaying;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingEntriesAdapter
        extends RecyclerView.Adapter<NowPlayingEntriesAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);

    private final NowPlayingFragment context;
    private List<PlaybackEntry> queueData;
    private PlaylistItem currentPlaylistItem;
    private List<PlaybackEntry> playlistNext;
    private RecyclerView.ViewHolder contextMenuHolder;

    private static final int VIEWTYPE_UNKNOWN = -1;
    private static final int VIEWTYPE_QUEUE_ITEM = 0;
    private static final int VIEWTYPE_PLAYLIST_NEXT = 1;
    private SelectionTracker<Long> selectionTracker;
    private View selectedItemView;

    NowPlayingEntriesAdapter(NowPlayingFragment context) {
        this.context = context;
        queueData = new ArrayList<>();
        currentPlaylistItem = PlaylistItem.defaultPlaylist;
        playlistNext = new ArrayList<>();
    }

    PlaybackEntry getItemData(int childPosition) {
        return queueData.get(childPosition);
    }

    public void setQueue(List<PlaybackEntry> queue) {
        queueData = queue;
        notifyDataSetChanged();
    }

    void setCurrentPlaylistItem(PlaylistItem playlistItem) {
        currentPlaylistItem = playlistItem;
        notifyDataSetChanged();
    }

    void setPlaylistNext(List<PlaybackEntry> playbackEntries) {
        playlistNext = playbackEntries;
        notifyDataSetChanged();
    }

    void setSelectionTracker(SelectionTracker<Long> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    PlaybackEntry getPlaybackEntry(Long key) {
        if (key < queueData.size()) {
            return queueData.get(key.intValue());
        }
        key -= queueData.size();
        return playlistNext.get(key.intValue());
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final View item;
        private final TextView moreInfo;
        private final TextView artist;
        private final View moreActions;
        private final View actionPlay;
        private final View actionDequeue;
        private final View overflowMenu;
        TextView title;

        private final ItemDetailsLookup.ItemDetails<Long> itemDetails = new ItemDetailsLookup.ItemDetails<Long>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Nullable
            @Override
            public Long getSelectionKey() {
                return (long) getAdapterPosition();
            }
        };

        SongViewHolder(View v, boolean isQueueItem) {
            super(v);
            item = v.findViewById(R.id.nowplaying_item);
            title = v.findViewById(R.id.nowplaying_item_title);
            artist = v.findViewById(R.id.nowplaying_item_artist);
            moreInfo = v.findViewById(R.id.nowplaying_item_more_info);
            moreActions = v.findViewById(R.id.nowplaying_item_more_actions);
            actionPlay = v.findViewById(R.id.nowplaying_item_action_play);
            overflowMenu = v.findViewById(R.id.nowplaying_item_overflow_menu);
            actionDequeue = isQueueItem ?
                    v.findViewById(R.id.nowplaying_item_action_dequeue) : null;
        }

        ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return itemDetails;
        }
    }

    @Override
    public long getItemId(int position) {
        Log.e(LC, "THA ITEM ID SHOULD BE POSITION: " + position);
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        int queueSize = queueData.size();
        int nextSize = playlistNext.size();
        if (position < queueSize) {
            return VIEWTYPE_QUEUE_ITEM;
        }
        if (position < queueSize + nextSize) {
            return VIEWTYPE_PLAYLIST_NEXT;
        }
        return VIEWTYPE_UNKNOWN;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean isQueueType = viewType == VIEWTYPE_QUEUE_ITEM;
        int layout = isQueueType ?
                R.layout.nowplaying_queue_item : R.layout.nowplaying_playlist_item;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new SongViewHolder(v, isQueueType);
    }

    RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        PlaybackEntry entry;
        int queueSize = queueData.size();
        switch (getItemViewType(position)) {
            default:
            case VIEWTYPE_QUEUE_ITEM:
                entry = queueData.get(position);
                holder.actionDequeue.setOnClickListener(view -> {
                    context.dequeue(EntryID.from(entry.meta), position);
                    holder.moreActions.setVisibility(View.GONE);
                });
                break;
            case VIEWTYPE_PLAYLIST_NEXT:
                int nextPos = position - queueSize;
                entry = playlistNext.get(nextPos);
                break;
        }
        boolean selected = selectionTracker != null
                && selectionTracker.isSelected(holder.getItemDetails().getSelectionKey());
        holder.item.setActivated(selected);
        Log.e(LC, "nowplaying pos: " + holder.getAdapterPosition() + " selected: " + holder.item.isActivated());
        String title = entry.meta.getString(Meta.METADATA_KEY_TITLE);
        holder.item.setOnClickListener(view -> {
            if (selectedItemView != null && selectedItemView != holder.moreActions) {
                selectedItemView.setVisibility(View.GONE);
            }
            holder.moreActions.setVisibility(selectionTracker.hasSelection() ? View.GONE :
                    holder.moreActions.getVisibility() == View.VISIBLE ?
                            View.GONE : View.VISIBLE
            );
            selectedItemView = holder.moreActions;
        });
        holder.actionPlay.setOnClickListener(view -> {
            context.skipItems(position + 1);
            context.play();
            holder.moreActions.setVisibility(View.GONE);
        });
        holder.overflowMenu.setOnClickListener(view -> {
            contextMenuHolder = holder;
            view.showContextMenu();
        });
        holder.title.setText(title);
        String artist = entry.meta.getString(Meta.METADATA_KEY_ARTIST);
        holder.artist.setText(artist);
        String src = entry.meta.getString(Meta.METADATA_KEY_API);
        String preloadStatus = entry.meta.getString(Meta.METADATA_KEY_PLAYBACK_PRELOADSTATUS);
        String moreInfoText = preloadStatus == null ? src : "(" + preloadStatus + ") " + src;
        holder.moreInfo.setText(moreInfoText);
    }

    @Override
    public int getItemCount() {
        return playlistNext.size() + queueData.size();
    }
}
