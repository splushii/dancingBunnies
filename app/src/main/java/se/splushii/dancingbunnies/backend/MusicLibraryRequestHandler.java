package se.splushii.dancingbunnies.backend;

import java.util.ArrayList;

public abstract class MusicLibraryRequestHandler {
    public abstract void onStart();
    public abstract void onSuccess();
    public abstract void onFailure(String status);
}
