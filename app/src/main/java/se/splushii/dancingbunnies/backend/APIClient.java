package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.Song;

public abstract class APIClient {
    public abstract boolean hasLibrary();

    /**
     * @param handler Optional to call handler methods
     * @return A future with a list of MediaMetaData
     * Each MediaMetaData element must include the following entries:
     *  - MusicLibrary.METADATA_KEY_API
     *  - MusicLibrary.METADATA_KEY_MEDIA_ROOT
     *  - MediaMetadataCompat.METADATA_KEY_MEDIA_ID
     *  - MediaMetadataCompat.METADATA_KEY_TITLE
     */
    public CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> getLibrary(APIClientRequestHandler handler) {
        CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> ret = new CompletableFuture<>();
        ret.complete(Optional.<ArrayList<MediaMetadataCompat>>empty());
        return ret;
    }
    public abstract boolean hasPlaylists();
    public CompletableFuture<Optional<Playlist>> getPlaylists() {
        CompletableFuture<Optional<Playlist>> ret = new CompletableFuture<>();
        ret.complete(Optional.<Playlist>empty());
        return ret;
    }
    public AudioDataSource getAudioData(Song song) {
        return getAudioData(song.id());
    }
    public abstract AudioDataSource getAudioData(String id);
    public abstract void loadSettings(Context context);
}