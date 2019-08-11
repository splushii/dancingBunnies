package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaBrowserCompat;

import androidx.annotation.NonNull;

public class LibraryEntry implements Comparable<LibraryEntry>, Parcelable {
    private static final String BUNDLE_KEY_ENTRY_ID = "dancingbunnies.bundle.key.libraryentry.entryid";
    private static final String BUNDLE_KEY_NAME = "dancingbunnies.bundle.key.libraryentry.name";
    private static final String BUNDLE_KEY_SORTED_BY = "dancingbunnies.bundle.key.libraryentry.sortedby";

    // Uniquely identifies a LibraryEntry
    public final EntryID entryID;

    private final String name;
    private final String sortedBy;

    public LibraryEntry(EntryID entryID, String name, String sortedBy) {
        this.entryID = entryID;
        this.name = name;
        this.sortedBy = sortedBy;
    }

    private LibraryEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        name = in.readString();
        sortedBy = in.readString();
    }

    public static final Creator<LibraryEntry> CREATOR = new Creator<LibraryEntry>() {
        @Override
        public LibraryEntry createFromParcel(Parcel in) {
            return new LibraryEntry(in);
        }

        @Override
        public LibraryEntry[] newArray(int size) {
            return new LibraryEntry[size];
        }
    };

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putParcelable(BUNDLE_KEY_ENTRY_ID, entryID);
        b.putString(BUNDLE_KEY_NAME, name());
        b.putString(BUNDLE_KEY_SORTED_BY, sortedBy());
        return b;
    }

    public static LibraryEntry from(MediaBrowserCompat.MediaItem item) {
        Bundle b = item.getDescription().getExtras();
        EntryID entryID = b.getParcelable(BUNDLE_KEY_ENTRY_ID);
        String name = b.getString(BUNDLE_KEY_NAME);
        String sortedBy = b.getString(BUNDLE_KEY_SORTED_BY);
        return new LibraryEntry(entryID, name, sortedBy);
    }

    @Override
    public String toString() {
        return name + ":" + entryID.key();
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
        LibraryEntry e = (LibraryEntry) obj;
        return this.entryID.equals(e.entryID);
    }

    @Override
    public int compareTo(@NonNull LibraryEntry o) {
        if (o == null || o.name == null) {
            return -1;
        }
        if (name == null) {
            return 1;
        }
        int nameVal = name.toLowerCase().compareTo(o.name.toLowerCase());
        return nameVal != 0 ? nameVal : key().compareTo(o.key());
    }

    public String src() { return entryID.src; }
    public String id() { return entryID.id; }
    public String type() { return entryID.type; }
    public String key() { return entryID.key(); }
    public String name() { return name; }
    public String sortedBy() { return sortedBy; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(entryID, flags);
        dest.writeString(name);
        dest.writeString(sortedBy);
    }
}
