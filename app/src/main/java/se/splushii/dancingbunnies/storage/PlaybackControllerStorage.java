package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.PlaybackControllerEntry;
import se.splushii.dancingbunnies.storage.db.PlaybackControllerEntryDao;
import se.splushii.dancingbunnies.util.Util;

public class PlaybackControllerStorage {
    private static final String LC = Util.getLogContext(PlaybackControllerStorage.class);
    public static final int QUEUE_ID_QUEUE = 0;
    public static final int QUEUE_ID_PLAYLIST = 1;
    public static final int QUEUE_ID_HISTORY = 2;
    public static final int QUEUE_ID_LOCALAUDIOPLAYER_QUEUE = 3;
    public static final int QUEUE_ID_LOCALAUDIOPLAYER_PLAYLIST = 4;
    public static final int QUEUE_ID_LOCALAUDIOPLAYER_HISTORY = 5;

    private final PlaybackControllerEntryDao entryModel;
    private final SharedPreferences preferences;
    private final String current_src_key;
    private final String current_id_key;
    private final String current_playback_id_key;
    private final String lastPos_key;
    private final String playlist_src_key;
    private final String playlist_id_key;
    private final String playlist_type_key;
    private final String playlist_position_key;
    private final String playback_id_counter;
    private final String current_playlist_pos_key;

