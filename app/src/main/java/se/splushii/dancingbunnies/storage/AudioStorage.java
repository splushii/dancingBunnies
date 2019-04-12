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
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.db.CacheDao;
import se.splushii.dancingbunnies.storage.db.CacheEntry;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.util.Util;

public class AudioStorage {
    private static final String LC = Util.getLogContext(AudioStorage.class);
    private static volatile AudioStorage instance;
    private final HashMap<EntryID, AudioDataSource> audioMap;
    private final HashMap<EntryID, List<AudioDataDownloadHandler>> handlerMap;
    private final MutableLiveData<List<AudioDataFetchState>> fetchState;
    private final HashMap<EntryID, AudioDataFetchState> fetchStateMap;
    private final CacheDao cacheModel;

    public static synchronized AudioStorage getInstance(Context context) {
        if (instance == null) {
            instance = new AudioStorage(context);
        }
        return instance;
    }

    private AudioStorage(Context context) {
        audioMap = new HashMap<>();
        handlerMap = new HashMap<>();
        fetchStateMap = new HashMap<>();
        fetchState = new MutableLiveData<>();
        cacheModel = DB.getDB(context).cacheModel();
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
        AudioDataSource audioDataSource = audioMap.get(entryID);
        if (audioDataSource == null) {
            handler.onFailure("EntryID to fetch not found in AudioStorage");
            return;
        }
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
                public void onDownloadFinished() {
                    cacheModel.insert(CacheEntry.from(entryID));
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

    public LiveData<List<EntryID>> getCachedEntries() {
        return Transformations.map(cacheModel.getAll(), roomCacheEntryList ->
                roomCacheEntryList.stream().map(roomCacheEntry -> new EntryID(
                        roomCacheEntry.api,
                        roomCacheEntry.id,
                        Meta.FIELD_SPECIAL_MEDIA_ID
                )).collect(Collectors.toList()));
    }

    public class AudioDataFetchState {
        static final String IDLE = "idle";
        static final String DOWNLOADING = "downloading";
        static final String SUCCESS = "success";
        static final String FAILURE = "failure";
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

        void setProgress(long bytesFetched, long bytesTotal) {
            this.bytesFetched = bytesFetched;
            this.bytesTotal = bytesTotal;
        }

        String getProgress() {
            String fetched = String.format(Locale.getDefault(), "%.1f", bytesFetched / 1000_000d);
            String total = bytesTotal > 0 ?
                    String.format(Locale.getDefault(), "%.1f", bytesTotal / 1000_000d) : "?";
            return fetched + "/" + total + "MB";
        }

        public String getStatusMsg() {
            String msg;
            switch (getState()) {
                default:
                case AudioStorage.AudioDataFetchState.IDLE:
                case AudioStorage.AudioDataFetchState.SUCCESS:
                    msg = "";
                    break;
                case AudioStorage.AudioDataFetchState.DOWNLOADING:
                    msg = getProgress();
                    break;
                case AudioStorage.AudioDataFetchState.FAILURE:
                    msg = "dl failed";
                    break;
            }
            return msg;
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
        synchronized (fetchStateMap) {
            fetchState.postValue(fetchStateMap.entrySet().stream()
                    .map(Map.Entry::getValue).collect(Collectors.toList())
            );
        }
    }
}
