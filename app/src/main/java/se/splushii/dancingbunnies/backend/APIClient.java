package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.storage.transactions.TransactionResult;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_SRC_ID_DANCINGBUNNIES;
import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_SRC_ID_GIT;
import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_SRC_ID_SUBSONIC;

public abstract class APIClient {
    public static final String SETTINGS_KEY_DB_TAG_DELIM = "se.splushii.dancingbunnies.settings_key.db.tag_delim";
    public static final String SETTINGS_KEY_SUBSONIC_URL = "se.splushii.dancingbunnies.settings_key.subsonic.url";
    public static final String SETTINGS_KEY_SUBSONIC_USERNAME = "se.splushii.dancingbunnies.settings_key.subsonic.username";
    public static final String SETTINGS_KEY_SUBSONIC_PASSWORD = "se.splushii.dancingbunnies.settings_key.subsonic.password";
    public static final String SETTINGS_KEY_GIT_REPO = "se.splushii.dancingbunnies.settings_key.git.repo";
    public static final String SETTINGS_KEY_GIT_BRANCH = "se.splushii.dancingbunnies.settings_key.git.branch";
    public static final String SETTINGS_KEY_GIT_USERNAME = "se.splushii.dancingbunnies.settings_key.git.username";
    public static final String SETTINGS_KEY_GIT_PASSWORD = "se.splushii.dancingbunnies.settings_key.git.password";
    public static final String SETTINGS_KEY_GIT_SSH_KEY = "se.splushii.dancingbunnies.settings_key.git.ssh_key";
    public static final String SETTINGS_KEY_GIT_SSH_KEY_PASSPHRASE = "se.splushii.dancingbunnies.settings_key.git.ssh_key_passphrase";

    private static final String LC = Util.getLogContext(APIClient.class);
    private static final String CACHE_DIR = "apiclient";

    final String src;

    public APIClient(String api, String apiInstanceID) {
        this.src = MusicLibraryService.getAPISource(api, apiInstanceID);
    }

    public String getSource() {
        return src;
    }

    public abstract boolean hasLibrary();

    /**
     * @return A future with optional error message. Optional empty on success.
     */
    public abstract CompletableFuture<Void> heartbeat();

    public CompletableFuture<Optional<List<Meta>>> getLibrary(APIClientRequestHandler handler) {
        CompletableFuture<Optional<List<Meta>>> ret = new CompletableFuture<>();
        ret.complete(Optional.empty());
        handler.onFailure("Not implemented");
        return ret;
    }
    public abstract boolean hasPlaylists();
    public CompletableFuture<Optional<List<Playlist>>> getPlaylists(
            Context context,
            APIClientRequestHandler handler
    ) {
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
            return new DummyAPIClient();
        }
        if (!SettingsActivityFragment.isSourceEnabled(context, api, apiInstanceID)) {
            return new DummyAPIClient();
        }
        APIClient apiClient;
        switch (api) {
            case API_SRC_ID_DANCINGBUNNIES:
                apiClient = new LocalAPIClient(apiInstanceID);
                break;
            case API_SRC_ID_SUBSONIC:
                apiClient = new SubsonicAPIClient(apiInstanceID, context);
                break;
            case API_SRC_ID_GIT:
                apiClient = new GitAPIClient(apiInstanceID);
                break;
            default:
                return new DummyAPIClient();
        }
        Bundle settings = SettingsActivityFragment.getSettings(context, src);
        Path workDirPath = context.getCacheDir().toPath()
                .resolve(CACHE_DIR)
                .resolve(api)
                .resolve(Util.md5(apiInstanceID));
        Path workDir;
        try {
            workDir = Files.createDirectories(workDirPath);
        } catch (IOException e) {
            Log.e(LC, "Could not create working directory for API: " + src);
            e.printStackTrace();
            return new DummyAPIClient();
        }
        apiClient.loadSettings(context, workDir, settings);
        return apiClient;
    }

    public static AudioDataSource getAudioDataSource(Context context, EntryID entryID) {
        APIClient apiClient = getAPIClient(context, entryID.src);
        if (apiClient instanceof DummyAPIClient) {
            Log.e(LC, "Could not get API client for entry: " + entryID);
            return null;
        }
        return apiClient.getAudioData(entryID);
    }

    public abstract boolean supports(String action, String argumentSource);

    public boolean supportsAll(String action, Set<String> argumentSources) {
        return argumentSources.stream()
                .allMatch(argumentSource -> supports(action, argumentSource));
    }

    public boolean supportsDancingBunniesSmartPlaylist() {
        return false;
    }

    public CompletableFuture<List<TransactionResult>> applyTransactions(
            Context context,
            List<Transaction> transactions
    ) {
        Batch batch;
        try {
            batch = startBatch(context);
        } catch (BatchException e) {
            return Util.futureResult("Could not start transaction batch: " + e.getMessage());
        }
        List<TransactionResult> transactionResults = new ArrayList<>();
        TransactionResult failedToBatchTransactionResult = null;
        for (Transaction t: transactions) {
            if (!supports(t.getAction(), t.getArgsSource())) {
                failedToBatchTransactionResult = new TransactionResult(
                        t,
                        "Transaction " + t.getAction()
                                + " with arg source " + t.getArgsSource()
                                + " not supported for " + getSource()
                );
                break;
            }
            try {
                t.addToBatch(context, batch);
                transactionResults.add(new TransactionResult(t, null));
            } catch (BatchException e) {
                failedToBatchTransactionResult = new TransactionResult(
                        t,
                        "Could not add transaction "
                                + "(" + t.getDisplayableAction() + ")"
                                + ": " + e.getMessage()
                );
                break;
            }
        }
        if (failedToBatchTransactionResult != null) {
            if (transactionResults.size() <= 0) {
                // There are no successfully batched transactions to commit
                return Util.futureResult(Collections.singletonList(failedToBatchTransactionResult));
            }
            transactionResults.add(failedToBatchTransactionResult);
        }
        return batch.commit()
                .handle((aVoid, throwable) -> {
                    if (throwable != null) {
                        // Add error messages to all affected transactions if the commit failed
                        return transactionResults.stream()
                                .map(t -> {
                                    if (t.error != null) {
                                        // Keep existing error messages
                                        return t;
                                    }
                                    return new TransactionResult(t.transaction, throwable.getMessage());
                                })
                                .collect(Collectors.toList());
                    }
                    // Just return the transaction results if the commit succeeded
                    return transactionResults;
                });
    }

    public abstract Batch startBatch(Context context) throws BatchException;

    public static class BatchException extends Exception {
        public BatchException(String error) {
            super(error);
        }
    }

    public abstract static class Batch {

        public void addMeta(
                Context context,
                EntryID entryID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void deleteMeta(
                Context context,
                EntryID entryID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void editMeta(
                Context context,
                EntryID entryID,
                String key,
                String oldValue,
                String newValue
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void addPlaylist(
                Context context,
                EntryID playlistID,
                String name,
                String query
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void deletePlaylist(
                Context context,
                EntryID playlistID
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void addPlaylistMeta(
                Context context,
                EntryID playlistID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void addPlaylistEntry(
                Context context,
                EntryID playlistID,
                EntryID entryID,
                String beforePlaylistEntryID,
                Meta metaSnapshot
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void deletePlaylistEntry(
                Context context,
                EntryID playlistID,
                String playlistEntryID,
                EntryID entryID
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        public void movePlaylistEntry(
                Context context,
                EntryID playlistID,
                String playlistEntryID,
                EntryID entryID,
                String beforePlaylistEntryID
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        abstract CompletableFuture<Void> commit();
    }
}
