package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;

import java.util.List;

import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryQuery {
    private static final String LC = Util.getLogContext(MusicLibraryQuery.class);

    public static final String API_ANY = "ANY_API";

    public enum MusicLibraryQueryType {
        SUBSCRIPTION,
        SEARCH
    }
    private final MusicLibraryQueryType type;
    private EntryID entryID;
    private String query;

    public MusicLibraryQuery(EntryID entryID) {
        this.type = MusicLibraryQueryType.SUBSCRIPTION;
        this.entryID = entryID;
    }

    public MusicLibraryQuery(String query) {
        this.type = MusicLibraryQueryType.SEARCH;
        this.query = query;
    }

    public static String getMusicLibraryQueryOptionsString(Bundle options) {
        String api = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(options.getString(Meta.METADATA_KEY_TYPE));
        return "api: " + api + ", type: " + type.name();
    }

    public static MusicLibraryQuery generateMusicLibraryQuery(String parentId, Bundle options) {
        String src = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(options.getString(Meta.METADATA_KEY_TYPE));
        return new MusicLibraryQuery(new EntryID(src, parentId, type));
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(Meta.METADATA_KEY_API, entryID.src);
        b.putString(Meta.METADATA_KEY_MEDIA_ID, entryID.id);
        b.putString(Meta.METADATA_KEY_TYPE, entryID.type.name());
        return b;
    }

    private boolean isSubscription() {
        return type == MusicLibraryQueryType.SUBSCRIPTION;
    }

    private String subscriptionID() {
        return entryID.id;
    }

    public EntryID entryID() {
        return entryID;
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
        // TODO: Use query to filter results only including all parent entryID:s
        // TODO: THEN: Add checkbox to show all entries. (Not just including parents.)
        MediaBrowserCompat.SubscriptionCallback subCb = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId,
                                         @NonNull List<MediaBrowserCompat.MediaItem> children,
                                         @NonNull Bundle options) {
                String optString = MusicLibraryQuery.getMusicLibraryQueryOptionsString(options);
                Log.d(LC, "subscription(" + parentId + ") (" + optString + "): "
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
        mediaBrowser.search(query, null, new MediaBrowserCompat.SearchCallback() {
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
