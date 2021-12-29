package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return getAPIClient(context, src, true);
    }

    public static APIClient getAPIClient(Context context, String src, boolean onlyIfEnabled) {
        String api = MusicLibraryService.getAPIFromSource(src);
        String apiInstanceID = MusicLibraryService.getAPIInstanceIDFromSource(src);
        if (api == null || apiInstanceID == null) {
            return new DummyAPIClient();
        }
        if (onlyIfEnabled
                && !SettingsActivityFragment.isSourceEnabled(context, api, apiInstanceID)) {
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
        Bundle settings = SettingsActivityFragment.getSettings(context, src, onlyIfEnabled);
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
        CompletableFuture<List<TransactionResult>> ret =
                CompletableFuture.completedFuture(new ArrayList<>());

        List<TransactionResult> transactionResults = transactions.stream()
                .map(t -> new TransactionResult(t, null))
                .collect(Collectors.toList());

        Batch batch = null;
        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            if (!supports(t.getAction(), t.getArgsSource())) {
                transactionResults.set(i, new TransactionResult(
                        t,
                        "Transaction " + t.getAction()
                                + " with arg source " + t.getArgsSource()
                                + " not supported for " + getSource()
                ));
                break;
            }
            if (batch == null) {
                try {
                    batch = startBatch(context);
                } catch (BatchException e) {
                    transactionResults.set(i, new TransactionResult(
                            t,
                            "Could not start transaction batch: " + e.getMessage()
                    ));
                    break;
                }
            }
            try {
                if (!t.addToBatch(context, batch)) {
                    // Could not add another transaction to batch
                    if (batch.isEmpty()) {
                        // Could not add another transaction to an empty batch
                        throw new BatchException("Could not add transaction to empty batch");
                    } else {
                        // Apply current batch and start a new batch
                        ret = applyTransactionsBatch(context, ret, transactionResults, batch);
                        batch = null;
                        i--; // Retry current transaction in new batch
                        continue;
                    }
                }
                // Transaction successfully added to batch
            } catch (BatchException e) {
                transactionResults.set(i, new TransactionResult(
                        t,
                        "Could not add transaction "
                                + "(" + t.getDisplayableAction() + ")"
                                + ": " + e.getMessage()
                ));
                break;
            }
        }
        // Apply any batch tail
        if (batch != null && !batch.isEmpty()) {
            ret = applyTransactionsBatch(context, ret, transactionResults, batch);
        }

        return ret.thenApply(results -> {
            boolean encounteredError = false;
            for (int i = 0; i < transactionResults.size(); i++) {
                TransactionResult tr = transactionResults.get(i);
                if (i >= results.size()) {
                    transactionResults.set(i, new TransactionResult(
                            tr.transaction,
                            "Earlier transaction failed"
                    ));
                    continue;
                }
                String error = results.get(i).error;
                if (error != null) {
                    encounteredError = true;
                    if (tr.error == null) {
                        // Only replace if there is no error
                        transactionResults.set(i, new TransactionResult(
                                tr.transaction,
                                error
                        ));
                    }
                    continue;
                }
                // Mark all transactions after a failed transaction as failed
                if (encounteredError) {
                    transactionResults.set(i, new TransactionResult(
                            tr.transaction,
                            "Earlier transaction failed"
                    ));
                }
            }
            return transactionResults;
        });
    }

    private CompletableFuture<List<TransactionResult>> applyTransactionsBatch(
            Context context,
            CompletableFuture<List<TransactionResult>> ret,
            List<TransactionResult> transactionResults,
            Batch batch
    ) {
        return ret.thenCompose(results -> {
            if (results.stream().anyMatch(r -> r.error != null)) {
                // There are errors, do not apply next transaction batch
                return CompletableFuture.completedFuture(results);
            }
            // No previous errors, apply next transaction batch
            return CompletableFuture.completedFuture(results).thenCombine(
                    batch.commit(context).handle((aVoid, throwable) -> {
                        if (throwable != null) {
                            Log.e(LC, "TransactionBatch error: " + throwable.getMessage());
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
                    }),
                    (a, b) -> Stream.concat(a.stream(), b.stream())
                            .collect(Collectors.toList())
            );
        });
    }

    public abstract Batch startBatch(Context context) throws BatchException;

    public static class BatchException extends Exception {
        public BatchException(String error) {
            super(error);
        }
    }

    public abstract static class Batch {
        static class AddMetaParams {
            final EntryID entryID;
            final String key;
            final String value;
            AddMetaParams(EntryID entryID, String key, String value) {
                this.entryID = entryID;
                this.key = key;
                this.value = value;
            }
        }

        public boolean addMeta(
                Context context,
                EntryID entryID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class DeleteMetaParams {
            final EntryID entryID;
            final String key;
            final String value;
            DeleteMetaParams(EntryID entryID, String key, String value) {
                this.entryID = entryID;
                this.key = key;
                this.value = value;
            }
        }

        public boolean deleteMeta(
                Context context,
                EntryID entryID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class EditMetaParams {
            final EntryID entryID;
            final String key;
            final String oldValue;
            final String newValue;
            EditMetaParams(EntryID entryID, String key, String oldValue, String newValue) {
                this.entryID = entryID;
                this.key = key;
                this.oldValue = oldValue;
                this.newValue = newValue;
            }
        }

        public boolean editMeta(
                Context context,
                EntryID entryID,
                String key,
                String oldValue,
                String newValue
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class AddPlaylistParams {
            final EntryID playlistID;
            final String name;
            final String query;
            AddPlaylistParams(EntryID playlistID, String name, String query) {
                this.playlistID = playlistID;
                this.name = name;
                this.query = query;
            }
        }

        public boolean addPlaylist(
                Context context,
                EntryID playlistID,
                String name,
                String query
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class DeletePlaylistParams {
            final EntryID playlistID;
            DeletePlaylistParams(EntryID playlistID) {
                this.playlistID = playlistID;
            }
        }

        public boolean deletePlaylist(
                Context context,
                EntryID playlistID
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class AddPlaylistMetaParams {
            final EntryID playlistID;
            final String key;
            final String value;
            AddPlaylistMetaParams(EntryID playlistID, String key, String value) {
                this.playlistID = playlistID;
                this.key = key;
                this.value = value;
            }
        }

        public boolean addPlaylistMeta(
                Context context,
                EntryID playlistID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class DeletePlaylistMetaParams {
            final EntryID playlistID;
            final String key;
            final String value;
            DeletePlaylistMetaParams(EntryID playlistID, String key, String value) {
                this.playlistID = playlistID;
                this.key = key;
                this.value = value;
            }
        }

        public boolean deletePlaylistMeta(
                Context context,
                EntryID playlistID,
                String key,
                String value
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class EditPlaylistMetaParams {
            final EntryID playlistID;
            final String key;
            final String oldValue;
            final String newValue;
            EditPlaylistMetaParams(EntryID playlistID,
                                   String key,
                                   String oldValue,
                                   String newValue) {
                this.playlistID = playlistID;
                this.key = key;
                this.oldValue = oldValue;
                this.newValue = newValue;
            }
        }

        public boolean editPlaylistMeta(
                Context context,
                EntryID playlistID,
                String key,
                String oldValue,
                String newValue
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class AddPlaylistEntryParams {
            final EntryID playlistID;
            final EntryID entryID;
            final String beforePlaylistEntryID;
            final Meta metaSnapshot;
            AddPlaylistEntryParams(EntryID playlistID,
                                   EntryID entryID,
                                   String beforePlaylistEntryID,
                                   Meta metaSnapshot) {
                this.playlistID = playlistID;
                this.entryID = entryID;
                this.beforePlaylistEntryID = beforePlaylistEntryID;
                this.metaSnapshot = metaSnapshot;
            }
        }

        public boolean addPlaylistEntry(
                Context context,
                EntryID playlistID,
                EntryID entryID,
                String beforePlaylistEntryID,
                Meta metaSnapshot
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class DeletePlaylistEntryParams {
            final EntryID playlistID;
            final String playlistEntryID;
            final EntryID entryID;
            DeletePlaylistEntryParams(EntryID playlistID,
                                      String playlistEntryID,
                                      EntryID entryID) {
                this.playlistID = playlistID;
                this.playlistEntryID = playlistEntryID;
                this.entryID = entryID;
            }
        }

        public boolean deletePlaylistEntry(
                Context context,
                EntryID playlistID,
                String playlistEntryID,
                EntryID entryID
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        static class MovePlaylistEntryParams {
            final EntryID playlistID;
            final String playlistEntryID;
            final EntryID entryID;
            final String beforePlaylistEntryID;
            MovePlaylistEntryParams(EntryID playlistID,
                                    String playlistEntryID,
                                    EntryID entryID,
                                    String beforePlaylistEntryID) {
                this.playlistID = playlistID;
                this.playlistEntryID = playlistEntryID;
                this.entryID = entryID;
                this.beforePlaylistEntryID = beforePlaylistEntryID;
            }
        }

        public boolean movePlaylistEntry(
                Context context,
                EntryID playlistID,
                String playlistEntryID,
                EntryID entryID,
                String beforePlaylistEntryID
        ) throws BatchException {
            throw new BatchException("Not implemented");
        }

        abstract CompletableFuture<Void> commit(Context context);

        public abstract boolean isEmpty();
    }
}
