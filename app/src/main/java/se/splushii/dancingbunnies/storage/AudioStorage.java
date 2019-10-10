package se.splushii.dancingbunnies.storage;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.backend.AudioDataDownloadHandler;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.WaveformDao;
import se.splushii.dancingbunnies.storage.db.WaveformEntry;
import se.splushii.dancingbunnies.util.Util;

public class AudioStorage {
    private static final String LC = Util.getLogContext(AudioStorage.class);
    private static volatile AudioStorage instance;
    private final HashMap<EntryID, AudioDataSource> audioMap;
    private final HashMap<EntryID, List<AudioDataDownloadHandler>> handlerMap;
    private final MutableLiveData<HashMap<EntryID,AudioDataFetchState>> fetchStateMapLiveData;
    private final HashMap<EntryID, AudioDataFetchState> fetchStateMap;
    private final WaveformDao waveformModel;

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
                }

                @Override
                public void onDownloadFailed(String err) {
                    Log.e(LC, "onDownloadFailed: " + entryID);
                    onDownloadFailureEvent(entryID, err);
                }

                @Override
                public void onSampling() {
                    Log.d(LC, "onSampling: " + entryID);
                }

                @Override
                public void onSamplingFinished(double[] peakSamplesPositive,
                                               double[] peakSamplesNegative,
                                               double[] rmsSamplesPositive,
                                               double[] rmsSamplesNegative) {
                    try {
                        JSONArray peakSamplesPositiveJSON = new JSONArray(peakSamplesPositive);
                        JSONArray peakSamplesNegativeJSON = new JSONArray(peakSamplesNegative);
                        JSONArray rmsSamplesPositiveJSON = new JSONArray(rmsSamplesPositive);
                        JSONArray rmsSamplesNegativeJSON = new JSONArray(rmsSamplesNegative);
                        waveformModel.insert(WaveformEntry.from(
                                entryID,
                                peakSamplesPositiveJSON.toString().getBytes(),
                                peakSamplesNegativeJSON.toString().getBytes(),
                                rmsSamplesPositiveJSON.toString().getBytes(),
                                rmsSamplesNegativeJSON.toString().getBytes()
                        ));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(LC, "Could not convert samples to JSON");
                    }
                }

                @Override
                public void onSamplingFailed(String err) {
                    Log.e(LC, "onSamplingFailed: " + entryID);
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
        }
    }

    private synchronized void onDownloadFailureEvent(EntryID entryID, String message) {
        setFetchState(entryID, AudioDataFetchState.FAILURE);
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onFailure(message));
            }
        }
    }

    private synchronized void onSuccessEvent(EntryID entryID) {
        synchronized (handlerMap) {
            handlerMap.remove(entryID);
        }
        release(entryID);
    }

    private synchronized void onFailureEvent(EntryID entryID, String message) {
        synchronized (handlerMap) {
            List<AudioDataDownloadHandler> handlers = handlerMap.get(entryID);
            if (handlers != null) {
                handlers.forEach(handler -> handler.onFailure(message));
            }
            handlerMap.remove(entryID);
        }
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

    public CompletableFuture<Void> deleteWaveform(EntryID entryID) {
        if (entryID == null || !Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() ->
                waveformModel.delete(entryID.src, entryID.id)
        );
    }

    public static void deleteCacheFile(Context context, EntryID entryID) {
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
