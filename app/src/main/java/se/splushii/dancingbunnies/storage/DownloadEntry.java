package se.splushii.dancingbunnies.storage;

import android.os.Parcel;
import android.os.Parcelable;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class DownloadEntry implements Parcelable {
    public final EntryID entryID;
    public final int priority;

    public DownloadEntry(EntryID entryID, int priority) {
        this.entryID = entryID;
        this.priority = priority;
    }

    protected DownloadEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        priority = in.readInt();
    }

    public static final Creator<DownloadEntry> CREATOR = new Creator<DownloadEntry>() {
        @Override
        public DownloadEntry createFromParcel(Parcel in) {
            return new DownloadEntry(in);
        }

        @Override
        public DownloadEntry[] newArray(int size) {
            return new DownloadEntry[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(entryID, i);
        parcel.writeInt(priority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadEntry that = (DownloadEntry) o;
        return entryID.equals(that.entryID);
    }

    @Override
    public int hashCode() {
        return entryID.hashCode();
    }
}
