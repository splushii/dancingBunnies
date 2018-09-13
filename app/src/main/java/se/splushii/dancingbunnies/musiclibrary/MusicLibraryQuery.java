package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;

import java.util.List;

import androidx.annotation.NonNull;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryQuery {
    private static final String LC = Util.getLogContext(MusicLibraryQuery.class);

    Bundle subQuery() {
        return subQuery;
    }

    public enum MusicLibraryQueryType {
        SUBSCRIPTION,
        SEARCH
    }
    private final MusicLibraryQueryType type;
    private Bundle subQuery;
    private String searchQuery;

    public MusicLibraryQuery() {
        this.type = MusicLibraryQueryType.SUBSCRIPTION;
        this.subQuery = new Bundle();
    }

    public MusicLibraryQuery(MusicLibraryQuery query) {
        this.type = MusicLibraryQueryType.SUBSCRIPTION;
        this.subQuery = query.subQuery.deepCopy();
        this.searchQuery = query.searchQuery;
    }

    public MusicLibraryQuery(Bundle query) {
        this.type = MusicLibraryQueryType.SUBSCRIPTION;
        this.subQuery = query;
    }

    public void addToQuery(String key, String value) {
        if (type != MusicLibraryQueryType.SUBSCRIPTION) {
            Log.e(LC, "addToQuery on type: " + type.name());
            return;
        }
        subQuery.putString(key, value);
    }

    public MusicLibraryQuery(String searchQuery) {
        this.type = MusicLibraryQueryType.SEARCH;
        this.searchQuery = searchQuery;
    }

    public Bundle toBundle() {
        return subQuery;
    }

    private boolean isSubscription() {
        return type == MusicLibraryQueryType.SUBSCRIPTION;
    }

    public boolean isSearchQuery() {
        return !isSubscription();
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
        Bundle options = toBundle();
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
                Log.d(LC, "MediaBrowser.subscribe(" + parentId + ") onError");
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
