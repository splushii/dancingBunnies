package se.splushii.dancingbunnies.ui.nowplaying;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(NowPlayingFragmentModel.class);
    private MutableLiveData<NowPlayingState> state;

    LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> getFetchState(Context context) {
        return AudioStorage.getInstance(context).getFetchState();
    }

    LiveData<HashSet<EntryID>> getCachedEntries(Context context) {
        MusicLibraryQuery query = new MusicLibraryQuery();
        query.setShowField(Meta.FIELD_SPECIAL_MEDIA_ID);
        query.setSortByField(Meta.FIELD_TITLE);
        query.addToQuery(Meta.FIELD_LOCAL_CACHED, Meta.FIELD_LOCAL_CACHED_VALUE_YES);
        return Transformations.map(
                MetaStorage.getInstance(context).getEntries(
                        query.getShowField(),
                        query.getSortByFields(),
                        query.isSortOrderAscending(),
                        query.getQueryBundle()
                ),
                libraryEntries -> libraryEntries.stream()
                        .map(EntryID::from)
                        .collect(Collectors.toCollection(HashSet::new))
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