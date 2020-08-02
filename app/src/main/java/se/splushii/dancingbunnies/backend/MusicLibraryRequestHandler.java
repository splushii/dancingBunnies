package se.splushii.dancingbunnies.backend;

public abstract class MusicLibraryRequestHandler {
    public abstract void onStart();
    public void onProgress(int i, int max) {}
    public void onProgress(String s) {}
}
