package se.splushii.dancingbunnies.backend;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;

public abstract class AudioDataDownloadHandler {
    public abstract void onStart();
    public abstract void onSuccess(AudioDataSource audioDataSource);
    public abstract void onFailure(String status);
    public void onProgress(long i, long max) {}
}
