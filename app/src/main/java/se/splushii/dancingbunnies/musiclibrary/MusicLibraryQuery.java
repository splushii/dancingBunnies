package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;

import java.util.List;

import androidx.annotation.NonNull;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryQuery {
    private static final String LC = Util.getLogContext(MusicLibraryQuery.class);
    public static final String DEFAULT_SHOW_FIELD = Meta.FIELD_ARTIST;
    public static final String DEFAULT_SORT_FIELD = Meta.FIELD_ARTIST;
    public static final String BUNDLE_KEY_SHOW = "dancingbunnies.bundle.key.musiclibraryquery.show";
    public static final String BUNDLE_KEY_SORT = "dancingbunnies.bundle.key.musiclibraryquery.sort";
    public static final String BUNDLE_KEY_QUERY = "dancingbunnies.bundle.key.musiclibraryquery.query";

    public enum MusicLibraryQueryType {
        SUBSCRIPTION,
        SEARCH
    }
    private final MusicLibraryQueryType type;
    private Bundle subQuery;
    private String searchQuery;
    private String showField;
    private String sortByField;

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
        if (query.sortByField == null) {
            sortByField = DEFAULT_SORT_FIELD;
        } else {
            sortByField = query.sortByField;
        }
        this.searchQuery = query.searchQuery;
    }

    public MusicLibraryQuery(String searchQuery) {
        this.type = MusicLibraryQueryType.SEARCH;
        this.searchQuery = searchQuery;
    }

    private void init() {
        this.subQuery = new Bundle();
        this.showField = DEFAULT_SHOW_FIELD;
        this.sortByField = DEFAULT_SORT_FIELD;
    }

    public void setShowField(String field) {
        this.showField = field;
    }

    public String getShowField() {
        return showField;
    }

    public void setSortByField(String field) {
        this.sortByField = field;
    }

    public String getSortByField() {
        return sortByField;
    }

    public boolean querySortedByShow() {
        return getSortByField().equals(getShowField())
                || Meta.FIELD_SPECIAL_MEDIA_ID.equals(getShowField()) && Meta.FIELD_TITLE.equals(getSortByField());
    }

    public void addToQuery(String key, String value) {
        if (type != MusicLibraryQueryType.SUBSCRIPTION) {
            Log.e(LC, "addToQuery on type: " + type.name());
            return;
        }
        subQuery.putString(key, value);
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
        b.putString(BUNDLE_KEY_SORT, sortByField);
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
        if (!mediaBrowser.isConnected()) {
            Log.w(LC, "Search: MediaBrowser not connected.");
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
}
