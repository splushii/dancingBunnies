package se.splushii.dancingbunnies.audioplayer;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaDescriptionCompat;

import com.google.android.gms.cast.MediaMetadata;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class PlaybackEntry implements Parcelable {
    public static final String USER_TYPE_PLAYLIST = "playlist";
    public static final String USER_TYPE_QUEUE = "queue";
    public static final String USER_TYPE_HISTORY = "history";
    public static final String USER_TYPE_EXTERNAL = "external";
    private static final String BUNDLE_KEY_PLAYBACKTYPE = "dancingbunnies.bundle.key.playbackentry.playbackType";
    private static final String BUNDLE_KEY_PRELOADED = "dancingbunnies.bundle.key.playbackentry.preloaded";

    public final EntryID entryID;
    public final String playbackType;
    private boolean preloaded = false;

    public PlaybackEntry(MediaMetadata metadata) {
        this.entryID = EntryID.from(metadata);
        String playbackType = metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYBACK_TYPE);
        this.playbackType = playbackType != null ? playbackType : PlaybackEntry.USER_TYPE_EXTERNAL;
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

    public boolean isPreloaded() {
        return preloaded;
    }

    MediaDescriptionCompat toMediaDescriptionCompat() {
        Bundle b = toBundle();
        return new MediaDescriptionCompat.Builder()
                .setMediaId(entryID.id)
                .setExtras(b)
                .build();
    }

    private Bundle toBundle() {
        Bundle b = entryID.toBundle();
        b.putString(BUNDLE_KEY_PLAYBACKTYPE, playbackType);
        b.putBoolean(BUNDLE_KEY_PRELOADED, preloaded);
        return b;
    }

    public PlaybackEntry(MediaDescriptionCompat description) {
        Bundle b = description.getExtras();
        entryID = EntryID.from(b);
        playbackType = b.getString(BUNDLE_KEY_PLAYBACKTYPE);
        preloaded = b.getBoolean(BUNDLE_KEY_PRELOADED);
    }
}
