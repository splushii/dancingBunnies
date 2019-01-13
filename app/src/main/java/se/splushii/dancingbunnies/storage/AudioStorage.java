package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public class AudioStorage {
    private static final String LC = Util.getLogContext(AudioStorage.class);
    private static volatile AudioStorage instance;
    private final HashMap<EntryID, AudioDataSource> audioMap;
    private final HashMap<EntryID, List<AudioDataDownloadHandler>> handlerMap;
    private final MutableLiveData<List<AudioDataFetchState>> fetchState;
    private final HashMap<EntryID, AudioDataFetchState> fetchStateMap;

    public static synchronized AudioStorage getInstance() {
        if (instance == null) {
            instance = new AudioStorage();
        }
        return instance;
    }

    private AudioStorage() {
        audioMap = new HashMap<>();
        handlerMap = new HashMap<>();
        fetchStateMap = new HashMap<>();
        fetchState = new MutableLiveData<>();
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
        fetchStateMap.put(entryID, new AudioDataFetchState(entryID));
        return audioMap.put(entryID, audioDataSource);
    }

    private synchronized void release(EntryID entryID) {
        fetchStateMap.remove(entryID);
        audioMap.remove(entryID);
        Log.d(LC, audioMap.keySet().size() + " AudioDataSource entries in memory. "
                + "Released entryID: " + entryID);
    }

    public void fetch(EntryID entryID, AudioDataDownloadHandler handler) {
        if (!audioMap.containsKey(entryID)) {
            handler.onFailure("EntryID to fetch not found in AudioStorage");
            return;
        }
        AudioDataSource audioDataSource = audioMap.get(entryID);
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.computeIfAbsent(entryID, k -> new LinkedList<>());
            handlers.add(handler);
            // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
            audioDataSource.fetch(new AudioDataSource.Handler() {
                @Override
                public void onDownloading() {
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
    }

    private void onDownloadStartEvent(EntryID entryID) {
        setFetchState(entryID, AudioDataFetchState.DOWNLOADING);
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(AudioDataDownloadHandler::onDownloading);
            }
        }
    }

    private void onDownloadProgressEvent(EntryID entryID, long i, long max) {
        setFetchProgress(entryID, i, max);
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onProgress(i, max));
            }
        }
    }

    private synchronized void onDownloadSuccessEvent(EntryID entryID) {
        setFetchState(entryID, AudioDataFetchState.SUCCESS);
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
        setFetchState(entryID, AudioDataFetchState.FAILURE);
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onFailure(message));
            }
            handlerMap.remove(entryID);
        }
        release(entryID);
    }

    public LiveData<List<AudioDataFetchState>> getFetchState() {
        return fetchState;
    }

    public class AudioDataFetchState {
        public static final String IDLE = "idle";
        public static final String DOWNLOADING = "downloading";
        public static final String SUCCESS = "success";
        public static final String FAILURE = "failure";
        public final EntryID entryID;
        private String state;
        private long bytesFetched;
        private long bytesTotal;

        AudioDataFetchState(EntryID entryID) {
            this.entryID = entryID;
            state =  IDLE;
            bytesFetched = 0L;
            bytesTotal = 0L;
        }

        public void setState(String state) {
            this.state = state;
        }

        public void setProgress(long bytesFetched, long bytesTotal) {
            this.bytesFetched = bytesFetched;
            this.bytesTotal = bytesTotal;
        }

        public String getProgress() {
            String fetched = String.format(Locale.getDefault(), "%.1f", bytesFetched / 1000_000d);
            String total = bytesTotal > 0 ?
                    String.format(Locale.getDefault(), "%.1f", bytesTotal / 1000_000d) : "?";
            return fetched + "/" + total + "MB";
        }

        @NonNull
        @Override
        public String toString() {
            return "id: " + entryID
                    + " state: " + state +
                    " fetched: " + bytesFetched + "/" + bytesTotal;
        }

        public String getState() {
            return state;
        }
    }

    private void setFetchState(EntryID entryID, String state) {
        synchronized (fetchStateMap) {
            AudioDataFetchState audioDataFetchState = fetchStateMap.get(entryID);
            if (audioDataFetchState != null) {
                audioDataFetchState.setState(state);
            }
            updateFetchState();
        }
    }

    private void setFetchProgress(EntryID entryID, long bytesFetched, long bytesTotal) {
        synchronized (fetchStateMap) {
            AudioDataFetchState audioDataFetchState = fetchStateMap.get(entryID);
            if (audioDataFetchState != null) {
                audioDataFetchState.setProgress(bytesFetched, bytesTotal);
            }
            updateFetchState();
        }
    }

    private void updateFetchState() {
        fetchState.postValue(fetchStateMap.entrySet().stream()
                .map(Map.Entry::getValue).collect(Collectors.toList())
        );
    }
}
