package se.splushii.dancingbunnies.ui.nowplaying;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastButtonFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserCallback;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.MenuActions;
import se.splushii.dancingbunnies.ui.WaveformSeekBar;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.mediarouter.media.MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER;
import static androidx.mediarouter.media.MediaRouter.RouteInfo.DEVICE_TYPE_TV;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_CACHE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_HISTORY_CLEAR;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_HISTORY_DELETE_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_INFO;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_PLAY_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_ADD_MULTIPLE;
import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_QUEUE_CLEAR;

public class NowPlayingFragment extends Fragment implements AudioBrowserCallback {
    private static final String LC = Util.getLogContext(NowPlayingFragment.class);
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;
    private static final long PROGRESS_UPDATE_INTERNAL = 50;

    private View nowPlaying;
    private TextView nowPlayingTitle;
    private TextView nowPlayingArtist;
    private TextView nowPlayingAlbum;
    private ImageButton playPauseBtn;
    private boolean isPlaying = false;
    private PlaybackStateCompat playbackState;
    private TextView positionText;
    private TextView durationText;
    private TextView mediaInfoText;
    private View bufferingView;
    private TextView sizeText;
    private MutableLiveData<EntryID> entryIDLiveData;
    private LiveData<Meta> metaLiveData;

    private NowPlayingEntriesAdapter recViewAdapter;
    private FastScroller fastScroller;
    private RecyclerViewActionModeSelectionTracker
            <PlaybackEntry, NowPlayingEntriesAdapter.ViewHolder, NowPlayingEntriesAdapter>
            selectionTracker;
    private TextView currentPlaylistName;
    private ImageView currentPlaylistOrdered;
    private ImageView currentPlaylistMode;
    private View currentPlaylistDeselectBtn;
    private NowPlayingHistoryEntriesAdapter historyRecViewAdapter;
    private FastScroller historyFastScroller;
    private RecyclerViewActionModeSelectionTracker
            <PlaybackEntry, NowPlayingHistoryEntriesAdapter.ViewHolder, NowPlayingHistoryEntriesAdapter>
            historySelectionTracker;

    private NowPlayingFragmentModel model;
    private final MutableLiveData<EntryID> currentPlaylistIDLiveData = new MutableLiveData<>();
    private View currentPlaylistView;
    private WaveformSeekBar waveformSeekBar;
    private final MediaRouteSelector mediaRouteSelector;
    private MediaRouteButton mediaRouteButton;
    private ImageView mediaRouteIcon;
    private TextView mediaRouteName;
    private ImageButton nextBtn;
    private MediaRouter mediaRouter;
    private View nowPlayingActions;

    private AudioBrowser remote;

    private int mediaRouteTargetDrawable;
    private int mediaRouteTypeDrawable;

    public NowPlayingFragment() {
        entryIDLiveData = new MutableLiveData<>();
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(
                R.layout.nowplaying_fragment_layout,
                container,
                false
        );

        mediaRouteButton = rootView.findViewById(R.id.nowplaying_mediaroute_button);
        mediaRouteIcon = rootView.findViewById(R.id.nowplaying_mediaroute_icon);
        mediaRouteName = rootView.findViewById(R.id.nowplaying_mediaroute_name);

        RecyclerView recView = rootView.findViewById(R.id.nowplaying_recyclerview);
        LinearLayoutManager recViewLayoutManager = new LinearLayoutManager(this.getContext());
        recViewLayoutManager.setReverseLayout(true);
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new NowPlayingEntriesAdapter(this);
        recView.setAdapter(recViewAdapter);
        selectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_NOWPLAYING,
                recView,
                recViewAdapter,
                StorageStrategy.createParcelableStorage(PlaybackEntry.class),
                savedInstanceState
        );

