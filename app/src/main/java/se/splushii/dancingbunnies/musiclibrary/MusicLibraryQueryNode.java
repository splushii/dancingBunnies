package se.splushii.dancingbunnies.musiclibrary;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;

public abstract class MusicLibraryQueryNode {

    MusicLibraryQueryNode() {}

    public static MusicLibraryQueryNode fromJSON(String query) {
        try {
            JSONObject jsonRoot = new JSONObject(query);
            return fromJSON(jsonRoot);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected static MusicLibraryQueryNode fromJSON(JSONObject jsonObject) {
        if (jsonObject.has(MusicLibraryQueryLeaf.JSON_KEY_VALUE)) {
            // JSONObject is MusicLibraryQueryLeaf
            return new MusicLibraryQueryLeaf(jsonObject);
        } else {
            // Try to parse JSONObject as MusicLibraryQueryTree
            return new MusicLibraryQueryTree(jsonObject);
        }
    }

    public static MusicLibraryQueryNode fromEntryID(EntryID entryID) {
        if (entryID.isUnknown()) {
            return new MusicLibraryQueryTree(MusicLibraryQueryTree.Op.AND);
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
                .map(this::withEntryID)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public String toString() {
        return toJSON().toString();
    }

    public abstract MusicLibraryQueryNode deepCopy();
    public abstract JSONObject toJSON();
    public abstract MusicLibraryQueryNode withEntryID(EntryID entryID);
    public abstract HashSet<String> getKeys();
}
