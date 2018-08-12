package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.util.Util;

class PlaybackQueue {
    private static final String LC = Util.getLogContext(PlaybackQueue.class);
    private LinkedList<PlaybackEntry> queue;

    public Collection<? extends PlaybackEntry> poll(int num) {
        Log.d(LC, "poll(" + num + ")");
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        for (int i = 0; i < num; i++) {
            PlaybackEntry playbackEntry = queue.pollFirst();
            if (playbackEntry == null) {
                return playbackEntries;
            }
            playbackEntries.add(playbackEntry);
        }
        return playbackEntries;
    }

    public Collection<? extends PlaybackEntry> getEntries() {
        return new ArrayList<>(queue);
    }

    public void offer(List<PlaybackEntry> entries) {
        queue.addAll(0, entries);
    }

    enum QueueOp {
        NEXT,
        LAST
    }

    PlaybackQueue() {
        queue = new LinkedList<>();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public Collection<? extends PlaybackEntry> getEntries(int max) {
        return queue.stream().limit(max).collect(Collectors.toList());
    }

    public Collection<? extends PlaybackEntry> getEntries(int offset, int max) {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        int queueSize = queue.size();
        if (offset >= queueSize) {
            return playbackEntries;
        }
        int limit = offset + max < queueSize ? offset + max : queueSize;
        for (int i = offset; i < limit; i++) {
            playbackEntries.add(queue.get(i));
        }
        return playbackEntries;
    }

    int addToQueue(PlaybackEntry playbackEntry, QueueOp op) {
        int index;
        switch (op) {
            case NEXT:
                index = 0;
                if (queue.size() > 0) {
                    index = 1;
                }
                queue.add(index, playbackEntry);
                break;
            default:
            case LAST:
                queue.addLast(playbackEntry);
                index = queue.size() - 1;
                break;
        }
        Log.d(LC, queue.toString());
        Log.d(LC, "Added " + playbackEntry.meta.getDescription().getTitle() +
                (op == QueueOp.NEXT ? " next " : " last ") + "at index " + index + " in queue.");
        return index;
    }

    boolean removeFromQueue(int queuePosition) {
        if (queuePosition >= queue.size()) {
            return false;
        }
        queue.remove(queuePosition);
        return true;
    }

    PlaybackEntry current() {
        return queue.peekFirst();
    }

    void next() {
        queue.pollFirst();
    }

    PlaybackEntry skipTo(long queuePosition) {
        Log.d(LC, "skipTo id: " + queuePosition + " size: " + queue.size());
        int size = queue.size();
        PlaybackEntry playbackEntry = null;
        for (int i = 0; i < queuePosition && i < size; i++) {
            playbackEntry = queue.pollFirst();
        }
        return playbackEntry;
    }
}
