package se.splushii.dancingbunnies.ui.nowplaying;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.AudioStorage;

class NowPlayingFragmentModel extends ViewModel {
    LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> getFetchState(Context context) {
        return AudioStorage.getInstance(context).getFetchState();
    }

    LiveData<HashSet<EntryID>> getCachedEntries(Context context) {
        return Transformations.map(
                AudioStorage.getInstance(context).getCachedEntries(),
                HashSet::new
        );
    }
}