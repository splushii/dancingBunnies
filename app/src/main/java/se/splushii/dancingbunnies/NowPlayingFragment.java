package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.musiclibrary.Meta;
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

    public NowPlayingFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.nowplaying_fragment_layout, container,
                false);
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
    protected void onPlaybackStateChanged(PlaybackStateCompat state) {
        updatePlaybackState(state);
    }

    private void updatePlaybackState(PlaybackStateCompat state) {
        playbackState = state;
        switch (state.getState()) { // TODO: look at this...
            case PlaybackStateCompat.STATE_PLAYING:
                Log.d(LC, "state: playing");
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setText("||");
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
                playPauseBtn.setText(">");
                bufferingText.setVisibility(View.INVISIBLE);
                isPlaying = false;
                updateProgress();
                stopProgressUpdate();
                break;
            case PlaybackStateCompat.STATE_BUFFERING:
                playPauseBtn.setEnabled(true);
                seekBar.setEnabled(true);
                playPauseBtn.setText(">");
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
                playPauseBtn.setEnabled(false);
                seekBar.setEnabled(false);
                bufferingText.setVisibility(View.INVISIBLE);
                isPlaying = false;
                stopProgressUpdate();
                break;
        }
    }

    @Override
    protected void onMetadataChanged(MediaMetadataCompat metadata) {
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
    protected void onSessionEvent(String event, Bundle extras) {
        Log.d(LC, "event: " + event);
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
}