package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.ui.NowPlayingEntriesAdapter;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingFragment extends AudioBrowserFragment {
    private static final String LC = Util.getLogContext(NowPlayingFragment.class);
    private static final long PROGRESS_UPDATE_INITIAL_INTERVAL = 100;
    private static final long PROGRESS_UPDATE_INTERNAL = 1000;

    private TextView nowPlayingTitle;
    private TextView nowPlayingArtist;
    private TextView nowPlayingAlbum;
    private Button playPauseBtn;
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

    public NowPlayingFragment() {
        recViewAdapter = new NowPlayingEntriesAdapter();
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

        nowPlayingTitle = rootView.findViewById(R.id.nowplaying_title);
        nowPlayingArtist = rootView.findViewById(R.id.nowplaying_artist);
        nowPlayingAlbum = rootView.findViewById(R.id.nowplaying_album);
        Button previousBtn = rootView.findViewById(R.id.nowplaying_previous);
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
        Button nextBtn = rootView.findViewById(R.id.nowplaying_next);
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
        List<MediaSessionCompat.QueueItem> queue = getQueue();
        Log.d(LC, "refreshView: queue(" + queue.size() + ")");
        recViewAdapter.setQueue(queue);
    }

    private List<MediaSessionCompat.QueueItem> getQueue() {
        List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
        if (queue == null) {
            Log.w(LC, "queue is null");
            return new LinkedList<>();
        }
        return queue;
    }

    @Override
    protected void onPlaybackStateChanged(PlaybackStateCompat state) {
        updatePlaybackState(state);
    }

    private void updatePlaybackState(PlaybackStateCompat state) {
        playbackState = state;
        switch (state.getState()) { // TODO: Refactor to avoid repetition
            case PlaybackStateCompat.STATE_PLAYING:
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
            case PlaybackStateCompat.STATE_REWINDING:
                Log.d(LC, "state: playing");
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setBackgroundResource(R.drawable.pause);
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
                playPauseBtn.setBackgroundResource(R.drawable.play);
                bufferingText.setVisibility(View.INVISIBLE);
                isPlaying = false;
                updateProgress();
                stopProgressUpdate();
                break;
            case PlaybackStateCompat.STATE_CONNECTING:
            case PlaybackStateCompat.STATE_BUFFERING:
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setBackgroundResource(R.drawable.play);
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
                playPauseBtn.setBackgroundResource(R.drawable.play);
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
        menuInflater.inflate(R.menu.nowplaying_item_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = recViewAdapter.getContextMenuHolder().getAdapterPosition();
        EntryID entryID = EntryID.from(recViewAdapter.getItemData(position));
        Log.d(LC, "info pos: " + position);
        switch (item.getItemId()) {
            case R.id.nowplaying_item_context_play:
                skipTo(position);
                play();
                Log.d(LC, "nowplaying context play");
                return true;
            case R.id.nowplaying_item_context_dequeue:
                dequeue(entryID, position);
                Log.d(LC, "nowplaying context dequeue");
                return true;
            default:
                Log.d(LC, "nowplaying context unknown");
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registerForContextMenu(recView);
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
}