package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
            int previousSize;
            int newSize;
            boolean changed;
            synchronized (queue) {
                previousSize = queue.size();
                changed = !queue.equals(playbackEntries);
                if (changed) {
                    queue.clear();
                    queue.addAll(playbackEntries);
                }
                newSize = queue.size();
            }
            if (changed) {
                PlaybackQueue.this.onChanged(previousSize, newSize);
            }
        }
    };

    private void onChanged(int previousSize, int newSize) {
        Log.d(LC, PlaybackControllerStorage.getQueueName(queueID)
                + " changed (size " + previousSize + " -> " + newSize + ")");
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
        Log.d(LC, "add(toPosition: " + toPosition + ", entries.size: " + entries.size() + ")"
                + " to \"" + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        if (entries.isEmpty()) {
            return Util.futureResult(null);
        }
        // Optimistic update of in-memory queue
        int previousSize;
        int newSize;
        synchronized (queue) {
            previousSize = queue.size();
            int index = toPosition;
            for (PlaybackEntry entry : entries) {
                queue.add(index++, entry);
            }
            newSize = queue.size();
        }
        onChanged(previousSize, newSize);
        // Actual update of queue source data
        return storage.insert(queueID, toPosition, entries);
    }

    CompletableFuture<Void> update(List<PlaybackEntry> movedPlaybackEntries) {
        Log.d(LC, "update(entries.size: " + movedPlaybackEntries.size() + ")"
                + " in \"" + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        if (movedPlaybackEntries.isEmpty()) {
            return Util.futureResult(null);
        }
        // Optimistic update of in-memory queue
        int previousSize;
        int newSize;
        synchronized (queue) {
            previousSize = queue.size();
            for (PlaybackEntry entry: movedPlaybackEntries) {
                int index = queue.indexOf(entry);
                if (index >= 0) {
                    queue.set(index, entry);
                }
            }
            newSize = queue.size();
        }
        onChanged(previousSize, newSize);
        // Actual update of queue source data
        return storage.update(queueID, movedPlaybackEntries);
    }

    CompletableFuture<Void> replaceWith(List<PlaybackEntry> entries) {
        Log.d(LC, "replaceWith(entries.size: " + entries.size() + ")"
                + " in \"" + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        // Optimistic update of in-memory queue
        int previousSize;
        int newSize;
        synchronized (queue) {
            previousSize = queue.size();
            queue.clear();
            queue.addAll(entries);
            newSize = queue.size();
        }
        onChanged(previousSize, newSize);
        return storage.replaceWith(queueID, entries);
    }

    CompletableFuture<List<PlaybackEntry>> poll(int num) {
        Log.d(LC, "poll(" + num + ")");
        List<PlaybackEntry> entries;
        synchronized (queue) {
            entries = queue.stream()
                    .limit(num)
                    .collect(Collectors.toList());
        }
        return removeEntries(entries)
                .thenApply(aVoid -> entries);
    }

    CompletableFuture<Void> removeEntries(List<PlaybackEntry> playbackEntries) {
        Log.d(LC, "remove(entries.size: " + playbackEntries.size() + ")"
                + "from \"" + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        if (playbackEntries.isEmpty()) {
            return Util.futureResult(null);
        }
        // Optimistic update of in-memory queue
        int previousSize;
        int newSize;
        synchronized (queue) {
            previousSize = queue.size();
            for (PlaybackEntry entry : playbackEntries) {
                queue.remove(entry);
            }
            newSize = queue.size();
        }
        onChanged(previousSize, newSize);
        // Actual update of queue source data
        return storage.removeEntries(queueID, playbackEntries);
    }

    CompletableFuture<Void> clear() {
        Log.d(LC, "clear \"" + PlaybackControllerStorage.getQueueName(queueID) + "\"");
        // Optimistic update of in-memory queue
        int previousSize;
        int newSize;
        synchronized (queue) {
            previousSize = queue.size();
            queue.clear();
            newSize = queue.size();
        }
        onChanged(previousSize, newSize);
        // Actual update of queue source data
        return storage.removeAll(queueID);
    }

    public List<PlaybackEntry> getEntries() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    public int size() {
        synchronized (queue) {
            return queue.size();
        }
    }

    PlaybackEntry get(int queuePosition) {
        synchronized (queue) {
            return queuePosition < queue.size() ? queue.get(queuePosition) : null;
        }
    }

    void onDestroy() {
        playbackEntriesLiveData.removeObserver(observer);
    }

    boolean isEmpty() {
        synchronized (queue) {
            return queue.size() < 1;
        }
    }
}
