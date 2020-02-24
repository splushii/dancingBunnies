package se.splushii.dancingbunnies.musiclibrary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import androidx.annotation.NonNull;

public class MusicLibraryQueryTree extends MusicLibraryQueryNode implements Iterable<MusicLibraryQueryNode> {
    private static final String JSON_KEY_OP = "op";
    private static final String JSON_KEY_CHILDREN = "children";

    @NonNull
    @Override
    public Iterator<MusicLibraryQueryNode> iterator() {
        return Collections.unmodifiableList(children).iterator();
    }

    public enum Op {
        AND,
        OR
    }
    private Op operator;
    private final ArrayList<MusicLibraryQueryNode> children;

    private MusicLibraryQueryTree(MusicLibraryQueryTree source) {
        super(source);
        this.operator = source.operator;
        children = new ArrayList<>();
        for (MusicLibraryQueryNode child: source.children) {
            addChild(child.deepCopy());
        }
    }

    public MusicLibraryQueryTree(Op operator, boolean negate) {
        super(MusicLibraryQueryNode.JSON_VALUE_NODE_TYPE_TREE, negate);
        this.operator = operator;
        children = new ArrayList<>();
    }

    MusicLibraryQueryTree(JSONObject jsonRoot) {
        super(jsonRoot);
        operator = Op.AND;
        children = new ArrayList<>();
        try {
            operator = Op.valueOf(jsonRoot.getString(JSON_KEY_OP));
            JSONArray jsonChildren = jsonRoot.getJSONArray(JSON_KEY_CHILDREN);
            for (int i = 0; i < jsonChildren.length(); i++) {
                children.add(MusicLibraryQueryNode.fromJSON(jsonChildren.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void addChild(MusicLibraryQueryNode node) {
        children.add(node);
    }

    public void removeChild(MusicLibraryQueryNode node) {
        children.remove(node);
    }

    public void setOperator(Op op) {
        operator = op;
    }

    public Op getOperator() {
        return operator;
    }

    public MusicLibraryQueryTree deepCopy() {
        return new MusicLibraryQueryTree(this);
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonRoot = super.toJSON();
        try {
            jsonRoot.put(JSON_KEY_OP, operator.name());
            JSONArray jsonChildren = new JSONArray();
            for (MusicLibraryQueryNode node: children) {
                if (node != null) {
                    jsonChildren.put(node.toJSON());
                }
            }
            jsonRoot.put(JSON_KEY_CHILDREN, jsonChildren);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonRoot;
    }

    @Override
    public MusicLibraryQueryNode withEntryID(EntryID entryID) {
        if (entryID.isUnknown()) {
            return this;
        }
        MusicLibraryQueryTree queryTree;
        if (operator == Op.AND) {
            queryTree = this;
        } else {
            queryTree = new MusicLibraryQueryTree(Op.AND, false);
            queryTree.addChild(this);
        }
        queryTree.addChild(new MusicLibraryQueryLeaf(entryID.type, entryID.id));
        return queryTree;
    }

    @Override
    public HashSet<String> getKeys() {
        HashSet<String> keys = new HashSet<>();
        for (MusicLibraryQueryNode node: children) {
            if (node != null) {
                keys.addAll(node.getKeys());
            }
        }
        return keys;
    }
}
