package se.splushii.dancingbunnies.ui.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistFragment extends AudioBrowserFragment {

    private static final String LC = Util.getLogContext(PlaylistFragment.class);
    private PlaylistAdapter recViewAdapter;
    private RecyclerView recView;

    private PlaylistFragmentModel model;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        model = ViewModelProviders.of(getActivity()).get(PlaylistFragmentModel.class);
        model.getUserState().observe(getViewLifecycleOwner(), this::refreshView);
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        model.updateUserState(recViewAdapter.getCurrentPosition());
        super.onStop();
    }

    @Override
    protected void onSessionReady() {
        if (model != null) {
            refreshView(model.getUserState().getValue());
        }
    }

    @Override
    protected void onSessionEvent(String event, Bundle extras) {
        switch (event) {
            case AudioPlayerService.SESSION_EVENT_PLAYLIST_CHANGED:
                refreshView(model.getUserState().getValue());
                break;
        }
    }

    private void refreshView(PlaylistUserState newUserState) {
        if (mediaController == null || !mediaController.isSessionReady()) {
            Log.w(LC, "Media session not ready");
            return;
        }
        if (newUserState.playlistMode) {
            fetchPlaylistView(newUserState);
        } else {
            fetchPlaylistEntriesView(newUserState);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.playlist_fragment_layout, container, false);
        recView = rootView.findViewById(R.id.playlist_recyclerview);
        recView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new PlaylistAdapter(this);
        recView.setAdapter(recViewAdapter);
        return rootView;
    }

    public boolean onBackPressed() {
        return model.popBackStack();
    }

    void browsePlaylist(PlaylistID playlistID) {
        model.addBackStackHistory(recViewAdapter.getCurrentPosition());
        model.browsePlaylist(playlistID);
    }

    private void fetchPlaylistView(PlaylistUserState newUserState) {
        getPlaylists().thenAccept(opt -> opt.ifPresent(playlists -> {
            Log.d(LC, "playlists size: " + playlists.size());
            recViewAdapter.setPlaylistDataSet(playlists);
            scrollTo(newUserState.pos, newUserState.pad);
        }));
    }

    private void fetchPlaylistEntriesView(PlaylistUserState newUserState) {
        getPlaylistEntries(newUserState.playlistID).thenAccept(opt -> opt.ifPresent(entries -> {
            Log.d(LC, "playlist entries size: " + entries.size());
            recViewAdapter.setPlaylistEntriesDataSet(newUserState.playlistID, entries);
            scrollTo(newUserState.pos, newUserState.pad);
        }));
    }

    private void scrollTo(int pos, int pad) {
        LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
        llm.scrollToPositionWithOffset(pos, pad);
    }
}
