package se.splushii.dancingbunnies.ui;

import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class PlayListQueueAdapter
        extends RecyclerView.Adapter<PlayListQueueAdapter.SongViewHolder> {
    private Fragment fragment;
    private ArrayList<Song> dataset;

    public PlayListQueueAdapter(Fragment fragment) {
        this.fragment = fragment;
        dataset = new ArrayList<>();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        Button butt;
        SongViewHolder(View v) {
            super(v);
            butt = (Button) v.findViewById(R.id.song_title);
        }
    }

    @Override
    public PlayListQueueAdapter.SongViewHolder onCreateViewHolder(ViewGroup parent,
                                                                 int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlistqueue_item, parent, false);
        return new PlayListQueueAdapter.SongViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final PlayListQueueAdapter.SongViewHolder holder, int position) {
        holder.butt.setText(dataset.get(position).name());
        holder.butt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(fragment.getView(), "Clickety click!", Snackbar.LENGTH_SHORT);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
