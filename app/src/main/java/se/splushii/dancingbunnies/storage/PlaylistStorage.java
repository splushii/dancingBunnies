package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.util.Log;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
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

    private final PlaylistDao playlistModel;
    private final PlaylistEntryDao playlistEntryModel;

    public static synchronized PlaylistStorage getInstance(Context context) {
        if (instance == null) {
            instance = new PlaylistStorage(context);
        }
        return instance;
    }

    private PlaylistStorage(Context context) {
        playlistModel = DB.getDB(context).playlistModel();
        playlistEntryModel = DB.getDB(context).playlistEntryModel();
    }

    public void clearAll(String src) {
        playlistModel.deleteWhereSourceIs(src); // Delete cascades to playlistEntries
    }

    public static PlaylistID generatePlaylistID(int playlistType) {
        String id = DateTimeFormatter.ISO_DATE_TIME.format(OffsetDateTime.now());
        return new PlaylistID(
                MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                id,
                playlistType
        );
    }

    public CompletableFuture<Void> deletePlaylists(List<Playlist> playlistItems) {
        return CompletableFuture.runAsync(() ->
                playlistModel.delete(playlistItems.stream()
                        .map(Playlist::pos)
                        .collect(Collectors.toList())) // Delete cascades to playlistEntries
        );
    }

    public CompletableFuture<Void> insertPlaylists(
            int toPosition,
            List<? extends se.splushii.dancingbunnies.musiclibrary.Playlist> playlists
    ) {
        return CompletableFuture.runAsync(() -> {
            List<Playlist> roomPlaylists = new ArrayList<>();
            int entryPosition = toPosition;
            StringBuilder sb = new StringBuilder("insertPlaylists");
            HashMap<PlaylistID, List<PlaylistEntry>> playlistEntriesMap = new HashMap<>();
            for (se.splushii.dancingbunnies.musiclibrary.Playlist playlist: playlists) {
                sb.append("\ninsert playlist: ").append(playlist.name)
                        .append(" pos: ").append(entryPosition);
                if (playlist instanceof StupidPlaylist) {
                    StupidPlaylist p = (StupidPlaylist) playlist;
                    roomPlaylists.add(Playlist.from(p, entryPosition++));
                    playlistEntriesMap.put(p.id, p.getEntries());
                } else if (playlist instanceof SmartPlaylist) {
                    SmartPlaylist p = (SmartPlaylist) playlist;
                    roomPlaylists.add(Playlist.from(p, entryPosition++));
                } else {
                    Log.e(LC, "Unsupported playlist type: " + playlist);
                }
            }
            Log.d(LC, sb.toString());
            playlistModel.insert(toPosition, roomPlaylists);
            for (PlaylistID playlistID: playlistEntriesMap.keySet()) {
                List<PlaylistEntry> playlistEntries = playlistEntriesMap.get(playlistID);
                playlistEntryModel.addLast(playlistID, playlistEntries);
            }
        });
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
                                                 List<PlaylistEntry> playlistEntries) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.addLast(playlistID, playlistEntries)
        );
    }

    public CompletableFuture<Void> removeFromPlaylist(PlaylistID playlistID, List<PlaylistEntry> entries) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.remove(playlistID, entries));
    }

    public LiveData<List<PlaylistEntry>> getPlaylistEntries(PlaylistID playlistID) {
        return playlistEntryModel.getEntries(playlistID.src, playlistID.id, playlistID.type);
    }

    public CompletableFuture<List<PlaylistEntry>> getPlaylistEntriesOnce(PlaylistID playlistID) {
        return CompletableFuture.supplyAsync(() ->
                playlistEntryModel.getEntriesOnce(playlistID.src, playlistID.id, playlistID.type)
        );
    }

    public CompletableFuture<Void> movePlaylists(List<Playlist> selection,
                                                 PlaylistID idAfterTargetPos) {
        return CompletableFuture.runAsync(() ->
                playlistModel.move(selection, idAfterTargetPos)
        );
    }

    public CompletableFuture<Void> movePlaylistEntries(PlaylistID playlistID,
                                                       List<PlaylistEntry> selection,
                                                       String idAfterTargetPos) {
        return CompletableFuture.runAsync(() ->
                playlistEntryModel.move(playlistID, selection, idAfterTargetPos)
        );
    }

    public LiveData<List<String>> getSources() {
        return playlistModel.getSources();
    }
}
