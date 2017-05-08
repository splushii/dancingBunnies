package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public abstract class LibraryEntry {
    public enum EntryType {
        ALL,
        ARTIST,
        ALBUM,
        SONG
    }
    private String src;
    private String id;
    private EntryType type;
    private String name;
    private LibraryEntry parent;
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
        return (src + id).hashCode();
    }
    public String src() { return src; }
    public String id() { return id; }
    public EntryType type() { return type; }
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
    HashSet<LibraryEntry> getRefs() {
        return getRefs(EntryType.ALL);
    }
    HashSet<LibraryEntry> getRefs(EntryType type) {
        if (type == EntryType.ALL) {
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
    void setParent(LibraryEntry e) {
        parent = e;
        addRef(e);
    }
    LibraryEntry getParent() {
        return parent;
    }
    public abstract ArrayList<? extends LibraryEntry> getEntries();
}
