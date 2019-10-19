package se.splushii.dancingbunnies.ui.playlist;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.core.util.Pair;
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
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(PlaylistFragmentModel.class);

    private MutableLiveData<PlaylistUserState> userState;

    private MutableLiveData<PlaylistID> currentPlaylistID;
    private MutableLiveData<PlaybackEntry> currentEntry;
    private MutableLiveData<Long> currentPlaylistPos;

    private static PlaylistUserState initialUserState() {
        return new PlaylistUserState.Builder().build();
    }

    private synchronized MutableLiveData<PlaylistUserState> getMutableUserState() {
        if (userState == null) {
            userState = new MutableLiveData<>();
            userState.setValue(initialUserState());
        }
        return userState;
    }

    LiveData<PlaylistUserState> getUserState() {
        return getMutableUserState();
    }

    PlaylistUserState getUserStateValue() {
        return getUserState().getValue();
    }

    void savePlaylistScroll(Pair<Integer, Integer> recyclerViewPosition) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setPlaylistScroll(recyclerViewPosition.first, recyclerViewPosition.second)
                .build()
        );
    }

    void savePlaylistEntriesScroll(Pair<Integer, Integer> recyclerViewPosition) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setPlaylistEntriesScroll(recyclerViewPosition.first, recyclerViewPosition.second)
                .build()
        );
    }
    void savePlaylistPlaybackEntriesScroll(Pair<Integer, Integer> recyclerViewPosition) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setPlaylistPlaybackEntriesScroll(recyclerViewPosition.first, recyclerViewPosition.second)
                .build()
        );
    }

    LiveData<List<Playlist>> getPlaylists(Context context) {
        return PlaylistStorage.getInstance(context).getPlaylists();
    }

    LiveData<Playlist> getPlaylist(Context context, PlaylistID playlistID) {
        return PlaylistStorage.getInstance(context).getPlaylist(playlistID);
    }

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
                        query.getSortByField(),
                        query.isSortOrderAscending(),
                        query.getQueryBundle()
                ),
                libraryEntries -> libraryEntries.stream()
                        .map(EntryID::from)
                        .collect(Collectors.toCollection(HashSet::new))
        );
    }

    private synchronized MutableLiveData<PlaylistID> getMutableCurrentPlaylist() {
        if (currentPlaylistID == null) {
            currentPlaylistID = new MutableLiveData<>();
        }
        return currentPlaylistID;
    }

    void setCurrentPlaylist(PlaylistID playlistID) {
        getMutableCurrentPlaylist().setValue(playlistID);
    }

    LiveData<PlaylistID> getCurrentPlaylistID() {
        return getMutableCurrentPlaylist();
    }

    private synchronized MutableLiveData<PlaybackEntry> getMutableCurrentEntry() {
        if (currentEntry == null) {
            currentEntry = new MutableLiveData<>();
        }
        return currentEntry;
    }

    void setCurrentEntry(PlaybackEntry entry) {
        getMutableCurrentEntry().setValue(entry);
    }

    LiveData<PlaybackEntry> getCurrentEntry() {
        return getMutableCurrentEntry();
    }

    private synchronized MutableLiveData<Long> getMutableCurrentPlaylistPos() {
        if (currentPlaylistPos == null) {
            currentPlaylistPos = new MutableLiveData<>();
        }
        return currentPlaylistPos;
    }

    void setCurrentPlaylistPos(long playlistPos) {
        getMutableCurrentPlaylistPos().setValue(playlistPos);
    }

    LiveData<Long> getCurrentPlaylistPos() {
        return getMutableCurrentPlaylistPos();
    }

    boolean isBrowsedCurrent() {
        PlaylistID currentPlaylistID = getCurrentPlaylistID().getValue();
        PlaylistUserState userState = getUserStateValue();
        return userState != null && userState.isBrowsedCurrent(currentPlaylistID);
    }

    public void goToPlaylistPlaybackAtPlaylistPos(PlaylistID playlistID, long playlistPos) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setBrowsedPlaylist(playlistID)
                .setShowPlaylistPlaybackEntries(true)
                .setScrollPlaylistPlaybackToPlaylistPos(true)
                .setPlaylistPlaybackEntriesScroll((int) playlistPos, 0)
                .build()
        );
    }

    void unsetScrollPlaylistPlaybackToPlaylistPos() {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                        .fromState(getUserStateValue())
                        .setScrollPlaylistPlaybackToPlaylistPos(false)
                        .build()
        );
    }

    void browsePlaylists() {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setShowPlaylists(true)
                .build()
        );
    }

    void browsePlaylist(PlaylistID playlistID) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setBrowsedPlaylist(playlistID)
                .setShowPlaylistPlaybackEntries(isBrowsedCurrent())
                .build()
        );
    }

    void showPlaylistPlaybackEntries(boolean show) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setShowPlaylistPlaybackEntries(show)
                .build()
        );
    }
}
