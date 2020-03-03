package se.splushii.dancingbunnies.audioplayer;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaDescriptionCompat;

import com.google.android.gms.cast.MediaMetadata;

import java.util.Objects;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;

public class PlaybackEntry implements Parcelable {
    public static final String USER_TYPE_PLAYLIST = "playlist";
    public static final String USER_TYPE_QUEUE = "queue";
    public static final String USER_TYPE_EXTERNAL = "external";
    private static final String BUNDLE_KEY_PLAYBACKID = "dancingbunnies.bundle.key.playbackentry.playbackID";
    private static final String BUNDLE_KEY_PLAYBACKTYPE = "dancingbunnies.bundle.key.playbackentry.playbackType";
    private static final String BUNDLE_KEY_PRELOADED = "dancingbunnies.bundle.key.playbackentry.preloaded";
    private static final String BUNDLE_KEY_PLAYLIST_POS = "dancingbunnies.bundle.key.playbackentry.playlistPos";
    private static final String BUNDLE_KEY_PLAYLIST_SELECTION_ID = "dancingbunnies.bundle.key.playbackentry.playlistSelectionID";
    public static final long PLAYBACK_ID_INVALID = -1;
    static final long PLAYLIST_POS_NONE = -1;
    static final long PLAYLIST_SELECTION_ID_INVALID = -1;

    public final EntryID entryID;
    public final long playbackID;
    public final String playbackType;
    public final long playlistPos;
    public final long playlistSelectionID;
    private boolean preloaded = false;

    public PlaybackEntry(MediaMetadata metadata) {
        this.entryID = EntryID.from(metadata);
        this.playbackID = Long.parseLong(metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYBACK_ID));
        String playbackType = metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYBACK_TYPE);
        this.playbackType = playbackType != null ? playbackType : PlaybackEntry.USER_TYPE_EXTERNAL;
        this.playlistPos = Long.parseLong(metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYLIST_POS));
        this.playlistSelectionID = Long.parseLong(metadata.getString(CastAudioPlayer.CASTMETA_KEY_PLAYLIST_SELECTION_ID));
    }

    public PlaybackEntry(PlaylistEntry playlistEntry, long playlistSelectionID, long playbackID) {
        this.entryID = EntryID.from(playlistEntry);
        this.playbackID = playbackID;
        this.playbackType = USER_TYPE_PLAYLIST;
        this.playlistPos = playlistEntry.pos;
        this.playlistSelectionID = playlistSelectionID;
    }

    public PlaybackEntry(EntryID entryID,
                         long playbackID,
                         String playbackType,
                         long playlistPos,
                         long playlistSelectionID) {
        this.entryID = entryID;
        this.playbackID = playbackID;
        this.playbackType = playbackType;
        this.playlistPos = playlistPos;
        this.playlistSelectionID = playlistSelectionID;
    }

    private PlaybackEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        playbackID = in.readLong();
        playbackType = in.readString();
        preloaded = in.readByte() != 0;
        playlistPos = in.readLong();
        playlistSelectionID = in.readLong();
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
        return playbackID
                + (PlaybackEntry.USER_TYPE_PLAYLIST.equals(playbackType) ?
                "." + playlistSelectionID + "[" + playlistPos + "] " : "")
                + entryID.toString() + " " + playbackType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaybackEntry that = (PlaybackEntry) o;
        return playbackID == that.playbackID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playbackID);
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
        dest.writeLong(playlistPos);
        dest.writeLong(playlistSelectionID);
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
        b.putString(BUNDLE_KEY_PLAYLIST_POS, Long.toString(playlistPos));
        b.putString(BUNDLE_KEY_PLAYLIST_SELECTION_ID, Long.toString(playlistSelectionID));
        return b;
    }

    public PlaybackEntry(MediaDescriptionCompat description) {
        Bundle b = description.getExtras();
        entryID = EntryID.from(b);
        playbackID = Long.parseLong(b.getString(BUNDLE_KEY_PLAYBACKID));
        playbackType = b.getString(BUNDLE_KEY_PLAYBACKTYPE);
        preloaded = b.getBoolean(BUNDLE_KEY_PRELOADED);
        playlistPos = Long.parseLong(b.getString(BUNDLE_KEY_PLAYLIST_POS));
        playlistSelectionID = Long.parseLong(b.getString(BUNDLE_KEY_PLAYLIST_SELECTION_ID));
    }
}
