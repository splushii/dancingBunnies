package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
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
        List<PlaybackEntry> entries = queue.stream()
                .limit(num)
                .collect(Collectors.toList());
        return removeEntries(entries)
                .thenApply(aVoid -> entries);
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

    PlaybackEntry get(int queuePosition) {
        return queuePosition < queue.size() ? queue.get(queuePosition) : null;
    }

    void onDestroy() {
        playbackEntriesLiveData.removeObserver(observer);
    }
}
