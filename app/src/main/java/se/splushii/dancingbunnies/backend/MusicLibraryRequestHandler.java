package se.splushii.dancingbunnies.backend;

public abstract class MusicLibraryRequestHandler {
    public abstract void onStart();
    public abstract void onSuccess();
    public abstract void onFailure(String status);
}
