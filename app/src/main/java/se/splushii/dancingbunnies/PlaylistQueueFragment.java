package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.ui.PlayListQueueAdapter;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistQueueFragment extends AudioBrowserFragment {

    private static final String LC = Util.getLogContext(PlaylistQueueFragment.class);
    PlayListQueueAdapter recViewAdapter;

    public PlaylistQueueFragment() {
        recViewAdapter = new PlayListQueueAdapter(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshView();
        Log.d(LC, "onStart");
    }

    @Override
    protected void onSessionReady() {
        super.onSessionReady();
        refreshView();
    }

    public void refreshView() {
        if (mediaController == null || !mediaController.isSessionReady()) {
            Log.w(LC, "Media session not ready");
            return;
        }
        List<QueueItem> queue = mediaController.getQueue();
        if (queue == null) {
            Log.w(LC, "queue is null");
            return;
        }
        Log.d(LC, "Queue size: " + queue.size());
        recViewAdapter.setDataSet(queue);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.playlistqueue_fragment_layout, container, false);
        RecyclerView recView = rootView.findViewById(R.id.playlistqueue_recyclerview);
        recView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager = new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recView.setAdapter(recViewAdapter);
        return rootView;
    }

}
