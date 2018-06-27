package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.google.android.gms.cast.framework.CastContext;

import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

// TODO: PlaybackController should have audioplayers, a queue and a reference to the current playlist.
// TODO: It is responsible for giving entries to preload to the audioplayers.
// TODO: The data to preload is taken from the queue if non-empty, otherwise from the current playlist.
// TODO: It is also responsible for shifting state between players.
public class PlaybackController {
    private static final String LC = Util.getLogContext(PlaybackController.class);

    private AudioPlayer audioPlayer;
    private AudioPlayer.Callback audioPlayerCallback = new AudioPlayerCallback();
    private int lastPlaybackState = PlaybackStateCompat.STATE_NONE;
    private LocalAudioPlayer localAudioPlayer;
    private CastAudioPlayer castAudioPlayer;
    private PlaybackEntry currentEntry;
    private PlaybackQueue queue;
    private String playlistId;
    private String playlistItemId;
    private Callback callback;
    private boolean playWhenReady;

    PlaybackController(Context context, MusicLibraryService musicLibraryService, Callback callback) {
        this.callback = callback;
        CastContext castContext = CastContext.getSharedInstance(context);

        queue = new PlaybackQueue();

        localAudioPlayer = new LocalAudioPlayer(musicLibraryService);
        audioPlayer = localAudioPlayer;
        audioPlayer.setListener(audioPlayerCallback);

        castAudioPlayer = new CastAudioPlayer(castContext, new CastAudioPlayer.CastConnectionListener() {
            @Override
            void onConnected() {
//                setAudioPlayer(AudioPlayer.Type.CAST);
            }

            @Override
            void onDisconnected() {
//                playWhenReady = false;
//                setAudioPlayer(AudioPlayer.Type.LOCAL);
            }
        });
        castAudioPlayer.onCreate();
    }

    void onDestroy() {
        castAudioPlayer.onDestroy();
    }

    public long getCurrentPosition() {
        return audioPlayer.getCurrentPosition();
    }

    public void play() {
        playWhenReady = true;
        audioPlayer.play();
    }

    public void pause() {
        playWhenReady = false;
        audioPlayer.pause();
    }

    public void stop() {
        playWhenReady = false;
        audioPlayer.stop();
    }

    private List<PlaybackEntry> getNextItems(int offset, int max) {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        int numFromQueue = queue.size() > offset ? queue.size() - offset : 0;
        int offsetInPlaylist = offset > queue.size() ? offset - queue.size() : 0;
        int numFromPlaylist = max - numFromQueue;
        if (numFromQueue > 0) {
            playbackEntries.addAll(queue.getEntries(offset, max));
        }
        if (numFromPlaylist > 0) {
//            playbackEntries.addAll(
//                    musicLibraryService.getNextPlaylistEntries(
//                            playlistId,
//                            offsetInPlaylist,
//                            numFromPlaylist
//                    )
//            );
        }
        return playbackEntries;
    }

    public void skipToNext() {
        // TODO: If queue is empty, step next in playlist instead
        // Step forward in the queue
        queue.next();
        callback.onQueueChanged();
        // Fill audio player preload
        fillPreloadNext();
        // Step forward in the audio player
        audioPlayer.next();
    }

    private void fillPreloadNext() {
        // How many already preloaded?
        int numPreloaded = audioPlayer.getNumPreloadedNext();
        // How many to preload?
        int numToPreload = audioPlayer.getNumToPreload();
        // Get needed entries to preload
        int maxToPreload = numToPreload - numPreloaded > 0 ? numToPreload - numPreloaded : 0;
        List<PlaybackEntry> nextItems = getNextItems(numPreloaded, maxToPreload);
        // Add them to audio player
        for (PlaybackEntry entry: nextItems) {
            audioPlayer.addPreloadNext(entry, numPreloaded++);
        }
    }

    public void skipToPrevious() {
        // TODO: implement
        Log.e(LC, "skipToPrevious not implemented");
    }

    public void skipToQueueItem(long queuePosition) {
        if (queuePosition <= 0) {
            return;
        }
        queue.skipTo(queuePosition);
        callback.onQueueChanged();
        audioPlayer.clearPreloadNext();
        fillPreloadNext();
    }

    public void addToQueue(PlaybackEntry playbackEntry, PlaybackQueue.QueueOp op) {
        // Add to queue
        int index = queue.addToQueue(playbackEntry, op);
        callback.onQueueChanged();
        // Update audio player preload
        switch (op) {
            case LAST:
                if (audioPlayer.getNumPreloadedNext() < audioPlayer.getNumToPreload()) {
                    audioPlayer.addPreloadNext(playbackEntry, index);
                }
                break;
            case NEXT:
            default:
                audioPlayer.addPreloadNext(playbackEntry, index);
                break;
        }
    }

    public void removeFromQueue(int queuePosition) {
        if (queue.removeFromQueue(queuePosition)) {
            callback.onQueueChanged();
            audioPlayer.removePreloadNext(queuePosition);
            fillPreloadNext();
        }
    }

    public void seekTo(long pos) {
        audioPlayer.seekTo(pos);
    }

    public void playNow(PlaybackEntry playbackEntry) {
        playWhenReady = true;
        addToQueue(playbackEntry, PlaybackQueue.QueueOp.NEXT);
        if (queue.size() > 1) {
            skipToNext();
        }
    }

    private void setAudioPlayer(AudioPlayer.Type audioPlayerType) {
        // TODO: implement new version
        Log.e(LC, "setAudioPlayer not implemented");
//        AudioPlayer newAudioPlayer;
//        switch (audioPlayerType) {
//            case LOCAL:
//                if (audioPlayer instanceof LocalAudioPlayer) {
//                    return;
//                }
//                newAudioPlayer = localAudioPlayer;
//                break;
//            case CAST:
//                if (audioPlayer instanceof CastAudioPlayer) {
//                    return;
//                }
//                newAudioPlayer = castAudioPlayer;
//                break;
//            default:
//                return;
//        }
//        audioPlayer.pause();
//        long lastPos = audioPlayer.getCurrentPosition();
//        audioPlayer.removeListener();
//        audioPlayer.stop();
//        audioPlayer.clearPreloadNext();
//        newAudioPlayer.setListener(audioPlayerCallback);
//        audioPlayer = newAudioPlayer;
//        callback.onPlayerChanged(audioPlayerType);
//        audioPlayer.clearPreloadNext();
//        for (PlaybackEntry playbackEntry: getNextItems(0, audioPlayer.getNumToPreload())) {
//            audioPlayer.addPreloadNext(playbackEntry);
//        }
//        audioPlayer.seekTo(lastPos);
    }

    public List<MediaSessionCompat.QueueItem> getQueue() {
        // TODO: Merge the cast queue with the local queue
        return queue.getQueue();
    }

    interface Callback {
        void onPlayerChanged(AudioPlayer.Type type);
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
        void onQueueChanged();
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

        @Override
        public void onQueueChanged() {
            // TODO: If cast queue changed, add to local queue if possible
            callback.onQueueChanged();
        }

        @Override
        public void onPreloadNextConsumed() {
            fillPreloadNext();
        }
    }
}
