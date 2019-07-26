package se.splushii.dancingbunnies.ui.playlist;

import android.annotation.SuppressLint;
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
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.PlaybackController;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class PlaylistFragment extends AudioBrowserFragment {

    private static final String LC = Util.getLogContext(PlaylistFragment.class);

    private View playlistRootView;
    private RecyclerView playlistRecView;
    private PlaylistAdapter playlistRecViewAdapter;
    private LinearLayoutManager playlistRecViewLayoutManager;
    private FastScroller playlistFastScroller;
    private RecyclerViewActionModeSelectionTracker<Playlist, PlaylistAdapter, PlaylistAdapter.PlaylistHolder> playlistSelectionTracker;

    private View playlistContentRootView;
    private View playlistContentInfo;
    private View playlistContentInfoExtra;
    private TextView playlistContentInfoName;

    private View playlistEntriesRoot;
    private RecyclerView playlistEntriesRecView;
    private PlaylistEntriesAdapter playlistEntriesRecViewAdapter;
    private LinearLayoutManager playlistEntriesRecViewLayoutManager;
    private FastScroller playlistEntriesFastScroller;
    private RecyclerViewActionModeSelectionTracker<PlaylistEntry, PlaylistEntriesAdapter, PlaylistEntriesAdapter.PlaylistEntryHolder> playlistEntriesSelectionTracker;

    private View playlistPlaybackEntriesRoot;
    private RecyclerView playlistPlaybackEntriesRecView;
    private LinearLayoutManager playlistPlaybackEntriesRecViewLayoutManager;
    private PlaylistPlaybackEntriesAdapter playlistPlaybackEntriesRecViewAdapter;
    private FastScroller playlistPlaybackEntriesFastScroller;
    private RecyclerViewActionModeSelectionTracker<PlaybackEntry, PlaylistPlaybackEntriesAdapter, PlaylistPlaybackEntriesAdapter.ViewHolder> playlistPlaybackEntriesSelectionTracker;

    private SwitchCompat playlistSelectSwitch;
    private SwitchCompat playlistShowPlaybackOrderSwitch;

    private View playlistSortActionView;
    private View playlistShuffleActionView;
    private View playlistRandomActionView;
    private View playlistRepeatActionView;

    private FloatingActionButton newPlaylistFAB;
    private EditText newPlaylistName;

    private PlaylistFragmentModel model;

    private PlaylistStorage playlistStorage;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        model = ViewModelProviders.of(requireActivity()).get(PlaylistFragmentModel.class);
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
                setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_SEQUENTIAL);
                model.showPlaylistPlaybackEntries(checked);
                if (checked) {
                    PlaylistUserState userState = model.getUserStateValue();
                    if (userState == null) {
                        return;
                    }
                    setCurrentPlaylist(userState.browsedPlaylistID, 0);
                } else {
                    setCurrentPlaylist(null, 0);
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

    private void updatePlaylistPlaybackButtons(int playbackOrderMode, boolean repeat) {
        playlistSortActionView.setActivated(playbackOrderMode == PlaybackController.PLAYBACK_ORDER_SEQUENTIAL);
        playlistShuffleActionView.setActivated(playbackOrderMode == PlaybackController.PLAYBACK_ORDER_SHUFFLE);
        playlistRandomActionView.setActivated(playbackOrderMode == PlaybackController.PLAYBACK_ORDER_RANDOM);
        playlistRepeatActionView.setEnabled(playbackOrderMode != PlaybackController.PLAYBACK_ORDER_RANDOM);
        playlistRepeatActionView.setActivated(repeat);
    }

    @Override
    public void onStop() {
        Log.d(LC, "onStop");
        model.savePlaylistScroll(Util.getRecyclerViewPosition(playlistRecView));
        model.savePlaylistEntriesScroll(Util.getRecyclerViewPosition(playlistEntriesRecView));
        model.savePlaylistPlaybackEntriesScroll(Util.getRecyclerViewPosition(playlistPlaybackEntriesRecView));
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        playlistFastScroller.onDestroy();
        playlistEntriesFastScroller.onDestroy();
        super.onDestroyView();
    }

    @Override
    protected void onSessionReady() {
        updatePlaylistPlaybackButtons(getPlaylistPlaybackOrderMode(), isRepeat());
        if (model != null) {
            model.setCurrentPlaylist(getCurrentPlaylist());
            model.setCurrentPlaylistPos(getCurrentPlaylistPos());
            model.setCurrentEntry(getCurrentEntry());
            refreshView(model.getUserStateValue());
        }
    }

    @Override
    protected void onPlaylistSelectionChanged(PlaylistID playlistID, long pos) {
        model.setCurrentPlaylist(playlistID);
        model.setCurrentPlaylistPos(pos);
    }

    @Override
    protected void onPlaylistPlaybackOrderModeChanged(int shuffleMode) {
        updatePlaylistPlaybackButtons(shuffleMode, isRepeat());
    }

    @Override
    protected void onRepeatModeChanged(boolean repeat) {
        updatePlaylistPlaybackButtons(getPlaylistPlaybackOrderMode(), repeat);
    }

    @Override
    protected void onCurrentEntryChanged(PlaybackEntry entry) {
        model.setCurrentEntry(entry);
    }

    private void refreshView(@Nullable PlaylistUserState state) {
        if (state == null) {
            return;
        }
        if (mediaController == null || !mediaController.isSessionReady()) {
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
                    updatePlaylistPlaybackButtons(getPlaylistPlaybackOrderMode(), isRepeat());
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
                            playlistContentInfoName.setText(playlist.name);
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
            PlaylistID playlistID = new PlaylistID(playlist);
            Log.d(LC, "browse playlist: " + playlistID);
            model.browsePlaylist(playlistID);
        });
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

        playlistFastScroller = rootView.findViewById(R.id.playlist_fastscroller);
        playlistFastScroller.setRecyclerView(playlistRecView);

        playlistContentRootView = rootView.findViewById(R.id.playlist_content_rootview);
        playlistContentInfo = rootView.findViewById(R.id.playlist_content_info);
        playlistContentInfoExtra = rootView.findViewById(R.id.playlist_content_info_extra);
        playlistSortActionView = rootView.findViewById(R.id.playlist_playback_sort);
        playlistSortActionView.setOnClickListener(v ->
                setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_SEQUENTIAL)
        );
        playlistShuffleActionView = rootView.findViewById(R.id.playlist_playback_shuffle);
        playlistShuffleActionView.setOnClickListener(v ->
                setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_SHUFFLE)
        );
        playlistRandomActionView = rootView.findViewById(R.id.playlist_playback_random);
        playlistRandomActionView.setOnClickListener(v ->
                setPlaylistPlaybackOrderMode(PlaybackController.PLAYBACK_ORDER_RANDOM)
        );
        playlistRepeatActionView = rootView.findViewById(R.id.playlist_playback_repeat);
        playlistRepeatActionView.setOnClickListener(v -> toggleRepeat());
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
                getActivity(),
                R.menu.playlist_entries_actionmode_menu,
                MainActivity.SELECTION_ID_PLAYLIST_ENTRIES,
                playlistEntriesRecView,
                playlistEntriesRecViewAdapter,
                StorageStrategy.createParcelableStorage(PlaylistEntry.class),
                savedInstanceState
        );
        playlistEntriesRecView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    playlistEntriesRecViewAdapter.hideTrackItemActions();
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
                getActivity(),
                R.menu.playlist_playback_entries_actionmode_menu,
                MainActivity.SELECTION_ID_PLAYLIST_PLAYBACK_ENTRIES,
                playlistPlaybackEntriesRecView,
                playlistPlaybackEntriesRecViewAdapter,
                StorageStrategy.createParcelableStorage(PlaybackEntry.class),
                savedInstanceState
        );
        playlistPlaybackEntriesRecView.setOnScrollListener(new RecyclerView.OnScrollListener() {
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


        ViewGroup newPlaylistView = rootView.findViewById(R.id.playlist_new_playlist);
        newPlaylistName = rootView.findViewById(R.id.playlist_new_playlist_name);
        newPlaylistName.setShowSoftInputOnFocus(true);
        newPlaylistFAB = rootView.findViewById(R.id.playlist_new_playlist_fab);
        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newPlaylistView.getFocusedChild() == null) {
                newPlaylistView.setVisibility(GONE);
                newPlaylistFAB.show();
            }
        });
        newPlaylistFAB.setOnClickListener(v -> {
            newPlaylistView.setVisibility(VISIBLE);
            newPlaylistName.requestFocus();
            Util.showSoftInput(requireActivity(), newPlaylistName);
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
                Util.hideSoftInput(requireActivity(), newPlaylistName);
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
        if (newPlaylistName.hasFocus()) {
            newPlaylistName.clearFocus();
            return true;
        }
        if (!model.getUserStateValue().showPlaylists) {
            model.browsePlaylists();
        }
        return false;
    }
}
