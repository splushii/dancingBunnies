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
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryTree;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(MusicLibraryFragmentModel.class);

    private MutableLiveData<MusicLibraryUserState> userState;
    private LinkedList<MusicLibraryUserState> backStack;
    private String currentSubscriptionID;
    private MutableLiveData<List<LibraryEntry>> dataset;
    private MutableLiveData<PlaybackEntry> currentEntry;
    private List<Integer> queryTreeSelection;

    private static MusicLibraryUserState initialUserState() {
        return new MusicLibraryUserState(new MusicLibraryQuery(), 0, 0);
    }

    private MutableLiveData<MusicLibraryUserState> getMutableUserState() {
        if (userState == null) {
            userState = new MutableLiveData<>();
            userState.setValue(initialUserState());
        }
        return userState;
    }

    LiveData<MusicLibraryUserState> getUserState() {
        return getMutableUserState();
    }

    void updateUserState(Pair<Integer, Integer> currentPosition) {
        getMutableUserState().setValue(new MusicLibraryUserState(
                getMusicLibraryQuery(),
                currentPosition
        ));
    }

    private MusicLibraryQuery getMusicLibraryQuery() {
        MusicLibraryUserState state = getUserState().getValue();
        if (state == null) {
            return new MusicLibraryQuery();
        }
        return new MusicLibraryQuery(state.query);
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
                                    MusicLibraryQueryTree queryTree) {
        MusicLibraryQuery query = getMusicLibraryQuery();
        query.setQueryTree(queryTree);
        getBackStack().push(new MusicLibraryUserState(query, currentPosition));
    }

    boolean popBackStack() {
        if (getBackStack().size() > 0) {
            getMutableUserState().setValue(getBackStack().pop());
            return true;
        }
        return false;
    }

    void reset() {
        getMutableUserState().setValue(initialUserState());
    }

    void setQuery(MusicLibraryQuery query) {
        resetDataSet();
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0 , 0));
    }

    void displayType(String displayType) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.setShowField(displayType);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void sortBy(List<String> fields) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.setSortByFields(new ArrayList<>(fields));
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void setSortOrder(boolean ascending) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.setSortOrder(ascending);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
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
        displayType(Meta.FIELD_SPECIAL_MEDIA_ID);
    }

    private void showOnly(String filterType, String filter) {
        MusicLibraryQuery query = new MusicLibraryQuery();
        query.and(filterType, filter);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    public void search(String query) {
        if (!isEmptySearch()) {
            addBackStackHistory(new Pair<>(0, 0));
        }
        getMutableUserState().setValue(new MusicLibraryUserState(
                new MusicLibraryQuery(query), 0, 0
        ));
    }

    private boolean isEmptySearch() {
        return getMusicLibraryQuery().isSearchQuery()
                && getMusicLibraryQuery().getSearchQuery().isEmpty();
    }

    private MutableLiveData<List<LibraryEntry>> getMutableDataSet() {
        if (dataset == null) {
            dataset = new MutableLiveData<>();
            dataset.setValue(new LinkedList<>());
        }
        return dataset;
    }

    LiveData<List<LibraryEntry>> getDataSet() {
        return getMutableDataSet();
    }

    private void resetDataSet() {
        getMutableDataSet().setValue(new ArrayList<>());
    }

    void query(AudioBrowser remote) {
        String currentSubscriptionID = getCurrentSubscriptionID();
        currentSubscriptionID = remote.query(
                currentSubscriptionID,
                getMusicLibraryQuery(),
                items -> getMutableDataSet().setValue(items)
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
