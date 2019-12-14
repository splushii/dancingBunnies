package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryQuery {
    private static final String LC = Util.getLogContext(MusicLibraryQuery.class);
    public static final String DEFAULT_SHOW_FIELD = Meta.FIELD_ARTIST;
    public static final ArrayList<String> DEFAULT_SORT_FIELDS = new ArrayList<>(Collections.singletonList(Meta.FIELD_ARTIST));
    public static final String BUNDLE_KEY_SHOW = "dancingbunnies.bundle.key.musiclibraryquery.show";
    public static final String BUNDLE_KEY_SORT = "dancingbunnies.bundle.key.musiclibraryquery.sort";
    public static final String BUNDLE_KEY_QUERY = "dancingbunnies.bundle.key.musiclibraryquery.query";
    public static final String BUNDLE_KEY_SORT_ORDER = "dancingbunnies.bundle.key.musiclibraryquery.sort_order";

    public enum MusicLibraryQueryType {
        SUBSCRIPTION,
        SEARCH
    }
    private final MusicLibraryQueryType type;
    private Bundle subQuery;
    private String searchQuery;
    private String showField;
    private ArrayList<String> sortByFields;
    private boolean sortOrderAscending;

    public MusicLibraryQuery() {
        this.type = MusicLibraryQueryType.SUBSCRIPTION;
        init();
    }

    public MusicLibraryQuery(MusicLibraryQuery query) {
        this.type = query == null ? MusicLibraryQueryType.SUBSCRIPTION : query.type;
        if (query == null) {
            init();
            return;
        }
        if (query.subQuery == null) {
            subQuery = new Bundle();
        } else {
            this.subQuery = query.subQuery.deepCopy();
        }
        if (query.showField == null) {
            showField = DEFAULT_SHOW_FIELD;
        } else {
            showField = query.showField;
        }
        if (query.sortByFields == null) {
            sortByFields = DEFAULT_SORT_FIELDS;
        } else {
            sortByFields = query.sortByFields;
        }
        this.sortOrderAscending = query.sortOrderAscending;
        this.searchQuery = query.searchQuery;
    }

    public MusicLibraryQuery(String searchQuery) {
        this.type = MusicLibraryQueryType.SEARCH;
        this.searchQuery = searchQuery;
        this.showField = Meta.FIELD_TITLE;
    }

    private void init() {
        this.subQuery = new Bundle();
        this.showField = DEFAULT_SHOW_FIELD;
        this.sortByFields = DEFAULT_SORT_FIELDS;
        this.sortOrderAscending = true;
    }

    public void setShowField(String field) {
        this.showField = field;
    }

    public String getShowField() {
        return showField;
    }

    public void setSortByField(String field) {
        this.sortByFields = new ArrayList<>(Collections.singletonList(field));
    }

    public void setSortByFields(ArrayList<String> field) {
        this.sortByFields = field;
    }

    public ArrayList<String> getSortByFields() {
        return sortByFields;
    }

    public void setSortOrder(boolean ascending) {
        sortOrderAscending = ascending;
    }

    public boolean isSortOrderAscending() {
        return sortOrderAscending;
    }

    public boolean querySortedByShow() {
        List<String> sortByFields = getSortByFields();
        String showField = getShowField();
        if (sortByFields.size() != 1) {
            return false;
        }
        String sortByField = sortByFields.get(0);
        return sortByField.equals(showField)
                || Meta.FIELD_SPECIAL_MEDIA_ID.equals(showField) && Meta.FIELD_TITLE.equals(sortByField);
    }

    public void addEntryIDToQuery(EntryID entryID) {
        if (entryID != null && !entryID.isUnknown()) {
            addToQuery(entryID.type, entryID.id);
        }
    }

    public void addToQuery(String key, String value) {
        if (type != MusicLibraryQueryType.SUBSCRIPTION) {
            Log.e(LC, "addToQuery on type: " + type.name());
            return;
        }
        subQuery.putString(key, value);
    }

    public void addSortedByValuesToQuery(List<String> sortedByKeys,
                                         ArrayList<String> sortedByValues) {
        for (int i = 0; i < sortedByKeys.size(); i++) {
            String key = sortedByKeys.get(i);
            String value = sortedByValues == null || i >= sortedByValues.size() ?
                    null : sortedByValues.get(i);
            // TODO: OR DO WE NEED TO ADD THE NULL VALUE CONSTRAINT TO THE QUERY?
            if (value == null) {
                continue;
            }
            addToQuery(key, value);
        }
    }

    public Bundle getQueryBundle() {
        return subQuery;
    }

    public void removeFromQuery(String key) {
        if (type != MusicLibraryQueryType.SUBSCRIPTION) {
            Log.e(LC, "removeFromQuery on type: " + type.name());
            return;
        }
        subQuery.remove(key);
    }

    private Bundle toSubscriptionBundle() {
        Bundle b = new Bundle();
        b.putString(BUNDLE_KEY_SHOW, showField);
        b.putStringArrayList(BUNDLE_KEY_SORT, sortByFields);
        b.putBoolean(BUNDLE_KEY_SORT_ORDER, sortOrderAscending);
        b.putBundle(BUNDLE_KEY_QUERY, subQuery);
        return b;
    }

    private boolean isSubscription() {
        return type == MusicLibraryQueryType.SUBSCRIPTION;
    }

    public boolean isSearchQuery() {
        return !isSubscription();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    private String subscriptionID() {
        return subQuery.toString();
    }

    public String query(MediaBrowserCompat mediaBrowser,
                        MusicLibraryQueryCallback musicLibraryQueryCallback) {
        if (isSubscription()) {
            return subscribe(mediaBrowser, musicLibraryQueryCallback);
        } else {
            search(mediaBrowser, musicLibraryQueryCallback);
        }
        return null;
    }

    private String subscribe(MediaBrowserCompat mediaBrowser, MusicLibraryQueryCallback callback) {
        if (!mediaBrowser.isConnected()) {
            Log.w(LC, "MediaBrowser not connected.");
            return null;
        }
        Bundle options = toSubscriptionBundle();
        MediaBrowserCompat.SubscriptionCallback subCb = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId,
                                         @NonNull List<MediaBrowserCompat.MediaItem> children,
                                         @NonNull Bundle options) {
                Log.d(LC, "subscription(" + parentId + ") (" + options.toString() + "): "
                        + children.size());
                callback.onQueryResult(children);
            }

            @Override
            public void onError(@NonNull String parentId) {
                Log.e(LC, "MediaBrowser.subscribe(" + parentId + ") onError");
            }
        };
        mediaBrowser.subscribe(subscriptionID(), options, subCb);
        return subscriptionID();
    }

    private void search(MediaBrowserCompat mediaBrowser, MusicLibraryQueryCallback callback) {
        if (searchQuery == null || searchQuery.isEmpty()) {
            callback.onQueryResult(new ArrayList<>());
            return;
        }
        if (!mediaBrowser.isConnected()) {
            Log.w(LC, "Search: MediaBrowser not connected.");
            callback.onQueryResult(new ArrayList<>());
            return;
        }
        mediaBrowser.search(searchQuery, null, new MediaBrowserCompat.SearchCallback() {
            @Override
            public void onSearchResult(@NonNull String query, Bundle extras, @NonNull List<MediaBrowserCompat.MediaItem> items) {
                super.onSearchResult(query, extras, items);
                Log.d(LC, "search(" + query + "): " + items.size());
                callback.onQueryResult(items);
            }

            @Override
            public void onError(@NonNull String query, Bundle extras) {
                Log.e(LC, "Search onError: " + query);
            }
        });
    }

    public static abstract class MusicLibraryQueryCallback {
        public abstract void onQueryResult(@NonNull List<MediaBrowserCompat.MediaItem> items);
    }

    public static ArrayList<Bundle> toQueryBundles(List<EntryID> entryIDs, Bundle query) {
        return entryIDs.stream()
                .map(entryID -> toQueryBundle(entryID, query))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static Bundle toQueryBundle(EntryID entryID, Bundle query) {
        Bundle b = new Bundle();
        if (query != null) {
            b.putAll(query);
        }
        if (!entryID.isUnknown()) {
            b.putString(entryID.type, entryID.id);
        }
        return b;
    }
}
