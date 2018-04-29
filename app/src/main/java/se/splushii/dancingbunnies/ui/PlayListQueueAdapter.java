package se.splushii.dancingbunnies.ui;

import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.util.Util;

public class PlayListQueueAdapter
        extends RecyclerView.Adapter<PlayListQueueAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(PlayListQueueAdapter.class);

    private AudioBrowserFragment fragment;
    private List<QueueItem> dataset;
    private RecyclerView.ViewHolder contextMenuHolder;

    public PlayListQueueAdapter(AudioBrowserFragment fragment) {
        this.fragment = fragment;
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
        Button butt;
        SongViewHolder(View v) {
            super(v);
            butt = v.findViewById(R.id.queue_title);
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
        String title = item.getDescription().getTitle() + "";
        holder.butt.setText(title);
        holder.butt.setOnClickListener(view -> {
            contextMenuHolder = holder;
            view.showContextMenu();
        });
        holder.butt.setOnLongClickListener(view -> {
            Log.d(LC, "onLongClick on " + title);
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
