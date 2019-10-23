package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.backend.AudioDataHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.WaveformDao;
import se.splushii.dancingbunnies.storage.db.WaveformEntry;
import se.splushii.dancingbunnies.util.Util;

public class AudioStorage {
    private static final String LC = Util.getLogContext(AudioStorage.class);

    public static final int DOWNLOAD_PRIO_TOP = 0;
    public static final int DOWNLOAD_PRIO_MEDIUM = 500;
    public static final int DOWNLOAD_PRIO_LOW = 1000;

    private static volatile AudioStorage instance;
    private final HashMap<EntryID, AudioDataSource> audioMap;
    private final HashMap<EntryID, List<AudioDataHandler>> handlerMap;
    private final MutableLiveData<HashMap<EntryID,AudioDataFetchState>> fetchStateMapLiveData;
    private final HashMap<EntryID, AudioDataFetchState> fetchStateMap;
    private final WaveformDao waveformModel;

    private HashMap<EntryID, Integer> downloadPrioMap;
    private final List<AudioDataSource> downloadQueue;
    private AudioDataSource currentAudioDataSource;
    private Integer currentDownloadPrio;

    private ExecutorService samplerExecutor = Executors.newSingleThreadExecutor();

    private List<Consumer<EntryID>> onDeleteListeners;

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
        fetchStateMapLiveData = new MutableLiveData<>();
        waveformModel = DB.getDB(context).waveformModel();
        downloadPrioMap = new HashMap<>();
        downloadQueue = new ArrayList<>();
        onDeleteListeners = new ArrayList<>();
    }

    public static File getCacheFile(Context context, EntryID entryID) {
        return new File(context.getFilesDir()
                + File.separator + entryID.src
                + File.separator + entryID.id
        );
    }

    public synchronized AudioDataSource get(EntryID entryID) {
        return audioMap.get(entryID);
    }

    public synchronized AudioDataSource put(EntryID entryID, AudioDataSource audioDataSource) {
        synchronized (fetchStateMap) {
            fetchStateMap.put(entryID, new AudioDataFetchState(entryID));
        }
        return audioMap.put(entryID, audioDataSource);
    }

    private synchronized void release(EntryID entryID) {
        synchronized (fetchStateMap) {
            fetchStateMap.remove(entryID);
        }
        audioMap.remove(entryID);
        Log.d(LC, audioMap.keySet().size() + " AudioDataSource entries in memory. "
                + "Released entryID: " + entryID);
        triggerDownloadQueue();
    }

    public void fetch(Context context, EntryID entryID, int priority, AudioDataHandler handler) {
        AudioDataSource audioDataSource = get(entryID);
        if (audioDataSource == null) {
            audioDataSource = APIClient.getAudioDataSource(context, entryID);
            if (audioDataSource == null) {
                if (handler != null) {
                    handler.onFailure("Could not get AudioDataSource for song with src: "
                            + entryID.src + ", id: " + entryID.id
                            + ". Could not get audio data from api client.");
                }
                return;
            }
            put(entryID, audioDataSource);
        }
        if (audioDataSource.isDataReady()) {
            if (handler != null) {
                handler.onSuccess(audioDataSource);
            }
            return;
        }
        if (handler != null) {
            synchronized (handlerMap) {
                List<AudioDataHandler> handlers = handlerMap.computeIfAbsent(entryID, k -> new LinkedList<>());
                handlers.add(handler);
            }
        }
        addToDownloadQueue(audioDataSource, priority);
        triggerDownloadQueue();
    }

    private void triggerDownloadQueue() {
        synchronized (downloadQueue) {
            if (currentAudioDataSource != null) {
                Log.d(LC, "triggerDownloadQuue: already downloading");
                return;
            }
            if (downloadQueue.isEmpty()) {
                Log.d(LC, "triggerDownloadQuue: queue empty");
                return;
            }
            AudioDataSource audioDataSource = downloadQueue.remove(0);
            EntryID entryID = audioDataSource.entryID;
            currentAudioDataSource = audioDataSource;
            currentDownloadPrio = downloadPrioMap.get(audioDataSource.entryID);
            // TODO: Change to audioDataSource.buffer, and use a callback to play when buffered enough
            audioDataSource.fetch(new AudioDataSource.FetchDataHandler() {
                @Override
                public void onDownloading() {
                    onDownloadStartEvent(entryID);
                }

                @Override
                public void onDownloadProgress(long i, long max) {
                    onDownloadProgressEvent(entryID, i, max);
                }

                @Override
                public void onDownloadFinished() {
                    onDownloadSuccessEvent(entryID);
                    samplerExecutor.submit(() -> {
                        if (!audioDataSource.fetchSamples()) {
                            Log.e(LC, "Could not sample entry: " + entryID);
                        }
                    });
                }

                @Override
                public void onDownloadFailed(String err) {
                    Log.e(LC, "onDownloadFailed: " + entryID);
                    onDownloadFailureEvent(entryID, err);
                }

                @Override
                public void onFailure(String message) {
                    onFailureEvent(entryID, message);
                }

                @Override
                public void onSuccess() {
                    onSuccessEvent(entryID);
                }
            });
        }
    }

    private void addToDownloadQueue(AudioDataSource audioDataSource, int priority) {
        EntryID entryID = audioDataSource.entryID;
        synchronized (downloadQueue) {
            if (currentAudioDataSource != null) {
                if (currentAudioDataSource.entryID.equals(entryID)) {
                    return;
                }
                if (priority == DOWNLOAD_PRIO_TOP || priority < currentDownloadPrio) {
                    // Cancel current item if new item is higher prio
                    currentAudioDataSource.close();
                    downloadPrioMap.put(currentAudioDataSource.entryID, currentDownloadPrio);
                    downloadQueue.add(0, currentAudioDataSource);
                    currentAudioDataSource = null;
                    currentDownloadPrio = DOWNLOAD_PRIO_LOW;
                }
            }
            for (AudioDataSource otherAudioDataSource: downloadQueue) {
                if (entryID.equals(otherAudioDataSource.entryID)) {
                    if (priority > downloadPrioMap.getOrDefault(otherAudioDataSource.entryID, DOWNLOAD_PRIO_LOW)) {
                        // Another item trumps this item
                        return;
                    }
                    // This item trumps another item
                    downloadQueue.remove(otherAudioDataSource);
                    break;
                }
            }
            if (priority == DOWNLOAD_PRIO_TOP) {
                // Always put top prio first (even if there's other top prio items in queue)
                downloadPrioMap.put(entryID, priority);
                downloadQueue.add(0, audioDataSource);
                return;
            }
            // Else put it last in its prio class
            for (int i = 0; i < downloadQueue.size(); i++) {
                AudioDataSource otherAudioDataSource = downloadQueue.get(i);
                if (priority < downloadPrioMap.getOrDefault(
                        otherAudioDataSource.entryID,
                        DOWNLOAD_PRIO_LOW
                )) {
                    downloadPrioMap.put(entryID, priority);
                    downloadQueue.add(i, audioDataSource);
                    return;
                }
            }
            downloadPrioMap.put(entryID, priority);
            downloadQueue.add(audioDataSource);
        }
    }

    private void downloadEnded() {
        synchronized (downloadQueue) {
            currentAudioDataSource = null;
            currentDownloadPrio = DOWNLOAD_PRIO_LOW;
        }
    }

    private void onDownloadStartEvent(EntryID entryID) {
        setFetchState(entryID, AudioDataFetchState.DOWNLOADING);
        synchronized (handlerMap) {
            List<AudioDataHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(AudioDataHandler::onDownloading);
            }
        }
    }

    private void onDownloadProgressEvent(EntryID entryID, long i, long max) {
        setFetchProgress(entryID, i, max);
    }

    private synchronized void onDownloadSuccessEvent(EntryID entryID) {
        setFetchState(entryID, AudioDataFetchState.SUCCESS);
        synchronized (handlerMap) {
            List<AudioDataHandler> handlers = handlerMap.get(entryID);
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
        }
    }

    private synchronized void onDownloadFailureEvent(EntryID entryID, String message) {
        setFetchState(entryID, AudioDataFetchState.FAILURE);
        synchronized (handlerMap) {
            List<AudioDataHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onFailure(message));
            }
        }
    }

    private synchronized void onSuccessEvent(EntryID entryID) {
        synchronized (handlerMap) {
            handlerMap.remove(entryID);
        }
        downloadEnded();
        release(entryID);
    }

    private synchronized void onFailureEvent(EntryID entryID, String message) {
        synchronized (handlerMap) {
            List<AudioDataHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onFailure(message));
            }
            handlerMap.remove(entryID);
        }
        downloadEnded();
        release(entryID);
    }

    public LiveData<HashMap<EntryID,AudioDataFetchState>> getFetchState() {
        return fetchStateMapLiveData;
    }

    public LiveData<WaveformEntry> getWaveform(LiveData<EntryID> entryIDLiveData) {
        return Transformations.switchMap(entryIDLiveData, entryID -> {
            if (!Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
                return null;
            }
            return waveformModel.get(entryID.src, entryID.id);
        });
    }

    public WaveformEntry getWaveformSync(EntryID entryID) {
        if (entryID == null || !Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
            return null;
        }
        return waveformModel.getSync(entryID.src, entryID.id);
    }

    private CompletableFuture<Void> deleteWaveform(EntryID entryID) {
        if (entryID == null || !Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() ->
                waveformModel.delete(entryID.src, entryID.id)
        );
    }

    public void insertWaveform(WaveformEntry waveformEntry) {
        waveformModel.insert(waveformEntry);
    }

    private static void deleteCacheFile(Context context, EntryID entryID) {
        File cacheFile = AudioStorage.getCacheFile(context, entryID);
        if (!cacheFile.exists()) {
            Log.d(LC, "deleteCacheFile that not exists: " + entryID);
            return;
        }
        if (!cacheFile.isFile()) {
            Log.e(LC, "deleteCacheFile not a file: " + entryID);
            return;
        }
        if (!cacheFile.delete()) {
            Log.e(LC, "deleteCacheFile failed: " + entryID
                    + "\ncacheFile: " + cacheFile.getAbsolutePath());
        }
    }

    public CompletableFuture<Void> deleteAudioData(Context context, EntryID entryID) {
        AudioDataSource audioDataSource = get(entryID);
        if (audioDataSource != null) {
            audioDataSource.close();
        }
        synchronized (downloadQueue) {
            if (currentAudioDataSource != null && currentAudioDataSource.entryID.equals(entryID)) {
                currentAudioDataSource.close();
                currentAudioDataSource = null;
                currentDownloadPrio = DOWNLOAD_PRIO_LOW;
            }
        }
        release(entryID);
        AudioStorage.deleteCacheFile(context, entryID);
        return AudioStorage.getInstance(context).deleteWaveform(entryID)
                .thenCompose(aVoid -> MetaStorage.getInstance(context).deleteLocalMeta(
                        entryID,
                        Meta.FIELD_LOCAL_CACHED,
                        Meta.FIELD_LOCAL_CACHED_VALUE_YES
                ))
                .thenRunAsync(() -> onDeleteListeners.forEach(
                        onDeleteListener -> onDeleteListener.accept(entryID)
                ), Util.getMainThreadExecutor());
    }

    public void addDeleteListener(Consumer<EntryID> onDelete) {
        if (!onDeleteListeners.contains(onDelete)) {
            onDeleteListeners.add(onDelete);
        }
    }

    public void removeDeleteListener(Consumer<EntryID> onDelete) {
        onDeleteListeners.remove(onDelete);
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

        String getProgress(String fallbackFileSize) {
            String fetched = Meta.getDisplayValue(Meta.FIELD_FILE_SIZE, bytesFetched);
            String total = bytesTotal > 0 ?
                    Meta.getDisplayValue(Meta.FIELD_FILE_SIZE, bytesTotal) + "MB": fallbackFileSize;
            return fetched + "/" + total;
        }

        public String getStatusMsg(String fallbackFileSize) {
            String msg;
            switch (getState()) {
                default:
                case AudioStorage.AudioDataFetchState.IDLE:
                case AudioStorage.AudioDataFetchState.SUCCESS:
                    msg = "";
                    break;
                case AudioStorage.AudioDataFetchState.DOWNLOADING:
                    msg = getProgress(fallbackFileSize);
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
            fetchStateMapLiveData.postValue(fetchStateMap);
        }
    }
}
