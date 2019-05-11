package se.splushii.dancingbunnies.audioplayer;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaDescriptionCompat;

import com.google.android.gms.cast.MediaMetadata;

import java.util.Objects;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class PlaybackEntry implements Parcelable {
    public static final String USER_TYPE_PLAYLIST = "playlist";
    public static final String USER_TYPE_QUEUE = "queue";
    public static final String USER_TYPE_HISTORY = "history";
    public static final String USER_TYPE_EXTERNAL = "external";
    private static final String BUNDLE_KEY_PLAYBACKID = "dancingbunnies.bundle.key.playbackentry.playbackID";
    private static final String BUNDLE_KEY_PLAYBACKTYPE = "dancingbunnies.bundle.key.playbackentry.playbackType";
    private static final String BUNDLE_KEY_PRELOADED = "dancingbunnies.bundle.key.playbackentry.preloaded";

    public final EntryID entryID;
    public final long playbackID;
    public final String playbackType;
    private boolean preloaded = false;

    public PlaybackEntry(MediaMetadata metadata) {
        this.entryID = EntryID.from(metadata);
        this.playbackID = Long.parseLong(metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYBACK_ID));
        String playbackType = metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYBACK_TYPE);
        this.playbackType = playbackType != null ? playbackType : PlaybackEntry.USER_TYPE_EXTERNAL;
    }

    public PlaybackEntry(EntryID entryID, long playbackID, String playbackType) {
        this.entryID = entryID;
        this.playbackID = playbackID;
        this.playbackType = playbackType;
    }

    private PlaybackEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        playbackID = in.readLong();
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
        return playbackID + " " + entryID.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaybackEntry that = (PlaybackEntry) o;
        return playbackID == that.playbackID &&
                Objects.equals(entryID, that.entryID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryID, playbackID);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(entryID, flags);
        dest.writeLong(playbackID);
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
                .setMediaId(entryID.id) // TODO: Use entryID.key() for uniqueness?
                .setExtras(b)
                .build();
    }

    private Bundle toBundle() {
        Bundle b = entryID.toBundle();
        b.putString(BUNDLE_KEY_PLAYBACKID, Long.toString(playbackID));
        b.putString(BUNDLE_KEY_PLAYBACKTYPE, playbackType);
        b.putBoolean(BUNDLE_KEY_PRELOADED, preloaded);
        return b;
    }

    public PlaybackEntry(MediaDescriptionCompat description) {
        Bundle b = description.getExtras();
        entryID = EntryID.from(b);
        playbackID = Long.parseLong(b.getString(BUNDLE_KEY_PLAYBACKID));
        playbackType = b.getString(BUNDLE_KEY_PLAYBACKTYPE);
        preloaded = b.getBoolean(BUNDLE_KEY_PRELOADED);
    }
}
