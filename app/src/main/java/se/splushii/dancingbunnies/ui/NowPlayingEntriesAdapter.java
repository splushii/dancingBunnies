package se.splushii.dancingbunnies.ui;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingEntriesAdapter
        extends RecyclerView.Adapter<NowPlayingEntriesAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);

    private List<QueueItem> queueData;
    private PlaylistItem currentPlaylistItem;
    private List<PlaybackEntry> playlistNext;
    private RecyclerView.ViewHolder contextMenuHolder;

    private static final int VIEWTYPE_UNKNOWN = -1;
    private static final int VIEWTYPE_QUEUE_ITEM = 0;
    private static final int VIEWTYPE_PLAYLIST_NEXT = 1;

    public NowPlayingEntriesAdapter() {
        queueData = new ArrayList<>();
        currentPlaylistItem = PlaylistItem.defaultPlaylist;
        playlistNext = new ArrayList<>();
    }

    public QueueItem getItemData(int childPosition) {
        return queueData.get(childPosition);
    }

    public void setQueue(List<QueueItem> queue) {
        queueData = queue;
        notifyDataSetChanged();
    }

    public void setCurrentPlaylistItem(PlaylistItem playlistItem) {
        currentPlaylistItem = playlistItem;
        notifyDataSetChanged();
    }

    public void setPlaylistNext(List<PlaybackEntry> playbackEntries) {
        playlistNext = playbackEntries;
        notifyDataSetChanged();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        private final View queueEntry;
        private final TextView queueMoreInfo;
        private final TextView queueArtist;
        TextView queueTitle;
        SongViewHolder(View v) {
            super(v);
            queueEntry = v.findViewById(R.id.queue_entry);
            queueTitle = v.findViewById(R.id.queue_title);
            queueArtist = v.findViewById(R.id.queue_artist);
            queueMoreInfo = v.findViewById(R.id.queue_more_info);
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
            v.setBackgroundColor(Color.LTGRAY);
        }
        return new SongViewHolder(v);
    }

    public RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final NowPlayingEntriesAdapter.SongViewHolder holder, int position) {
        MediaMetadataCompat meta;
        int queueSize = queueData.size();
        switch (getItemViewType(position)) {
            default:
            case VIEWTYPE_QUEUE_ITEM:
                QueueItem item = queueData.get(position);
                meta = Meta.desc2meta(item.getDescription());
                break;
            case VIEWTYPE_PLAYLIST_NEXT:
                int nextPos = position - queueSize;
                PlaybackEntry playbackEntry = playlistNext.get(nextPos);
                meta = playbackEntry.meta;
                break;

        }
        String title = meta.getString(Meta.METADATA_KEY_TITLE);
        holder.queueEntry.setOnClickListener(view -> {
            contextMenuHolder = holder;
            view.showContextMenu();
        });
        holder.queueEntry.setOnLongClickListener(view -> {
            Log.d(LC, "onLongClick on " + title);
            return false;
        });
        holder.queueTitle.setText(title);
        String artist = meta.getString(Meta.METADATA_KEY_ARTIST);
        holder.queueArtist.setText(artist);
        String src = meta.getString(Meta.METADATA_KEY_API);
        holder.queueMoreInfo.setText(src);
    }

    @Override
    public int getItemCount() {
        return playlistNext.size() + queueData.size();
    }
}
