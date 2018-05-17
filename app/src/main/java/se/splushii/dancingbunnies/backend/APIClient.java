package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Playlist;

public abstract class APIClient {
    public abstract boolean hasLibrary();

    /**
     * @param handler Optional to call handler methods
     * @return A future with a list of MediaMetaData
     * Each MediaMetaData element must include the following entries:
     *  - Meta.METADATA_KEY_API
     *  - Meta.METADATA_KEY_MEDIA_ROOT
     *  - Meta.METADATA_KEY_MEDIA_ID
     *  - Meta.METADATA_KEY_TITLE
     */
    public CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> getLibrary(APIClientRequestHandler handler) {
        CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> ret = new CompletableFuture<>();
        ret.complete(Optional.empty());
        return ret;
    }
    public abstract boolean hasPlaylists();
    public CompletableFuture<Optional<Playlist>> getPlaylists() {
        CompletableFuture<Optional<Playlist>> ret = new CompletableFuture<>();
        ret.complete(Optional.empty());
        return ret;
    }
    public abstract AudioDataSource getAudioData(EntryID entryID);
    public abstract void loadSettings(Context context);
}