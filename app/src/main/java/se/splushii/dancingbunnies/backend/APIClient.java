package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import androidx.preference.PreferenceManager;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_ID_SUBSONIC;

public abstract class APIClient {
    private static final String LC = Util.getLogContext(APIClient.class);

    public abstract boolean hasLibrary();

    /**
     * @return A future with optional error message. Optional empty on success.
     */
    public abstract CompletableFuture<Optional<String>> heartbeat();

    public CompletableFuture<Optional<List<Meta>>> getLibrary(APIClientRequestHandler handler) {
        CompletableFuture<Optional<List<Meta>>> ret = new CompletableFuture<>();
        ret.complete(Optional.empty());
        handler.onFailure("Not implemented");
        return ret;
    }
    public abstract boolean hasPlaylists();
    public CompletableFuture<Optional<List<Playlist>>> getPlaylists(APIClientRequestHandler handler) {
        CompletableFuture<Optional<List<Playlist>>> ret = new CompletableFuture<>();
        ret.complete(Optional.empty());
        handler.onFailure("Not implemented");
        return ret;
    }
    public abstract AudioDataSource getAudioData(EntryID entryID);
    public abstract void loadSettings(Context context, String apiInstanceID);

    public static APIClient getAPIClient(Context context, String src) {
        String api = MusicLibraryService.getAPIFromSource(src);
        String apiInstanceID = MusicLibraryService.getAPIInstanceIDFromSource(src);
        if (api == null || apiInstanceID == null) {
            return null;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        APIClient apiClient = null;
        if (API_ID_SUBSONIC.equals(api)
                && settings.getBoolean(context.getResources().getString(R.string.pref_key_subsonic), false)) {
            apiClient = new SubsonicAPIClient(context);
        }
        if (apiClient == null) {
            return null;
        }
        apiClient.loadSettings(context, apiInstanceID);
        return apiClient;
    }

    public static AudioDataSource getAudioDataSource(Context context, EntryID entryID) {
        APIClient apiClient = getAPIClient(context, entryID.src);
        if (apiClient == null) {
            Log.e(LC, "Could not get API client for entry: " + entryID);
            return null;
        }
        return apiClient.getAudioData(entryID);
    }
}
