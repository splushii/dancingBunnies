package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import se.splushii.dancingbunnies.util.Util;

public class PlaylistID implements Parcelable {
    public static final String TYPE_STUPID = "static";
    public static final String TYPE_SMART = "smart";
    static final String JSON_KEY_SRC = "src";
    static final String JSON_KEY_ID = "id";
    static final String JSON_KEY_TYPE = "type";
    private static final String BUNDLE_KEY_SRC = "dancingbunnies.bundle.key.playlistid.src";
    private static final String BUNDLE_KEY_ID = "dancingbunnies.bundle.key.playlistid.id";
    private static final String BUNDLE_KEY_TYPE = "dancingbunnies.bundle.key.playlistid.type";
    public final String src;
    public final String id;
    public final String type;

    public PlaylistID(String src, String id, String type) {
        this.src = src;
        this.id = id;
        this.type = type;
    }

    public PlaylistID(Parcel in) {
        src = in.readString();
        id = in.readString();
        type = in.readString();
    }

    public static PlaylistID generate(String src, String type) {
        return new PlaylistID(
                src,
                Util.generateID(),
                type
        );
    }

    public static final Creator<PlaylistID> CREATOR = new Creator<PlaylistID>() {
        @Override
        public PlaylistID createFromParcel(Parcel in) {
            return new PlaylistID(in);
        }

        @Override
        public PlaylistID[] newArray(int size) {
            return new PlaylistID[size];
        }
    };

    @Override
    public String toString() {
        return "{src: " + src + ", id: " + id + ", type: " + type + "}";
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PlaylistID e = (PlaylistID) obj;
        return src.equals(e.src) && id.equals(e.id) && type.equals(e.type);
    }

    private String key() {
        return src + id + type;
    }

    public static PlaylistID from(Bundle extras) {
        String src = extras.getString(BUNDLE_KEY_SRC);
        String id = extras.getString(BUNDLE_KEY_ID);
        String type = extras.getString(BUNDLE_KEY_TYPE);
        return new PlaylistID(src, id, type);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(BUNDLE_KEY_SRC, src);
        b.putString(BUNDLE_KEY_ID, id);
        b.putString(BUNDLE_KEY_TYPE, type);
        return b;
    }

    public static PlaylistID from(JSONObject json) throws JSONException {
        String src = json.getString(JSON_KEY_SRC);
        String id = json.getString(JSON_KEY_ID);
        String type = json.getString(JSON_KEY_TYPE);
        return new PlaylistID(src, id, type);
    }

    public JSONObject toJSON() {
        JSONObject root = new JSONObject();
        try {
            root.put(JSON_KEY_SRC, src);
            root.put(JSON_KEY_ID, id);
            root.put(JSON_KEY_TYPE, type);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
        return root;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(src);
        dest.writeString(id);
        dest.writeString(type);
    }

    public String getDisplayableString() {
        return id + " from " + src;
    }
}
