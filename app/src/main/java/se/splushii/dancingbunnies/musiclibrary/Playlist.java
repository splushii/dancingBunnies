package se.splushii.dancingbunnies.musiclibrary;

public abstract class Playlist {
    public final String name;
    public final PlaylistID id;
    Playlist(PlaylistID playlistID, String name) {
        this.id = playlistID;
        this.name = name;
    }
}
