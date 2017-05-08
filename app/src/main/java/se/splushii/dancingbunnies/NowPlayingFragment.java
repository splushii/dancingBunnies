package se.splushii.dancingbunnies;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import se.splushii.dancingbunnies.events.PlaySongEvent;

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
        return rootView;
    }

    @Subscribe
    public void onMessageEvent(PlaySongEvent pse) {
        nowPlaying = pse.song.name();
        TextView nowPlayingText;
        try {
            nowPlayingText = (TextView) getView().findViewById(R.id.nowplaying_text);
            nowPlayingText.setText(nowPlaying);
        } catch (NullPointerException e) {
            // Do nothing. Wait for onCreateView
        }
    }
}