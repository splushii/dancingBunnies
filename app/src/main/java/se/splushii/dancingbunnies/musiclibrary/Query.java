package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import se.splushii.dancingbunnies.util.Util;

public class Query {
    private static final String LC = Util.getLogContext(Query.class);
    public static final String DEFAULT_SHOW_FIELD = Meta.FIELD_ARTIST;
    public static final ArrayList<String> DEFAULT_SORT_FIELDS = new ArrayList<>(Collections.singletonList(Meta.FIELD_ARTIST));
    public static final String BUNDLE_KEY_SHOW = "dancingbunnies.bundle.key.query.show";
    public static final String BUNDLE_KEY_SORT = "dancingbunnies.bundle.key.query.sort";
    public static final String BUNDLE_KEY_QUERY_TREE = "dancingbunnies.bundle.key.query.query_tree";
    public static final String BUNDLE_KEY_SORT_ORDER = "dancingbunnies.bundle.key.query.sort_order";

    public enum Type {
        SUBSCRIPTION,
        SEARCH
    }
    private final Type type;
    private QueryTree queryTree;
    private String searchQuery;
    private String showField;
    private ArrayList<String> sortByFields;
    private boolean sortOrderAscending;

    public Query() {
        this.type = Type.SUBSCRIPTION;
        init();
    }

    public Query(Query query) {
        this.type = query == null ? Type.SUBSCRIPTION : query.type;
        if (query == null) {
            init();
            return;
        }
        if (query.queryTree == null) {
            this.queryTree = new QueryTree(QueryTree.Op.AND, false);
        } else {
            this.queryTree = query.queryTree.deepCopy();
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

    public Query(String searchQuery) {
        this.type = Type.SEARCH;
        this.searchQuery = searchQuery;
        this.showField = Meta.FIELD_TITLE;
    }

    private void init() {
        this.queryTree = new QueryTree(QueryTree.Op.AND, false);
        this.showField = DEFAULT_SHOW_FIELD;
        this.sortByFields = DEFAULT_SORT_FIELDS;
        this.sortOrderAscending = true;
    }

    public QueryTree getQueryTree() {
        return queryTree;
    }

    public void setQueryTree(QueryTree queryTree) {
        this.queryTree = queryTree;
    }

    public void setShowField(String field) {
        this.showField = field;
    }

    public String getShowField() {
        return showField;
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
                || (Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(showField)
                && Meta.FIELD_TITLE.equals(sortByField));
    }

    public void andEntryIDToQuery(EntryID entryID) {
        if (entryID != null && !entryID.isUnknown()) {
            and(entryID.type, entryID.id);
        }
    }

    public void and(String key, String value) {
        if (type != Type.SUBSCRIPTION) {
            Log.e(LC, "and on type: " + type.name());
            return;
        }
        if (queryTree.getOperator() != QueryTree.Op.AND) {
            QueryTree newRoot = new QueryTree(
                    QueryTree.Op.AND,
                    false
            );
            newRoot.addChild(queryTree);
            queryTree = newRoot;
        }
        queryTree.addChild(new QueryLeaf(key, value));
    }

    public void andSortedByValuesToQuery(List<String> sortedByKeys,
                                         ArrayList<String> sortedByValues) {
        for (int i = 0; i < sortedByKeys.size(); i++) {
            String key = sortedByKeys.get(i);
            String value = sortedByValues == null || i >= sortedByValues.size() ?
                    null : sortedByValues.get(i);
            // TODO: OR DO WE NEED TO ADD THE NULL VALUE CONSTRAINT TO THE QUERY?
            if (value == null) {
                continue;
            }
            and(key, value);
        }
    }

    private Bundle toSubscriptionBundle() {
        Bundle b = new Bundle();
        b.putString(BUNDLE_KEY_SHOW, showField);
        b.putStringArrayList(BUNDLE_KEY_SORT, sortByFields);
        b.putBoolean(BUNDLE_KEY_SORT_ORDER, sortOrderAscending);
        b.putString(BUNDLE_KEY_QUERY_TREE, queryTree.toJSON().toString());
        return b;
    }

    private boolean isSubscription() {
        return type == Type.SUBSCRIPTION;
    }

    public boolean isSearchQuery() {
        return !isSubscription();
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    private String subscriptionID() {
        return queryTree.toJSON().toString();
    }

    public String query(MediaBrowserCompat mediaBrowser,
                        QueryCallback queryCallback) {
        if (isSubscription()) {
            return subscribe(mediaBrowser, queryCallback);
        } else {
            search(mediaBrowser, queryCallback);
        }
        return null;
    }

    private String subscribe(MediaBrowserCompat mediaBrowser, QueryCallback callback) {
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

    private void search(MediaBrowserCompat mediaBrowser, QueryCallback callback) {
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

    public static abstract class QueryCallback {
        public abstract void onQueryResult(@NonNull List<MediaBrowserCompat.MediaItem> items);
    }
}
