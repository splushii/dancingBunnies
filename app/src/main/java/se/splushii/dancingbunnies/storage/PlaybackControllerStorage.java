package se.splushii.dancingbunnies.storage;

import android.content.Context;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class PlaybackControllerStorage {
    private static final String LC = Util.getLogContext(PlaylistStorage.class);
    public static final int QUEUE_ID_QUEUE = 0;
    public static final int QUEUE_ID_PLAYLIST = 1;
    public static final int QUEUE_ID_HISTORY = 2;

    private final RoomPlaybackControllerEntryDao entryModel;

    public PlaybackControllerStorage(Context context) {
        entryModel = RoomDB.getDB(context).playbackControllerEntryModel();
    }

    public LiveData<List<PlaybackEntry>> getQueueEntries() {
        return getEntries(PlaybackControllerStorage.QUEUE_ID_QUEUE);
    }

    public LiveData<List<PlaybackEntry>> getPlaylistEntries() {
        return getEntries(PlaybackControllerStorage.QUEUE_ID_PLAYLIST);
    }

    public LiveData<List<PlaybackEntry>> getHistoryEntries() {
        return getEntries(PlaybackControllerStorage.QUEUE_ID_HISTORY);
    }

    private LiveData<List<PlaybackEntry>> getEntries(int queueID) {
        String playbackType;
        switch (queueID) {
            case PlaybackControllerStorage.QUEUE_ID_QUEUE:
                playbackType = PlaybackEntry.USER_TYPE_QUEUE;
                break;
            case PlaybackControllerStorage.QUEUE_ID_PLAYLIST:
                playbackType = PlaybackEntry.USER_TYPE_PLAYLIST;
                break;
            case PlaybackControllerStorage.QUEUE_ID_HISTORY:
                playbackType = PlaybackEntry.USER_TYPE_HISTORY;
                break;
            default:
                return new MutableLiveData<>();
        }
        String finalPlaybackType = playbackType;
        return Transformations.map(entryModel.getEntries(queueID), entries ->
            entries.stream().map(entry ->
                    new PlaybackEntry(
                            new EntryID(entry.api, entry.id, Meta.METADATA_KEY_MEDIA_ID),
                            finalPlaybackType,
                            "type: " + finalPlaybackType + " pos: " + entry.pos
                    )
            ).collect(Collectors.toList())
        );
    }

    public CompletableFuture<Void> insert(int queueID, int toPosition, List<EntryID> entries) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.insert(queueID, toPosition, entries);
            return null;
        });
    }

    public CompletableFuture<Void> remove(int queueID, List<Integer> positions) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.remove(queueID, positions);
            return null;
        });
    }
}
