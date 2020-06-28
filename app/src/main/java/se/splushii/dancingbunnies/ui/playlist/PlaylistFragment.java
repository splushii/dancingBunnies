package se.splushii.dancingbunnies.ui.playlist;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserCallback;
import se.splushii.dancingbunnies.audioplayer.PlaybackController;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_PLAYLIST;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_ADD_MULTIPLE_TO_QUEUE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_SHUFFLE_MULTIPLE_IN_PLAYLIST_PLAYBACK;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_SORT_MULTIPLE_IN_PLAYLIST_PLAYBACK;

public class PlaylistFragment extends Fragment implements AudioBrowserCallback {

    private static final String LC = Util.getLogContext(PlaylistFragment.class);

    private View playlistRootView;
    private RecyclerView playlistRecView;
    private PlaylistAdapter playlistRecViewAdapter;
    private LinearLayoutManager playlistRecViewLayoutManager;
    private FastScroller playlistFastScroller;
    private RecyclerViewActionModeSelectionTracker
            <Playlist, PlaylistAdapter.PlaylistHolder, PlaylistAdapter>
            playlistSelectionTracker;

    private View playlistContentRootView;
    private ImageButton playlistContentBackBtn;
    private View playlistContentInfo;
    private View playlistContentInfoExtra;
    private TextView playlistContentInfoName;

    private View playlistEntriesRoot;
    private RecyclerView playlistEntriesRecView;
    private PlaylistEntriesAdapter playlistEntriesRecViewAdapter;
    private LinearLayoutManager playlistEntriesRecViewLayoutManager;
    private FastScroller playlistEntriesFastScroller;
    private RecyclerViewActionModeSelectionTracker
            <PlaylistEntry, PlaylistEntriesAdapter.PlaylistEntryHolder, PlaylistEntriesAdapter>
            playlistEntriesSelectionTracker;

    private View playlistPlaybackEntriesRoot;
    private RecyclerView playlistPlaybackEntriesRecView;
    private LinearLayoutManager playlistPlaybackEntriesRecViewLayoutManager;
    private PlaylistPlaybackEntriesAdapter playlistPlaybackEntriesRecViewAdapter;
    private FastScroller playlistPlaybackEntriesFastScroller;
    private RecyclerViewActionModeSelectionTracker
            <PlaybackEntry, PlaylistPlaybackEntriesAdapter.ViewHolder, PlaylistPlaybackEntriesAdapter>
            playlistPlaybackEntriesSelectionTracker;

    private SwitchCompat playlistSelectSwitch;
    private SwitchCompat playlistShowPlaybackOrderSwitch;

    private View playlistSortActionView;
    private View playlistShuffleActionView;
    private View playlistRandomActionView;
    private View playlistRepeatActionView;

    private FloatingActionButton newPlaylistFAB;
    private View newPlaylistView;
    private EditText newPlaylistName;

    private AudioBrowser remote;
    private PlaylistFragmentModel model;

    private PlaylistStorage playlistStorage;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        remote = AudioBrowser.getInstance(requireActivity());

