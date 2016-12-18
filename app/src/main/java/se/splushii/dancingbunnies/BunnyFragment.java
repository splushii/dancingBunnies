package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.ui.MusicLibraryAdapter;

public class BunnyFragment extends Fragment {
    MusicLibrary lib;
    private RecyclerView recView;
    private MusicLibraryAdapter recViewAdapter;
    private RecyclerView.LayoutManager recViewLayoutManager;

    public BunnyFragment() {
        lib = new MusicLibrary();
    }

    @Override
    public void onStart() {
        super.onStart();
        final ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_bar);
/*
        CompletableFuture<Optional<ArrayList<Artist>>> req = lib.getAllArtists("", true);
        req.thenAccept(new Consumer<Optional<ArrayList<Artist>>>() {
            @Override
            public void accept(Optional<ArrayList<Artist>> alba) {
                if (alba.isPresent()) {
                    progressBar.setVisibility(View.GONE);
                    recViewAdapter.setDataset(alba.get());
                    recView.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Could not fetch artists", Toast.LENGTH_LONG).show();
                }
            }
        });*/

        recViewAdapter.getAllArtists(new MusicLibraryRequestHandler() {
            @Override
            public void onStart() {
                recView.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onSuccess() {
                progressBar.setVisibility(View.GONE);
                recView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(String status) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), status, Toast.LENGTH_LONG).show();
            }
        });

        /*
        recViewAdapter.getAllAlbums(new MusicLibraryRequestHandler() {
            @Override
            public void onStart() {

            }

            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(String status) {

            }
        })
*/    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.bunny_fragment_layout, container, false);

        recView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        recView.setHasFixedSize(true);
        recViewLayoutManager = new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new MusicLibraryAdapter(lib);
        recViewAdapter.setView(MusicLibraryAdapter.libraryView.ARTIST);
        recView.setAdapter(recViewAdapter);

        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        return rootView;
    }
}
