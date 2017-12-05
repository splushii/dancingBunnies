package se.splushii.dancingbunnies.musiclibrary;

public class MusicLibraryQuery {
    public final String src;
    public final String id;
    public final LibraryEntry.EntryType type;

    public MusicLibraryQuery(String src, String id, LibraryEntry.EntryType type) {
        this.src = src;
        this.id = id;
        this.type = type;
    }
}
