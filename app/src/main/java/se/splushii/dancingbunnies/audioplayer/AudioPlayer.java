package se.splushii.dancingbunnies.audioplayer;

import se.splushii.dancingbunnies.backend.AudioDataSource;

interface AudioPlayer {
    long getCurrentPosition();
    boolean play();
    boolean pause();
    boolean stop();
    void setSource(AudioDataSource audioDataSource,
                   Runnable runWhenReady,
                   Runnable runWhenEnded);
}
