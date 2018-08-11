package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.cast.framework.CastContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.util.Util;

// TODO: PlaybackController should have audio players, a queue and a reference to the current playlist.
// TODO: It is responsible for giving entries to preload to the audio players.
// TODO: The data to preload is taken from the queue if non-empty, otherwise from the current playlist.
// TODO: It is also responsible for shifting state between audio players.
public class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);

    private final MusicLibraryService musicLibraryService;
    private final Context context;
    private AudioPlayer audioPlayer;
    private AudioPlayer.Callback audioPlayerCallback = new AudioPlayerCallback();
    private int lastPlaybackState = PlaybackStateCompat.STATE_NONE;
    private LocalAudioPlayer localAudioPlayer;
    private CastAudioPlayer castAudioPlayer;
    private PlaybackEntry currentEntry;
    private PlaybackQueue queue;
    private PlaylistItem currentPlaylist = PlaylistItem.defaultPlaylist;
    private long playlistPosition;
    private Callback callback;
    private boolean playWhenReady;

    PlaybackController(Context context, MusicLibraryService musicLibraryService, Callback callback) {
        this.context = context;
        this.musicLibraryService = musicLibraryService;
        this.callback = callback;
        CastContext castContext = CastContext.getSharedInstance(context);

        queue = new PlaybackQueue();

        localAudioPlayer = new LocalAudioPlayer(musicLibraryService);
        audioPlayer = localAudioPlayer;
        audioPlayer.setListener(audioPlayerCallback);

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

    private CompletableFuture<Optional<String>> updateAudioPlayerPreload() {
        List<PlaybackEntry> nextItems = getNextItems(0, audioPlayer.getNumToPreload());
        StringBuilder sb = new StringBuilder();
        sb.append("nextItems: ").append(nextItems.size()).append(": (");
        sb.append((String.join(", ", nextItems.stream()
                .map(playbackEntry -> playbackEntry.meta.getString(Meta.METADATA_KEY_TITLE))
                .collect(Collectors.toList())
        )));
        Log.e(LC, sb.append(")").toString());
        return audioPlayer.setPreloadNext(nextItems);
    }

    private List<PlaybackEntry> getNextItems(int offset, int max) {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        if (max <= 0) {
            return playbackEntries;
        }
        if (currentEntry != null) {
            playbackEntries.add(currentEntry);
            max--;
        }
        int numFromQueue = queue.size() > offset ? queue.size() - offset : 0;
        if (numFromQueue > 0) {
            playbackEntries.addAll(queue.getEntries(offset, max));
        }
        int offsetInPlaylist = offset > queue.size() ? offset - queue.size() : 0;
        if (currentEntry != null) {
            offsetInPlaylist++;
        }
        Log.e(LC, "playlistOffset: " + offsetInPlaylist);
        int numFromPlaylist = max - numFromQueue;
        if (numFromPlaylist > 0) {
            playbackEntries.addAll(
                    musicLibraryService.playlistGetNext(
                            currentPlaylist.playlistID,
                            playlistPosition + offsetInPlaylist,
                            numFromPlaylist
                    )
            );
        }
        return playbackEntries;
    }

    private void updateCurrentEntry() {
        if (currentEntry == null) {
            if (!queue.isEmpty()) {
                currentEntry = queue.current();
                queue.next();
                callback.onQueueChanged();
            } else {
                List<PlaybackEntry> entries = musicLibraryService.playlistGetNext(
                        currentPlaylist.playlistID,
                        playlistPosition,
                        1
                );
                currentEntry = entries.isEmpty() ? null : entries.get(0);
            }
        }
    }

    void onDestroy() {
        castAudioPlayer.onDestroy();
    }

    public void initialize() {
        playWhenReady = false;
        updateCurrentEntry();
        updateAudioPlayerPreload().thenAccept(e ->
                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
        );
    }

    public long getPlayerSeekPosition() {
        return audioPlayer.getSeekPosition();
    }

    public PlaylistItem getCurrentPlaylist() {
        return currentPlaylist;
    }

    public long getCurrentPlaylistPosition() {
        return playlistPosition;
    }

    public void play() {
        playWhenReady = true;
        audioPlayer.play().thenAccept(e ->
                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
        );
    }

    public void pause() {
        playWhenReady = false;
        audioPlayer.pause().thenAccept(e ->
                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
        );
    }

    public void playPause() {
        if (playWhenReady) {
            pause();
        } else {
            play();
        }
    }

    public void stop() {
        playWhenReady = false;
        audioPlayer.stop().thenAccept(e ->
                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
        );
    }

    public CompletableFuture<Optional<String>> skipToNext() {
        // Update controller state
        currentEntry = null;
        if (queue.isEmpty()) {
            playlistPosition = musicLibraryService.playlistNext(
                    currentPlaylist.playlistID,
                    playlistPosition
            );
            callback.onPlaylistPositionChanged();
        }
        updateCurrentEntry();
        // AudioPlayer next
        return audioPlayer.next().thenCompose(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            // Update AudioPlayer preload
            return updateAudioPlayerPreload();
        }).thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public void skipToPrevious() {
        // TODO: implement
        Log.e(LC, "skipToPrevious not implemented");
    }

    public void skipToQueueItem(long queuePosition) {
        // Update controller state
        if (queuePosition <= 0 || queue.isEmpty()) {
            return;
        }
        currentEntry = queue.skipTo(queuePosition);
        callback.onQueueChanged();
        updateCurrentEntry();
        // Update AudioPlayer preload
        updateAudioPlayerPreload().thenAccept(e ->
                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
        );
    }

    public CompletableFuture<Optional<String>> addToQueue(PlaybackEntry playbackEntry, PlaybackQueue.QueueOp op) {
        // Update controller state
        queue.addToQueue(playbackEntry, op);
        callback.onQueueChanged();
        updateCurrentEntry();
        // Update AudioPlayer preload
        return updateAudioPlayerPreload().thenApply(e -> {
            e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
            return e;
        });
    }

    public void removeFromQueue(int queuePosition) {
        if (queue.removeFromQueue(queuePosition)) {
            // Update controller state
            callback.onQueueChanged();
            updateCurrentEntry();
            // Update AudioPlayer preload
            updateAudioPlayerPreload().thenAccept(e ->
                    e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
            );
        }
    }

    public void seekTo(long pos) {
        audioPlayer.seekTo(pos).thenAccept(e ->
                e.ifPresent(s -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show())
        );
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
        updateAudioPlayerPreload();
        audioPlayer.seekTo(lastPos);
    }

    public List<MediaSessionCompat.QueueItem> getQueue() {
        // TODO: Merge the cast queue with the local queue?
        Log.w(LC, "getQueue not implemented for Cast");
        return queue.getQueue();
    }

    public PlaybackEntry getCurrentPlaybackEntry() {
        return currentEntry;
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
            if (playWhenReady) {
                play();
            }
        }

        @Override
        public void onEnded() {
            skipToNext();
        }

        @Override
        public void onStateChanged(int newPlaybackState) {
            if (PlaybackStateCompat.STATE_STOPPED == newPlaybackState) {
                playWhenReady = false;
            }
            lastPlaybackState = newPlaybackState;
            callback.onStateChanged(newPlaybackState);
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            callback.onMetaChanged(entryID);
        }
    }
}
