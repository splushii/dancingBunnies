package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioPlayer;
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
    private final Context context;

    private final PlaybackControllerEntryDao entryModel;
    private final SharedPreferences preferences;
    private final String player_key;
    private final String play_when_ready_key;
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
            instance = new PlaybackControllerStorage(context.getApplicationContext());
        }
        return instance;
    }

    private PlaybackControllerStorage(Context context) {
        this.context = context;
        entryModel = DB.getDB(context).playbackControllerEntryModel();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        player_key = context.getResources().getString(R.string.pref_key_playbackcontroller_player);
        play_when_ready_key = context.getResources().getString(R.string.pref_key_playbackcontroller_play_when_ready);
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
                        new EntryID(entry.src, entry.id, Meta.FIELD_SPECIAL_MEDIA_ID),
                        entry.playbackID,
                        entry.playbackType,
                        entry.playlistPos,
                        entry.playlistSelectionID
                )
        ).collect(Collectors.toList());
    }

    public CompletableFuture<Void> insert(int queueID, int toPosition, List<PlaybackEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult();
        }
        return CompletableFuture.runAsync(() -> entryModel.insert(queueID, toPosition, entries));
    }

    public CompletableFuture<Void> updatePositions(int queueID, List<PlaybackEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Util.futureResult();
        }
        return CompletableFuture.runAsync(() -> entryModel.updatePositions(queueID, entries));
    }

    public CompletableFuture<Void> replaceWith(int queueID, List<PlaybackEntry> entries) {
        return CompletableFuture.runAsync(() -> entryModel.replaceWith(queueID, entries));
    }

    public CompletableFuture<Void> removeEntries(int queueID, List<PlaybackEntry> playbackEntries) {
        return CompletableFuture.runAsync(() -> entryModel.removeEntries(queueID, playbackEntries));
    }

    public CompletableFuture<Void> removeAll(int queueID) {
        return CompletableFuture.runAsync(() -> entryModel.removeAll(queueID));
    }

    public CompletableFuture<Void> move(int queueID,
                                        long beforePlaybackID,
                                        List<PlaybackEntry> playbackEntries) {
        return CompletableFuture.runAsync(() ->
                entryModel.move(queueID, playbackEntries, beforePlaybackID)
        );
    }

    public CompletableFuture<Void> shuffle(int queueID, List<PlaybackEntry> playbackEntries) {
        List<PlaybackEntry> shuffledEntries = new ArrayList<>(playbackEntries);
        Collections.shuffle(shuffledEntries);
        return reorderQueueItemsAccordingToList(queueID, shuffledEntries);
    }

    private CompletableFuture<Void> reorderQueueItemsAccordingToList(
            int queueID,
            List<PlaybackEntry> playbackEntries
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        LiveData<List<PlaybackEntry>> qEntriesLiveData = getEntries(queueID);
        qEntriesLiveData.observeForever(new Observer<List<PlaybackEntry>>() {
            @Override
            public void onChanged(List<PlaybackEntry> allEntries) {
                List<Pair<Long, List<PlaybackEntry>>> entryChunksToMove =
                        PlaybackController.getEntryChunksToMove(allEntries, playbackEntries);
                for (Pair<Long, List<PlaybackEntry>> entry: entryChunksToMove) {
                    long beforePlaybackID = entry.first;
                    List<PlaybackEntry> entriesToMove = entry.second;
                    move(queueID, beforePlaybackID, entriesToMove).join();
                }
                future.complete(null);
                qEntriesLiveData.removeObserver(this);
            }
        });
        return future;
    }

    public CompletableFuture<Void> sort(int queueID,
                                        List<PlaybackEntry> playbackEntries,
                                        List<String> sortBy) {
        CompletableFuture<List<PlaybackEntry>> sortedEntries = PlaybackController.sorted(
                context,
                playbackEntries,
                sortBy
        );
        try {
            return reorderQueueItemsAccordingToList(queueID, sortedEntries.get());
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Util.futureResult("Could not sort: " + e.getMessage());
        }
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

    public void setCurrentPlayerType(AudioPlayer.Type currentPlayer) {
        preferences.edit()
                .putString(player_key, currentPlayer.name())
                .apply();
    }

    public AudioPlayer.Type getCurrentPlayerType() {
        return AudioPlayer.Type.valueOf(preferences.getString(player_key, AudioPlayer.Type.LOCAL.name()));
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        preferences.edit()
                .putBoolean(play_when_ready_key, playWhenReady)
                .apply();
    }

    public boolean getPlayWhenReady() {
        return preferences.getBoolean(play_when_ready_key, false);
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
            return Util.getNextIDs(preferences, playback_id_counter_key, num);
        }
    }

    public long getNextPlaylistSelectionID() {
        synchronized (playlist_selection_id_counter_key) {
            return Util.getNextIDs(preferences, playlist_selection_id_counter_key, 1);
        }
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
