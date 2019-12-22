package se.splushii.dancingbunnies.musiclibrary;

import se.splushii.dancingbunnies.util.Util;

public class SmartPlaylist extends Playlist {
    private static final String LC = Util.getLogContext(SmartPlaylist.class);
    private final MusicLibraryQueryNode queryNode;

    public SmartPlaylist(PlaylistID playlistID, String name, MusicLibraryQueryNode queryNode) {
        super(playlistID, name);
        this.queryNode = queryNode;
    }

    public String getJSONQueryString() {
        return queryNode.toJSON().toString();
    }
}
