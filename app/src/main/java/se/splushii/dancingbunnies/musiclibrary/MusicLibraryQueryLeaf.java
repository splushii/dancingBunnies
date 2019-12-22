package se.splushii.dancingbunnies.musiclibrary;

import android.os.Parcel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;

public class MusicLibraryQueryLeaf extends MusicLibraryQueryNode {
    private static final String JSON_KEY_KEY = "key";
    private static final String JSON_KEY_OP = "op";
    static final String JSON_KEY_VALUE = "value";

    enum Op {
        EQUALS
    }
    private String key;
    private Op operator;
    private String value;

    public MusicLibraryQueryLeaf(String key, String value) {
        this.key = key;
        operator = Op.EQUALS;
        this.value = value;
    }

    public MusicLibraryQueryLeaf(JSONObject jsonRoot) {
        try {
            key = jsonRoot.getString(JSON_KEY_KEY);
            operator = Op.valueOf(jsonRoot.getString(JSON_KEY_OP));
            value = jsonRoot.getString(JSON_KEY_VALUE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected MusicLibraryQueryLeaf(Parcel in) {
        super(in);
        key = in.readString();
        operator = Op.valueOf(in.readString());
        value = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(MusicLibraryQueryNode.CLASS_TYPE_LEAF);
        super.writeToParcel(parcel, i);
        parcel.writeString(key);
        parcel.writeString(operator.name());
        parcel.writeString(value);
    }

    public void setOperator(Op op) {
        operator = op;
    }

    public String getKey() {
        return key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public MusicLibraryQueryLeaf deepCopy() {
        MusicLibraryQueryLeaf copy = new MusicLibraryQueryLeaf(key, value);
        copy.setOperator(operator);
        return copy;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(JSON_KEY_KEY, key);
            jsonRoot.put(JSON_KEY_OP, operator.name());
            jsonRoot.put(JSON_KEY_VALUE, value);
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
        MusicLibraryQueryTree queryTree = new MusicLibraryQueryTree(MusicLibraryQueryTree.Op.AND);
        queryTree.addChild(this);
        queryTree.addChild(new MusicLibraryQueryLeaf(entryID.type, entryID.id));
        return queryTree;
    }

    @Override
    public HashSet<String> getKeys() {
        return new HashSet<>(Collections.singleton(key));
    }
}
