package se.splushii.dancingbunnies.ui.nowplaying;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;

class NowPlayingFragmentModel extends ViewModel {
    private MutableLiveData<NowPlayingState> state;

    LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> getFetchState(Context context) {
        return AudioStorage.getInstance(context).getFetchState();
    }

    LiveData<HashSet<EntryID>> getCachedEntries(Context context) {
        return Transformations.map(
                AudioStorage.getInstance(context).getCachedEntries(),
                HashSet::new
        );
    }

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

    public void setQueue(List<PlaybackEntry> queue) {
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

    void setCurrentPlaylistEntry(PlaylistEntry playlistEntry) {
        getMutableState().setValue(new NowPlayingState.Builder()
                .fromState(getStateValue())
                .setCurrentPlaylistEntry(playlistEntry)
                .build()
        );
    }
}