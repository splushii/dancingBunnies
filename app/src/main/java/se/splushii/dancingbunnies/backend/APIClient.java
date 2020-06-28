package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_SRC_ID_GIT;
import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_SRC_ID_SUBSONIC;

public abstract class APIClient {
    public static final String SETTINGS_KEY_GENERAL_TAG_DELIM = "se.splushii.dancingbunnies.settings_key.general.tag_delim";
    public static final String SETTINGS_KEY_SUBSONIC_URL = "se.splushii.dancingbunnies.settings_key.subsonic.url";
    public static final String SETTINGS_KEY_SUBSONIC_USERNAME = "se.splushii.dancingbunnies.settings_key.subsonic.username";
    public static final String SETTINGS_KEY_SUBSONIC_PASSWORD = "se.splushii.dancingbunnies.settings_key.subsonic.password";
    public static final String SETTINGS_KEY_GIT_URL = "se.splushii.dancingbunnies.settings_key.subsonic.url";

    private static final String LC = Util.getLogContext(APIClient.class);

    final String src;

    public APIClient(String src) {
        this.src = src;
    }

    public String getSource() {
        return src;
    }

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
    public abstract void loadSettings(Context context, Path workDir, Bundle settings);

    public static APIClient getAPIClient(Context context, String src) {
        String api = MusicLibraryService.getAPIFromSource(src);
        String apiInstanceID = MusicLibraryService.getAPIInstanceIDFromSource(src);
        if (api == null || apiInstanceID == null) {
            return null;
        }
//        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!SettingsActivityFragment.isSourceEnabled(context, api, apiInstanceID)) {
            return null;
        }
        APIClient apiClient;
        switch (api) {
            case API_SRC_ID_SUBSONIC:
                apiClient = new SubsonicAPIClient(src, context);
                break;
            case API_SRC_ID_GIT:
                apiClient = new GitAPIClient(src);
                break;
            default:
                return null;
        }
        Bundle settings = SettingsActivityFragment.getSettings(context, src);
        // TODO: Use FilesDir instead of CacheDir
        File cacheDir = context.getCacheDir();
        Path gitAPIClientPath = cacheDir.toPath().resolve(api);
        Path gitAPIClientInstancePath = gitAPIClientPath.resolve(apiInstanceID);
        Path workDir;
        try {
            workDir = Files.createDirectories(gitAPIClientInstancePath);
            Log.d(LC, "API (" + src + ") working directory: " + workDir.toString());
        } catch (IOException e) {
            Log.e(LC, "Could not create working directory for API: " + src);
            e.printStackTrace();
            return null;
        }
        apiClient.loadSettings(context, workDir, settings);
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
