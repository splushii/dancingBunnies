package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import se.splushii.dancingbunnies.storage.db.Playlist;

public class PlaylistID implements Parcelable {
    public static final int TYPE_INVALID = -1;
    public static final int TYPE_STUPID = 0;
    public static final int TYPE_SMART = 1;
    private static final String BUNDLE_KEY_SRC = "dancingbunnies.bundle.key.playlistid.src";
    private static final String BUNDLE_KEY_ID = "dancingbunnies.bundle.key.playlistid.id";
    private static final String BUNDLE_KEY_TYPE = "dancingbunnies.bundle.key.playlistid.type";
    public final String src;
    public final String id;
    public final int type;

    public PlaylistID(String src, String id, int type) {
        this.src = src;
        this.id = id;
        this.type = type;
    }

    public PlaylistID(Parcel in) {
        src = in.readString();
        id = in.readString();
        type = in.readInt();
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

    private String typeToString() {
        switch (type) {
            default:
            case TYPE_INVALID:
                return "invalid";
            case TYPE_STUPID:
                return "stupid";
            case TYPE_SMART:
                return "smart";
        }
    }

    @Override
    public String toString() {
        return "{src: " + src + ", id: " + id + ", type: " + typeToString() + "}";
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
        return src.equals(e.src) && id.equals(e.id) && type == e.type;
    }

    private String key() {
        return src + id + type;
    }

    public static PlaylistID from(Bundle extras) {
        String src = extras.getString(BUNDLE_KEY_SRC);
        String id = extras.getString(BUNDLE_KEY_ID);
        int type = extras.getInt(BUNDLE_KEY_TYPE);
        return new PlaylistID(src, id, type);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(BUNDLE_KEY_SRC, src);
        b.putString(BUNDLE_KEY_ID, id);
        b.putInt(BUNDLE_KEY_TYPE, type);
        return b;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(src);
        dest.writeString(id);
        dest.writeInt(type);
    }
}
