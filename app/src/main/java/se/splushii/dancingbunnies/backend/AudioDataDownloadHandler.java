package se.splushii.dancingbunnies.backend;

public abstract class AudioDataDownloadHandler {
    public abstract void onStart();
    public abstract void onSuccess();
    public abstract void onFailure(String status);
    public void onProgress(long i, long max) {}
}
