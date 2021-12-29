package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_EDIT;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_MOVE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_META_ADD;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_META_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_META_EDIT;

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
    public boolean supports(String action, String argumentSource) {
        if (action == null) {
            return false;
        }
        String argumentAPI = MusicLibraryService.getAPIFromSource(argumentSource);
        switch (argumentAPI) {
            default:
                return false;
            case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
            case MusicLibraryService.API_SRC_ID_GIT:
                break;
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
            case PLAYLIST_META_ADD:
            case PLAYLIST_META_DELETE:
            case PLAYLIST_META_EDIT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean supportsDancingBunniesSmartPlaylist() {
        return true;
    }

    @Override
    public Batch startBatch(Context context) throws BatchException {
        return new Batch();
    }

    public static class Batch extends APIClient.Batch {
        private static final String LC = Util.getLogContext(Batch.class);

        private String currentType = null;
        private final ArrayList<AddMetaParams> add = new ArrayList<>();
        private final ArrayList<DeleteMetaParams> delete = new ArrayList<>();
        private final ArrayList<EditMetaParams> edit = new ArrayList<>();
        private final ArrayList<AddPlaylistParams> addPlaylist = new ArrayList<>();
        private final ArrayList<DeletePlaylistParams> deletePlaylist = new ArrayList<>();
        private final ArrayList<AddPlaylistEntryParams> addPlaylistEntry = new ArrayList<>();
        private final ArrayList<DeletePlaylistEntryParams> deletePlaylistEntry = new ArrayList<>();
        private final ArrayList<MovePlaylistEntryParams> movePlaylistEntry = new ArrayList<>();
        private final ArrayList<AddPlaylistMetaParams> addPlaylistMeta = new ArrayList<>();
        private final ArrayList<DeletePlaylistMetaParams> deletePlaylistMeta = new ArrayList<>();
        private final ArrayList<EditPlaylistMetaParams> editPlaylistMeta = new ArrayList<>();

        @Override
        public boolean isEmpty() {
            return currentType == null;
        }

        @Override
        public boolean addMeta(Context context,
                               EntryID entryID,
                               String key,
                               String value) {
            return addTransaction(
                    META_ADD,
                    add,
                    new AddMetaParams(entryID, key, value)
            );
        }

        @Override
        public boolean deleteMeta(Context context,
                                  EntryID entryID,
                                  String key,
                                  String value) {
            return addTransaction(
                    META_DELETE,
                    delete,
                    new DeleteMetaParams(entryID, key, value)
            );
        }

        @Override
        public boolean editMeta(Context context,
                                EntryID entryID,
                                String key,
                                String oldValue,
                                String newValue) {
            return addTransaction(
                    META_EDIT,
                    edit,
                    new EditMetaParams(entryID, key, oldValue, newValue)
            );
        }

        @Override
        public boolean addPlaylist(Context context,
                                   EntryID playlistID,
                                   String name,
                                   String query) {
            return addTransaction(
                    PLAYLIST_ADD,
                    addPlaylist,
                    new AddPlaylistParams(playlistID, name, query)
            );
        }

        @Override
        public boolean deletePlaylist(Context context, EntryID playlistID) {
            return addTransaction(
                    PLAYLIST_DELETE,
                    deletePlaylist,
                    new DeletePlaylistParams(playlistID)
            );
        }

        @Override
        public boolean addPlaylistEntry(Context context,
                                        EntryID playlistID,
                                        EntryID entryID,
                                        String beforePlaylistEntryID,
                                        Meta metaSnapshot) {
            return addTransaction(
                    PLAYLIST_ENTRY_ADD,
                    addPlaylistEntry,
                    new AddPlaylistEntryParams(
                            playlistID,
                            entryID,
                            beforePlaylistEntryID,
                            metaSnapshot
                    )
            );
        }

        @Override
        public boolean deletePlaylistEntry(Context context,
                                           EntryID playlistID,
                                           String playlistEntryID,
                                           EntryID entryID) {
            return addTransaction(
                    PLAYLIST_ENTRY_DELETE,
                    deletePlaylistEntry,
                    new DeletePlaylistEntryParams(playlistID, playlistEntryID, entryID)
            );
        }

        @Override
        public boolean movePlaylistEntry(Context context,
                                         EntryID playlistID,
                                         String playlistEntryID,
                                         EntryID entryID,
                                         String beforePlaylistEntryID) {
            return addTransaction(
                    PLAYLIST_ENTRY_MOVE,
                    movePlaylistEntry,
                    new MovePlaylistEntryParams(
                            playlistID,
                            playlistEntryID,
                            entryID,
                            beforePlaylistEntryID
                    )
            );
        }

        @Override
        public boolean addPlaylistMeta(Context context,
                                       EntryID playlistID,
                                       String key,
                                       String value) {
            return addTransaction(
                    PLAYLIST_META_ADD,
                    addPlaylistMeta,
                    new AddPlaylistMetaParams(playlistID, key, value)
            );
        }


        @Override
        public boolean deletePlaylistMeta(Context context,
                                          EntryID playlistID,
                                          String key,
                                          String value) {
            return addTransaction(
                    PLAYLIST_META_DELETE,
                    deletePlaylistMeta,
                    new DeletePlaylistMetaParams(playlistID, key, value)
            );
        }


        @Override
        public boolean editPlaylistMeta(Context context,
                                        EntryID playlistID,
                                        String key,
                                        String oldValue,
                                        String newValue) {
            return addTransaction(
                    PLAYLIST_META_EDIT,
                    editPlaylistMeta,
                    new EditPlaylistMetaParams(playlistID, key, oldValue, newValue)
            );
        }

        @Override
        CompletableFuture<Void> commit(Context context) {
            CompletableFuture<Void> ret = CompletableFuture.completedFuture(null);
            // TODO: Optimize {,PLAYLIST_}META_{ADD,DELETE,EDIT}
            switch (currentType) {
                case META_ADD:
                    for (AddMetaParams p: add) {
                        ret = ret.thenCompose(aVoid -> MetaStorage.getInstance(context)
                                .insertTrackMeta(p.entryID, p.key, p.value)
                        );
                    }
                    return ret;
                case META_DELETE:
                    for (DeleteMetaParams p: delete) {
                        ret = ret.thenCompose(aVoid -> MetaStorage.getInstance(context)
                                .deleteTrackMeta(p.entryID, p.key, p.value)
                        );
                    }
                    return ret;
                case META_EDIT:
                    for (EditMetaParams p: edit) {
                        ret = ret.thenCompose(aVoid -> MetaStorage.getInstance(context)
                                .replaceMeta(p.entryID, p.key, p.oldValue, p.newValue)
                        );
                    }
                    return ret;
                case PLAYLIST_ADD:
                    return MetaStorage.getInstance(context).addPlaylists(
                            addPlaylist.stream().map(p -> p.playlistID).collect(Collectors.toList()),
                            addPlaylist.stream().map(p -> p.name).collect(Collectors.toList()),
                            addPlaylist.stream().map(p -> p.query).collect(Collectors.toList())
                    );
                case PLAYLIST_DELETE:
                    return MetaStorage.getInstance(context)
                            .deletePlaylists(
                                    deletePlaylist.stream()
                                            .map(p -> p.playlistID)
                                            .collect(Collectors.toList())
                            );
                case PLAYLIST_ENTRY_ADD:
                    return batchApply(
                            addPlaylistEntry,
                            (p1, p2) -> p1.playlistID.equals(p2.playlistID)
                                    && (p1.beforePlaylistEntryID == null && p2.beforePlaylistEntryID == null
                                    || p1.beforePlaylistEntryID.equals(p2.beforePlaylistEntryID)),
                            (params, batch) -> PlaylistStorage.getInstance(context)
                                    .addToPlaylist(
                                            params.playlistID,
                                            batch.stream()
                                                    .map(p -> p.entryID)
                                                    .collect(Collectors.toList()),
                                            params.beforePlaylistEntryID
                                    ).thenRun(() -> {
                            })
                    );
                case PLAYLIST_ENTRY_DELETE:
                    return batchApply(
                            deletePlaylistEntry,
                            (p1, p2) -> p1.playlistID.equals(p2.playlistID),
                            (params, batch) -> PlaylistStorage.getInstance(context)
                                    .removeFromPlaylist(
                                            params.playlistID,
                                            batch.stream()
                                                    .map(p -> p.playlistEntryID)
                                                    .collect(Collectors.toList())
                                    )
                    );
                case PLAYLIST_ENTRY_MOVE:
                    return batchApply(
                            movePlaylistEntry,
                            (p1, p2) -> p1.playlistID.equals(p2.playlistID)
                                    && (p1.beforePlaylistEntryID == null && p2.beforePlaylistEntryID == null
                                    || p1.beforePlaylistEntryID.equals(p2.beforePlaylistEntryID)),
                            (params, batch) -> PlaylistStorage.getInstance(context)
                                    .movePlaylistEntries(
                                            params.playlistID,
                                            batch.stream()
                                                    .map(p -> p.playlistEntryID)
                                                    .collect(Collectors.toList()),
                                            params.beforePlaylistEntryID
                                    )
                    );
                case PLAYLIST_META_ADD:
                    for (AddPlaylistMetaParams p: addPlaylistMeta) {
                        ret = ret.thenCompose(aVoid -> MetaStorage.getInstance(context)
                                .insertPlaylistMeta(p.playlistID, p.key, p.value)
                        );
                    }
                    return ret;
                case PLAYLIST_META_DELETE:
                    for (DeletePlaylistMetaParams p: deletePlaylistMeta) {
                        ret = ret.thenCompose(aVoid -> MetaStorage.getInstance(context)
                                .deletePlaylistMeta(p.playlistID, p.key, p.value)
                        );
                    }
                    return ret;
                case PLAYLIST_META_EDIT:
                    for (EditPlaylistMetaParams p: editPlaylistMeta) {
                        ret = ret.thenCompose(aVoid -> MetaStorage.getInstance(context)
                                .replacePlaylistMeta(p.playlistID, p.key, p.oldValue, p.newValue)
                        );
                    }
                    return ret;
                default:
                    return Util.futureResult("No transactions to commit");
            }
        }

        private <T> boolean addTransaction(String type, List<T> list, T item) {
            if (currentType != null && !type.equals(currentType)) {
                return false;
            }
            currentType = type;
            list.add(item);
            return true;
        }

        private <T> CompletableFuture<Void> batchApply(
                List<T> params,
                BiFunction<T, T, Boolean> paramsEqualsFunc,
                BiFunction<T, List<T>, CompletableFuture<Void>> batchApply
        ) {
            CompletableFuture<Void> ret = CompletableFuture.completedFuture(null);
            ArrayList<T> fromSamePlaylist = new ArrayList<>();
            T currentParams = null;
            for (T p: params) {
                if (currentParams == null) {
                    currentParams = p;
                    fromSamePlaylist.add(p);
                } else if (paramsEqualsFunc.apply(currentParams, p)) {
                    fromSamePlaylist.add(p);
                } else {
                    T finalCurrentParams = currentParams;
                    ret = ret.thenCompose(aVoid -> batchApply.apply(
                            finalCurrentParams,
                            fromSamePlaylist
                    ));
                    currentParams = null;
                    fromSamePlaylist.clear();
                }
            }
            if (currentParams != null) {
                T finalCurrentParams = currentParams;
                ret = ret.thenCompose(aVoid -> batchApply.apply(
                        finalCurrentParams,
                        fromSamePlaylist
                ));
            }
            return ret;
        }
    }
}
