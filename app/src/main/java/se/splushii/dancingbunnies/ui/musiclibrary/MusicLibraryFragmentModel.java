package se.splushii.dancingbunnies.ui.musiclibrary;

import android.util.Pair;

import java.util.LinkedList;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;

public class MusicLibraryFragmentModel extends ViewModel {
    static final String INITIAL_DISPLAY_TYPE = Meta.METADATA_KEY_ARTIST;

    private MutableLiveData<MusicLibraryUserState> userState;
    private LinkedList<MusicLibraryUserState> backStack;
    private String currentSubscriptionID;

    private static MusicLibraryUserState initialUserState() {
        MusicLibraryQuery query = new MusicLibraryQuery();
        query.addToQuery(Meta.METADATA_KEY_TYPE, INITIAL_DISPLAY_TYPE);
        return new MusicLibraryUserState(query, 0, 0);
    }

    public MutableLiveData<MusicLibraryUserState> getUserState() {
        if (userState == null) {
            userState = new MutableLiveData<>();
            userState.setValue(initialUserState());
        }
        return userState;
    }

    void updateUserState(Pair<Integer, Integer> currentPosition) {
        MusicLibraryQuery query = getUserState().getValue().query;
        getUserState().setValue(new MusicLibraryUserState(
                query,
                currentPosition
        ));
    }

    private LinkedList<MusicLibraryUserState> getBackStack() {
        if (backStack == null) {
            backStack = new LinkedList<>();
            backStack.push(initialUserState());
        }
        return backStack;
    }

    void addBackStackHistory(Pair<Integer, Integer> currentPosition) {
        getBackStack().push(new MusicLibraryUserState(
                getUserState().getValue().query,
                currentPosition
        ));
    }

    boolean popBackStack() {
        if (getBackStack().size() > 0) {
            getUserState().setValue(getBackStack().pop());
            return true;
        }
        return false;
    }

    void filter(String filterType, String filter) {
        MusicLibraryQuery query = new MusicLibraryQuery(getUserState().getValue().query);
        query.addToQuery(filterType, filter);
        getUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void browse(EntryID entryID) {
        MusicLibraryQuery query = new MusicLibraryQuery(getUserState().getValue().query);
        String displayType = entryID.type.equals(Meta.METADATA_KEY_ARTIST) ?
                Meta.METADATA_KEY_ALBUM : Meta.METADATA_KEY_MEDIA_ID;
        query.addToQuery(Meta.METADATA_KEY_TYPE, displayType);
        if (entryID.id != null) {
            query.addToQuery(entryID.type, entryID.id);
        }
        getUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void displayType(String displayType) {
        MusicLibraryQuery query = new MusicLibraryQuery(getUserState().getValue().query);
        query.addToQuery(Meta.METADATA_KEY_TYPE, displayType);
        getUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void clearFilter(String filterType) {
        MusicLibraryQuery query = new MusicLibraryQuery(getUserState().getValue().query);
        query.removeFromQuery(filterType);
        getUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void setCurrentSubscriptionID(String currentSubscriptionID) {
        this.currentSubscriptionID = currentSubscriptionID;
    }

    String getCurrentSubscriptionID() {
        return currentSubscriptionID;
    }

    public void search(String query) {
        addBackStackHistory(new Pair<>(0, 0));
        getUserState().setValue(new MusicLibraryUserState(
                new MusicLibraryQuery(query), 0, 0
        ));
    }
}
