package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import se.splushii.dancingbunnies.ui.PlayListQueueAdapter;

public class PlaylistQueueFragment extends Fragment {

    public PlaylistQueueFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.playlistqueue_fragment_layout, container, false);
        RecyclerView recView = (RecyclerView) rootView.findViewById(R.id.playlistqueue_recyclerview);
        recView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager = new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        PlayListQueueAdapter recViewAdapter = new PlayListQueueAdapter(this);
        recView.setAdapter(recViewAdapter);
        return rootView;
    }
}
