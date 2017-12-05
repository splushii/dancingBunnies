package se.splushii.dancingbunnies.backend;

public abstract class MusicLibraryRequestHandler {
    public abstract void onStart();
    public abstract void onSuccess(String status);
    public abstract void onFailure(String status);
    public void onProgress(int i, int max) {}
    public void onProgress(String s) {}
}