        recView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    recViewAdapter.hideTrackItemActions();
                }
            }
        });
        fastScroller = rootView.findViewById(R.id.nowplaying_fastscroller);
        fastScroller.setRecyclerView(recView);
        fastScroller.setReversed(true);

        nowPlaying = rootView.findViewById(R.id.nowplaying_current);
        nowPlayingTitle = rootView.findViewById(R.id.nowplaying_title);
        nowPlayingArtist = rootView.findViewById(R.id.nowplaying_artist);
        nowPlayingAlbum = rootView.findViewById(R.id.nowplaying_album);
        ImageButton historyBtn = rootView.findViewById(R.id.nowplaying_history_btn);
        View historyRoot = rootView.findViewById(R.id.nowplaying_history_root);
        historyBtn.setActivated(historyRoot.getVisibility() == VISIBLE);
        historyBtn.setOnClickListener(view -> {
            boolean showHistory = historyRoot.getVisibility() != VISIBLE;
            historyRoot.setVisibility(showHistory ? VISIBLE: GONE);
            historyBtn.setActivated(showHistory);
        });
        isPlaying = false;
        playPauseBtn = rootView.findViewById(R.id.nowplaying_play_pause);
        nextBtn = rootView.findViewById(R.id.nowplaying_next);

        waveformSeekBar = rootView.findViewById(R.id.waveformSeekBar);
        AudioStorage.getInstance(requireContext()).getWaveform(entryIDLiveData).observe(
                getViewLifecycleOwner(),
                waveformEntry -> {
                    if (waveformEntry == null) {
                        waveformSeekBar.resetData();
                    } else {
                        waveformSeekBar.setData(
                                waveformEntry.getRMSPositive(),
                                waveformEntry.getRMSNegative(),
                                waveformEntry.getPeakPositive(),
                                waveformEntry.getPeakNegative()
                        );
                    }
                }
        );
        metaLiveData = MetaStorage.getInstance(requireContext()).getTrackMeta(entryIDLiveData);
        metaLiveData.observe(getViewLifecycleOwner(), this::updateMeta);

        positionText = rootView.findViewById(R.id.nowplaying_position);
        durationText = rootView.findViewById(R.id.nowplaying_duration);
        mediaInfoText = rootView.findViewById(R.id.nowplaying_media_info);
        bufferingView = rootView.findViewById(R.id.nowplaying_buffering);
        sizeText = rootView.findViewById(R.id.nowplaying_size);

        currentPlaylistView = rootView.findViewById(R.id.nowplaying_current_playlist);
        nowPlayingActions = rootView.findViewById(R.id.nowplaying_actions);
        View currentPlaylistNameLayout =
                rootView.findViewById(R.id.nowplaying_current_playlist_info_layout);
        currentPlaylistNameLayout.setOnClickListener(v -> goToPlaylistEntry());
        currentPlaylistName = rootView.findViewById(R.id.nowplaying_current_playlist_name);
        currentPlaylistOrdered = rootView.findViewById(R.id.nowplaying_current_ordered);
        currentPlaylistMode = rootView.findViewById(R.id.nowplaying_current_mode);

        currentPlaylistDeselectBtn =
                rootView.findViewById(R.id.nowplaying_current_playlist_deselect);

        RecyclerView historyRecView = rootView.findViewById(R.id.nowplaying_history_recyclerview);
        LinearLayoutManager historyRecViewLayoutManager = new LinearLayoutManager(this.getContext());
        historyRecView.setLayoutManager(historyRecViewLayoutManager);
        historyRecViewAdapter = new NowPlayingHistoryEntriesAdapter(this);
        historyRecView.setAdapter(historyRecViewAdapter);
        historySelectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_NOWPLAYING_HISTORY,
                historyRecView,
                historyRecViewAdapter,
                StorageStrategy.createParcelableStorage(PlaybackEntry.class),
                savedInstanceState
        );

        historyRecView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    historyRecViewAdapter.hideTrackItemActions();
                }
            }
        });

        historyFastScroller = rootView.findViewById(R.id.nowplaying_history_fastscroller);
        historyFastScroller.setRecyclerView(historyRecView);

        return rootView;
    }

    AudioBrowser getRemote() {
        return remote;
    }

    private void goToPlaylistEntry() {
        NowPlayingState state = model.getState().getValue();
        if (state == null) {
            return;
        }
        EntryID playlistID = state.currentPlaylistID;
        long pos = remote.getCurrentPlaylistPos();
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.putExtra(MainActivity.INTENT_EXTRA_PLAYLIST_ID, playlistID);
        intent.putExtra(MainActivity.INTENT_EXTRA_PLAYLIST_POS, pos);
        requireContext().startActivity(intent);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(LC, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        remote = AudioBrowser.getInstance(requireActivity());

        ActionModeCallback actionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return selectionTracker.getSelection().stream()
                                .map(playbackEntry -> playbackEntry.entryID)
                                .collect(Collectors.toList());
                    }

                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return selectionTracker.getSelection();
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<EntryID> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public QueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public EntryID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<QueryNode> getQueryNodes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Transaction> getTransactions() {
                        return null;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        selectionTracker.clearSelection();
                    }
                }
        );
        selectionTracker.setActionModeCallback(ActionMode.TYPE_PRIMARY, actionModeCallback);
        playPauseBtn.setOnClickListener(view -> {
            if (isPlaying) {
                remote.pause();
                stopProgressUpdate();
            } else {
                remote.play();
                scheduleProgressUpdate();
            }
        });
        nextBtn.setOnClickListener(view -> remote.next());
        waveformSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    positionText.setText(Meta.getDurationString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(LC, "seekbar onstart");
                stopProgressUpdate();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(LC, "seekbar onstop: " + seekBar.getProgress());
                remote.seekTo(seekBar.getProgress());
            }
        });
        nowPlayingActions.setOnClickListener(v ->
                MenuActions.showPopupMenu(
                        getContext(),
                        v,
                        new int[] {
                                ACTION_QUEUE_CLEAR,
                                ACTION_HISTORY_CLEAR
                        },
                        new HashSet<>(),
                        menuItem -> MenuActions.doAction(
                                menuItem.getItemId(),
                                remote,
                                requireContext(),
                                requireActivity().getSupportFragmentManager(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        );
        nowPlaying.setOnClickListener(v ->
                MenuActions.showPopupMenu(
                        getContext(),
                        v,
                        new int[] {
                                ACTION_QUEUE_ADD,
                                ACTION_PLAYLIST_ENTRY_ADD,
                                ACTION_CACHE,
                                ACTION_CACHE_DELETE,
                                ACTION_INFO
                        },
                        new HashSet<>(),
                        menuItem -> MenuActions.doAction(
                                menuItem.getItemId(),
                                remote,
                                requireContext(),
                                requireActivity().getSupportFragmentManager(),
                                () -> entryIDLiveData.getValue(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                )
        );
        currentPlaylistDeselectBtn.setOnClickListener(v -> remote.setCurrentPlaylist(null, 0));
        ActionModeCallback historyActionModeCallback = new ActionModeCallback(
                requireActivity(),
                remote,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return historySelectionTracker.getSelection().stream()
                                .map(playbackEntry -> playbackEntry.entryID)
                                .collect(Collectors.toList());
                    }

                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return historySelectionTracker.getSelection();
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<EntryID> getPlaylistSelection() {
                        return Collections.emptyList();
                    }

                    @Override
                    public QueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public EntryID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public List<QueryNode> getQueryNodes() {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Transaction> getTransactions() {
                        return null;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        historySelectionTracker.clearSelection();
                    }
                }
        );
        historyActionModeCallback.setActions(
                new int[] {
                        ACTION_QUEUE_ADD_MULTIPLE,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE
                },
                new int[] {
                        ACTION_PLAY_MULTIPLE,
                        ACTION_QUEUE_ADD_MULTIPLE,
                        ACTION_PLAYLIST_ENTRY_ADD_MULTIPLE,
                        ACTION_HISTORY_DELETE_MULTIPLE,
                        ACTION_CACHE_MULTIPLE,
                        ACTION_CACHE_DELETE_MULTIPLE
                },
                new int[0]
        );
        historySelectionTracker.setActionModeCallback(
                ActionMode.TYPE_PRIMARY,
                historyActionModeCallback
        );

        model = new ViewModelProvider(requireActivity()).get(NowPlayingFragmentModel.class);
        AudioStorage.getInstance(requireContext()).getFetchState().observe(getViewLifecycleOwner(), audioDataFetchStates -> {
            boolean showSize = false;
            Meta meta = metaLiveData.getValue();
            EntryID entryID = meta == null ? null : meta.entryID;
            AudioStorage.AudioDataFetchState state = audioDataFetchStates.get(entryID);
            String formattedFileSize = meta == null ? null : meta.getFormattedFileSize();
            if (state != null) {
                sizeText.setText(state.getStatusMsg(formattedFileSize));
                showSize = true;
            } else if (formattedFileSize != null) {
                sizeText.setText(formattedFileSize);
                showSize = true;
            }
            sizeText.setVisibility(showSize ? VISIBLE : INVISIBLE);
        });
        recViewAdapter.setModel(model);
        MetaStorage.getInstance(requireContext())
                .getPlaylistMeta(currentPlaylistIDLiveData)
                .observe(getViewLifecycleOwner(), meta -> {
                    if (meta == null) {
                        currentPlaylistView.setVisibility(GONE);
                    } else {
                        currentPlaylistName.setText(meta.getAsString(Meta.FIELD_TITLE));
                        currentPlaylistView.setVisibility(VISIBLE);
                    }
                });
        model.getState().observe(getViewLifecycleOwner(), this::refreshView);

        CastButtonFactory.setUpMediaRouteButton(
                requireContext(),
                mediaRouteButton
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        remote.registerCallback(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mediaRouter = MediaRouter.getInstance(requireContext());
        mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback
        );
        setSelectedMediaRoute(mediaRouter.getSelectedRoute());
    }

    private void setSelectedMediaRoute(MediaRouter.RouteInfo selectedRoute) {
        boolean isTargetLocal = selectedRoute.isDeviceSpeaker();
        boolean isTargetBluetooth = selectedRoute.isBluetooth();
        boolean isTargetCast = false;
        boolean isSpeaker = isTargetLocal;
        boolean isGroup = false;
        boolean isTV = false;
        String name = selectedRoute.getName();
        switch (selectedRoute.getDeviceType()) {
            case DEVICE_TYPE_TV:
                isTV = true;
                break;
            case DEVICE_TYPE_SPEAKER:
                isSpeaker = true;
                break;
        }
        CastDevice castDevice = CastDevice.getFromBundle(selectedRoute.getExtras());
        if (castDevice != null) {
            isTargetCast = true;
            if (castDevice.hasCapability(CastDevice.CAPABILITY_MULTIZONE_GROUP)) {
                isGroup = true;
            }
        }

        int targetDrawable;
        if (isTargetLocal) {
            targetDrawable = R.drawable.ic_phone_android_black_24dp;
        } else if (isTargetBluetooth) {
            targetDrawable = R.drawable.ic_bluetooth_black_24dp;
        } else if (isTargetCast) {
            switch (selectedRoute.getConnectionState()) {
                default:
                case MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED:
                    targetDrawable = R.drawable.ic_baseline_cast_24;
                    break;
                case MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING:
                    targetDrawable = R.drawable.cast_ic_notification_connecting;
                    break;
                case MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED:
                    targetDrawable = R.drawable.ic_baseline_cast_connected_24;
                    break;
            }
        } else {
            targetDrawable = R.drawable.ic_baseline_device_unknown_24;
        }

        int typeDrawable;
        if (isTargetLocal) {
            typeDrawable = R.drawable.ic_baseline_volume_up_24;
        } else if (isGroup) {
            typeDrawable = R.drawable.ic_baseline_speaker_group_24;
        } else if (isSpeaker) {
            typeDrawable = R.drawable.ic_baseline_speaker_24;
        } else if (isTV) {
            typeDrawable = R.drawable.ic_baseline_tv_24;
        } else {
            typeDrawable = 0;
        }
        if (targetDrawable != mediaRouteTargetDrawable) {
            mediaRouteButton.setRemoteIndicatorDrawable(ContextCompat.getDrawable(
                    requireContext(),
                    targetDrawable
            ));
            mediaRouteTargetDrawable = targetDrawable;
        }
        if (typeDrawable != mediaRouteTypeDrawable) {
            mediaRouteIcon.setImageResource(typeDrawable);
            mediaRouteTypeDrawable = typeDrawable;
        }
        mediaRouteName.setText(name == null ? "unknown" : name);
    }

    private final MediaRouter.Callback mediaRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(@NonNull MediaRouter router,
                                    @NonNull MediaRouter.RouteInfo route,
                                    int reason) {
            setSelectedMediaRoute(route);
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            if (route.isSelected()) {
                setSelectedMediaRoute(route);
            }
        }
    };

    @Override
    public void onPause() {
        mediaRouter.removeCallback(mediaRouterCallback);
        mediaRouter = null;
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(LC, "onStop");
        remote.unregisterCallback(this);
    }

    @Override
    public void onDestroyView() {
        fastScroller.onDestroy();
        historyFastScroller.onDestroy();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        executor.shutdown();
    }

    @Override
    public void onSessionReady() {
        PlaybackStateCompat state = remote.getPlaybackState();
        updatePlaybackState(state);
        MediaMetadataCompat mediaMetadataCompat = remote.getCurrentMediaMetadata();
        if (mediaMetadataCompat != null) {
            EntryID entryID = EntryID.from(mediaMetadataCompat);
            setEntryID(entryID);
        }
        updateProgress();
        if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            scheduleProgressUpdate();
        }
        model.setCurrentPlaylist(remote.getCurrentPlaylist());
        model.setCurrentPlaylistPos(remote.getCurrentPlaylistPos());
        model.setQueue(remote.getQueue());
        updatePlaylistPlaybackMode(
                remote.isPlaylistPlaybackOrdered(),
                remote.isPlaylistPlaybackRandom(),
                remote.isPlaylistPlaybackRepeat()
        );
        refreshView(model.getState().getValue());
    }

    private void refreshView(NowPlayingState state) {
        if (remote == null || !remote.isSessionReady()) {
            Log.w(LC, "Media session not ready");
            return;
        }
        if (state == null) {
            return;
        }
        currentPlaylistIDLiveData.setValue(state.currentPlaylistID);
        Log.d(LC, "refreshView: queue(" + state.queue.size() + ")");
        recViewAdapter.setQueueEntries(state.queue);
    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {
        updatePlaybackState(state);
    }

    private void updatePlaybackState(PlaybackStateCompat state) {
        Drawable playDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.quantum_ic_play_arrow_grey600_36);
        playDrawable.setTint(ContextCompat.getColor(requireContext(), R.color.bluegrey900));
        Drawable pauseDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.quantum_ic_pause_grey600_36);
        pauseDrawable.setTint(ContextCompat.getColor(requireContext(), R.color.bluegrey900));

        playbackState = state;
        switch (state.getState()) { // TODO: Refactor to avoid repetition
            case PlaybackStateCompat.STATE_PLAYING:
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
            case PlaybackStateCompat.STATE_REWINDING:
                Log.d(LC, "state: playing");
                waveformSeekBar.setEnabled(true);
                playPauseBtn.setImageDrawable(pauseDrawable);
                bufferingView.setVisibility(INVISIBLE);
                isPlaying = true;
                scheduleProgressUpdate();
                break;
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
            case PlaybackStateCompat.STATE_PAUSED:
                waveformSeekBar.setEnabled(true);
                playPauseBtn.setImageDrawable(playDrawable);
                bufferingView.setVisibility(INVISIBLE);
                isPlaying = false;
                updateProgress();
                stopProgressUpdate();
                break;
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_BUFFERING:
                waveformSeekBar.setEnabled(true);
                playPauseBtn.setImageDrawable(playDrawable);
                bufferingView.setVisibility(VISIBLE);
                isPlaying = false;
                updateProgress();
                stopProgressUpdate();
                break;
            default:
                Log.w(LC, "Unknown playbackstate.\n"
                        + "contents: " + state.describeContents() + " actions: " + state.getActions()
                        + " queue id: " + state.getActiveQueueItemId() + " state: " + state.getState());
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
            case PlaybackStateCompat.STATE_ERROR:
                waveformSeekBar.setEnabled(false);
                playPauseBtn.setImageDrawable(playDrawable);
                bufferingView.setVisibility(INVISIBLE);
                isPlaying = false;
                stopProgressUpdate();
                break;
        }
    }

    @Override
    public void onMetadataChanged(EntryID entryID) {
        setEntryID(entryID);
    }

    @Override
    public void onCurrentEntryChanged(PlaybackEntry entry) {}

    @Override
    public void onSessionDestroyed() {}

    private void setEntryID(EntryID entryID) {
        if (!entryID.equals(entryIDLiveData.getValue())) {
            waveformSeekBar.resetData();
        }
        entryIDLiveData.setValue(entryID);
    }

    private void updateMeta(Meta meta) {
        nowPlaying.setEnabled(!meta.entryID.isUnknown());
        updateDescription(meta);
        updateMediaInfo(meta);
        updateDuration(meta);
    }

    private void updateDescription(Meta metadata) {
        String title = metadata.getAsString(Meta.FIELD_TITLE);
        String album = metadata.getAsString(Meta.FIELD_ALBUM);
        String artist = metadata.getAsString(Meta.FIELD_ARTIST);
        String trackNum = metadata.getAsString(Meta.FIELD_TRACKNUMBER);
        String discNum = metadata.getAsString(Meta.FIELD_DISCNUMBER);
        if (title == null || title.isEmpty()) {
            title = "Unknown title";
        }
        if (album == null || album.isEmpty()) {
            album = "";
        } else {
            String year = metadata.getAsString(Meta.FIELD_YEAR);
            if (!trackNum.isEmpty()) {
                album = String.format(Locale.getDefault(), "#%s, %s", trackNum, album);
            }
            if (!discNum.isEmpty()) {
                album = String.format(Locale.getDefault(), "%s (%s)", album, discNum);
            }
            if (!year.isEmpty()) {
                album = String.format(Locale.getDefault(), "%s - %s", album, year);
            }
        }
        if (artist == null || artist.isEmpty()) {
            artist = "Unknown artist";
        }
        nowPlayingTitle.setText(title);
        nowPlayingAlbum.setText(album);
        nowPlayingArtist.setText(artist);
    }

    private void updateMediaInfo(Meta metadata) {
        String formattedFileSize = metadata.getFormattedFileSize();
        if (formattedFileSize != null) {
            sizeText.setText(formattedFileSize);
            sizeText.setVisibility(VISIBLE);
        } else {
            sizeText.setVisibility(INVISIBLE);
        }
        String contentType = metadata.getFirstString(Meta.FIELD_CONTENT_TYPE);
        long bitrate = metadata.getFirstLong(Meta.FIELD_BITRATE, -1);
        ArrayList<String> info = new ArrayList<>();
        if (!contentType.isEmpty()) {
            info.add(contentType);
        }
        if (bitrate >= 0) {
            info.add(Meta.getDisplayValue(Meta.FIELD_BITRATE, bitrate));
        }
        mediaInfoText.setText(String.join(" ", info));
    }

    @Override
    public void onMediaBrowserConnected() {}

    private void updateProgress() {
        long pos = playbackState.getPosition();
        if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            long elapsed = SystemClock.elapsedRealtime() -
                    playbackState.getLastPositionUpdateTime();
            pos += elapsed * playbackState.getPlaybackSpeed();
        }
        if (pos > waveformSeekBar.getMax()) {
            waveformSeekBar.setProgress(waveformSeekBar.getMax());
            positionText.setText(Meta.getDurationString(waveformSeekBar.getMax()));
            stopProgressUpdate();
        } else {
            waveformSeekBar.setProgress((int) pos);
            positionText.setText(Meta.getDurationString(pos));
        }
    }

    private void updateDuration(Meta metadata) {
        int duration = (int) metadata.getFirstLong(Meta.FIELD_DURATION, 0);
        waveformSeekBar.setMax(duration);
        durationText.setText(Meta.getDurationString(duration));
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private final Handler handler = new Handler();

    private void scheduleProgressUpdate() {
        stopProgressUpdate();
        if (!executor.isShutdown()) {
            Log.d(LC, "schedule progress update");
            scheduledFuture = executor.scheduleAtFixedRate(
                    () -> handler.post(this::updateProgress),
                    PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void stopProgressUpdate() {
        if (scheduledFuture != null) {
            Log.d(LC, "stop progress update");
            scheduledFuture.cancel(false);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        selectionTracker.onSaveInstanceState(outState);
        historySelectionTracker.onSaveInstanceState(outState);
    }

    @Override
    public void onQueueChanged(List<PlaybackEntry> queue) {
        model.setQueue(queue);
    }

    @Override
    public void onPlaylistSelectionChanged(EntryID playlistID, long pos) {
        model.setCurrentPlaylist(playlistID);
        model.setCurrentPlaylistPos(pos);
    }

    @Override
    public void onPlaylistPlaybackOrderChanged(boolean ordered) {
        updatePlaylistPlaybackMode(
                ordered,
                remote.isPlaylistPlaybackRandom(),
                remote.isPlaylistPlaybackRepeat()
        );
    }

    @Override
    public void onPlaylistPlaybackRandomChanged(boolean random) {
        updatePlaylistPlaybackMode(
                remote.isPlaylistPlaybackOrdered(),
                random,
                remote.isPlaylistPlaybackRepeat()
        );
    }

    @Override
    public void onPlaylistPlaybackRepeatModeChanged(boolean repeat) {
        updatePlaylistPlaybackMode(
                remote.isPlaylistPlaybackOrdered(),
                remote.isPlaylistPlaybackRandom(),
                repeat
        );
    }

    private void updatePlaylistPlaybackMode(boolean ordered,
                                            boolean random,
                                            boolean repeat) {
        currentPlaylistOrdered.setImageResource(ordered
                ? R.drawable.ic_sort_black_24dp
                : R.drawable.ic_shuffle_black_24dp
        );
        if (random) {
            currentPlaylistMode.setImageResource(R.drawable.ic_gesture_black_24dp);
        } else if (repeat) {
            currentPlaylistMode.setImageResource(R.drawable.ic_repeat_black_24dp);
        } else {
            currentPlaylistMode.setImageResource(0);
        }
    }

    public void clearSelection() {
        if (selectionTracker != null) {
            selectionTracker.clearSelection();
        }
        if (historySelectionTracker != null) {
            historySelectionTracker.clearSelection();
        }
        if (recViewAdapter != null) {
            recViewAdapter.hideTrackItemActions();
        }
        if (historyRecViewAdapter!= null) {
            historyRecViewAdapter.hideTrackItemActions();
        }
    }
}