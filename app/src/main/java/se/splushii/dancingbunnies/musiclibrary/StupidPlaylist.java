package se.splushii.dancingbunnies.musiclibrary;

import java.util.List;

public class StupidPlaylist extends Playlist {
    private List<EntryID> entries;
    public StupidPlaylist(PlaylistID playlistID, String name, List<EntryID> entries) {
        super(playlistID, name);
        this.entries = entries;
    }

    public List<EntryID> getEntries() {
        return entries;
    }
}
