package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.cast.framework.CastContext;

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

public class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);

    private final Context context;
    private final MusicLibraryService musicLibraryService;
    private final Callback callback;

    // Audio players
    private AudioPlayer audioPlayer;
    private LocalAudioPlayer localAudioPlayer;
    private CastAudioPlayer castAudioPlayer;
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

        localAudioPlayer = new LocalAudioPlayer(musicLibraryService);
        audioPlayer = localAudioPlayer;
        audioPlayer.setListener(audioPlayerCallback);

        CastContext castContext = CastContext.getSharedInstance(context);
        castAudioPlayer = new CastAudioPlayer(
                musicLibraryService,
                castContext,
                new CastAudioPlayer.CastConnectionListener() {
                    @Override
                    void onConnected() {
                        setAudioPlayer(AudioPlayer.Type.CAST);
                    }

                    @Override
                    void onDisconnected() {
                        playWhenReady = false;
                        setAudioPlayer(AudioPlayer.Type.LOCAL);
                    }
                }
        );
        castAudioPlayer.onCreate();
    }

    public void initialize() {
        playWhenReady = false;
    }

    void onDestroy() {
        castAudioPlayer.onDestroy();
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

    public void skipToQueueItem(long queuePosition) {
        // TODO: implement
        Log.e(LC, "skipToQueueItem not implemented");
//        // Update controller state
//        if (queuePosition <= 0 || queue.isEmpty()) {
//            return;
//        }
//        currentEntry = queue.skipTo(queuePosition);
//        callback.onQueueChanged();
//        updateCurrentEntry();
//        // Update AudioPlayer preload
//        updateAudioPlayerPreload().thenAccept(e ->
//                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
//        );
    }

    public CompletableFuture<Optional<String>> addToQueue(PlaybackEntry playbackEntry, PlaybackQueue.QueueOp op) {
        Log.e(LC, "addToQueue: " + playbackEntry.toString());
        return audioPlayer.queue(playbackEntry, op).thenApply(e -> {
            if (e.isPresent()) {
                Toast.makeText(context, e.get(), Toast.LENGTH_SHORT).show();
            } else {
                callback.onQueueChanged();
            }
            return e;
        });
    }

    public void removeFromQueue(int queuePosition) {
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

    private void setAudioPlayer(AudioPlayer.Type audioPlayerType) {
        AudioPlayer newAudioPlayer;
        switch (audioPlayerType) {
            case LOCAL:
                if (audioPlayer instanceof LocalAudioPlayer) {
                    return;
                }
                newAudioPlayer = localAudioPlayer;
                break;
            case CAST:
                if (audioPlayer instanceof CastAudioPlayer) {
                    return;
                }
                newAudioPlayer = castAudioPlayer;
                break;
            default:
                return;
        }
        audioPlayer.pause();
        long lastPos = audioPlayer.getSeekPosition();
        audioPlayer.removeListener();
        audioPlayer.stop();
        newAudioPlayer.setListener(audioPlayerCallback);
        audioPlayer = newAudioPlayer;
        callback.onPlayerChanged(audioPlayerType);
        audioPlayer.seekTo(lastPos);
    }

    public List<MediaSessionCompat.QueueItem> getQueue() {
        Log.d(LC, "getQueue");
        List<PlaybackEntry> entries = new LinkedList<>();
        entries.addAll(audioPlayer.getPreloadedQueueEntries(Integer.MAX_VALUE));
        entries.forEach(p -> Log.e(LC, "appe: " + p.toString()));
        entries.addAll(queue.getEntries());
        entries.forEach(p -> Log.e(LC, "qupe: " + p.toString()));
        List<MediaSessionCompat.QueueItem> queueItems = new LinkedList<>();
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
        if (maxNum > entries.size()) {
            entries.addAll(playlistItems.getEntries(maxNum - entries.size()));
        }
        if (maxNum > entries.size()) {
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
        void onQueueChanged();
        void onPlaylistPositionChanged();
    }

    private class AudioPlayerCallback implements AudioPlayer.Callback {
        @Override
        public void onReady() {
        }

        @Override
        public void onEnded() {
        }

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
            callback.onQueueChanged();
            callback.onPlaylistPositionChanged();
        }

        @Override
        public List<PlaybackEntry> requestPreload(int num) {
            return getNextPreloadItems(num);
        }

        @Override
        public void dePreload(List<PlaybackEntry> queueEntries,
                              List<PlaybackEntry> playlistEntries) {
            queue.offer(queueEntries);
            playlistItems.offer(playlistEntries);
        }
    }

    private List<PlaybackEntry> getNextPreloadItems(int num) {
        Log.d(LC, "getNextPreloadItems");
        List<PlaybackEntry> entries = new LinkedList<>();
        // Get from internal queue
        if (!queue.isEmpty()) {
            entries.addAll(queue.poll(num));
        }
        // Get from internal playlist items
        if (entries.size() < num && !playlistItems.isEmpty()) {
            entries.addAll(playlistItems.poll(num - entries.size()));
        }
        // Get from current playlist reference
        if (entries.size() < num) {
            entries.addAll(musicLibraryService.playlistGetNext(
                    currentPlaylist.playlistID,
                    playlistPosition,
                    num - entries.size()
            ));
        }
        Log.e(LC, "PL position: " + playlistPosition);
        Log.e(LC, "offset: " + (num - entries.size()));
        playlistPosition = musicLibraryService.playlistPosition(
                currentPlaylist.playlistID,
                playlistPosition,
                entries.size()
        );
        Log.e(LC, "PL position: " + playlistPosition);
        callback.onPlaylistPositionChanged();
        return entries;
    }
}
