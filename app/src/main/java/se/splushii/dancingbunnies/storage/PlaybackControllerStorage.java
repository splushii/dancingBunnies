package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackController;
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
    public static final int QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK = 6;
    public static final int QUEUE_ID_HISTORY = 2;
    public static final int QUEUE_ID_LOCALAUDIOPLAYER_PRELOAD = 3;
    public static final int QUEUE_ID_LOCALAUDIOPLAYER_HISTORY = 5;

    private static PlaybackControllerStorage instance;

    private final PlaybackControllerEntryDao entryModel;
    private final SharedPreferences preferences;
    private final String playback_id_counter_key;
    private final String playlist_selection_id_counter_key;
    private final String playlist_src_key;
    private final String playlist_id_key;
    private final String playlist_type_key;
    private final String playlist_position_key;
    private final String playlist_playback_position_key;
    private final String playlist_selection_id_key;
    private final String playlist_playback_order_key;
    private final String playlist_playback_repeat_key;
    private final String localaudioplayer_current_src_key;
    private final String localaudioplayer_current_id_key;
    private final String localaudioplayer_current_playback_type_key;
    private final String localaudioplayer_current_playback_id_key;
    private final String localaudioplayer_current_lastPos_key;
    private final String localaudioplayer_current_playlist_pos_key;
    private final String localaudioplayer_current_playlist_selection_id_key;

    public static synchronized PlaybackControllerStorage getInstance(Context context) {
        if (instance == null) {
            instance = new PlaybackControllerStorage(context);
        }
        return instance;
    }

    private PlaybackControllerStorage(Context context) {
        entryModel = DB.getDB(context).playbackControllerEntryModel();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        playback_id_counter_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playback_id_counter);
        playlist_selection_id_counter_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_selection_id_counter);
        playlist_src_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_src);
        playlist_id_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_id);
        playlist_type_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_type);
        playlist_position_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_position);
        playlist_playback_position_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_playback_position);
        playlist_selection_id_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_selection_id);
        playlist_playback_order_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_playback_order);
        playlist_playback_repeat_key = context.getResources().getString(R.string.pref_key_playbackcontroller_playlist_playback_repeat);
        localaudioplayer_current_src_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_src);
        localaudioplayer_current_id_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_id);
        localaudioplayer_current_playback_type_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_playback_type);
        localaudioplayer_current_playback_id_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_playback_id);
        localaudioplayer_current_lastPos_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_lastpos);
        localaudioplayer_current_playlist_pos_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_playlist_pos);
        localaudioplayer_current_playlist_selection_id_key = context.getResources().getString(R.string.pref_key_localaudioplayer_current_playlist_selection_id);
    }

    public static String getQueueName(int queueID) {
        switch (queueID) {
            case PlaybackControllerStorage.QUEUE_ID_QUEUE:
                return "controller_queue";
            case PlaybackControllerStorage.QUEUE_ID_PLAYLIST:
                return "controller_playlist";
            case PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK:
                return "controller_playlist_playback";
            case PlaybackControllerStorage.QUEUE_ID_HISTORY:
                return "controller_history";
            case PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PRELOAD:
                return "localaudioplayer_preload";
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

    public LiveData<List<PlaybackEntry>> getCurrentPlaylistPlaybackEntries() {
        return getEntries(PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK);
    }

    private LiveData<List<PlaybackEntry>> getEntries(int queueID) {
        return Transformations.map(
                entryModel.getEntries(queueID),
                this::RoomPlaybackControllerEntryList2PlaybackEntryList
        );
    }

    private List<PlaybackEntry> RoomPlaybackControllerEntryList2PlaybackEntryList(
            List<PlaybackControllerEntry> roomPlaybackControllerEntries
    ) {
        return roomPlaybackControllerEntries.stream().map(entry ->
                new PlaybackEntry(
                        new EntryID(entry.api, entry.id, Meta.FIELD_SPECIAL_MEDIA_ID),
                        entry.playbackID,
                        entry.playbackType,
                        entry.playlistPos,
                        entry.playlistSelectionID
                )
        ).collect(Collectors.toList());
    }

    public CompletableFuture<Void> insert(int queueID, int toPosition, List<PlaybackEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            entryModel.insert(queueID, toPosition, entries);
            return null;
        });
    }

    public CompletableFuture<Void> update(int queueID, List<PlaybackEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            entryModel.update(queueID, entries);
            return null;
        });
    }

    public CompletableFuture<Void> replaceWith(int queueID, List<PlaybackEntry> entries) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.replaceWith(queueID, entries);
            return null;
        });
    }

    public CompletableFuture<Void> removeEntries(int queueID, List<PlaybackEntry> playbackEntries) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.removeEntries(queueID, playbackEntries);
            return null;
        });
    }

    public CompletableFuture<Void> removeAll(int queueID) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.removeAll(queueID);
            return null;
        });
    }

    public CompletableFuture<Void> move(int queueID,
                                        long beforePlaybackID,
                                        List<PlaybackEntry> playbackEntries) {
        return CompletableFuture.supplyAsync(() -> {
            entryModel.move(queueID, beforePlaybackID, playbackEntries);
            return null;
        });
    }

    public void setLocalAudioPlayerCurrent(PlaybackEntry playbackEntry, long lastPos) {
        preferences.edit()
                .putString(localaudioplayer_current_src_key, playbackEntry.entryID.src)
                .putString(localaudioplayer_current_id_key, playbackEntry.entryID.id)
                .putString(localaudioplayer_current_playback_type_key, playbackEntry.playbackType)
                .putString(localaudioplayer_current_playback_id_key, Long.toString(playbackEntry.playbackID))
                .putString(localaudioplayer_current_lastPos_key, Long.toString(lastPos))
                .putString(localaudioplayer_current_playlist_pos_key, Long.toString(playbackEntry.playlistPos))
                .putString(localaudioplayer_current_playlist_selection_id_key, Long.toString(playbackEntry.playlistSelectionID))
                .apply();
    }

    public void removeLocalAudioPlayerCurrent() {
        preferences.edit()
                .remove(localaudioplayer_current_src_key)
                .remove(localaudioplayer_current_id_key)
                .remove(localaudioplayer_current_playback_type_key)
                .remove(localaudioplayer_current_playback_id_key)
                .remove(localaudioplayer_current_lastPos_key)
                .remove(localaudioplayer_current_playlist_pos_key)
                .remove(localaudioplayer_current_playlist_selection_id_key)
                .apply();
    }

    public Pair<PlaybackEntry, Long> getLocalAudioPlayerCurrentEntry() {
        String src = preferences.getString(localaudioplayer_current_src_key, null);
        String id = preferences.getString(localaudioplayer_current_id_key, null);
        String playbackType = preferences.getString(localaudioplayer_current_playback_type_key, null);
        String playbackID = preferences.getString(localaudioplayer_current_playback_id_key, null);
        String lastPos = preferences.getString(localaudioplayer_current_lastPos_key, null);
        String playlistPos = preferences.getString(localaudioplayer_current_playlist_pos_key, null);
        String playlistSelectionID = preferences.getString(localaudioplayer_current_playlist_selection_id_key, null);
        if (src == null
                || id == null
                || playbackType == null
                || playbackID == null
                || lastPos == null
                || playlistPos == null
                || playlistSelectionID == null) {
            return null;
        }
        return new Pair<>(
                new PlaybackEntry(
                        new EntryID(src, id, Meta.FIELD_SPECIAL_MEDIA_ID),
                        Long.parseLong(playbackID),
                        PlaybackEntry.USER_TYPE_QUEUE,
                        Long.parseLong(playlistPos),
                        Long.parseLong(playlistSelectionID)
                ),
                Long.parseLong(lastPos)
        );
    }

    public CompletableFuture<List<PlaybackEntry>> getLocalAudioPlayerQueueEntries() {
        return getEntriesSync(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_PRELOAD);
    }

    public CompletableFuture<List<PlaybackEntry>> getLocalAudioPlayerHistoryEntries() {
        return getEntriesSync(PlaybackControllerStorage.QUEUE_ID_LOCALAUDIOPLAYER_HISTORY);
    }

    private CompletableFuture<List<PlaybackEntry>> getEntriesSync(int queueID) {
        return CompletableFuture.supplyAsync(() -> entryModel.getEntriesSync(queueID))
                .thenApply(this::RoomPlaybackControllerEntryList2PlaybackEntryList);
    }

    public PlaylistID getCurrentPlaylist() {
        String src = preferences.getString(playlist_src_key, null);
        String id = preferences.getString(playlist_id_key, null);
        int type = preferences.getInt(playlist_type_key, PlaylistID.TYPE_INVALID);
        if (src == null || id == null || type == PlaylistID.TYPE_INVALID) {
            return null;
        }
        return new PlaylistID(src, id, type);
    }

    public void setCurrentPlaylist(PlaylistID playlistID) {
        String src = null;
        String id = null;
        int type = PlaylistID.TYPE_INVALID;
        if (playlistID != null) {
            src = playlistID.src;
            id = playlistID.id;
            type = playlistID.type;
        }
        preferences.edit()
                .putString(playlist_src_key, src)
                .putString(playlist_id_key, id)
                .putInt(playlist_type_key, type)
                .apply();
    }

    public void setCurrentPlaylistPosition(long playlistPos, long playlistPlaybackPos) {
        preferences.edit()
                .putLong(playlist_position_key, playlistPos)
                .putLong(playlist_playback_position_key, playlistPlaybackPos)
                .apply();
    }

    public long getCurrentPlaylistPosition() {
        return preferences.getLong(playlist_position_key, 0);
    }

    public long getCurrentPlaylistPlaybackPosition() {
        return preferences.getLong(playlist_playback_position_key, 0);
    }

    public long getCurrentPlaylistSelectionID() {
        return preferences.getLong(playlist_selection_id_key, -1);
    }

    public void setCurrentPlaylistSelectionID(long id) {
        preferences.edit()
                .putLong(playlist_selection_id_key, id)
                .apply();
    }

    public long getNextPlaybackIDs(int num) {
        synchronized (playback_id_counter_key) {
            return getNextIDs(playback_id_counter_key, num);
        }
    }

    public long getNextPlaylistSelectionID() {
        synchronized (playlist_selection_id_counter_key) {
            return getNextIDs(playlist_selection_id_counter_key, 1);
        }
    }

    private long getNextIDs(String idCounterKey, int num) {
        long id = preferences.getLong(idCounterKey, 0);
        if (id + num < 0) { // overflow
            id = 0;
        }
        if (!preferences.edit()
                .putLong(idCounterKey, id + num)
                .commit()) {
            throw new RuntimeException("Could not update ID for: " + idCounterKey);
        }
        return id;
    }

    public int getCurrentPlaylistPlaybackOrderMode() {
        return preferences.getInt(
                playlist_playback_order_key,
                PlaybackController.PLAYBACK_ORDER_SEQUENTIAL
        );
    }

    public void setCurrentPlaylistPlaybackOrderMode(int playbackOrderMode) {
        preferences.edit()
                .putInt(playlist_playback_order_key, playbackOrderMode)
                .apply();
    }

    public boolean getCurrentPlaylistPlaybackRepeatMode() {
        return preferences.getBoolean(playlist_playback_repeat_key, true);
    }

    public void setCurrentPlaylistPlaybackRepeatMode(boolean repeat) {
        preferences.edit()
                .putBoolean(playlist_playback_repeat_key, repeat)
                .apply();
    }
}
