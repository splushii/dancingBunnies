package se.splushii.dancingbunnies.musiclibrary;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import se.splushii.dancingbunnies.util.Util;

public abstract class QueryNode {

    private static final String LC = Util.getLogContext(QueryNode.class);

    static final String JSON_KEY_NODE_TYPE = "type";
    static final String JSON_VALUE_NODE_TYPE_LEAF = "leaf";
    static final String JSON_VALUE_NODE_TYPE_TREE = "tree";
    private static final String JSON_KEY_NEGATE = "not";
    private boolean negated = false;
    private String nodeType;

    QueryNode(QueryNode source) {
        this.nodeType = source.nodeType;
        this.negated = source.negated;
    }

    QueryNode(String nodeType, boolean negate) {
        this.nodeType = nodeType;
        this.negated = negate;
    }

    QueryNode(JSONObject jsonRoot) {
        try {
            nodeType = jsonRoot.getString(JSON_KEY_NODE_TYPE);
            negated = jsonRoot.getBoolean(JSON_KEY_NEGATE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void negate(boolean negate) {
        this.negated = negate;
    }

    public boolean isNegated() {
        return negated;
    }

    public static QueryNode fromJSON(String query) {
        if (query == null) {
            return null;
        }
        try {
            JSONObject jsonRoot = new JSONObject(query);
            return fromJSON(jsonRoot);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    static QueryNode fromJSON(JSONObject jsonObject) {
        String nodeType;
        try {
            nodeType = jsonObject.getString(JSON_KEY_NODE_TYPE);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        QueryNode node;
        if (nodeType.equals(JSON_VALUE_NODE_TYPE_LEAF)) {
            // JSONObject is QueryLeaf
            node = new QueryLeaf(jsonObject);
        } else if (nodeType.equals(JSON_VALUE_NODE_TYPE_TREE)){
            // Try to parse JSONObject as QueryTree
            node = new QueryTree(jsonObject);
        } else {
            Log.e(LC, "Unknown QueryNode type: " + nodeType);
            return null;
        }
        return node;
    }

    public static QueryNode fromEntryID(EntryID entryID) {
        if (entryID.isUnknown()) {
            return new QueryTree(QueryTree.Op.AND, false);
        }
        return new QueryLeaf(entryID.type, entryID.id);
    }

    public static String[] toJSONStringArray(List<QueryNode> queryNodes) {
        return queryNodes.stream()
                .map(queryNode -> queryNode.toJSON().toString())
                .toArray(String[]::new);
    }

    public static List<QueryNode> fromJSONStringArray(String[] queryNodeJSONs) {
        if (queryNodeJSONs == null || queryNodeJSONs.length <= 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(queryNodeJSONs)
                .map(QueryNode::fromJSON)
                .collect(Collectors.toList());
    }

    public List<QueryNode> withEntryIDs(List<EntryID> entryIDs) {
        return entryIDs.stream()
                .map(entryID -> this.deepCopy().withEntryID(entryID))
                .collect(Collectors.toList());
    }

    public JSONObject toJSON() {
        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(QueryNode.JSON_KEY_NODE_TYPE, nodeType);
            jsonRoot.put(QueryNode.JSON_KEY_NEGATE, negated);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonRoot;
    }


    @NonNull
    @Override
    public String toString() {
        return toJSON().toString();
    }

    public abstract QueryNode deepCopy();
    public abstract QueryNode withEntryID(EntryID entryID);
    public abstract HashSet<String> getKeys();
}
