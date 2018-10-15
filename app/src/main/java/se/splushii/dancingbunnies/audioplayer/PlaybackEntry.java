package se.splushii.dancingbunnies.audioplayer;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaMetadataCompat;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;

public class PlaybackEntry implements Parcelable {
    public static final String USER_TYPE_PLAYLIST = "playlist";
    public static final String USER_TYPE_QUEUE = "queue";
    public static final String USER_TYPE_EXTERNAL = "external";
    public static final String PRELOADSTATUS_NOT_PRELOADED = "not_preloaded";
    public static final String PRELOADSTATUS_PRELOADED = "preloaded";
    public static final String PRELOADSTATUS_CACHED = "cached";

    public final MediaMetadataCompat meta;
    public final EntryID entryID;
    public final String playbackType;

    public PlaybackEntry(MediaMetadataCompat meta) {
        this.meta = meta;
        this.entryID = EntryID.from(meta);
        String playbackType = meta.getString(Meta.METADATA_KEY_PLAYBACK_TYPE);
        this.playbackType = playbackType != null ? playbackType : PlaybackEntry.USER_TYPE_EXTERNAL;
    }

    public PlaybackEntry(MediaMetadataCompat meta, String playbackType) {
        this.meta = meta;
        this.entryID = EntryID.from(meta);
        this.playbackType = playbackType;
    }

    protected PlaybackEntry(Parcel in) {
        meta = in.readParcelable(MediaMetadataCompat.class.getClassLoader());
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        playbackType = in.readString();
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
        return entryID.toString() + ":  " + meta.getString(Meta.METADATA_KEY_TITLE);
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
        dest.writeParcelable(meta, flags);
        dest.writeParcelable(entryID, flags);
        dest.writeString(playbackType);
    }
}
