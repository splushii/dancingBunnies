package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaMetadataCompat;

import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.backend.AudioDataSource;

interface AudioPlayer {
    long getCurrentPosition();
    CompletableFuture<Boolean> play();
    CompletableFuture<Boolean> pause();
    CompletableFuture<Boolean> stop();
    void setSource(AudioDataSource audioDataSource,
                   MediaMetadataCompat meta,
                   Runnable runWhenReady,
                   Runnable runWhenEnded);
}
