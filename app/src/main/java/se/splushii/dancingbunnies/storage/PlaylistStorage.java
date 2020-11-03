package se.splushii.dancingbunnies.storage;

import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.db.PlaylistEntryDao;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistStorage {
    private static final String LC = Util.getLogContext(PlaylistStorage.class);
    private static PlaylistStorage instance;

    private final DB db;
    private final MetaStorage metaStorage;
    private final PlaylistEntryDao playlistEntryModel;

    public static synchronized PlaylistStorage getInstance(Context context) {
        if (instance == null) {
            instance = new PlaylistStorage(context);
        }
        return instance;
    }

    private PlaylistStorage(Context context) {
        db = DB.getDB(context);
        metaStorage = MetaStorage.getInstance(context);
        playlistEntryModel = db.playlistEntryModel();
    }

    public CompletableFuture<Void> replaceAllPlaylistsFromSource(
            String src,
            List<se.splushii.dancingbunnies.musiclibrary.Playlist> playlists,
            boolean allowLocalKeys,
            Consumer<String> progressHandler
    ) {
        HashMap<EntryID, List<PlaylistEntry>> playlistEntriesMap = new HashMap<>();
        for (se.splushii.dancingbunnies.musiclibrary.Playlist playlist: playlists) {
            if (playlist instanceof StupidPlaylist) {
                StupidPlaylist p = (StupidPlaylist) playlist;
                playlistEntriesMap.put(p.meta.entryID, p.getEntries());
            }
        }
        return CompletableFuture.runAsync(() ->
                db.runInTransaction(() -> {
                    metaStorage.replaceAllPlaylistsAndMetasFromSource(
                            src,
                            playlists.stream().map(p -> p.meta).collect(Collectors.toList()),
                            allowLocalKeys,
                            progressHandler
                    );
                    for (EntryID playlistID: playlistEntriesMap.keySet()) {
                        List<PlaylistEntry> playlistEntries = playlistEntriesMap.get(playlistID);
                        if (playlistEntries == null) {
                            continue;
                        }
                        playlistEntryModel.add(playlistID, playlistEntries, null);
                    }
                })
        );
    }

    public CompletableFuture<Void> addToPlaylist(EntryID playlistID,
                                                 List<EntryID> entryIDs,
                                                 String beforePlaylistEntryID) {
        List<PlaylistEntry> playlistEntries = PlaylistEntry.generatePlaylistEntries(
                playlistID,
                entryIDs.toArray(new EntryID[0])
        );
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.add(playlistID, playlistEntries, beforePlaylistEntryID)
        );
    }

    public CompletableFuture<Void> removeFromPlaylist(EntryID playlistID,
                                                      List<String> playlistEntryIDs) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.remove(playlistID, playlistEntryIDs));
    }

    public LiveData<Integer> getNumPlaylistEntries(EntryID playlistID) {
        return playlistEntryModel.getNumEntries(playlistID.src, playlistID.id);
    }

    public LiveData<List<PlaylistEntry>> getPlaylistEntries(EntryID playlistID) {
        return playlistEntryModel.getEntries(playlistID.src, playlistID.id);
    }

    public CompletableFuture<List<PlaylistEntry>> getPlaylistEntriesOnce(EntryID playlistID) {
        return CompletableFuture.supplyAsync(() ->
                playlistEntryModel.getEntriesOnce(playlistID.src, playlistID.id)
        );
    }

    public CompletableFuture<Void> movePlaylistEntries(EntryID playlistID,
                                                       List<String> playlistEntryIDs,
                                                       String beforePlaylistEntryID) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.move(playlistID, playlistEntryIDs, beforePlaylistEntryID)
        );
    }
}
