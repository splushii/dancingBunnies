package se.splushii.dancingbunnies.ui.nowplaying;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.storage.AudioStorage;

class NowPlayingFragmentModel extends ViewModel {
    private LiveData<List<AudioStorage.AudioDataFetchState>> fetchState;

    LiveData<List<AudioStorage.AudioDataFetchState>> getFetchState() {
        if (fetchState == null) {
            fetchState = AudioStorage.getInstance().getFetchState();
        }
        return fetchState;
    }
}