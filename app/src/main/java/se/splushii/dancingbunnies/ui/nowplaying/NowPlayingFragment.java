package se.splushii.dancingbunnies.ui.nowplaying;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingFragment extends AudioBrowserFragment {
    private static final String LC = Util.getLogContext(NowPlayingFragment.class);
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;
    private static final long PROGRESS_UPDATE_INTERNAL = 1000;

    private TextView nowPlayingTitle;
    private TextView nowPlayingArtist;
    private TextView nowPlayingAlbum;
    private ImageButton playPauseBtn;
    private boolean isPlaying = false;
    private SeekBar seekBar;
    private PlaybackStateCompat playbackState;
    private TextView positionText;
    private TextView durationText;
    private TextView mediaInfoText;
    private TextView bufferingText;
    private MediaMetadataCompat currentMeta;

    private NowPlayingEntriesAdapter recViewAdapter;
    private RecyclerView recView;
    private SelectionTracker<Long> selectionTracker;
    private ActionMode mActionMode;

    public NowPlayingFragment() {
        recViewAdapter = new NowPlayingEntriesAdapter(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.nowplaying_fragment_layout, container,
                false);

        recView = rootView.findViewById(R.id.nowplaying_recyclerview);
        recView.setHasFixedSize(true);
        LinearLayoutManager recViewLayoutManager =
                new LinearLayoutManager(this.getContext());
        recViewLayoutManager.setReverseLayout(true);
        recView.setLayoutManager(recViewLayoutManager);
        recView.setAdapter(recViewAdapter);

        // TODO:
        selectionTracker = new SelectionTracker.Builder<>(
                MainActivity.SELECTION_ID_NOWPLAYING,
                recView,
                new NowPlayingKeyProvider(),
                new NowPlayingDetailsLookup(recView),
                StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
                SelectionPredicates.createSelectAnything()
        ).withOnItemActivatedListener((item, e) -> {
            // Respond to taps/enter/double-click on items.
            Log.e(LC, "ACTION: " + MotionEvent.actionToString(e.getAction())
                    + " SELECTION: [" + item.getPosition() + "] " + item.getSelectionKey());
            return false;
        }).withOnContextClickListener(e -> {
            // Respond to right-click.
            Log.e(LC, "ACTION_YEAH: " + MotionEvent.actionToString(e.getAction()));
            return false;
        }).withOnDragInitiatedListener(e -> {
            // Add support for drag and drop.
            Log.e(LC, "DRAG INITIATED: " + MotionEvent.actionToString(e.getAction()));
            View view = recView.findChildViewUnder(e.getX(), e.getY());
            view.startDragAndDrop(null, new View.DragShadowBuilder(view), null, 0);
            return true;
        }).build();
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onItemStateChanged(@NonNull Object key, boolean selected) {
                Log.e(LC, "super.onItemStateChanged("
                        + key.toString() + ", " + selected + ");");
                printSelection();
            }

            @Override
            public void onSelectionRefresh() {
                Log.e(LC, "super.onSelectionRefresh();");
                printSelection();
            }

            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection() && mActionMode == null) {
                    mActionMode = getActivity().startActionMode(actionModeCallback);
                }
                if (!selectionTracker.hasSelection() && mActionMode != null) {
                    mActionMode.finish();
                }
                Log.e(LC, "super.onSelectionChanged();");
                printSelection();
            }

            @Override
            public void onSelectionRestored() {
                Log.e(LC, "super.onSelectionRestored();");
                printSelection();
            }

            private void printSelection() {
                Log.e(LC, "selection: " + selectionTracker.getSelection().toString());
            }
        });
        recViewAdapter.setSelectionTracker(selectionTracker);
        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        nowPlayingTitle = rootView.findViewById(R.id.nowplaying_title);
        nowPlayingArtist = rootView.findViewById(R.id.nowplaying_artist);
        nowPlayingAlbum = rootView.findViewById(R.id.nowplaying_album);
        ImageButton previousBtn = rootView.findViewById(R.id.nowplaying_previous);
        previousBtn.setOnClickListener(view -> previous());
        isPlaying = false;
        playPauseBtn = rootView.findViewById(R.id.nowplaying_play_pause);
        playPauseBtn.setOnClickListener(view -> {
            if (isPlaying) {
                pause();
                stopProgressUpdate();
            } else {
                play();
                scheduleProgressUpdate();
            }
        });
        ImageButton nextBtn = rootView.findViewById(R.id.nowplaying_next);
        nextBtn.setOnClickListener(view -> next());
        seekBar = rootView.findViewById(R.id.nowplaying_seekbar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    positionText.setText(getDurationString(progress));
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
                seekTo(seekBar.getProgress());
            }
        });
        positionText = rootView.findViewById(R.id.nowplaying_position);
        durationText = rootView.findViewById(R.id.nowplaying_duration);
        mediaInfoText = rootView.findViewById(R.id.nowplaying_media_info);
        bufferingText = rootView.findViewById(R.id.nowplaying_buffering);
        return rootView;
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
    public void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        executor.shutdown();
    }

    @Override
    protected void onSessionReady() {
        super.onSessionReady();
        refreshView();
    }

    private void refreshView() {
        if (mediaController == null || !mediaController.isSessionReady()) {
            Log.w(LC, "Media session not ready");
            return;
        }
        getCurrentPlaylist().thenAccept(opt -> opt.ifPresent(
                recViewAdapter::setCurrentPlaylistItem
        ));
        getPlaylistNext(3).thenAccept(opt -> opt.ifPresent(entries -> {
            Log.d(LC, "refreshView: playlist(" + entries.size() + ")");
            recViewAdapter.setPlaylistNext(entries);
        }));
        // TODO: Implement playbackhistory in AudioPlayerService/PlaybackController, then in UI.
        //getPlaybackHistory().thenAccept(opt -> opt.ifPresent(recViewAdapter::setPlaybackHistory));
        List<PlaybackEntry> queue = getQueue();
        Log.d(LC, "refreshView: queue(" + queue.size() + ")");
        recViewAdapter.setQueue(queue);
    }

    private List<PlaybackEntry> getQueue() {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        for (MediaSessionCompat.QueueItem queueItem: mediaController.getQueue()) {
            MediaMetadataCompat meta = Meta.desc2meta(queueItem.getDescription());
            PlaybackEntry playbackEntry = new PlaybackEntry(meta, PlaybackEntry.USER_TYPE_QUEUE);
            playbackEntries.add(playbackEntry);
        }
        return playbackEntries;
    }

    @Override
    protected void onPlaybackStateChanged(PlaybackStateCompat state) {
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
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setImageDrawable(pauseDrawable);
                bufferingText.setVisibility(View.INVISIBLE);
                isPlaying = true;
                scheduleProgressUpdate();
                break;
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
            case PlaybackStateCompat.STATE_PAUSED:
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setImageDrawable(playDrawable);
                bufferingText.setVisibility(View.INVISIBLE);
                isPlaying = false;
                updateProgress();
                stopProgressUpdate();
                break;
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_BUFFERING:
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setImageDrawable(playDrawable);
                bufferingText.setVisibility(View.VISIBLE);
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
                playPauseBtn.setEnabled(
                        currentMeta != null
                                && !Meta.UNKNOWN_ENTRY.equals(currentMeta)
                );
                seekBar.setEnabled(false);
                playPauseBtn.setImageDrawable(playDrawable);
                bufferingText.setVisibility(View.INVISIBLE);
                isPlaying = false;
                stopProgressUpdate();
                break;
        }
    }

    @Override
    protected void onMetadataChanged(MediaMetadataCompat metadata) {
        currentMeta = metadata;
        updateDescription(metadata);
        updateDuration(metadata);
        updateProgress();
    }

    private void updateDescription(MediaMetadataCompat metadata) {
        updateMediaInfo(metadata);
        String title = metadata.getString(Meta.METADATA_KEY_TITLE);
        String album = metadata.getString(Meta.METADATA_KEY_ALBUM);
        String artist = metadata.getString(Meta.METADATA_KEY_ARTIST);
        if (title == null || title.equals(Meta.METADATA_VALUE_UNKNOWN_TITLE)) {
            title = "Unknown title";
        }
        if (album == null || album.equals(Meta.METADATA_VALUE_UNKNOWN_ALBUM)) {
            album = "Unknown album";
        } else {
            long year = metadata.getLong(Meta.METADATA_KEY_YEAR);
            if (year != 0L) {
                album = String.format(Locale.getDefault(), "%s - %d", album, year);
            }
        }
        if (artist == null || artist.equals(Meta.METADATA_VALUE_UNKNOWN_ARTIST)) {
            artist = "Unknown artist";
        }
        nowPlayingTitle.setText(title);
        nowPlayingAlbum.setText(album);
        nowPlayingArtist.setText(artist);
    }

    private void updateMediaInfo(MediaMetadataCompat metadata) {
        String contentType = metadata.getString(Meta.METADATA_KEY_CONTENT_TYPE);
        String suffix = metadata.getString(Meta.METADATA_KEY_FILE_SUFFIX);
        long bitrate = metadata.getLong(Meta.METADATA_KEY_BITRATE);
        ArrayList<String> info = new ArrayList<>();
        if (contentType != null) {
            info.add(contentType);
        }
        if (suffix != null) {
            info.add(suffix);
        }
        if (bitrate != 0) {
            info.add(String.format(Locale.getDefault(), "%dkbps", bitrate));
        }
        mediaInfoText.setText(String.join(" ", info));
    }

    @Override
    protected void onMediaBrowserConnected() {
        PlaybackStateCompat state = mediaController.getPlaybackState();
        updatePlaybackState(state);
        MediaMetadataCompat metadata = mediaController.getMetadata();
        if (metadata != null) {
            updateDescription(metadata);
            updateDuration(metadata);
        }
        updateProgress();
        if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            scheduleProgressUpdate();
        }
    }

    private void updateProgress() {
        long pos = playbackState.getPosition();
        if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            long elapsed = SystemClock.elapsedRealtime() -
                    playbackState.getLastPositionUpdateTime();
            pos += elapsed * playbackState.getPlaybackSpeed();
        }
        if (pos > seekBar.getMax()) {
            seekBar.setProgress(seekBar.getMax());
            positionText.setText(getDurationString(seekBar.getMax()));
            stopProgressUpdate();
        }
        seekBar.setProgress((int) pos);
        positionText.setText(getDurationString(pos));
    }

    private String getDurationString(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds %= 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void updateDuration(MediaMetadataCompat metadata) {
        int duration = (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        seekBar.setMax(duration);
        durationText.setText(getDurationString(duration));
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;
    private final Handler handler = new Handler();
    private final Runnable updateProgressTask = this::updateProgress;

    private void scheduleProgressUpdate() {
        stopProgressUpdate();
        if (!executor.isShutdown()) {
            Log.d(LC, "schedule progress update");
            scheduledFuture = executor.scheduleAtFixedRate(
                    () -> handler.post(updateProgressTask),
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater menuInflater = getActivity().getMenuInflater();
        menuInflater.inflate(R.menu.nowplaying_queueitem_contextmenu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int menuItemId = item.getItemId();
        if (menuItemId != R.id.nowplaying_queueitem_contextmenu_play
                && menuItemId != R.id.nowplaying_queueitem_contextmenu_dequeue) {
            return false;
        }
        int position = recViewAdapter.getContextMenuHolder().getAdapterPosition();
        EntryID entryID = recViewAdapter.getItemData(position).entryID;
        Log.d(LC, "info pos: " + position);
        switch (menuItemId) {
            case R.id.nowplaying_queueitem_contextmenu_play:
                skipItems(position);
                play();
                Log.d(LC, "nowplaying context play");
            case R.id.nowplaying_queueitem_contextmenu_dequeue:
                dequeue(entryID, position);
                Log.d(LC, "nowplaying context dequeue");
        }
        return true;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(recView);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        selectionTracker.onSaveInstanceState(outState);
    }

    @Override
    protected void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
        refreshView();
    }

    @Override
    protected void onSessionEvent(String event, Bundle extras) {
        switch (event) {
            case AudioPlayerService.SESSION_EVENT_PLAYLIST_POSITION_CHANGED:
            case AudioPlayerService.SESSION_EVENT_PLAYLIST_CHANGED:
                refreshView();
                break;
        }
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.nowplaying_actionmode_menu, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.nowplaying_actionmode_action_play:
                    Log.e(LC, "actionmode action play");
                    MutableSelection<Long> selection = new MutableSelection<>();
                    selectionTracker.copySelection(selection);
                    List<EntryID> entryIDs = new LinkedList<>();
                    for (Long key: selection) {
                        entryIDs.add(recViewAdapter.getPlaybackEntry(key).entryID);
                    }
                    queue(entryIDs);
                    // TODO:
//                    next();
//                    play();
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionTracker.clearSelection();
            mActionMode = null;
        }
    };
}