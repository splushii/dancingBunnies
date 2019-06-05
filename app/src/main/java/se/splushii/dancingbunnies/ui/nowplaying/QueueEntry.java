package se.splushii.dancingbunnies.ui.nowplaying;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;

public class QueueEntry implements Parcelable {
    final PlaybackEntry playbackEntry;
    final long pos;
    final long id;

    public QueueEntry(PlaybackEntry playbackEntry, long id, int pos) {
        this.playbackEntry = playbackEntry;
        this.pos = pos;
        this.id = id;
    }

    private QueueEntry(Parcel in) {
        playbackEntry = in.readParcelable(PlaybackEntry.class.getClassLoader());
        pos = in.readLong();
        id = in.readLong();
    }

    public static final Creator<QueueEntry> CREATOR = new Creator<QueueEntry>() {
        @Override
        public QueueEntry createFromParcel(Parcel in) {
            return new QueueEntry(in);
        }

        @Override
        public QueueEntry[] newArray(int size) {
            return new QueueEntry[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(playbackEntry, flags);
        dest.writeLong(pos);
        dest.writeLong(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueEntry that = (QueueEntry) o;
        return id == that.id;
    }

    @Override
    public String toString() {
        return "QueueEntry{" +
                "pos=" + pos +
                ", id=" + id +
                ", playbackEntry=" + playbackEntry +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
