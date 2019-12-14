package se.splushii.dancingbunnies.ui.nowplaying;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(NowPlayingFragmentModel.class);
    private MutableLiveData<NowPlayingState> state;

    private static NowPlayingState initialUserState() {
        return new NowPlayingState.Builder().build();
    }

    private synchronized MutableLiveData<NowPlayingState> getMutableState() {
        if (state == null) {
            state = new MutableLiveData<>();
            state.setValue(initialUserState());
        }
        return state;
    }

    LiveData<NowPlayingState> getState() {
        return getMutableState();
    }

    private NowPlayingState getStateValue() {
        return getState().getValue();
    }

    void setQueue(List<PlaybackEntry> queue) {
        getMutableState().setValue(new NowPlayingState.Builder()
                .fromState(getStateValue())
                .setQueue(queue)
                .build()
        );
    }

    void setCurrentPlaylist(PlaylistID playlistID) {
        getMutableState().setValue(new NowPlayingState.Builder()
                .fromState(getStateValue())
                .setCurrentPlaylist(playlistID)
                .build()
        );
    }

    void setCurrentPlaylistPos(long playlistPos) {
        getMutableState().setValue(new NowPlayingState.Builder()
                .fromState(getStateValue())
                .setCurrentPlaylistPos(playlistPos)
                .build()
        );
    }
}