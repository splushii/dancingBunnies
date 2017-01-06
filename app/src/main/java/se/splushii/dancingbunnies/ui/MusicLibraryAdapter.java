package se.splushii.dancingbunnies.ui;

import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;
import java.util.LinkedList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Consumer;
import se.splushii.dancingbunnies.MusicLibraryFragment;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Song;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private ArrayList<? extends LibraryEntry> dataset;
    private MusicLibrary library;
    private MusicLibraryFragment fragment;
    private LibraryView currentView = LibraryView.ARTIST;
    private LinkedList<ArrayList<? extends LibraryEntry>> historyDataset;
    private LinkedList<Integer> historyPosition;
    private LinkedList<Integer> historyPositionPadding;
    private LinkedList<LibraryView> historyView;

    public enum LibraryView {
        ARTIST,
        ALBUM,
        SONG
    }

    public MusicLibraryAdapter(MusicLibraryFragment fragment, MusicLibrary library) {
        this.library = library;
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
        this.historyDataset = new LinkedList<>();
        this.historyPosition = new LinkedList<>();
        this.historyPositionPadding = new LinkedList<>();
        this.historyView = new LinkedList<>();
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

    public void setView(LibraryView view) {
        currentView = view;
        switch (view) {
            case ARTIST:
                setDataset(library.artists());
                break;
            case SONG:
                setDataset(library.songs());
                break;
            case ALBUM:
                setDataset(library.albums());
                break;
        }
    }

    public void setDataset(ArrayList<? extends LibraryEntry> dataset) {
        this.dataset = dataset;
        notifyDataSetChanged();
    }

    public void getAllLibraryEntries(final MusicLibraryRequestHandler handler) {
        handler.onStart();
        System.out.println("Getting all artists...");
        final CompletableFuture artistReq = new CompletableFuture();
        final CompletableFuture albumReq = new CompletableFuture();
        getAllArtists("", true, new MusicLibraryRequestHandler() {
            @Override
            public void onStart() {}
            @Override
            public void onFailure(String status) {
                handler.onFailure("Failure during getAllArtists: " + status);
            }
            @Override
            public void onSuccess() {
                artistReq.complete(null);
            }
        });
        artistReq.thenAccept(new Consumer() {
            @Override
            public void accept(Object o) {
                System.out.println("Getting all artists done.");
                System.out.println("Number of artists: " + library.artists().size());
                System.out.println("Getting all albums...");
                Snackbar.make(fragment.getView(), "Getting all albums", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                getAlbums(library.artists(), true, new MusicLibraryRequestHandler() {
                    @Override
                    public void onStart() {}
                    @Override
                    public void onFailure(String status) {
                        handler.onFailure("Failure during getAlbums: " + status);
                    }
                    @Override
                    public void onSuccess() {
                        albumReq.complete(null);
                    }
                });
            }
        });
        albumReq.thenAccept(new Consumer() {
            @Override
            public void accept(Object o) {
                System.out.println("Getting all albums done.");
                System.out.println("Number of albums: " + library.albums().size());
                System.out.println("Getting all songs...");
                Snackbar.make(fragment.getView(), "Getting all songs", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                getSongs(library.albums(), true, new MusicLibraryRequestHandler() {
                    @Override
                    public void onStart() {}
                    @Override
                    public void onFailure(String status) {
                        handler.onFailure("Failure during getSongs: " + status);
                    }
                    @Override
                    public void onSuccess() {
                        System.out.println("Getting all songs done.");
                        System.out.println("Number of songs: " + library.songs().size());
                        System.out.println("Artists:\t" + library.artists().size());
                        System.out.println("Albums:\t" + library.albums().size());
                        System.out.println("Songs:\t" + library.songs().size());
                        setDataset(library.artists()); // TODO: check LibraryView enum
                        handler.onSuccess();
                    }

                });
            }
        });
    }

    public void getAllArtists(String dir, boolean refresh, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        CompletableFuture<Optional<ArrayList<Artist>>> req = library.getAllArtists(dir, refresh);
        req.thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
            @Override
            public void accept(Optional<ArrayList<Artist>> alba) {
                if (alba.isPresent()) {
                    handler.onSuccess();
                } else {
                    handler.onFailure("Could not fetch artists.");
                }
            }
        });
    }

    private void getAlbums(final ArrayList<Artist> artists, final boolean refresh, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        Looper.prepare();
        final LinkedList<Artist> queue = new LinkedList<>(artists);
        ArrayList<CompletableFuture<String>> futures = new ArrayList<>();
        while (!queue.isEmpty()) {
            final Artist a = queue.remove();
            CompletableFuture<String> albumReq = library.getAlbums(a, refresh);
            albumReq.thenAccept(new Consumer<String>() {
                @Override
                public void accept(String alba) {
                    if (!alba.isEmpty()) {
                        String status = alba + ". Retrying...";
                        System.out.println(status);
                        queue.add(a);
                    }
                }
            });
            futures.add(albumReq);
        }
        CompletableFuture req = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        req.thenAccept(new Consumer() {
            @Override
            public void accept(Object o) {
                handler.onSuccess();
            }
        });
    }

    private void getSongs(final ArrayList<Album> albums, boolean refresh, final MusicLibraryRequestHandler handler) {
        handler.onStart();
        final LinkedList<Album> queue = new LinkedList<>(albums);
        ArrayList<CompletableFuture<String>> futures = new ArrayList<>();
        while (!queue.isEmpty()) {
            final Album a = queue.remove();
            CompletableFuture<String> songReq = library.getSongs(a, refresh);
            songReq.thenAccept(new Consumer<String>() {
                @Override
                public void accept(String songa) {
                    if (!songa.isEmpty()) {
                        String status = songa + ". Retrying...";
                        System.out.println(status);
                        queue.add(a);
                    }
                }
            });
            futures.add(songReq);
        }
        CompletableFuture req = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        req.thenAccept(new Consumer() {
            @Override
            public void accept(Object o) {
                handler.onSuccess();
            }
        });
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
    public void onBindViewHolder(final SongViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final int n = position;
        RecyclerView rv = (RecyclerView) fragment.getView().findViewById(R.id.recycler_view);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        final int hPos = llm.findFirstVisibleItemPosition();
        View v = llm.getChildAt(0);
        final int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        switch (currentView) {
            case ARTIST:
                Artist artist = (Artist) dataset.get(position);
                holder.butt.setText(artist.name());
                break;
            case SONG:
                Song song = (Song) dataset.get(position);
                holder.butt.setText(song.name() + " " + song.getAlbum().name() + " " + song.getAlbum().getArtist().name());
                break;
            case ALBUM:
                Album album = (Album) dataset.get(position);
                holder.butt.setText(album.name() + " " + album.getArtist().name());
                break;
        }
        holder.butt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (currentView) {
                    case ARTIST:
                        addBackButtonHistory(currentView, hPos, hPad);
                        currentView = LibraryView.ALBUM;
                        setDataset(dataset.get(n).getEntries());
                        break;
                    case ALBUM:
                        addBackButtonHistory(currentView, hPos, hPad);
                        currentView = LibraryView.SONG;
                        setDataset(dataset.get(n).getEntries());
                        break;
                    case SONG:
                        break;
                }
            }
        });
    }

    private void addBackButtonHistory(LibraryView view, int pos, int pad) {
        historyDataset.push(dataset);
        historyPosition.push(pos);
        historyPositionPadding.push(pad);
        historyView.push(view);
        fragment.pushBackStack();
    }

    public void onBackPressed() {
        if (historyDataset.size() > 0) {
            setDataset(historyDataset.pop());
            int i = historyPosition.pop();
            int pad = historyPositionPadding.pop();
            currentView = historyView.pop();
            RecyclerView rv = (RecyclerView) fragment.getView().findViewById(R.id.recycler_view);
//            rv.scrollToPosition(i);
            ((LinearLayoutManager) rv.getLayoutManager()).scrollToPositionWithOffset(i, pad);
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
