package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistStorage {
    private static final String LC = Util.getLogContext(PlaylistStorage.class);

    private final RoomPlaylistDao playlistModel;
    private final RoomPlaylistEntryDao playlistEntryModel;

    public PlaylistStorage(Context context) {
        playlistModel = RoomDB.getDB(context).playlistModel();
        playlistEntryModel = RoomDB.getDB(context).playlistEntryModel();
    }

    public void clearAll(String src) {
        playlistModel.deleteWhereSourceIs(src); // Delete cascades to playlistEntries
    }

    public void insertPlaylists(List<Playlist> playlists) {
        for (Playlist playlist: playlists) {
            if (playlist instanceof StupidPlaylist) {
                StupidPlaylist p = (StupidPlaylist) playlist;
                playlistModel.insert(RoomPlaylist.from(p));
                playlistEntryModel.addLast(playlist.id, p.getEntries());
            } else {
                Log.e(LC, "Unsupported playlist type: " + playlist);
            }
        }
    }

    public LiveData<List<PlaylistItem>> getPlaylists() {
        return Transformations.map(playlistModel.getAll(), playlists -> playlists.stream()
                .map(p -> new PlaylistItem(
                        new PlaylistID(p.api, p.id, PlaylistID.TYPE_STUPID),
                        p.name))
                .collect(Collectors.toList()));
    }

    public Playlist getPlaylist(PlaylistID playlistID) {
        RoomPlaylist rp = playlistModel.get(playlistID.id, playlistID.src);
        List<RoomPlaylistEntry> rpe = playlistEntryModel.getEntries(playlistID.id, playlistID.src);
        List<EntryID> playlistEntries = rpe.stream()
                .map(e -> new EntryID(e.api, e.id, Meta.METADATA_KEY_MEDIA_ID))
                .collect(Collectors.toList());
        return new StupidPlaylist(
                new PlaylistID(rp.api, rp.id, PlaylistID.TYPE_STUPID),
                rp.name,
                playlistEntries
        );
    }

    public void addToPlaylist(PlaylistID playlistID, List<EntryID> entryIDs) {
        playlistEntryModel.addLast(playlistID, entryIDs);
    }

    public void removeFromPlaylist(PlaylistID playlistID, int position) {
        playlistEntryModel.remove(playlistID.src, playlistID.id, position);
    }

    public LiveData<List<RoomPlaylistEntry>> getPlaylistEntries() {
        return playlistEntryModel.getAllEntries();
    }
}
