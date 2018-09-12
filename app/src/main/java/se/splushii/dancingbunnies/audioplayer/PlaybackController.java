package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.util.Util;

// PlaybackController should have audio players, an internal queue, an internal playlist and a
// reference to the current playlist.
//
// It is responsible for giving entries to preload to the audio players.
// The data to preload is supplied by the following sources in order until depleted:
// internal queue, internal playlist, current playlist by reference.
//
// It is responsible for exposing the UI queue items and playlist items.
// The UI queue is taken from: current audio player, internal queue.
// The UI playlist items is taken from: current audio player, internal playlist items, current
// playlist by reference.
//
// It is responsible for shifting state between audio players.

// TODO: Sequentialize control calls to AudioPlayers here, to avoid doing it in every AudioPlayer.
public class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);

    private final Context context;
    private final MusicLibraryService musicLibraryService;
    private final Callback callback;
    private final SessionManagerListener<Session> sessionManagerListener = new SessionManagerListenerImpl();
    private final SessionManager sessionManager;

    // Audio players
    private AudioPlayer audioPlayer;
    private AudioPlayer.Callback audioPlayerCallback = new AudioPlayerCallback();

    // Internal queue items
    private PlaybackQueue queue;

    // Internal playlist items
    private PlaybackQueue playlistItems;

    // Current playlist reference
    private PlaylistItem currentPlaylist = PlaylistItem.defaultPlaylist;
    private long playlistPosition = 0;

    // Internal state
    private boolean playWhenReady;

    PlaybackController(Context context, MusicLibraryService musicLibraryService, Callback callback) {
        this.context = context;
        this.musicLibraryService = musicLibraryService;
        this.callback = callback;

        queue = new PlaybackQueue();
        playlistItems = new PlaybackQueue();

        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                AudioPlayer.EmptyState
        );

        CastContext castContext = CastContext.getSharedInstance(context);
        sessionManager = castContext.getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener);
    }

    public void initialize() {
        playWhenReady = false;
        audioPlayer.checkPreload();
    }

    void onDestroy() {
        sessionManager.removeSessionManagerListener(sessionManagerListener);
    }

    public long getPlayerSeekPosition() {
        return audioPlayer.getSeekPosition();
    }

    public PlaylistItem getCurrentPlaylist() {
        return currentPlaylist;
    }

    public CompletableFuture<Optional<String>> play() {
        playWhenReady = true;
        return audioPlayer.play().thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public CompletableFuture<Optional<String>> pause() {
        playWhenReady = false;
        return audioPlayer.pause().thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public CompletableFuture<Optional<String>> playPause() {
        return playWhenReady ? pause() : play();
    }

    public CompletableFuture<Optional<String>> stop() {
        playWhenReady = false;
        return audioPlayer.stop().thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public CompletableFuture<Optional<String>> skipToNext() {
        return audioPlayer.next().thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public void skipToPrevious() {
        // TODO: implement
        Log.e(LC, "skipToPrevious not implemented");
    }

    public void skipItems(int offset) {
        audioPlayer.skipItems(offset);
    }

    public CompletableFuture<Optional<String>> addToQueue(PlaybackEntry playbackEntry, PlaybackQueue.QueueOp op) {
        Log.e(LC, "addToQueue: " + playbackEntry.toString());
        return audioPlayer.queue(playbackEntry, op).thenApply(e -> {
            if (e.isPresent()) {
                Toast.makeText(context, e.get(), Toast.LENGTH_SHORT).show();
            } else {
                callback.onQueueChanged(getQueue());
            }
            return e;
        });
    }

    public void removeFromQueue(int queuePosition) {
        // TODO: implement
        Log.e(LC, "removeFromQueue not implemented");
//        if (queue.removeFromQueue(queuePosition)) {
//            // Update controller state
//            callback.onQueueChanged();
//            updateCurrentEntry();
//            // Update AudioPlayer preload
//            updateAudioPlayerPreload().thenAccept(e ->
//                    e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
//            );
//        }
    }

    public CompletableFuture<Optional<String>> seekTo(long pos) {
        return audioPlayer.seekTo(pos).thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public CompletableFuture<Optional<String>> playNow(PlaybackEntry playbackEntry) {
        playWhenReady = true;
        return addToQueue(playbackEntry, PlaybackQueue.QueueOp.NEXT).thenCompose(e -> {
            if (e.isPresent()) {
                Toast.makeText(context, e.get(), Toast.LENGTH_SHORT).show();
                return CompletableFuture.completedFuture(Optional.of(""));
            }
            return skipToNext();
        });
    }

    public List<MediaSessionCompat.QueueItem> getQueue() {
        List<MediaSessionCompat.QueueItem> queueItems = new LinkedList<>();
        List<PlaybackEntry> entries = new LinkedList<>();
        entries.addAll(audioPlayer.getPreloadedQueueEntries(Integer.MAX_VALUE));
        entries.addAll(queue.getEntries());
        for (PlaybackEntry playbackEntry: entries) {
            MediaDescriptionCompat description = Meta.meta2desc(playbackEntry.meta);
            MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(
                    description,
                    playbackEntry.entryID.hashCode()
            );
            queueItems.add(queueItem);
        }
        return queueItems;
    }

    public List<PlaybackEntry> getPlaylistEntries(int maxNum) {
        List<PlaybackEntry> entries = audioPlayer.getPreloadedPlaylistEntries(maxNum);
        if (entries.size() < maxNum) {
            entries.addAll(playlistItems.getEntries(maxNum - entries.size()));
        }
        if (entries.size() < maxNum) {
            entries.addAll(musicLibraryService.playlistGetNext(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    maxNum
            ));
        }
        return entries;
    }

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onQueueChanged(List<MediaSessionCompat.QueueItem> queue);
        void onPlaylistPositionChanged();
    }

    private class AudioPlayerCallback implements AudioPlayer.Callback {
        @Override
        public void onStateChanged(int newPlaybackState) {
            if (PlaybackStateCompat.STATE_STOPPED == newPlaybackState) {
                playWhenReady = false;
            }
            callback.onStateChanged(newPlaybackState);
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            callback.onMetaChanged(entryID);
        }

        @Override
        public void onPreloadChanged() {
            callback.onQueueChanged(getQueue());
            callback.onPlaylistPositionChanged();
        }

        @Override
        public List<PlaybackEntry> requestPreload(int num) {
            return pollNextPreloadItems(num);
        }

        @Override
        public void dePreload(List<PlaybackEntry> queueEntries,
                              List<PlaybackEntry> playlistEntries) {
            queue.offer(queueEntries);
            playlistItems.offer(playlistEntries);
        }
    }

    private List<PlaybackEntry> pollNextPreloadItems(int num) {
        Log.d(LC, "pollNextPreloadItems");
        List<PlaybackEntry> entries = new LinkedList<>();
        // Get from internal queue
        if (!queue.isEmpty()) {
            entries.addAll(queue.poll(num));
            callback.onQueueChanged(getQueue());
        }
        // Get from internal playlist items
        if (entries.size() < num && !playlistItems.isEmpty()) {
            entries.addAll(playlistItems.poll(num - entries.size()));
            callback.onPlaylistPositionChanged();
        }
        // Get from current playlist reference
        if (entries.size() < num) {
            List<PlaybackEntry> playlistEntries = musicLibraryService.playlistGetNext(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    num - entries.size()
            );
            long oldPosition = playlistPosition;
            playlistPosition = musicLibraryService.playlistPosition(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    playlistEntries.size()
            );
            entries.addAll(playlistEntries);
            Log.d(LC, "Playlist position +" + (num - playlistEntries.size())
                    + " from " + oldPosition + " to " + playlistPosition);
            callback.onPlaylistPositionChanged();
        }
        return entries;
    }

    private void onCastConnect(CastSession session) {
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastConnect", lastState);
        audioPlayer.stop();
        audioPlayer = new CastAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                lastState,
                session
        );
        callback.onPlayerChanged(AudioPlayer.Type.CAST);
    }

    private void onCastDisconnect() {
        AudioPlayer.AudioPlayerState lastState = audioPlayer.getLastState();
        printState("onCastDisconnect", lastState);
        audioPlayer = new LocalAudioPlayer(
                audioPlayerCallback,
                musicLibraryService,
                lastState
        );
        callback.onPlayerChanged(AudioPlayer.Type.LOCAL);
    }

    private void printState(String caption, AudioPlayer.AudioPlayerState lastState) {
        Log.d(LC, caption);
        Log.d(LC, "history:");
        for (PlaybackEntry entry: lastState.history) {
            Log.d(LC, entry.toString());
        }
        Log.d(LC, "entries:");
        for (PlaybackEntry entry: lastState.entries) {
            Log.d(LC, entry.toString());
        }
        Log.d(LC, "lastPos: " + lastState.lastPos);
    }

    private class SessionManagerListenerImpl implements SessionManagerListener<Session> {
        @Override
        public void onSessionStarting(Session session) {
            Log.d(LC, "CastSession starting");
        }

        @Override
        public void onSessionStarted(Session session, String s) {
            Log.d(LC, "CastSession started");
            onCastConnect((CastSession) session);
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {
            Log.d(LC, "CastSession start failed");
            onCastDisconnect();
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(LC, "CastSession ending");
            onCastDisconnect();
        }

        @Override
        public void onSessionEnded(Session session, int i) {
            Log.d(LC, "CastSession ended");
        }

        @Override
        public void onSessionResuming(Session session, String s) {
            Log.d(LC, "CastSession resuming");
        }

        @Override
        public void onSessionResumed(Session session, boolean b) {
            Log.d(LC, "CastSession resumed");
            onCastConnect((CastSession) session);
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {
            Log.d(LC, "CastSession resume failed");
            onCastDisconnect();
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
            Log.d(LC, "CastSession suspended");
        }
    }
}
