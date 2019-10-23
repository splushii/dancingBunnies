package se.splushii.dancingbunnies.backend;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;

public abstract class AudioDataHandler {
    public abstract void onDownloading();
    public abstract void onSuccess(AudioDataSource audioDataSource);
    public abstract void onFailure(String status);
}
