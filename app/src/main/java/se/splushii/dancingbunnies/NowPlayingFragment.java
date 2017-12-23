package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import se.splushii.dancingbunnies.events.PlaySongEvent;
import se.splushii.dancingbunnies.events.PlaybackEvent;

public class NowPlayingFragment extends Fragment {
    String nowPlaying = "SONG INFO";

    public NowPlayingFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.nowplaying_fragment_layout, container,
                false);
        TextView nowPlayingText = rootView.findViewById(R.id.nowplaying_text);
        nowPlayingText.setText(nowPlaying);
        Button previousBtn = rootView.findViewById(R.id.nowplaying_previous);
        previousBtn.setOnClickListener(view -> EventBus.getDefault()
                .post(new PlaybackEvent(PlaybackEvent.PlaybackAction.PREVIOUS)));
        Button pauseBtn = rootView.findViewById(R.id.nowplaying_pause);
        pauseBtn.setOnClickListener(view ->
                EventBus.getDefault().post(new PlaybackEvent(PlaybackEvent.PlaybackAction.PAUSE)));
        Button playBtn = rootView.findViewById(R.id.nowplaying_play);
        playBtn.setOnClickListener(view ->
                EventBus.getDefault().post(new PlaybackEvent(PlaybackEvent.PlaybackAction.PLAY)));
        Button nextBtn = rootView.findViewById(R.id.nowplaying_next);
        nextBtn.setOnClickListener(view ->
                EventBus.getDefault().post(new PlaybackEvent(PlaybackEvent.PlaybackAction.NEXT)));
        return rootView;
    }

    @Subscribe
    public void onMessageEvent(PlaySongEvent pse) {
        nowPlaying = pse.id;
        TextView nowPlayingText;
        nowPlayingText = getView().findViewById(R.id.nowplaying_text);
        nowPlayingText.setText(nowPlaying);
    }
}