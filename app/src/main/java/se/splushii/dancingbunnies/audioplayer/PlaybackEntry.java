package se.splushii.dancingbunnies.audioplayer;

import android.os.Parcel;
import android.os.Parcelable;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;

public class PlaybackEntry implements Parcelable {
    public static final String USER_TYPE_PLAYLIST = "playlist";
    public static final String USER_TYPE_QUEUE = "queue";
    public static final String USER_TYPE_HISTORY = "history";
    public static final String USER_TYPE_EXTERNAL = "external";

    public final EntryID entryID;
    public final String playbackType;
    private boolean preloaded = false;

    public PlaybackEntry(Meta meta) {
        this.entryID = EntryID.from(meta);
        String playbackType = meta.getString(Meta.METADATA_KEY_PLAYBACK_TYPE);
        this.playbackType = playbackType != null ? playbackType : PlaybackEntry.USER_TYPE_EXTERNAL;
    }

    public PlaybackEntry(Meta meta, String playbackType) {
        this.entryID = EntryID.from(meta);
        this.playbackType = playbackType;
    }

    public PlaybackEntry(EntryID entryID, String playbackType) {
        this.entryID = entryID;
        this.playbackType = playbackType;
    }

    private PlaybackEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        playbackType = in.readString();
        preloaded = in.readByte() != 0;
    }

    public static final Creator<PlaybackEntry> CREATOR = new Creator<PlaybackEntry>() {
        @Override
        public PlaybackEntry createFromParcel(Parcel in) {
            return new PlaybackEntry(in);
        }

        @Override
        public PlaybackEntry[] newArray(int size) {
            return new PlaybackEntry[size];
        }
    };

    @Override
    public String toString() {
        return entryID.toString();
    }

    @Override
    public int hashCode() {
        return entryID.hashCode();
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
        PlaybackEntry e = (PlaybackEntry) obj;
        return entryID.equals(e.entryID);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(entryID, flags);
        dest.writeString(playbackType);
        dest.writeByte((byte) (preloaded ? 1 : 0));
    }

    void setPreloaded(boolean preloaded) {
        this.preloaded = preloaded;
    }

    boolean isPreloaded() {
        return preloaded;
    }
}
