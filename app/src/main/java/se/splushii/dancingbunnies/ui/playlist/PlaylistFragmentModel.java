package se.splushii.dancingbunnies.ui.playlist;

import android.content.Context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(PlaylistFragmentModel.class);

    private MutableLiveData<PlaylistUserState> userState;
    private LinkedList<PlaylistUserState> backStack;

    private MutableLiveData<PlaylistID> currentPlaylistID;
    private MutableLiveData<PlaybackEntry> currentPlaylistEntry;
    private MutableLiveData<PlaybackEntry> currentEntry;

    private static PlaylistUserState initialUserState() {
        return new PlaylistUserState.Builder().build();
    }

    boolean isUserStateInitial() {
        return getUserStateValue().isInitial();
    }

    synchronized void resetUserState() {
        userState.setValue(initialUserState());
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

    private void updateUserState(int pos, int pad) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setPos(pos, pad)
                .build()
        );
    }

    void updateUserState(Pair<Integer,Integer> currentPosition) {
        updateUserState(currentPosition.first, currentPosition.second);
    }

    private LinkedList<PlaylistUserState> getBackStack() {
        if (backStack == null) {
            backStack = new LinkedList<>();
            backStack.push(initialUserState());
        }
        return backStack;
    }

    void addBackStackHistory(Pair<Integer, Integer> currentPosition) {
        getBackStack().push(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setPos(currentPosition.first, currentPosition.second)
                .build()
        );
    }

    boolean popBackStack() {
        if (getBackStack().size() > 0) {
            getMutableUserState().setValue(getBackStack().pop());
            return true;
        }
        return false;
    }

    void browsePlaylist(PlaylistID playlistID) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setBrowsedPlaylist(playlistID)
                .setShowPlaybackEntries(true)
                .build()
        );
    }

    LiveData<List<Playlist>> getPlaylists(Context context) {
        return PlaylistStorage.getInstance(context).getPlaylists();
    }

    LiveData<Playlist> getPlaylist(Context context, PlaylistID playlistID) {
        return PlaylistStorage.getInstance(context).getPlaylist(playlistID);
    }

    LiveData<List<PlaylistEntry>> getPlaylistEntries(PlaylistID playlistID, Context context) {
        return PlaylistStorage.getInstance(context).getPlaylistEntries(playlistID);
    }

    LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> getFetchState(Context context) {
        return AudioStorage.getInstance(context).getFetchState();
    }

    LiveData<HashSet<EntryID>> getCachedEntries(Context context) {
        return Transformations.map(
                AudioStorage.getInstance(context).getCachedEntries(),
                HashSet::new
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

    private synchronized MutableLiveData<PlaybackEntry> getMutableCurrentPlaylistEntry() {
        if (currentPlaylistEntry == null) {
            currentPlaylistEntry = new MutableLiveData<>();
        }
        return currentPlaylistEntry;
    }

    void setCurrentPlaylistEntry(PlaybackEntry playlistEntry) {
        getMutableCurrentPlaylistEntry().setValue(playlistEntry);
    }

    LiveData<PlaybackEntry> getCurrentPlaylistEntry() {
        return getMutableCurrentPlaylistEntry();
    }

    boolean isBrowsedCurrent() {
        PlaylistID currentPlaylistID = getCurrentPlaylistID().getValue();
        PlaylistUserState userState = getUserStateValue();
        return userState != null && userState.isBrowsedCurrent(currentPlaylistID);
    }

    public void goToPlaylistPlayback(PlaylistID playlistID, long playlistPos) {
        // TODO: Use playlistPos. Scroll to it somehow. (Can't just use setPos() in current impl)
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setBrowsedPlaylist(playlistID)
                .setShowPlaybackEntries(true)
                .setPos(0, 0)
                .build()
        );
    }

    void showPlaybackEntries(boolean show) {
        getMutableUserState().setValue(new PlaylistUserState.Builder()
                .fromState(getUserStateValue())
                .setShowPlaybackEntries(show)
                .build()
        );
    }
}
