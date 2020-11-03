package se.splushii.dancingbunnies.musiclibrary;

import java.util.List;

import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class StupidPlaylist extends Playlist {
    private static final String LC = Util.getLogContext(StupidPlaylist.class);

    private final List<PlaylistEntry> entries;
    public StupidPlaylist(Meta meta, List<PlaylistEntry> entries) {
        super(meta);
        this.entries = entries;
    }

    public List<PlaylistEntry> getEntries() {
        return entries;
    }
}
