package se.splushii.dancingbunnies.ui.musiclibrary;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Query;
import se.splushii.dancingbunnies.musiclibrary.QueryEntry;
import se.splushii.dancingbunnies.musiclibrary.QueryTree;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(MusicLibraryFragmentModel.class);

    private MutableLiveData<MusicLibraryUserState> userState;
    private LinkedList<MusicLibraryUserState> backStack;
    private String currentSubscriptionID;
    private MutableLiveData<List<QueryEntry>> queryEntries;
    private MutableLiveData<PlaybackEntry> currentEntry;
    private List<Integer> queryTreeSelection;

    private static MusicLibraryUserState initialUserState() {
        return new MusicLibraryUserState(new Query(), 0, 0);
    }

    private MutableLiveData<MusicLibraryUserState> getMutableUserState() {
        if (userState == null) {
            userState = new MutableLiveData<>();
            userState.setValue(initialUserState());
        }
        return userState;
    }

    private void setUserState(MusicLibraryUserState state) {
        getMutableUserState().setValue(state);
    }

    LiveData<MusicLibraryUserState> getUserState() {
        return getMutableUserState();
    }

    void updateUserState(Pair<Integer, Integer> currentPosition) {
        setUserState(new MusicLibraryUserState(
                getMusicLibraryQuery(),
                currentPosition
        ));
    }

    private Query getMusicLibraryQuery() {
        MusicLibraryUserState state = getUserState().getValue();
        if (state == null) {
            return new Query();
        }
        return new Query(state.query);
    }

    private LinkedList<MusicLibraryUserState> getBackStack() {
        if (backStack == null) {
            backStack = new LinkedList<>();
            backStack.push(initialUserState());
        }
        return backStack;
    }

    void addBackStackHistory(Pair<Integer, Integer> currentPosition) {
        getBackStack().push(new MusicLibraryUserState(getMusicLibraryQuery(), currentPosition));
    }

    public void addBackStackHistory(Pair<Integer, Integer> currentPosition,
                                    QueryTree queryTree) {
        Query query = getMusicLibraryQuery();
        query.setQueryTree(queryTree);
        getBackStack().push(new MusicLibraryUserState(query, currentPosition));
    }

    boolean popBackStack() {
        if (getBackStack().size() > 0) {
            setUserState(getBackStack().pop());
            return true;
        }
        return false;
    }

    void reset() {
        setUserState(initialUserState());
    }

    void setQuery(Query query) {
        resetQueryEntries();
        setUserState(new MusicLibraryUserState(query, 0 , 0));
    }

    void displayType(String displayType) {
        Query query = new Query(getMusicLibraryQuery());
        query.setShowField(displayType);
        setUserState(new MusicLibraryUserState(query, 0, 0));
    }

    void sortBy(List<String> fields) {
        Query query = new Query(getMusicLibraryQuery());
        query.setSortByFields(new ArrayList<>(fields));
        setUserState(new MusicLibraryUserState(query, 0, 0));
    }

    void setSortOrder(boolean ascending) {
        Query query = new Query(getMusicLibraryQuery());
        query.setSortOrder(ascending);
        setUserState(new MusicLibraryUserState(query, 0, 0));
    }

    private void setCurrentSubscriptionID(String currentSubscriptionID) {
        this.currentSubscriptionID = currentSubscriptionID;
    }

    private String getCurrentSubscriptionID() {
        return currentSubscriptionID;
    }

    public void setQueryTreeSelection(List<Integer> queryTreeSelection) {
        this.queryTreeSelection.clear();
        this.queryTreeSelection.addAll(queryTreeSelection);
    }

    public List<Integer> getQueryTreeSelection() {
        if (queryTreeSelection == null) {
            queryTreeSelection = new ArrayList<>();
        }
        return queryTreeSelection;
    }

    public void query(String filterType, String filter) {
        addBackStackHistory(new Pair<>(0, 0));
        showOnly(filterType, filter);
        displayType(Meta.FIELD_SPECIAL_ENTRY_ID_TRACK);
    }

    private void showOnly(String filterType, String filter) {
        Query query = new Query();
        query.and(filterType, filter);
        setUserState(new MusicLibraryUserState(query, 0, 0));
    }

    public void search(String query, boolean saveLastQuery) {
        MusicLibraryUserState lastState = getBackStack().peek();
        if (lastState != null
                && lastState.query.isSearchQuery()
                && lastState.query.getSearchQuery().equals(query)) {
            // No change
            return;
        }
        if (saveLastQuery && !isEmptySearch()) { // No need to save the empty search
            addBackStackHistory(new Pair<>(0, 0));
        }
        setUserState(new MusicLibraryUserState(
                new Query(query), 0, 0
        ));
    }

    private boolean isEmptySearch() {
        return getMusicLibraryQuery().isSearchQuery()
                && getMusicLibraryQuery().getSearchQuery().isEmpty();
    }

    private MutableLiveData<List<QueryEntry>> getMutableQueryEntries() {
        if (queryEntries == null) {
            queryEntries = new MutableLiveData<>();
            queryEntries.setValue(new LinkedList<>());
        }
        return queryEntries;
    }

    LiveData<List<QueryEntry>> getQueryEntries() {
        return getMutableQueryEntries();
    }

    private void resetQueryEntries() {
        getMutableQueryEntries().setValue(new ArrayList<>());
    }

    void query(AudioBrowser remote) {
        String currentSubscriptionID = getCurrentSubscriptionID();
        currentSubscriptionID = remote.query(
                currentSubscriptionID,
                getMusicLibraryQuery(),
                items -> getMutableQueryEntries().setValue(items)
        );
        setCurrentSubscriptionID(currentSubscriptionID);
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
}
