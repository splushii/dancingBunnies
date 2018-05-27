package se.splushii.dancingbunnies.ui;

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
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class PlayListQueueAdapter
        extends RecyclerView.Adapter<PlayListQueueAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(PlayListQueueAdapter.class);

    private List<QueueItem> dataset;
    private RecyclerView.ViewHolder contextMenuHolder;

    public PlayListQueueAdapter() {
        dataset = new ArrayList<>();
    }

    public QueueItem getItemData(int childPosition) {
        return dataset.get(childPosition);
    }

    public void setDataSet(List<QueueItem> queue) {
        dataset = queue;
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

    @NonNull
    @Override
    public PlayListQueueAdapter.SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                  int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlistqueue_item, parent, false);
        return new PlayListQueueAdapter.SongViewHolder(v);
    }

    public RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final PlayListQueueAdapter.SongViewHolder holder, int position) {
        QueueItem item = dataset.get(position);
        MediaMetadataCompat meta = Meta.desc2meta(item.getDescription());
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
        return dataset.size();
    }
}
