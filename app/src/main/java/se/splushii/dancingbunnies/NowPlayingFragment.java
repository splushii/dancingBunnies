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
        TextView nowPlayingText = (TextView) rootView.findViewById(R.id.nowplaying_text);
        nowPlayingText.setText(nowPlaying);
        Button previousBtn = (Button) rootView.findViewById(R.id.nowplaying_previous);
        previousBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault()
                        .post(new PlaybackEvent(PlaybackEvent.PlaybackAction.PREVIOUS));
            }
        });
        Button pauseBtn = (Button) rootView.findViewById(R.id.nowplaying_pause);
        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new PlaybackEvent(PlaybackEvent.PlaybackAction.PAUSE));
            }
        });
        Button playBtn = (Button) rootView.findViewById(R.id.nowplaying_play);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new PlaybackEvent(PlaybackEvent.PlaybackAction.PLAY));
            }
        });
        Button nextBtn = (Button) rootView.findViewById(R.id.nowplaying_next);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new PlaybackEvent(PlaybackEvent.PlaybackAction.NEXT));
            }
        });
        return rootView;
    }

    @Subscribe
    public void onMessageEvent(PlaySongEvent pse) {
        nowPlaying = pse.id;
        TextView nowPlayingText;
        nowPlayingText = (TextView) getView().findViewById(R.id.nowplaying_text);
        nowPlayingText.setText(nowPlaying);
    }
}