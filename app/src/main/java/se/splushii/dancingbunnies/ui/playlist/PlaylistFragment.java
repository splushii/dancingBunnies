package se.splushii.dancingbunnies.ui.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistFragment extends AudioBrowserFragment {

    private static final String LC = Util.getLogContext(PlaylistFragment.class);

    private View playlistRootView;
    private RecyclerView playlistRecView;
    private PlaylistAdapter playlistRecViewAdapter;
    private RecyclerViewActionModeSelectionTracker<Playlist, PlaylistAdapter, PlaylistAdapter.PlaylistHolder> playlistSelectionTracker;

    private View playlistContentRootView;
    private View playlistContentInfo;
    private TextView playlistContentInfoName;
    private RecyclerView playlistEntriesRecView;
    private PlaylistEntriesAdapter playlistEntriesRecViewAdapter;
    private RecyclerViewActionModeSelectionTracker<PlaylistEntry, PlaylistEntriesAdapter, PlaylistEntriesAdapter.PlaylistEntryHolder> playlistEntriesSelectionTracker;

    private PlaylistFragmentModel model;
    private EditText newPlaylistName;

    private PlaylistStorage playlistStorage;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        model = ViewModelProviders.of(getActivity()).get(PlaylistFragmentModel.class);
        model.getUserState().observe(getViewLifecycleOwner(), this::refreshView);
        playlistRecViewAdapter.setModel(model);
        playlistEntriesRecViewAdapter.setModel(model);
        playlistStorage = PlaylistStorage.getInstance(getContext());
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        PlaylistUserState state = model.getUserState().getValue();
        if (state != null && state.playlistMode) {
            model.updateUserState(Util.getRecyclerViewPosition(playlistRecView));
        } else {
            model.updateUserState(Util.getRecyclerViewPosition(playlistEntriesRecView));
        }
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

    private void refreshView(@Nullable PlaylistUserState newUserState) {
        if (newUserState == null) {
            return;
        }
        if (mediaController == null || !mediaController.isSessionReady()) {
            Log.w(LC, "Media session not ready");
            return;
        }
        if (newUserState.playlistMode) {
            playlistContentInfo.setVisibility(View.GONE);
            playlistContentRootView.setVisibility(View.GONE);
            playlistRootView.setVisibility(View.VISIBLE);
            scrollTo(playlistRecView, newUserState.pos, newUserState.pad);
        } else {
            PlaylistID playlistID = newUserState.playlistID;
            playlistRootView.setVisibility(View.GONE);
            playlistContentRootView.setVisibility(View.VISIBLE);
            playlistContentInfo.setVisibility(View.VISIBLE);
            model.getPlaylist(getContext(), playlistID)
                    .observe(getViewLifecycleOwner(), playlist -> {
                        if (playlist != null) {
                            playlistContentInfoName.setText(playlist.name);
                        }
                    });
            scrollTo(playlistEntriesRecView, newUserState.pos, newUserState.pad);
        }
    }

    private void scrollTo(RecyclerView recView, int pos, int pad) {
        LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
        llm.scrollToPositionWithOffset(pos, pad);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.playlist_fragment_layout, container, false);
        playlistRootView = rootView.findViewById(R.id.playlist_rootview);
        playlistRecView = rootView.findViewById(R.id.playlist_recyclerview);
        LinearLayoutManager playlistRecViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        playlistRecView.setLayoutManager(playlistRecViewLayoutManager);
        playlistRecViewAdapter = new PlaylistAdapter(this);
        playlistRecView.setAdapter(playlistRecViewAdapter);
        playlistSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                getActivity(),
                R.menu.playlist_actionmode_menu,
                MainActivity.SELECTION_ID_PLAYLIST,
                playlistRecView,
                playlistRecViewAdapter,
                StorageStrategy.createParcelableStorage(se.splushii.dancingbunnies.storage.db.Playlist.class),
                savedInstanceState
        );

        playlistContentRootView = rootView.findViewById(R.id.playlist_content_rootview);
        playlistContentInfo = rootView.findViewById(R.id.playlist_content_info);
        playlistContentInfoName = rootView.findViewById(R.id.playlist_content_info_name);
        playlistEntriesRecView = rootView.findViewById(R.id.playlist_content_entries_recyclerview);
        playlistEntriesRecView.setHasFixedSize(true);
        LinearLayoutManager playlistEntriesRecViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        playlistEntriesRecView.setLayoutManager(playlistEntriesRecViewLayoutManager);
        playlistEntriesRecViewAdapter = new PlaylistEntriesAdapter(this);
        playlistEntriesRecView.setAdapter(playlistEntriesRecViewAdapter);
        playlistEntriesSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                getActivity(),
                R.menu.playlist_entries_actionmode_menu,
                MainActivity.SELECTION_ID_PLAYLIST_ENTRIES,
                playlistEntriesRecView,
                playlistEntriesRecViewAdapter,
                StorageStrategy.createParcelableStorage(PlaylistEntry.class),
                savedInstanceState
        );

        ViewGroup newPlaylistView = rootView.findViewById(R.id.playlist_new_playlist);
        newPlaylistName = rootView.findViewById(R.id.playlist_new_playlist_name);
        newPlaylistName.setShowSoftInputOnFocus(true);
        FloatingActionButton newPlaylistFAB = rootView.findViewById(R.id.playlist_new_playlist_fab);
        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newPlaylistView.getFocusedChild() == null) {
                newPlaylistView.setVisibility(View.GONE);
                newPlaylistFAB.show();
            }
        });
        newPlaylistFAB.setOnClickListener(v -> {
            newPlaylistView.setVisibility(View.VISIBLE);
            newPlaylistName.requestFocus();
            Util.showSoftInput(getActivity(), newPlaylistName);
            newPlaylistFAB.hide();
        });
        newPlaylistName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String name = newPlaylistName.getText().toString();
                String id = DateTimeFormatter.ISO_DATE_TIME.format(OffsetDateTime.now());
                PlaylistID playlistID = new PlaylistID(
                        MusicLibraryService.API_ID_DANCINGBUNNIES,
                        id,
                        PlaylistID.TYPE_STUPID
                );
                playlistStorage.insertPlaylists(
                        0,
                        Collections.singletonList(new StupidPlaylist(
                                playlistID,
                                name,
                                Collections.emptyList())
                        )
                );
                newPlaylistName.setText("");
                newPlaylistName.clearFocus();
                Util.hideSoftInput(getActivity(), newPlaylistName);
                return true;
            }
            return false;
        });
        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        playlistSelectionTracker.onSaveInstanceState(outState);
        playlistEntriesSelectionTracker.onSaveInstanceState(outState);
    }

    public void clearSelection() {
        if (playlistSelectionTracker != null) {
            playlistSelectionTracker.clearSelection();
        }
        if (playlistEntriesSelectionTracker != null) {
            playlistEntriesSelectionTracker.clearSelection();
        }
    }

    public boolean onBackPressed() {
        if (newPlaylistName.hasFocus()) {
            newPlaylistName.clearFocus();
            return true;
        }
        return model.popBackStack();
    }
}
