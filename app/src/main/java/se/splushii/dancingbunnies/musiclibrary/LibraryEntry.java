package se.splushii.dancingbunnies.musiclibrary;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public abstract class LibraryEntry implements Comparable<LibraryEntry> {
    public enum EntryType {
        ANY,
        ARTIST,
        ALBUM,
        SONG
    }
    // Uniquely identifies a LibraryEntry
    private String src;
    private String id;
    private EntryType type;

    private String name;
    private HashSet<LibraryEntry> references;

    LibraryEntry(String src, String id, EntryType type, String name) {
        this.src = src;
        this.id = id;
        this.type = type;
        this.name = name;
        references = new HashSet<>();
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
        return Objects.equals(this.src, e.src)
                && Objects.equals(this.id, e.id)
                && this.type == e.type;
    }

    @Override
    public int compareTo(@NonNull LibraryEntry o) {
        int nameVal = name.compareTo(o.name);
        return nameVal != 0 ? nameVal : key().compareTo(o.key());
    }

    public String src() { return src; }
    public String id() { return id; }
    public EntryType type() { return type; }
    public String key() { return src + id + type.name(); }
    public String name() { return name; }
    void addRef(LibraryEntry e) {
        if (!references.contains(e)) {
            references.add(e);
            e.addRef(this);
        }
    }
    void removeRef(LibraryEntry e) {
        if (references.contains(e)) {
            references.remove(e);
            e.removeRef(this);
        }
    }
    void removeAllRefs() {
        ArrayList<LibraryEntry> refsCopy = new ArrayList<>(references);
        for (LibraryEntry e: refsCopy) {
            references.remove(e);
            e.removeRef(this);
        }
    }
    HashSet<LibraryEntry> getRefs() {
        return getRefs(EntryType.ANY);
    }
    HashSet<LibraryEntry> getRefs(EntryType type) {
        if (type == EntryType.ANY) {
            return references;
        }
        HashSet<LibraryEntry> ret = new HashSet<>();
        for (LibraryEntry e: references) {
            if (e.type == type) {
                ret.add(e);
            }
        }
        return ret;
    }

    public abstract ArrayList<? extends LibraryEntry> getEntries();
}
