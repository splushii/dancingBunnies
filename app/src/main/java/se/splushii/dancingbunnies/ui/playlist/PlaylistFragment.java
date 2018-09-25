package se.splushii.dancingbunnies.ui.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistFragment extends AudioBrowserFragment {

    private static final String LC = Util.getLogContext(PlaylistFragment.class);
    private PlaylistAdapter recViewAdapter;
    private RecyclerView recView;
    private PlaylistUserState userState;
    private LinkedList<PlaylistUserState> viewBackStack;

    public PlaylistFragment() {
        recViewAdapter = new PlaylistAdapter(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        viewBackStack = new LinkedList<>();
        refreshView(userState);
        Log.d(LC, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        if (userState != null) {
            userState = new PlaylistUserState(userState, recViewAdapter.getCurrentPosition());
        }
        Log.d(LC, "onStop");
    }

    @Override
    protected void onSessionReady() {
        super.onSessionReady();
        refreshView(userState);
    }

    @Override
    protected void onSessionEvent(String event, Bundle extras) {
        switch (event) {
            case AudioPlayerService.SESSION_EVENT_PLAYLIST_CHANGED:
                refreshView(userState);
                break;
        }
    }

    private void refreshView(PlaylistUserState newUserState) {
        if (newUserState == null) {
            refreshView(new PlaylistUserState(0, 0));
            return;
        }
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
        PlaylistItem playlistItem = recViewAdapter.getPlaylistItemData(position);
        Log.d(LC, "info pos: " + position);
        switch (item.getItemId()) {
            case R.id.playlist_context_play:
                Log.d(LC, "playlist context play");
                return true;
            case R.id.playlist_context_dequeue:
                Log.d(LC, "playlist context dequeue");
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

    private void addBackButtonHistory(PlaylistUserState libView) {
        viewBackStack.push(libView);
    }

    public boolean onBackPressed() {
        if (viewBackStack.size() > 0) {
            refreshView(viewBackStack.pop());
            return true;
        }
        refreshView(null);
        return false;
    }

    void browsePlaylist(PlaylistID playlistID) {
        addBackButtonHistory(userState);
        refreshView(new PlaylistUserState(playlistID, 0, 0));
    }

    private void fetchPlaylistView(PlaylistUserState newUserState) {
        getPlaylists().thenAccept(opt -> opt.ifPresent(playlists -> {
            Log.d(LC, "playlists size: " + playlists.size());
            recViewAdapter.setPlaylistDataSet(playlists);
            setNewState(newUserState);
        }));
    }

    private void fetchPlaylistEntriesView(PlaylistUserState newUserState) {
        getPlaylistEntries(newUserState.playlistID).thenAccept(opt -> opt.ifPresent(entries -> {
            Log.d(LC, "playlist entries size: " + entries.size());
            recViewAdapter.setPlaylistEntriesDataSet(entries);
            setNewState(newUserState);
        }));
    }

    private void setNewState(PlaylistUserState newUserState) {
        userState = newUserState;
        LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
        llm.scrollToPositionWithOffset(newUserState.pos, newUserState.pad);
    }
}
