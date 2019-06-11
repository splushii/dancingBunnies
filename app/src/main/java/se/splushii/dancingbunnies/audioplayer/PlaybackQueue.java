package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
            int previousSize = queue.size();
            boolean changed = !queue.equals(playbackEntries);
            queue.clear();
            queue.addAll(playbackEntries);
            if (changed) {
                PlaybackQueue.this.onChanged(previousSize);
            }
        }
    };

    private void onChanged(int previousSize) {
        String statusTitle = PlaybackControllerStorage.getQueueName(queueID)
                + " changed (size " + previousSize + " -> " + queue.size() + "):";
        if (queueID != PlaybackControllerStorage.QUEUE_ID_HISTORY) {
            Log.d(LC, Util.getPlaybackEntriesChangedStatus(
                    statusTitle,
                    "\n",
                    "",
                    queue
            ));
        } else {
            Log.d(LC, statusTitle);
        }
        onQueueChanged.run();
    }

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
        Log.d(LC, Util.getPlaybackEntriesChangedStatus(
                "add(toPosition: " + toPosition
                        + ", entries.size: " + entries.size() + ") to \""
                        + PlaybackControllerStorage.getQueueName(queueID) + "\":",
                "\n+ ",
                "",
                entries
        ));
        if (entries.isEmpty()) {
            return Util.futureResult(null);
        }
        // Optimistic update of in-memory queue
        int previousSize = queue.size();
        int index = toPosition;
        for (PlaybackEntry entry: entries) {
            queue.add(index++, entry);
        }
        onChanged(previousSize);
        // Actual update of queue source data
        return storage.insert(queueID, toPosition, entries);
    }

    CompletableFuture<List<PlaybackEntry>> poll(int num) {
        Log.d(LC, "poll(" + num + ")");
        List<Integer> positionsToRemove = IntStream.range(0, num)
                .boxed().collect(Collectors.toList());
        return remove(positionsToRemove);
    }

    public CompletableFuture<List<PlaybackEntry>> remove(List<Integer> queuePositions) {
        String statusTitle = "remove(" + queuePositions.toString() + ") from \""
                + PlaybackControllerStorage.getQueueName(queueID) + "\" entries:";
        if (queuePositions.isEmpty()) {
            Log.d(LC, statusTitle);
            return Util.futureResult(null, Collections.emptyList());
        }
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
        Log.d(LC, Util.getPlaybackEntriesChangedStatus(
                statusTitle,
                "\n- ",
                "",
                entries
        ));
        // Optimistic update of in-memory queue
        int previousSize = queue.size();
        for (int i = queuePositions.size() - 1; i >= 0; i--) {
            int indexToRemove = queuePositions.get(i);
            if (indexToRemove < queue.size()) {
                queue.remove(indexToRemove);
            }
        }
        onChanged(previousSize);
        // Actual update of queue source data
        return storage.remove(queueID, positionsToRemove)
                .thenApply(v -> entries);
    }

    CompletableFuture<Void> removeEntries(List<PlaybackEntry> playbackEntries) {
        Log.d(LC, Util.getPlaybackEntriesChangedStatus(
                "remove(" + playbackEntries.size() + ") from \""
                        + PlaybackControllerStorage.getQueueName(queueID) + "\":",
                "\n- ",
                "",
                playbackEntries
        ));
        if (playbackEntries.isEmpty()) {
            return Util.futureResult(null);
        }
        // Optimistic update of in-memory queue
        int previousSize = queue.size();
        for (PlaybackEntry entry: playbackEntries) {
            queue.remove(entry);
        }
        onChanged(previousSize);
        // Actual update of queue source data
        return storage.removeEntries(queueID, playbackEntries);
    }

    CompletableFuture<Void> clear() {
        Log.d(LC, "clear \"" + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        // Optimistic update of in-memory queue
        int previousSize = queue.size();
        queue.clear();
        onChanged(previousSize);
        // Actual update of queue source data
        return storage.removeAll(queueID);
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
