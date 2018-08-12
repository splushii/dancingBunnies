package se.splushii.dancingbunnies.audioplayer;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaMetadataCompat;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class PlaybackEntry implements Parcelable {
    public static final String USER_TYPE_PLAYLIST = "playlist";
    public static final String USER_TYPE_QUEUE = "queue";
    public static final String USER_TYPE_EXTERNAL = "external";
    public final EntryID entryID;
    public final MediaMetadataCompat meta;
    public final String playbackType;

    // TODO: Why not use LibraryEntry for this instead?
    public PlaybackEntry(EntryID entryID, String playbackType, MediaMetadataCompat meta) {
        this.entryID = entryID;
        this.meta = meta;
        this.playbackType = playbackType;
    }

    protected PlaybackEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        meta = in.readParcelable(MediaMetadataCompat.class.getClassLoader());
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
        return entryID.toString() + ":  " + meta.getDescription().getDescription();
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
        dest.writeParcelable(meta, flags);
        dest.writeString(playbackType);
    }
}
