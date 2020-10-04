package se.splushii.dancingbunnies.storage;

import android.content.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.SmartPlaylist;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistDao;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.db.PlaylistEntryDao;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistStorage {
    private static final String LC = Util.getLogContext(PlaylistStorage.class);
    private static PlaylistStorage instance;

    private final DB db;
    private final PlaylistDao playlistModel;
    private final PlaylistEntryDao playlistEntryModel;

    public static synchronized PlaylistStorage getInstance(Context context) {
        if (instance == null) {
            instance = new PlaylistStorage(context);
        }
        return instance;
    }

    private PlaylistStorage(Context context) {
        db = DB.getDB(context);
        playlistModel = db.playlistModel();
        playlistEntryModel = db.playlistEntryModel();
    }

    public void clearAll(String src) {
        playlistModel.deleteWhereSourceIs(src); // Delete cascades to playlistEntries
    }

    public CompletableFuture<Void> addPlaylist(PlaylistID playlistID,
                                               String name,
                                               String query,
                                               PlaylistID beforePlaylistID) {
        se.splushii.dancingbunnies.musiclibrary.Playlist playlist;
        if (query != null) {
            playlist = new SmartPlaylist(playlistID, name, MusicLibraryQueryNode.fromJSON(query));
        } else {
            playlist = new StupidPlaylist(playlistID, name, Collections.emptyList());
        }
        return CompletableFuture.runAsync(() -> playlistModel.add(
                Collections.singletonList(playlist),
                beforePlaylistID
        ));
    }

//    public CompletableFuture<Void> addPlaylists(
//            List<? extends se.splushii.dancingbunnies.musiclibrary.Playlist> playlists,
//            PlaylistID beforePlaylistID
//    ) {
//        return CompletableFuture.runAsync(() -> playlistModel.add(playlists, beforePlaylistID));
//    }

//    // TODO: Reserve this for backend synced playlists (where PlaylistEntries are already present)
//    //  Check its usages
//    //  Local playlist creation/editing should be using other functions (generating PlaylistEntries)
//    public CompletableFuture<Void> insertPlaylists(
//            int toPosition,
//            List<? extends se.splushii.dancingbunnies.musiclibrary.Playlist> playlists
//    ) {
//        return CompletableFuture.runAsync(() -> {
//            List<Playlist> roomPlaylists = new ArrayList<>();
//            int entryPosition = toPosition;
//            StringBuilder sb = new StringBuilder("insertPlaylists");
//            HashMap<PlaylistID, List<PlaylistEntry>> playlistEntriesMap = new HashMap<>();
//            for (se.splushii.dancingbunnies.musiclibrary.Playlist playlist: playlists) {
//                sb.append("\ninsert playlist: ").append(playlist.name)
//                        .append(" pos: ").append(entryPosition);
//                if (playlist instanceof StupidPlaylist) {
//                    StupidPlaylist p = (StupidPlaylist) playlist;
//                    roomPlaylists.add(Playlist.from(p, entryPosition++));
//                    playlistEntriesMap.put(p.id, p.getEntries());
//                } else if (playlist instanceof SmartPlaylist) {
//                    SmartPlaylist p = (SmartPlaylist) playlist;
//                    roomPlaylists.add(Playlist.from(p, entryPosition++));
//                } else {
//                    Log.e(LC, "Unsupported playlist type: " + playlist);
//                }
//            }
//            Log.d(LC, sb.toString());
//            playlistModel.insert(toPosition, roomPlaylists);
//            for (PlaylistID playlistID: playlistEntriesMap.keySet()) {
//                List<PlaylistEntry> playlistEntries = playlistEntriesMap.get(playlistID);
//                playlistEntryModel.add(playlistID, playlistEntries, null);
//            }
//        });
//    }

    public CompletableFuture<Void> deletePlaylists(List<PlaylistID> playlistIDs) {
        return CompletableFuture.runAsync(() ->
                playlistModel.delete(playlistIDs) // Delete cascades to playlistEntries
        );
    }

    public CompletableFuture<Void> replaceWith(
            String src,
            List<se.splushii.dancingbunnies.musiclibrary.Playlist> playlists,
            PlaylistID beforePlaylistID
    ) {
        HashMap<PlaylistID, List<PlaylistEntry>> playlistEntriesMap = new HashMap<>();
        for (se.splushii.dancingbunnies.musiclibrary.Playlist playlist: playlists) {
            if (playlist instanceof StupidPlaylist) {
                StupidPlaylist p = (StupidPlaylist) playlist;
                playlistEntriesMap.put(p.id, p.getEntries());
            }
        }
        return CompletableFuture.runAsync(() ->
                db.runInTransaction(() -> {
                    playlistModel.deleteWhereSourceIs(src);
                    playlistModel.add(playlists, beforePlaylistID);
                    for (PlaylistID playlistID: playlistEntriesMap.keySet()) {
                        List<PlaylistEntry> playlistEntries = playlistEntriesMap.get(playlistID);
                        if (playlistEntries == null) {
                            continue;
                        }
                        playlistEntryModel.add(playlistID, playlistEntries, null);
                    }
                })
        );
    }

    public CompletableFuture<Void> movePlaylists(List<PlaylistID> playlistIDs,
                                                 PlaylistID idAfterTargetPos) {
        return CompletableFuture.runAsync(() ->
                playlistModel.move(playlistIDs, idAfterTargetPos)
        );
    }

    public LiveData<List<Playlist>> getPlaylists() {
        return playlistModel.getAll();
    }

    public LiveData<Playlist> getPlaylist(PlaylistID playlistID) {
        if (playlistID == null) {
            MutableLiveData<Playlist> ret = new MutableLiveData<>();
            ret.setValue(null);
            return ret;
        }
        return playlistModel.get(playlistID.src, playlistID.id, playlistID.type);
    }

    public CompletableFuture<Void> addToPlaylist(PlaylistID playlistID,
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

    public CompletableFuture<Void> removeFromPlaylist(PlaylistID playlistID,
                                                      List<String> playlistEntryIDs) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.remove(playlistID, playlistEntryIDs));
    }

    public LiveData<List<PlaylistEntry>> getPlaylistEntries(PlaylistID playlistID) {
        return playlistEntryModel.getEntries(playlistID.src, playlistID.id, playlistID.type);
    }

    public CompletableFuture<List<PlaylistEntry>> getPlaylistEntriesOnce(PlaylistID playlistID) {
        return CompletableFuture.supplyAsync(() ->
                playlistEntryModel.getEntriesOnce(playlistID.src, playlistID.id, playlistID.type)
        );
    }

    public CompletableFuture<Void> movePlaylistEntries(PlaylistID playlistID,
                                                       List<String> playlistEntryIDs,
                                                       String beforePlaylistEntryID) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.move(playlistID, playlistEntryIDs, beforePlaylistEntryID)
        );
    }

    public LiveData<List<String>> getSources() {
        return playlistModel.getSources();
    }
}
