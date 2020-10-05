package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class DummyAPIClient extends APIClient {

    public DummyAPIClient() {
        super(MusicLibraryService.API_SRC_ID_DUMMY, "dummy");
    }

    @Override
    public boolean hasLibrary() {
        return false;
    }

    @Override
    public CompletableFuture<Void> heartbeat() {
        return Util.futureResult("Dummy API does not support heartbeat", null);
    }

    @Override
    public boolean hasPlaylists() {
        return false;
    }

    @Override
    public AudioDataSource getAudioData(EntryID entryID) {
        return null;
    }

    @Override
    public void loadSettings(Context context, Path workDir, Bundle settings) {}

    @Override
    public boolean supports(String action, String argumentSource) {
        return false;
    }

    @Override
    public Batch startBatch(Context context) throws BatchException {
        throw new BatchException("Dummy API does not support transaction batching");
    }
}
