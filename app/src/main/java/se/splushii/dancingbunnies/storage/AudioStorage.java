package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public class AudioStorage {
    private static final String LC = Util.getLogContext(AudioStorage.class);
    private final HashMap<EntryID, AudioDataSource> audioMap;
    private final HashMap<EntryID, List<AudioDataDownloadHandler>> handlerMap;

    public AudioStorage() {
        audioMap = new HashMap<>();
        handlerMap = new HashMap<>();
    }

    public static File getCacheFile(Context context, EntryID entryID) {
        return new File(
                context.getFilesDir()
                        + "/" + entryID.src
                        + "/" + entryID.id
        );
    }

    public synchronized AudioDataSource get(EntryID entryID) {
        return audioMap.get(entryID);
    }

    public synchronized AudioDataSource put(EntryID entryID, AudioDataSource audioDataSource) {
        return audioMap.put(entryID, audioDataSource);
    }

    private synchronized void release(EntryID entryID) {
        audioMap.remove(entryID);
        Log.d(LC, audioMap.keySet().size() + " AudioDataSource entries in memory. "
                + "Released entryID: " + entryID);
    }

    public void download(EntryID entryID, AudioDataDownloadHandler handler) {
        if (!audioMap.containsKey(entryID)) {
            handler.onFailure("EntryID to download not found in AudioStorage");
            return;
        }
        AudioDataSource audioDataSource = audioMap.get(entryID);
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers == null) {
                handlers = new LinkedList<>();
                handlerMap.put(entryID, handlers);
                // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
                audioDataSource.download(new AudioDataSource.Handler() {
                    @Override
                    public void onStart() {
                        onDownloadStartEvent(entryID);
                    }

                    @Override
                    public void onFailure(String message) {
                        onDownloadFailureEvent(entryID, message);
                    }

                    @Override
                    public void onSuccess() {
                        onDownloadSuccessEvent(entryID);
                    }

                    @Override
                    public void onProgress(long i, long max) {
                        onDownloadProgressEvent(entryID, i, max);
                    }
                });
            }
            handlers.add(handler);
        }
    }

    private void onDownloadStartEvent(EntryID entryID) {
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(AudioDataDownloadHandler::onStart);
            }
        }
    }

    private void onDownloadProgressEvent(EntryID entryID, long i, long max) {
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onProgress(i, max));
            }
        }
    }

    private synchronized void onDownloadSuccessEvent(EntryID entryID) {
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                AudioDataSource audioDataSource = audioMap.get(entryID);
                if (audioDataSource == null) {
                    handlers.forEach(handler -> handler.onFailure(
                            "AudioDataSource not present in AudioStorage."
                    ));
                } else {
                    handlers.forEach(handler -> handler.onSuccess(audioDataSource));
                }
            }
            handlerMap.remove(entryID);
        }
        release(entryID);
    }

    private synchronized void onDownloadFailureEvent(EntryID entryID, String message) {
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onFailure(message));
            }
            handlerMap.remove(entryID);
        }
        release(entryID);
    }
}
