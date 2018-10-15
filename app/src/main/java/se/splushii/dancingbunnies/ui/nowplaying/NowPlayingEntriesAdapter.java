package se.splushii.dancingbunnies.ui.nowplaying;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
    private List<QueueItem> queueData;
    private PlaylistItem currentPlaylistItem;
    private List<PlaybackEntry> playlistNext;
    private RecyclerView.ViewHolder contextMenuHolder;

    private static final int VIEWTYPE_UNKNOWN = -1;
    private static final int VIEWTYPE_QUEUE_ITEM = 0;
    private static final int VIEWTYPE_PLAYLIST_NEXT = 1;

    NowPlayingEntriesAdapter(NowPlayingFragment context) {
        this.context = context;
        queueData = new ArrayList<>();
        currentPlaylistItem = PlaylistItem.defaultPlaylist;
        playlistNext = new ArrayList<>();
    }

    QueueItem getItemData(int childPosition) {
        return queueData.get(childPosition);
    }

    public void setQueue(List<QueueItem> queue) {
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

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final View item;
        private final TextView moreInfo;
        private final TextView artist;
        private final View moreActions;
        private final View actionPlay;
        private final View actionDequeue;
        private final View overflowMenu;
        TextView title;
        SongViewHolder(View v) {
            super(v);
            item = v.findViewById(R.id.nowplaying_item);
            title = v.findViewById(R.id.nowplaying_item_title);
            artist = v.findViewById(R.id.nowplaying_item_artist);
            moreInfo = v.findViewById(R.id.nowplaying_item_more_info);
            moreActions = v.findViewById(R.id.nowplaying_item_more_actions);
            actionPlay = v.findViewById(R.id.nowplaying_item_action_play);
            actionDequeue = v.findViewById(R.id.nowplaying_item_action_dequeue);
            overflowMenu = v.findViewById(R.id.nowplaying_item_overflow_menu);
        }
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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.nowplaying_item, parent, false);
        if (viewType == VIEWTYPE_QUEUE_ITEM) {
            v.setBackgroundColor(ContextCompat.getColor(context.requireContext(), R.color.gray));
        }
        return new SongViewHolder(v);
    }

    RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        MediaMetadataCompat meta;
        int queueSize = queueData.size();
        switch (getItemViewType(position)) {
            default:
            case VIEWTYPE_QUEUE_ITEM:
                QueueItem item = queueData.get(position);
                meta = Meta.desc2meta(item.getDescription());
                holder.actionDequeue.setVisibility(View.VISIBLE);
                break;
            case VIEWTYPE_PLAYLIST_NEXT:
                int nextPos = position - queueSize;
                PlaybackEntry playbackEntry = playlistNext.get(nextPos);
                meta = playbackEntry.meta;
                holder.actionDequeue.setVisibility(View.GONE);
                break;

        }
        String title = meta.getString(Meta.METADATA_KEY_TITLE);
        holder.item.setOnClickListener(view ->
                holder.moreActions.setVisibility(
                        holder.moreActions.getVisibility() == View.VISIBLE ?
                                View.GONE : View.VISIBLE
                )
        );
        holder.actionPlay.setOnClickListener(view -> {
            context.skipItems(position + 1);
            context.play();
        });
        holder.actionDequeue.setOnClickListener(view ->
                context.dequeue(EntryID.from(meta), position)
        );
        holder.overflowMenu.setOnClickListener(view -> {
            contextMenuHolder = holder;
            view.showContextMenu();
        });
        holder.item.setOnLongClickListener(view -> {
            Log.d(LC, "onLongClick on " + title);
            return false;
        });
        holder.title.setText(title);
        String artist = meta.getString(Meta.METADATA_KEY_ARTIST);
        holder.artist.setText(artist);
        String src = meta.getString(Meta.METADATA_KEY_API);
        String preloadStatus = meta.getString(Meta.METADATA_KEY_PLAYBACK_PRELOADSTATUS);
        String moreInfoText = preloadStatus == null ? src : "(" + preloadStatus + ") " + src;
        holder.moreInfo.setText(moreInfoText);
    }

    @Override
    public int getItemCount() {
        return playlistNext.size() + queueData.size();
    }
}
