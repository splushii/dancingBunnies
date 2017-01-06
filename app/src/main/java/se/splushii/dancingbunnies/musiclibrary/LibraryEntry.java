package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

public abstract class LibraryEntry {
    private String name;
    private String id;
    LibraryEntry(String id, String name) {
        this.name = name;
        this.id = id;
    }
    public String name() { return name; };
    public String id() { return id; };
    public abstract ArrayList<? extends LibraryEntry> getEntries();
}
