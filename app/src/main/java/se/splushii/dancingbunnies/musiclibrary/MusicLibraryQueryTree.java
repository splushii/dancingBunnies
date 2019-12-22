package se.splushii.dancingbunnies.musiclibrary;

import android.os.Parcel;

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

    // TODO: Add NOT as a separate boolean, both for Tree and for Leaf
    public enum Op {
        AND,
        OR
    }
    private Op operator;
    private final ArrayList<MusicLibraryQueryNode> children;

    public MusicLibraryQueryTree(Op operator) {
        this.operator = operator;
        children = new ArrayList<>();
    }

    public MusicLibraryQueryTree(JSONObject jsonRoot) {
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

    protected MusicLibraryQueryTree(Parcel in) {
        super(in);
        operator = Op.valueOf(in.readString());
        children = in.createTypedArrayList(MusicLibraryQueryNode.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(MusicLibraryQueryNode.CLASS_TYPE_TREE);
        super.writeToParcel(parcel, i);
        parcel.writeString(operator.name());
        parcel.writeList(children);
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
        MusicLibraryQueryTree copy = new MusicLibraryQueryTree(operator);
        for (MusicLibraryQueryNode child: children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(JSON_KEY_OP, operator.name());
            JSONArray jsonChildren = new JSONArray();
            for (MusicLibraryQueryNode node: children) {
                jsonChildren.put(node.toJSON());
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
            queryTree = new MusicLibraryQueryTree(Op.AND);
            queryTree.addChild(this);
        }
        queryTree.addChild(new MusicLibraryQueryLeaf(entryID.type, entryID.id));
        return queryTree;
    }

    @Override
    public HashSet<String> getKeys() {
        HashSet<String> keys = new HashSet<>();
        for (MusicLibraryQueryNode node: children) {
            keys.addAll(node.getKeys());
        }
        return keys;
    }
}
