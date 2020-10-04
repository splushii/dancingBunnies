package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_EDIT;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_MOVE;

public class LocalAPIClient extends APIClient {
    public LocalAPIClient(String apiInstanceID) {
        super(MusicLibraryService.API_SRC_ID_DANCINGBUNNIES, apiInstanceID);
    }

    @Override
    public boolean hasLibrary() {
        return false;
    }

    @Override
    public CompletableFuture<Void> heartbeat() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean hasPlaylists() {
        return false;
    }

    @Override
    public AudioDataSource getAudioData(EntryID entryID) {
        return null;
    }

    @Override
    public void loadSettings(Context context, Path workDir, Bundle settings) {}

    @Override
    public boolean checkAPISupport(String action, String argumentSource) {
        if (action == null) {
            return false;
        }
        switch (action) {
            case META_ADD:
            case META_DELETE:
            case META_EDIT:
            case PLAYLIST_ADD:
            case PLAYLIST_DELETE:
            case PLAYLIST_ENTRY_DELETE:
            case PLAYLIST_ENTRY_MOVE:
            case PLAYLIST_ENTRY_ADD:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Batch startBatch(Context context) throws BatchException {
        return new Batch();
    }

    public static class Batch extends APIClient.Batch {
        @Override
        public void addMeta(Context context,
                            EntryID entryID,
                            String key,
                            String value) {
            // Already applied in TransactionMetaAdd::applyLocally
        }

        @Override
        public void deleteMeta(Context context,
                               EntryID entryID,
                               String key,
                               String value) {
            // Already applied in TransactionMetaDelete::applyLocally
        }

        @Override
        public void editMeta(Context context,
                             EntryID entryID,
                             String key,
                             String oldValue,
                             String newValue) {
            // Already applied in TransactionMetaEdit::applyLocally
        }

        @Override
        public void addPlaylist(Context context,
                                PlaylistID playlistID,
                                String name,
                                String query,
                                PlaylistID beforePlaylistID) {
            // Already applied in TransactionPlaylistAdd::applyLocally
        }

        @Override
        public void deletePlaylist(Context context, PlaylistID playlistID) {
            // Already applied in TransactionPlaylistDelete::applyLocally
        }

        @Override
        public void addPlaylistEntry(Context context,
                                     PlaylistID playlistID,
                                     EntryID entryID,
                                     String beforePlaylistEntryID,
                                     Meta metaSnapshot) {
            // Already applied in TransactionPlaylistEntryAdd::applyLocally
        }

        @Override
        public void deletePlaylistEntry(Context context,
                                        PlaylistID playlistID,
                                        String playlistEntryID,
                                        EntryID entryID) {
            // Already applied in TransactionPlaylistEntryDelete::applyLocally
        }

        @Override
        public void movePlaylistEntry(Context context,
                                      PlaylistID playlistID,
                                      String playlistEntryID,
                                      EntryID entryID,
                                      String beforePlaylistEntryID) {
            // Already applied in TransactionPlaylistEntryMove::applyLocally
        }

        @Override
        CompletableFuture<Void> commit() {
            return Util.futureResult();
        }
    }
}
