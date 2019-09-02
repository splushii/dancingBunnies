package se.splushii.dancingbunnies.ui.musiclibrary;

import android.support.v4.media.MediaBrowserCompat;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFragmentModel extends ViewModel {
    private static final String LC = Util.getLogContext(MusicLibraryFragmentModel.class);

    private MutableLiveData<MusicLibraryUserState> userState;
    private LinkedList<MusicLibraryUserState> backStack;
    private String currentSubscriptionID;
    private MutableLiveData<List<LibraryEntry>> dataset;
    private Consumer<CharSequence> setSearchQueryListener = s -> {};

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

    void filter(String filterType, String filter) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.addToQuery(filterType, filter);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void setQuery(MusicLibraryQuery query) {
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0 , 0));
    }

    void displayType(String displayType) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.setShowField(displayType);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void sortBy(String field) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.setSortByField(field);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    void clearFilter(String filterType) {
        MusicLibraryQuery query = new MusicLibraryQuery(getMusicLibraryQuery());
        query.removeFromQuery(filterType);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    private void setCurrentSubscriptionID(String currentSubscriptionID) {
        this.currentSubscriptionID = currentSubscriptionID;
    }

    private String getCurrentSubscriptionID() {
        return currentSubscriptionID;
    }

    public void query(String filterType, String filter) {
        addBackStackHistory(new Pair<>(0, 0));
        showOnly(filterType, filter);
        displayType(Meta.FIELD_SPECIAL_MEDIA_ID);
    }

    private void showOnly(String filterType, String filter) {
        MusicLibraryQuery query = new MusicLibraryQuery();
        query.addToQuery(filterType, filter);
        getMutableUserState().setValue(new MusicLibraryUserState(query, 0, 0));
    }

    public void search(String query) {
        addBackStackHistory(new Pair<>(0, 0));
        getMutableUserState().setValue(new MusicLibraryUserState(
                new MusicLibraryQuery(query), 0, 0
        ));
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

    void query(MediaBrowserCompat mediaBrowser) {
        // Unsubscribe
        String currentSubscriptionID = getCurrentSubscriptionID();
        if (currentSubscriptionID != null && mediaBrowser.isConnected()) {
            mediaBrowser.unsubscribe(currentSubscriptionID);
        }
        currentSubscriptionID = getMusicLibraryQuery().query(
                mediaBrowser,
                new MusicLibraryQuery.MusicLibraryQueryCallback() {
                    @Override
                    public void onQueryResult(@NonNull List<MediaBrowserCompat.MediaItem> items) {
                        getMutableDataSet().setValue(
                                items.stream().map(LibraryEntry::from).collect(Collectors.toList())
                        );
                    }
                }
        );
        setCurrentSubscriptionID(currentSubscriptionID);
    }

    public void setSearchQueryClickedListener(Consumer<CharSequence> listener) {
        setSearchQueryListener = listener;
    }

    void searchQueryClicked(CharSequence query) {
        setSearchQueryListener.accept(query);
    }
}
