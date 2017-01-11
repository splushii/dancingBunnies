package se.splushii.dancingbunnies.ui;

import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class PlayListQueueAdapter extends RecyclerView.Adapter<PlayListQueueAdapter.SongViewHolder> {
    private Fragment fragment;
    private ArrayList<Song> dataset;

    public PlayListQueueAdapter(Fragment fragment) {
        this.fragment = fragment;
        dataset = new ArrayList<>();
        Artist artist = new Artist("TestArtist", "TestArtist", 1);
        Album album = new Album("TestAlbum", "TestAlbum", artist, 1);
        Song song = new Song("TestSong", "Test", album);
        dataset.add(song);
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    static class SongViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        Button butt;
        SongViewHolder(View v) {
            super(v);
            butt = (Button) v.findViewById(R.id.song_title);
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public PlayListQueueAdapter.SongViewHolder onCreateViewHolder(ViewGroup parent,
                                                                 int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlistqueue_item, parent, false);
        return new PlayListQueueAdapter.SongViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final PlayListQueueAdapter.SongViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final int n = position;
        RecyclerView rv = (RecyclerView) fragment.getView().findViewById(R.id.playlistqueue_recyclerview);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        final int hPos = llm.findFirstVisibleItemPosition();
        View v = llm.getChildAt(0);
        final int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        holder.butt.setText(dataset.get(position).name());
        holder.butt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(fragment.getView(), "Clickety click!", Snackbar.LENGTH_SHORT);
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
