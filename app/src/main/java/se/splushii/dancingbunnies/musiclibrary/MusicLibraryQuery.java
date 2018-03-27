package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;

public class MusicLibraryQuery {
    public final String src;
    public final String id;
    public final LibraryEntry.EntryType type;

    public MusicLibraryQuery(String src, String id, LibraryEntry.EntryType type) {
        this.src = src;
        this.id = id;
        this.type = type;
    }

    public static String getMusicLibraryQueryOptionsString(Bundle options) {
        String api = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(options.getString(Meta.METADATA_KEY_TYPE));
        return "api: " + api + ", type: " + type.name();
    }

    public static MusicLibraryQuery generateMusicLibraryQuery(String parentId, Bundle options) {
        String src = options.getString(Meta.METADATA_KEY_API);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(options.getString(Meta.METADATA_KEY_TYPE));
        return new MusicLibraryQuery(src, parentId, type);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(Meta.METADATA_KEY_API, src);
        b.putString(Meta.METADATA_KEY_MEDIA_ID, id);
        b.putString(Meta.METADATA_KEY_TYPE, type.name());
        return b;
    }
}
