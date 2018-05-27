package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.ui.PlayListQueueAdapter;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistQueueFragment extends AudioBrowserFragment {

    private static final String LC = Util.getLogContext(PlaylistQueueFragment.class);
    PlayListQueueAdapter recViewAdapter;
    private RecyclerView recView;

    public PlaylistQueueFragment() {
        recViewAdapter = new PlayListQueueAdapter();
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshView();
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(LC, "onStop");
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
        recView = rootView.findViewById(R.id.playlistqueue_recyclerview);
        recView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recView.setAdapter(recViewAdapter);
        return rootView;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater menuInflater = getActivity().getMenuInflater();
        menuInflater.inflate(R.menu.playlist_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = recViewAdapter.getContextMenuHolder().getAdapterPosition();
        EntryID entryID = EntryID.from(recViewAdapter.getItemData(position));
        Log.d(LC, "info pos: " + position);
        switch (item.getItemId()) {
            case R.id.playlist_context_play:
                skipTo(position);
                play();
                Log.d(LC, "playlist context play");
                return true;
            case R.id.playlist_context_dequeue:
                dequeue(entryID);
                Log.d(LC, "playlist context queue");
                return true;
            default:
                Log.d(LC, "playlist context unknown");
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(recView);
    }

    @Override
    protected void onQueueChanged(List<QueueItem> queue) {
        refreshView();
    }
}
