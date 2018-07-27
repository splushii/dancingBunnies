package se.splushii.dancingbunnies.musiclibrary;

public class SmartPlaylist extends Playlist {
    private String query;

    public SmartPlaylist(PlaylistID playlistID, String name, String query) {
        super(playlistID, name);
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
