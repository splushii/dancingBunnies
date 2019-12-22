package se.splushii.dancingbunnies.musiclibrary;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public abstract class MusicLibraryQueryNode implements Parcelable {
    static final int CLASS_TYPE_LEAF = 0;
    static final int CLASS_TYPE_TREE = 1;

    MusicLibraryQueryNode() {}

    protected MusicLibraryQueryNode(Parcel in) {}

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MusicLibraryQueryNode> CREATOR = new Creator<MusicLibraryQueryNode>() {
        @Override
        public MusicLibraryQueryNode createFromParcel(Parcel in) {
            return getImplementation(in);
        }

        @Override
        public MusicLibraryQueryNode[] newArray(int size) {
            return new MusicLibraryQueryNode[size];
        }
    };

    private static MusicLibraryQueryNode getImplementation(Parcel in) {
        switch (in.readInt()) {
            case CLASS_TYPE_LEAF:
                return new MusicLibraryQueryLeaf(in);
            case CLASS_TYPE_TREE:
                return new MusicLibraryQueryTree(in);
            default:
                return null;
        }
    }

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

    public List<MusicLibraryQueryNode> withEntryIDs(List<EntryID> entryIDs) {
        return entryIDs.stream()
                .map(this::withEntryID)
                .collect(Collectors.toList());
    }

    public abstract MusicLibraryQueryNode deepCopy();
    public abstract JSONObject toJSON();
    public abstract MusicLibraryQueryNode withEntryID(EntryID entryID);
    public abstract HashSet<String> getKeys();
}
