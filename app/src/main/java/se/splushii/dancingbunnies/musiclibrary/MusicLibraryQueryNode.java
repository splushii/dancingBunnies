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

public abstract class MusicLibraryQueryNode {

    private static final String LC = Util.getLogContext(MusicLibraryQueryNode.class);

    static final String JSON_KEY_NODE_TYPE = "type";
    static final String JSON_VALUE_NODE_TYPE_LEAF = "leaf";
    static final String JSON_VALUE_NODE_TYPE_TREE = "tree";
    private static final String JSON_KEY_NEGATE = "not";
    private boolean negated = false;
    private String nodeType;

    MusicLibraryQueryNode(MusicLibraryQueryNode source) {
        this.nodeType = source.nodeType;
        this.negated = source.negated;
    }

    MusicLibraryQueryNode(String nodeType, boolean negate) {
        this.nodeType = nodeType;
        this.negated = negate;
    }

    MusicLibraryQueryNode(JSONObject jsonRoot) {
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

    public static MusicLibraryQueryNode fromJSON(String query) {
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

    static MusicLibraryQueryNode fromJSON(JSONObject jsonObject) {
        String nodeType;
        try {
            nodeType = jsonObject.getString(JSON_KEY_NODE_TYPE);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        MusicLibraryQueryNode node;
        if (nodeType.equals(JSON_VALUE_NODE_TYPE_LEAF)) {
            // JSONObject is MusicLibraryQueryLeaf
            node = new MusicLibraryQueryLeaf(jsonObject);
        } else if (nodeType.equals(JSON_VALUE_NODE_TYPE_TREE)){
            // Try to parse JSONObject as MusicLibraryQueryTree
            node = new MusicLibraryQueryTree(jsonObject);
        } else {
            Log.e(LC, "Unknown MusicLibraryQueryNode type: " + nodeType);
            return null;
        }
        return node;
    }

    public static MusicLibraryQueryNode fromEntryID(EntryID entryID) {
        if (entryID.isUnknown()) {
            return new MusicLibraryQueryTree(MusicLibraryQueryTree.Op.AND, false);
        }
        return new MusicLibraryQueryLeaf(entryID.type, entryID.id);
    }

    public static String[] toJSONStringArray(List<MusicLibraryQueryNode> queryNodes) {
        return queryNodes.stream()
                .map(queryNode -> queryNode.toJSON().toString())
                .toArray(String[]::new);
    }

    public static List<MusicLibraryQueryNode> fromJSONStringArray(String[] queryNodeJSONs) {
        if (queryNodeJSONs == null || queryNodeJSONs.length <= 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(queryNodeJSONs)
                .map(MusicLibraryQueryNode::fromJSON)
                .collect(Collectors.toList());
    }

    public List<MusicLibraryQueryNode> withEntryIDs(List<EntryID> entryIDs) {
        return entryIDs.stream()
                .map(entryID -> this.deepCopy().withEntryID(entryID))
                .collect(Collectors.toList());
    }

    public JSONObject toJSON() {
        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(MusicLibraryQueryNode.JSON_KEY_NODE_TYPE, nodeType);
            jsonRoot.put(MusicLibraryQueryNode.JSON_KEY_NEGATE, negated);
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

    public abstract MusicLibraryQueryNode deepCopy();
    public abstract MusicLibraryQueryNode withEntryID(EntryID entryID);
    public abstract HashSet<String> getKeys();
}
