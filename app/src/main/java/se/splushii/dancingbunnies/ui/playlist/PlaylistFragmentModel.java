package se.splushii.dancingbunnies.ui.playlist;

import java.util.LinkedList;

import androidx.core.util.Pair;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

class PlaylistFragmentModel extends ViewModel {

    private MutableLiveData<PlaylistUserState> userState;
    private LinkedList<PlaylistUserState> backStack;

    private static PlaylistUserState initialUserState() {
        return new PlaylistUserState(0, 0);
    }

    MutableLiveData<PlaylistUserState> getUserState() {
        if (userState == null) {
            userState = new MutableLiveData<>();
            userState.setValue(initialUserState());
        }
        return userState;
    }

    private void updateUserState(int pos, int pad) {
        getUserState().setValue(new PlaylistUserState(getUserState().getValue(), pos, pad));
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
        getBackStack().push(new PlaylistUserState(
                getUserState().getValue(),
                currentPosition.first,
                currentPosition.second
        ));
    }

    boolean popBackStack() {
        if (getBackStack().size() > 0) {
            getUserState().setValue(getBackStack().pop());
            return true;
        }
        return false;
    }

    void browsePlaylist(PlaylistID playlistID) {
        getUserState().setValue(new PlaylistUserState(playlistID, 0, 0));
    }
}