        ActionModeCallback playlistActionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return Collections.emptyList();
                    }

                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Playlist> getPlaylistSelection() {
                        return playlistSelectionTracker.getSelection();
                    }

                    @Override
                    public MusicLibraryQueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<MusicLibraryQueryNode> getQueryNodes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        playlistSelectionTracker.clearSelection();
                    }
                }
        );
        playlistSelectionTracker.setActionModeCallback(
                ActionMode.TYPE_PRIMARY,
                playlistActionModeCallback
        );
        playlistSortActionView.setOnClickListener(v ->
                remote.setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_SEQUENTIAL)
        );
        playlistShuffleActionView.setOnClickListener(v ->
                remote.setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_SHUFFLE)
        );
        playlistRandomActionView.setOnClickListener(v ->
                remote.setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_RANDOM)
        );
        playlistRepeatActionView.setOnClickListener(v -> remote.toggleRepeat());
        ActionModeCallback playlistEntriesActionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return playlistEntriesSelectionTracker.getSelection().stream()
                                .map(PlaylistEntry::entryID)
                                .collect(Collectors.toList());
                    }

                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return playlistEntriesSelectionTracker.getSelection();
                    }

                    @Override
                    public List<Playlist> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public MusicLibraryQueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return model.getUserStateValue().browsedPlaylistID;
                    }

                    @Override
                    public List<MusicLibraryQueryNode> getQueryNodes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        playlistEntriesSelectionTracker.clearSelection();
                    }
                }
        );
        playlistEntriesSelectionTracker.setActionModeCallback(
                ActionMode.TYPE_PRIMARY,
                playlistEntriesActionModeCallback
        );
        ActionModeCallback playlistPlaybackActionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return playlistPlaybackEntriesSelectionTracker.getSelection().stream()
                                .map(playbackEntry -> playbackEntry.entryID)
                                .collect(Collectors.toList());
                    }

                    @Override
                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return playlistPlaybackEntriesSelectionTracker.getSelection();
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Playlist> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public MusicLibraryQueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public PlaylistID getPlaylistID() {
                        return remote.getCurrentPlaylist();
                    }

                    @Override
                    public List<MusicLibraryQueryNode> getQueryNodes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        playlistPlaybackEntriesSelectionTracker.clearSelection();
                    }
                });
        playlistPlaybackActionModeCallback.setActions(
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_QUEUE
                },
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_ADD_MULTIPLE_TO_QUEUE,
                        ACTION_SHUFFLE_MULTIPLE_IN_PLAYLIST_PLAYBACK,
                        ACTION_SORT_MULTIPLE_IN_PLAYLIST_PLAYBACK,
                        ACTION_ADD_MULTIPLE_TO_PLAYLIST,
                        ACTION_CACHE_MULTIPLE,
                        ACTION_CACHE_DELETE_MULTIPLE
                },
                new int[0]
        );
        playlistPlaybackEntriesSelectionTracker.setActionModeCallback(
                ActionMode.TYPE_PRIMARY,
                playlistPlaybackActionModeCallback
        );

        model = new ViewModelProvider(requireActivity()).get(PlaylistFragmentModel.class);
        model.getUserState().observe(getViewLifecycleOwner(), this::refreshView);
        model.getCurrentPlaylistID().observe(getViewLifecycleOwner(), currentPlaylistID ->
                refreshView(model.getUserStateValue())
        );
        playlistRecViewAdapter.setModel(model, playlists -> playlists);
        playlistEntriesRecViewAdapter.setModel(model);
        playlistPlaybackEntriesRecViewAdapter.setModel(model);
        playlistStorage = PlaylistStorage.getInstance(getContext());
        playlistSelectSwitch.setChecked(model.isBrowsedCurrent());
        AtomicBoolean userSelectedPlaylist = new AtomicBoolean(false);
        playlistSelectSwitch.setOnTouchListener((v, event) -> {
            userSelectedPlaylist.set(true);
            return false;
        });
        playlistSelectSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (userSelectedPlaylist.get()) {
                userSelectedPlaylist.set(false);
                remote.setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_SEQUENTIAL);
                model.showPlaylistPlaybackEntries(checked);
                if (checked) {
                    PlaylistUserState userState = model.getUserStateValue();
                    if (userState == null) {
                        return;
                    }
                    remote.setCurrentPlaylist(userState.browsedPlaylistID, 0);
                } else {
                    remote.setCurrentPlaylist(null, 0);
                }
            }
        });
        AtomicBoolean userSelectedShowPlaylistOrder = new AtomicBoolean(false);
        playlistShowPlaybackOrderSwitch.setOnTouchListener((v, event) -> {
            userSelectedShowPlaylistOrder.set(true);
            return false;
        });
        playlistShowPlaybackOrderSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (userSelectedShowPlaylistOrder.get()) {
                userSelectedShowPlaylistOrder.set(false);
                model.showPlaylistPlaybackEntries(checked);
            }
        });
    }

    AudioBrowser getRemote() {
        return remote;
    }

    private void updatePlaylistPlaybackButtons(int playbackOrderMode, boolean repeat) {
        if (getView() == null) {
            return;
        }
        playlistSortActionView.setActivated(playbackOrderMode == PlaybackController.PLAYBACK_ORDER_SEQUENTIAL);
        playlistShuffleActionView.setActivated(playbackOrderMode == PlaybackController.PLAYBACK_ORDER_SHUFFLE);
        playlistRandomActionView.setActivated(playbackOrderMode == PlaybackController.PLAYBACK_ORDER_RANDOM);
        playlistRepeatActionView.setEnabled(playbackOrderMode != PlaybackController.PLAYBACK_ORDER_RANDOM);
        playlistRepeatActionView.setActivated(repeat);
    }

    @Override
    public void onStart() {
        super.onStart();
        remote.registerCallback(requireActivity(), this);
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        model.savePlaylistScroll(Util.getRecyclerViewPosition(playlistRecView));
        model.savePlaylistEntriesScroll(Util.getRecyclerViewPosition(playlistEntriesRecView));
        model.savePlaylistPlaybackEntriesScroll(Util.getRecyclerViewPosition(playlistPlaybackEntriesRecView));
        remote.unregisterCallback(requireActivity(), this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        playlistFastScroller.onDestroy();
        playlistEntriesFastScroller.onDestroy();
        super.onDestroyView();
    }

    @Override
    public void onSessionReady() {
        updatePlaylistPlaybackButtons(remote.getPlaylistPlaybackOrderMode(), remote.isRepeat());
        if (model != null) {
            model.setCurrentPlaylist(remote.getCurrentPlaylist());
            model.setCurrentPlaylistPos(remote.getCurrentPlaylistPos());
            model.setCurrentEntry(remote.getCurrentEntry());
            refreshView(model.getUserStateValue());
        }
    }

    @Override
    public void onQueueChanged(List<PlaybackEntry> queue) {}

    @Override
    public void onPlaylistSelectionChanged(PlaylistID playlistID, long pos) {
        model.setCurrentPlaylist(playlistID);
        model.setCurrentPlaylistPos(pos);
    }

    @Override
    public void onPlaylistPlaybackOrderModeChanged(int shuffleMode) {
        updatePlaylistPlaybackButtons(shuffleMode, remote.isRepeat());
    }

    @Override
    public void onRepeatModeChanged(boolean repeat) {
        updatePlaylistPlaybackButtons(remote.getPlaylistPlaybackOrderMode(), repeat);
    }

    @Override
    public void onMediaBrowserConnected() {}

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {}

    @Override
    public void onMetadataChanged(EntryID entryID) {}

    @Override
    public void onCurrentEntryChanged(PlaybackEntry entry) {
        if (model == null) {
            return;
        }
        model.setCurrentEntry(entry);
    }

    @Override
    public void onSessionDestroyed() {}

    private void refreshView(@Nullable PlaylistUserState state) {
        if (state == null) {
            return;
        }
        if (remote == null || !remote.isSessionReady()) {
            Log.w(LC, "Media session not ready");
            return;
        }
        if (state.showPlaylists) {
            newPlaylistFAB.show();
            playlistContentInfo.setVisibility(GONE);
            playlistContentInfoName.setText("");
            playlistContentRootView.setVisibility(GONE);
            playlistSelectSwitch.setChecked(false);
            playlistShowPlaybackOrderSwitch.setVisibility(INVISIBLE);
            playlistRootView.setVisibility(VISIBLE);
        } else {
            PlaylistID playlistID = state.browsedPlaylistID;
            newPlaylistFAB.hide();
            playlistRootView.setVisibility(GONE);
            if (model.isBrowsedCurrent()) {
                if (state.showPlaybackEntries) {
                    updatePlaylistPlaybackButtons(
                            remote.getPlaylistPlaybackOrderMode(),
                            remote.isRepeat()
                    );
                    playlistEntriesRoot.setVisibility(GONE);
                    playlistShowPlaybackOrderSwitch.setChecked(true);
                    playlistContentInfoExtra.setVisibility(VISIBLE);
                    playlistPlaybackEntriesRoot.setVisibility(VISIBLE);
                } else {
                    playlistPlaybackEntriesRoot.setVisibility(GONE);
                    playlistContentInfoExtra.setVisibility(GONE);
                    playlistShowPlaybackOrderSwitch.setChecked(false);
                    playlistEntriesRoot.setVisibility(VISIBLE);
                }
                playlistSelectSwitch.setChecked(true);
                playlistShowPlaybackOrderSwitch.setVisibility(VISIBLE);
            } else {
                playlistSelectSwitch.setChecked(false);
                playlistPlaybackEntriesRoot.setVisibility(GONE);
                playlistContentInfoExtra.setVisibility(GONE);
                playlistShowPlaybackOrderSwitch.setChecked(false);
                playlistEntriesRoot.setVisibility(VISIBLE);
                playlistShowPlaybackOrderSwitch.setVisibility(INVISIBLE);
            }
            playlistContentRootView.setVisibility(VISIBLE);
            playlistContentInfo.setVisibility(VISIBLE);
            model.getPlaylist(getContext(), playlistID)
                    .observe(getViewLifecycleOwner(), playlist -> {
                        if (playlist != null) {
                            playlistContentInfoName.setText(playlist.name());
                        }
                    });
        }
    }

    void scrollPlaylistsTo(int pos, int pad) {
        playlistRecViewLayoutManager.scrollToPositionWithOffset(pos, pad);
    }

    void scrollPlaylistEntriesTo(int pos, int pad) {
        playlistEntriesRecViewLayoutManager.scrollToPositionWithOffset(pos, pad);
    }

    void scrollPlaylistPlaybackEntriesTo(int pos, int pad) {
        playlistPlaybackEntriesRecViewLayoutManager.scrollToPositionWithOffset(pos, pad);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.playlist_fragment_layout, container, false);
        playlistRootView = rootView.findViewById(R.id.playlist_rootview);
        playlistRecView = rootView.findViewById(R.id.playlist_recyclerview);
        playlistRecViewLayoutManager = new LinearLayoutManager(this.getContext());
        playlistRecView.setLayoutManager(playlistRecViewLayoutManager);
        playlistRecViewAdapter = new PlaylistAdapter(this);
        playlistRecViewAdapter.setOnItemClickListener(playlist -> {
            PlaylistID playlistID = playlist.playlistID();
            Log.d(LC, "browse playlist: " + playlistID);
            model.browsePlaylist(playlistID);
        });
        playlistRecView.setAdapter(playlistRecViewAdapter);
        playlistSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_PLAYLIST,
                playlistRecView,
                playlistRecViewAdapter,
                StorageStrategy.createParcelableStorage(Playlist.class),
                savedInstanceState
        );

        playlistRecView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    clearFocus();
                }
            }
        });

        playlistFastScroller = rootView.findViewById(R.id.playlist_fastscroller);
        playlistFastScroller.setRecyclerView(playlistRecView);

        playlistContentRootView = rootView.findViewById(R.id.playlist_content_rootview);
        playlistContentBackBtn = rootView.findViewById(R.id.playlist_content_info_back_btn);
        playlistContentBackBtn.setOnClickListener(v -> {
            clearSelection();
            onBackPressed();
        });
        playlistContentInfo = rootView.findViewById(R.id.playlist_content_info);
        playlistContentInfoExtra = rootView.findViewById(R.id.playlist_content_info_extra);
        playlistSortActionView = rootView.findViewById(R.id.playlist_playback_sort);
        playlistShuffleActionView = rootView.findViewById(R.id.playlist_playback_shuffle);
        playlistRandomActionView = rootView.findViewById(R.id.playlist_playback_random);
        playlistRepeatActionView = rootView.findViewById(R.id.playlist_playback_repeat);
        playlistContentInfoName = rootView.findViewById(R.id.playlist_content_info_name);
        playlistSelectSwitch = rootView.findViewById(R.id.playlist_select_switch);
        playlistShowPlaybackOrderSwitch = rootView.findViewById(R.id.playlist_show_playback_order_switch);

        playlistEntriesRoot = rootView.findViewById(R.id.playlist_entries_root);
        playlistEntriesRecView = rootView.findViewById(R.id.playlist_content_entries_recyclerview);
        playlistEntriesRecViewLayoutManager = new LinearLayoutManager(this.getContext());
        playlistEntriesRecView.setLayoutManager(playlistEntriesRecViewLayoutManager);
        playlistEntriesRecViewAdapter = new PlaylistEntriesAdapter(this);
        playlistEntriesRecView.setAdapter(playlistEntriesRecViewAdapter);
        playlistEntriesSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_PLAYLIST_ENTRIES,
                playlistEntriesRecView,
                playlistEntriesRecViewAdapter,
                StorageStrategy.createParcelableStorage(PlaylistEntry.class),
                savedInstanceState
        );
        playlistEntriesRecView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    playlistEntriesRecViewAdapter.hideTrackItemActions();
                    clearFocus();
                }
            }
        });
        playlistEntriesFastScroller = rootView.findViewById(R.id.playlist_entries_fastscroller);
        playlistEntriesFastScroller.setRecyclerView(playlistEntriesRecView);

        playlistPlaybackEntriesRoot = rootView.findViewById(R.id.playlist_playback_entries_root);
        playlistPlaybackEntriesRecView = rootView.findViewById(R.id.playlist_content_playback_entries_recyclerview);
        playlistPlaybackEntriesRecViewLayoutManager = new LinearLayoutManager(this.getContext());
        playlistPlaybackEntriesRecView.setLayoutManager(playlistPlaybackEntriesRecViewLayoutManager);
        playlistPlaybackEntriesRecViewAdapter = new PlaylistPlaybackEntriesAdapter(this);
        playlistPlaybackEntriesRecView.setAdapter(playlistPlaybackEntriesRecViewAdapter);
        playlistPlaybackEntriesSelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_PLAYLIST_PLAYBACK_ENTRIES,
                playlistPlaybackEntriesRecView,
                playlistPlaybackEntriesRecViewAdapter,
                StorageStrategy.createParcelableStorage(PlaybackEntry.class),
                savedInstanceState
        );

        playlistPlaybackEntriesRecView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    playlistPlaybackEntriesRecViewAdapter.hideTrackItemActions();
                }
            }
        });
        playlistPlaybackEntriesFastScroller = rootView.findViewById(R.id.playlist_playback_entries_fastscroller);
        playlistPlaybackEntriesFastScroller.setRecyclerView(playlistPlaybackEntriesRecView);


        newPlaylistView = rootView.findViewById(R.id.playlist_new_playlist);
        newPlaylistName = rootView.findViewById(R.id.playlist_new_playlist_name);
        newPlaylistFAB = rootView.findViewById(R.id.playlist_new_playlist_fab);
        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null && model.getUserStateValue().showPlaylists) {
                newPlaylistFAB.show();
            } else {
                newPlaylistFAB.hide();
            }
        });
        newPlaylistFAB.setOnClickListener(v -> {
            newPlaylistView.setVisibility(VISIBLE);
            newPlaylistName.requestFocus();
            Util.showSoftInput(requireActivity(), newPlaylistName);
        });
        newPlaylistName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String name = newPlaylistName.getText().toString();
                playlistStorage.insertPlaylists(
                        0,
                        Collections.singletonList(new StupidPlaylist(
                                PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_STUPID),
                                name,
                                Collections.emptyList())
                        )
                );
                newPlaylistName.setText("");
                newPlaylistName.clearFocus();
                Util.hideSoftInput(requireActivity(), newPlaylistName);
                newPlaylistView.setVisibility(GONE);
                return true;
            }
            return false;
        });
        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (playlistSelectionTracker != null) {
            playlistSelectionTracker.onSaveInstanceState(outState);
        }
        if (playlistEntriesSelectionTracker != null) {
            playlistEntriesSelectionTracker.onSaveInstanceState(outState);
        }
    }

    public void clearSelection() {
        if (playlistSelectionTracker != null) {
            playlistSelectionTracker.clearSelection();
        }
        if (playlistEntriesSelectionTracker != null) {
            playlistEntriesSelectionTracker.clearSelection();
        }
        if (playlistEntriesRecViewAdapter != null) {
            playlistEntriesRecViewAdapter.hideTrackItemActions();
        }
        if (playlistPlaybackEntriesSelectionTracker != null) {
            playlistPlaybackEntriesSelectionTracker.clearSelection();
        }
        if (playlistPlaybackEntriesRecViewAdapter != null) {
            playlistPlaybackEntriesRecViewAdapter.hideTrackItemActions();
        }
    }

    public boolean onBackPressed() {
        if (clearFocus()) {
            return true;
        }
        if (!model.getUserStateValue().showPlaylists) {
            model.browsePlaylists();
            return true;
        }
        return false;
    }

    public boolean clearFocus() {
        if (newPlaylistName != null && newPlaylistName.hasFocus()) {
            newPlaylistName.clearFocus();
            Util.hideSoftInput(requireActivity(), newPlaylistName);
            newPlaylistView.setVisibility(GONE);
            return true;
        }
        return false;
    }
}
