package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

class PlaybackQueue {
    private static final String LC = Util.getLogContext(PlaybackQueue.class);
    private final LinkedList<PlaybackEntry> queue;
    private final PlaybackControllerStorage storage;
    private final int queueID;
    private final Runnable onQueueChanged;
    private final LiveData<List<PlaybackEntry>> playbackEntriesLiveData;
    private final Observer<List<PlaybackEntry>> observer = new Observer<List<PlaybackEntry>>() {
        @Override
        public void onChanged(List<PlaybackEntry> playbackEntries) {
            queue.clear();
            queue.addAll(playbackEntries);
            StringBuilder sb = new StringBuilder(
                    PlaybackControllerStorage.getQueueName(queueID)
                            +" changed (size " + queue.size() + ")\n"
            );
            for (PlaybackEntry e: queue) {
                sb.append(e.toString()).append("\n");
            }
            Log.d(LC, sb.toString());
            onQueueChanged.run();
        }
    };

    PlaybackQueue(int queueID,
                  PlaybackControllerStorage storage,
                  LiveData<List<PlaybackEntry>> playbackEntriesLiveData,
                  Runnable onQueueChanged) {
        this.playbackEntriesLiveData = playbackEntriesLiveData;
        this.storage = storage;
        this.queueID = queueID;
        this.onQueueChanged = onQueueChanged;
        queue = new LinkedList<>();
        playbackEntriesLiveData.observeForever(observer);
    }

    CompletableFuture<Void> add(int toPosition, List<PlaybackEntry> entries) {
        Log.d(LC, "add(" + toPosition + ", " + entries + ") to \""
                + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        return storage.insert(
                queueID,
                toPosition,
                entries.stream()
                        .map(pe -> pe.entryID)
                        .collect(Collectors.toList())
        );
    }

    CompletableFuture<List<PlaybackEntry>> poll(int num) {
        Log.d(LC, "poll(" + num + ")");
        List<Integer> positionsToRemove = IntStream.range(0, num)
                .boxed().collect(Collectors.toList());
        return remove(positionsToRemove);
    }

    public CompletableFuture<List<PlaybackEntry>> remove(List<Integer> queuePositions) {
        Log.d(LC, "remove(" + queuePositions.toString() + ") from \""
                + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        List<PlaybackEntry> entries = new ArrayList<>();
        List<Integer> positionsToRemove = new ArrayList<>();
        for (int pos: queuePositions) {
            if (pos >= 0 && pos < queue.size()) {
                PlaybackEntry entry = queue.get(pos);
                if (entry != null) {
                    entries.add(entry);
                    positionsToRemove.add(pos);
                }
            }
        }
        return storage.remove(queueID, positionsToRemove)
                .thenApply(v -> entries);
    }

    public Collection<? extends PlaybackEntry> getEntries() {
        return new ArrayList<>(queue);
    }

    public int size() {
        return queue.size();
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    Collection<? extends PlaybackEntry> getEntries(int max) {
        return queue.stream().limit(max).collect(Collectors.toList());
    }

    PlaybackEntry get(int queuePosition) {
        return queuePosition < queue.size() ? queue.get(queuePosition) : null;
    }

    void onDestroy() {
        playbackEntriesLiveData.removeObserver(observer);
    }
}
