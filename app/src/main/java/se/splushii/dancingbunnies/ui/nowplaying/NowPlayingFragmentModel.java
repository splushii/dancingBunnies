package se.splushii.dancingbunnies.ui.nowplaying;

import android.content.Context;

import java.util.HashSet;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.AudioStorage;

class NowPlayingFragmentModel extends ViewModel {
    private LiveData<List<AudioStorage.AudioDataFetchState>> fetchState;
    private LiveData<List<EntryID>> cachedEntries;

    LiveData<List<AudioStorage.AudioDataFetchState>> getFetchState(Context context) {
        if (fetchState == null) {
            fetchState = AudioStorage.getInstance(context).getFetchState();
        }
        return fetchState;
    }

    LiveData<HashSet<EntryID>> getCachedEntries(Context context) {
        if (cachedEntries == null) {
            cachedEntries = AudioStorage.getInstance(context).getCachedEntries();
        }
        return Transformations.map(cachedEntries, HashSet::new);
    }
}