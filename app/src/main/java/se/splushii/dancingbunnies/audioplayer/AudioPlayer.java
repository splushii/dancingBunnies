package se.splushii.dancingbunnies.audioplayer;

import se.splushii.dancingbunnies.backend.AudioDataSource;

interface AudioPlayer {
    long getCurrentPosition();
    void play();
    void pause();
    void stop();
    void setSource(AudioDataSource audioDataSource, Runnable r);
}
