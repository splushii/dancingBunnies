package se.splushii.dancingbunnies.musiclibrary;

import android.os.Parcel;
import android.os.Parcelable;

import org.apache.lucene.document.Document;

import androidx.annotation.NonNull;
import se.splushii.dancingbunnies.search.Indexer;

public class LibraryEntry implements Comparable<LibraryEntry>, Parcelable {
    // Uniquely identifies a LibraryEntry
    public final EntryID entryID;

    private final String name;

    public LibraryEntry(EntryID entryID, String name) {
        this.entryID = entryID;
        this.name = name;
    }

    private LibraryEntry(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        name = in.readString();
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

    public static LibraryEntry from(Document doc) {
        EntryID entryID = EntryID.from(doc);
        String name = doc.get(Indexer.meta2fieldNameMap.get(Meta.METADATA_KEY_TITLE));
        return new LibraryEntry(entryID, name);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(entryID, flags);
        dest.writeString(name);
    }
}
