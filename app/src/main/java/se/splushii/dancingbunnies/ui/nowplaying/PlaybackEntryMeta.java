package se.splushii.dancingbunnies.ui.nowplaying;

import android.os.Parcel;
import android.os.Parcelable;

import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;

public class PlaybackEntryMeta implements Parcelable {
    final PlaybackEntry playbackEntry;
    public final Meta meta;

    private PlaybackEntryMeta(Parcel in) {
        playbackEntry = in.readParcelable(PlaybackEntry.class.getClassLoader());
        meta = in.readParcelable(Meta.class.getClassLoader());
    }

    public static final Creator<PlaybackEntryMeta> CREATOR = new Creator<PlaybackEntryMeta>() {
        @Override
        public PlaybackEntryMeta createFromParcel(Parcel in) {
            return new PlaybackEntryMeta(in);
        }

        @Override
        public PlaybackEntryMeta[] newArray(int size) {
            return new PlaybackEntryMeta[size];
        }
    };

    public PlaybackEntryMeta(PlaybackEntry playbackEntry, Meta meta) {
        this.playbackEntry = playbackEntry;
        this.meta = meta;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(playbackEntry, flags);
        dest.writeParcelable(meta, flags);
    }
}
