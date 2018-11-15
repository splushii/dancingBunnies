package se.splushii.dancingbunnies.audioplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.util.Util;

public class PlaybackQueue {
    private static final String LC = Util.getLogContext(PlaybackQueue.class);
    public enum QueueOp {
        NEXT,
        LAST
    }
    private LinkedList<PlaybackEntry> queue;

    Collection<? extends PlaybackEntry> poll(int num) {
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

    void addFirst(List<PlaybackEntry> entries) {
        queue.addAll(0, entries);
    }

    void add(List<PlaybackEntry> entries) {
        queue.addAll(entries);
    }

    void add(int offset, List<PlaybackEntry> entries) {
        queue.addAll(offset, entries);
    }

    PlaybackQueue() {
        queue = new LinkedList<>();
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

    PlaybackEntry remove(int queuePosition) {
        if (queuePosition >= queue.size()) {
            return null;
        }
        return queue.remove(queuePosition);
    }

    PlaybackEntry next() {
        return queue.pollFirst();
    }
}
