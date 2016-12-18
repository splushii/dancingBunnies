package se.splushii.dancingbunnies.ui;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    ArrayList<Artist> dataset;
    ArrayList<Album> albums;
    ArrayList<Song> songs;
    MusicLibrary library;
    libraryView currentView = libraryView.ARTIST;
    public enum libraryView {
        ARTIST,
        ALBUM,
        SONG
    }

    public MusicLibraryAdapter(MusicLibrary library) {
        this.library = library;
        this.dataset = new ArrayList<>();
    }

    public void setView(libraryView view) {
        currentView = view;
        notifyDataSetChanged();
    }

    public void setDataset(ArrayList<Artist> dataset) {
        this.dataset = dataset;
        notifyDataSetChanged();
    }

    public void getAllArtists (final MusicLibraryRequestHandler handler) {
        handler.onStart();
        CompletableFuture<Optional<ArrayList<Artist>>> req = library.getAllArtists("", true);
        req.thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
            @Override
            public void accept(Optional<ArrayList<Artist>> alba) {
                if (alba.isPresent()) {
                    setDataset(alba.get());
                    handler.onSuccess();
                    getAlbums();
                } else {
                    handler.onFailure("Could not fetch artists.");
                }
            }
        });
    }

    private void getAlbums() {
        ArrayList<CompletableFuture<String>> futures = new ArrayList<>();
        for (final Artist a: dataset) {
            CompletableFuture<String> albumReq = library.getAlbums(a);
            albumReq.thenAccept(new Consumer<String>() {
                @Override
                public void accept(String alba) {

                }
            });
            futures.add(albumReq);
        }
        CompletableFuture req = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        req.thenAccept(new Consumer() {
            @Override
            public void accept(Object o) {
                // TODO tell someone that all albums are downloaded
                int count = 0;
                for (Artist a: dataset) {
                    count += a.getAlbums().size();
                }
                System.out.println("Number of albums: " + count);
            }
        });
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class SongViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public Button butt;
        public SongViewHolder(View v) {
            super(v);
            butt = (Button) v.findViewById(R.id.song_title);
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public SongViewHolder onCreateViewHolder(ViewGroup parent,
                                             int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.music_library_item, parent, false);
        // TODO set the view's size, margins, paddings and layout parameters

        return new SongViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(SongViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.butt.setText(dataset.get(position).name());
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
