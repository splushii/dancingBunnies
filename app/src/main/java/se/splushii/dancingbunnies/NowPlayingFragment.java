package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingFragment extends AudioBrowserFragment {
    private static final String LC = Util.getLogContext(NowPlayingFragment.class);

    private TextView nowPlayingText;

    public NowPlayingFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.nowplaying_fragment_layout, container,
                false);
        nowPlayingText = rootView.findViewById(R.id.nowplaying_text);
        nowPlayingText.setText("");
        Button previousBtn = rootView.findViewById(R.id.nowplaying_previous);
        previousBtn.setOnClickListener(view -> previous());
        Button pauseBtn = rootView.findViewById(R.id.nowplaying_pause);
        pauseBtn.setOnClickListener(view -> pause());
        Button playBtn = rootView.findViewById(R.id.nowplaying_play);
        playBtn.setOnClickListener(view -> play());
        Button nextBtn = rootView.findViewById(R.id.nowplaying_next);
        nextBtn.setOnClickListener(view -> next());
        return rootView;
    }

    @Override
    protected void onPlaybackStateChanged(PlaybackStateCompat state) {
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PAUSED:
                Log.d(LC, "state: paused");
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                Log.d(LC, "state: playing");
                break;
            default:
                Log.w(LC, "Unknown playbackstate.\n"
                        + "contents: " + state.describeContents() + " actions: " + state.getActions()
                        + " queue id: " + state.getActiveQueueItemId() + " state: " + state.getState());
        }
    }

    @Override
    protected void onMetadataChanged(MediaMetadataCompat metadata) {
        String description = Meta.getLongDescription(metadata);
        Log.d(LC, "meta: " + description);
        nowPlayingText.setText(description);
    }

    @Override
    protected void onSessionEvent(String event, Bundle extras) {
        Log.d(LC, "event: " + event);
    }
}