    public PlaybackControllerStorage(Context context) {
        entryModel = DB.getDB(context).playbackControllerEntryModel();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        playlist_src_key = context.getResources().getString(
                R.string.pref_key_playbackcontroller_playlist_src
        );
        playlist_id_key = context.getResources().getString(
                R.string.pref_key_playbackcontroller_playlist_id
        );
        playlist_type_key = context.getResources().getString(
                R.string.pref_key_playbackcontroller_playlist_type
        );
        playlist_position_key = context.getResources().getString(
                R.string.pref_key_playbackcontroller_playlist_position
        );
        current_src_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_src);
        current_id_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_id);
        current_playback_id_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_playback_id);
        current_playlist_pos_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_playlist_pos);
        lastPos_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_lastpos);
        playback_id_counter = context.getResources().getString(R.string.pref_key_localaudioplayer_playback_id_counter);
    }

    public static String getQueueName(int queueID) {
        switch (queueID) {
            case PlaybackControllerStorage.QUEUE_ID_QUEUE:
                return "controller_queue";
            case PlaybackControllerStorage.QUEUE_ID_PLAYLIST:
                return "controller_playlist";
            case PlaybackControllerStorage.QUEUE_ID_HISTORY:
                return "controller_history";
            case PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_QUEUE:
                return "localaudioplayer_queue";
            case PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PLAYLIST:
                return "localaudioplayer_playlist";
            case PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY:
                return "localaudioplayer_history";
            default:
                return "unknown";
        }
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
        String playbackType = getPlaybackType(queueID);
        if (playbackType == null) {
                return new MutableLiveData<>();
        }
        return Transformations.map(entryModel.getEntries(queueID), entries ->
                RoomPlaybackControllerEntryList2PlaybackEntryList(entries, playbackType)
        );
    }

    private List<PlaybackEntry> RoomPlaybackControllerEntryList2PlaybackEntryList(
            List<PlaybackControllerEntry> roomPlaybackControllerEntries,
            String playbackType
    ) {
        return roomPlaybackControllerEntries.stream().map(entry ->
                new PlaybackEntry(
                        new EntryID(entry.api, entry.id, Meta.FIELD_SPECIAL_MEDIA_ID),
                        entry.playbackID,
                        playbackType,
                        entry.playlistPos
                )
        ).collect(Collectors.toList());
    }

    public CompletableFuture<Void> insert(int queueID, int toPosition, List<PlaybackEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            List<PlaybackControllerEntry> roomEntries = new ArrayList<>();
            int entryPosition = toPosition;
            for (PlaybackEntry playbackEntry: entries) {
                roomEntries.add(PlaybackControllerEntry.from(queueID, playbackEntry, entryPosition++));
            }
            Log.d(LC, Util.getPlaybackEntriesChangedStatus(
                    "insert to " + getQueueName(queueID) + "[" + entryPosition + "]:",
                    "\n+ ",
                    "",
                    entries
            ));
            entryModel.insert(queueID, toPosition, roomEntries);
            return null;
        });
    }

    public CompletableFuture<Void> removeEntries(int queueID, List<PlaybackEntry> playbackEntries) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.removeEntries(queueID, playbackEntries);
            return null;
        });
    }

    public CompletableFuture<Void> remove(int queueID, List<Integer> positions) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.remove(queueID, positions);
            return null;
        });
    }

    public CompletableFuture<Void> removeAll(int queueID) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.removeAll(queueID);
            return null;
        });
    }

    public void setLocalAudioPlayerCurrent(PlaybackEntry playbackEntry, long lastPos) {
        preferences.edit()
                .putString(current_src_key, playbackEntry.entryID.src)
                .putString(current_id_key, playbackEntry.entryID.id)
                .putString(current_playback_id_key, Long.toString(playbackEntry.playbackID))
                .putLong(lastPos_key, lastPos)
                .apply();
    }

    public void removeLocalAudioPlayerCurrent() {
        preferences.edit()
                .remove(current_src_key)
                .remove(current_id_key)
                .remove(current_playback_id_key)
                .remove(lastPos_key)
                .apply();
    }

    public PlaybackEntry getLocalAudioPlayerCurrentEntry() {
        String src = preferences.getString(current_src_key, null);
        String id = preferences.getString(current_id_key, null);
        String playbackID = preferences.getString(current_playback_id_key, null);
        String playlistPos = preferences.getString(current_playlist_pos_key, null);
        if (src == null || id == null || playbackID == null || playlistPos == null) {
            return null;
        }
        return new PlaybackEntry(
                new EntryID(src, id, Meta.FIELD_SPECIAL_MEDIA_ID),
                Long.parseLong(playbackID),
                PlaybackEntry.USER_TYPE_QUEUE,
                Long.parseLong(playlistPos)
        );
    }

    public long getLocalAudioPlayerCurrentLastPos() {
        return preferences.getLong(lastPos_key, 0);
    }

    public CompletableFuture<List<PlaybackEntry>> getLocalAudioPlayerQueueEntries() {
        return getEntriesSync(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_QUEUE);
    }

    public CompletableFuture<List<PlaybackEntry>> getLocalAudioPlayerPlaylistEntries() {
        return getEntriesSync(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PLAYLIST);
    }

    public CompletableFuture<List<PlaybackEntry>> getLocalAudioPlayerHistoryEntries() {
        return getEntriesSync(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY);
    }

    private CompletableFuture<List<PlaybackEntry>> getEntriesSync(int queueID) {
        String playbackType = getPlaybackType(queueID);
        if (playbackType == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> entryModel.getEntriesSync(queueID))
                .thenApply(entries ->
                        RoomPlaybackControllerEntryList2PlaybackEntryList(entries, playbackType)
                );
    }

    private String getPlaybackType(int queueID) {
        switch (queueID) {
            case PlaybackControllerStorage.QUEUE_ID_QUEUE:
            case PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_QUEUE:
                return PlaybackEntry.USER_TYPE_QUEUE;
            case PlaybackControllerStorage.QUEUE_ID_PLAYLIST:
            case PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PLAYLIST:
                return PlaybackEntry.USER_TYPE_PLAYLIST;
            case PlaybackControllerStorage.QUEUE_ID_HISTORY:
                return PlaybackEntry.USER_TYPE_HISTORY;
        }
        return null;
    }

    public PlaylistID getCurrentPlaylist() {
        String src = preferences.getString(playlist_src_key, null);
        String id = preferences.getString(playlist_id_key, null);
        String type = preferences.getString(playlist_type_key, null);
        if (src == null || id == null || type == null) {
            return null;
        }
        return new PlaylistID(src, id, type);
    }

    public void setCurrentPlaylist(PlaylistID playlistID) {
        String src = null;
        String id = null;
        String type = null;
        if (playlistID != null) {
            src = playlistID.src;
            id = playlistID.id;
            type = playlistID.type;
        }
        preferences.edit()
                .putString(playlist_src_key, src)
                .putString(playlist_id_key, id)
                .putString(playlist_type_key, type)
                .apply();
    }

    public long getPlaylistPosition() {
        return preferences.getLong(playlist_position_key, 0);
    }

    public void setPlaylistPosition(long position) {
        preferences.edit()
                .putLong(playlist_position_key, position)
                .apply();
    }

    public synchronized long getNextPlaybackID() {
        long id = preferences.getLong(playback_id_counter, 0);
        if (!preferences.edit()
                .putLong(playback_id_counter, id + 1 >= 0 ? id + 1 : 0)
                .commit()) {
            throw new RuntimeException("Could not update playback ID");
        }
        return id;
    }
}
