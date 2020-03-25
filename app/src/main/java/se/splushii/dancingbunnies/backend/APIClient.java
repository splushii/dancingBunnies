package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import androidx.preference.PreferenceManager;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
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

    /**
     * @param handler Optional to call handler methods
     * @return A future with a list of MediaMetaData
     * Each MediaMetaData element must include the following entries:
     *  - Meta.METADATA_KEY_API
     *  - Meta.METADATA_KEY_MEDIA_ROOT
     *  - Meta.METADATA_KEY_MEDIA_ID
     *  - Meta.METADATA_KEY_TITLE
     */
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
    public abstract void loadSettings(Context context);

    public static APIClient getAPIClient(Context context, String api) {
        HashMap<String, APIClient> apis = new HashMap<>();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!settings.getBoolean(context.getResources().getString(R.string.pref_key_subsonic), false)) {
            apis.remove(API_ID_SUBSONIC);
        } else if (!apis.containsKey(API_ID_SUBSONIC)) {
            apis.put(API_ID_SUBSONIC, new SubsonicAPIClient(context));
        }
        for (String key: apis.keySet()) {
            apis.get(key).loadSettings(context);
        }
        return apis.get(api);
    }

    public static AudioDataSource getAudioDataSource(Context context, EntryID entryID) {
        HashMap<String, APIClient> apis = new HashMap<>();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (settings.getBoolean(context.getResources().getString(R.string.pref_key_subsonic), false)) {
            apis.put(API_ID_SUBSONIC, new SubsonicAPIClient(context));
        }
        APIClient apiClient = apis.get(entryID.src);
        if (apiClient == null) {
            Log.e(LC, "Could not get API client for entry: " + entryID);
            return null;
        }
        apiClient.loadSettings(context);
        return apiClient.getAudioData(entryID);
    }
}
