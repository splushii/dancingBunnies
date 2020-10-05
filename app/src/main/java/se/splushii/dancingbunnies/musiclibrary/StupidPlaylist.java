package se.splushii.dancingbunnies.musiclibrary;

import java.util.List;

import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class StupidPlaylist extends Playlist {
    private static final String LC = Util.getLogContext(StupidPlaylist.class);

    private final List<PlaylistEntry> entries;
    public StupidPlaylist(PlaylistID playlistID, String name, List<PlaylistEntry> entries) {
        super(playlistID, name);
        this.entries = entries;
    }

    public List<PlaylistEntry> getEntries() {
        return entries;
    }
}
