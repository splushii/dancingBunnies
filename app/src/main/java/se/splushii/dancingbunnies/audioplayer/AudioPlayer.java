package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public abstract class AudioPlayer {
    private static final String LC = Util.getLogContext(AudioPlayer.class);

    enum Type {
        LOCAL,
        CAST
    }

    Callback audioPlayerCallback;
    private final Callback emptyAudioPlayerCallback = new Callback() {
        @Override
        public void onReady() {
            Log.w(LC, "onReady");
        }

        @Override
        public void onEnded() {
            Log.w(LC, "onEnded");
        }

        @Override
        public void onStateChanged(int playBackState) {
            Log.w(LC, "onStateChanged");
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            Log.w(LC, "onMetaChanged");
        }
    };
    AudioPlayer() {
        audioPlayerCallback = emptyAudioPlayerCallback;
    }
    void setListener(Callback audioPlayerCallback) {
        this.audioPlayerCallback = audioPlayerCallback;
    }
    public void removeListener() {
        audioPlayerCallback = emptyAudioPlayerCallback;
    }
    abstract long getSeekPosition();
    abstract int getNumToPreload();
    abstract CompletableFuture<Optional<String>> play();
    abstract CompletableFuture<Optional<String>> pause();
    abstract CompletableFuture<Optional<String>> stop();
    abstract CompletableFuture<Optional<String>> seekTo(long pos);
    abstract CompletableFuture<Optional<String>> setPreloadNext(List<PlaybackEntry> playbackEntries);
    abstract CompletableFuture<Optional<String>> next();
    abstract CompletableFuture<Optional<String>> previous();

    interface Callback {
        void onReady();
        void onEnded();
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
    }

    CompletableFuture<Optional<String>> actionResult(String error) {
        CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        if (error != null) {
            result.complete(Optional.of(error));
            return result;
        }
        result.complete(Optional.empty());
        return result;
    }
}
