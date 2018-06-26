package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

class PlaybackQueue {
    private static final String LC = Util.getLogContext(PlaybackQueue.class);
    private HashMap<EntryID, QueueItem> itemMap;
    private LinkedList<QueueItem> queue;
    private LinkedList<PlaybackEntry> entryQueue;

    public List<QueueItem> getQueue() {
        List<QueueItem> ret = new LinkedList<>();
        ret.addAll(queue);
        return ret;
    }

    public Collection<? extends PlaybackEntry> getEntries(int max) {
        return entryQueue.stream().limit(max).collect(Collectors.toList());
    }

    public Collection<? extends PlaybackEntry> getEntries(int offset, int max) {
        List<PlaybackEntry> playbackEntries = new LinkedList<>();
        int queueSize = entryQueue.size();
        if (offset >= queueSize) {
            return playbackEntries;
        }
        int limit = offset + max < queueSize ? offset + max : queueSize;
        for (int i = offset; i < limit; i++) {
            playbackEntries.add(entryQueue.get(i));
        }
        return playbackEntries;
    }

    enum QueueOp {
        NEXT,
        LAST
    }

    PlaybackQueue() {
        itemMap = new HashMap<>();
        entryQueue = new LinkedList<>();
        queue = new LinkedList<>();
    }

    public int size() {
        return entryQueue.size();
    }

    public boolean isEmpty() {
        return entryQueue.isEmpty();
    }

    int addToQueue(PlaybackEntry playbackEntry, QueueOp op) {
        MediaDescriptionCompat description = Meta.meta2desc(playbackEntry.meta);
        QueueItem queueItem = new QueueItem(description, playbackEntry.entryID.hashCode());
        itemMap.put(playbackEntry.entryID, queueItem);
        int index;
        switch (op) {
            case NEXT:
                queue.addFirst(queueItem);
                entryQueue.addFirst(playbackEntry);
                index = 0;
                break;
            default:
            case LAST:
                queue.addLast(queueItem);
                entryQueue.addLast(playbackEntry);
                index = queue.size() - 1;
                break;
        }
        Log.d(LC, queue.toString());
        Log.d(LC, "Added " + playbackEntry.meta.getDescription().getTitle() +
                (op == QueueOp.NEXT ? " first" : " last at index " + index) +
                " in queue.");
        return index;
    }

    void removeFromQueue(PlaybackEntry playbackEntry) {
        // TODO: Fixme. Not working when there is the same playbackEntry multiple times in queue
        QueueItem queueItem = itemMap.remove(playbackEntry.entryID);
        if (queueItem == null) {
            Log.w(LC, "Tried to remove queue item not in play queue: " + playbackEntry.toString());
            for(EntryID e: itemMap.keySet()) {
                Log.w(LC, e.toString());
            }
        }
        queue.remove(queueItem);
        entryQueue.remove(playbackEntry);
    }

    PlaybackEntry current() {
        return entryQueue.peekFirst();
    }

    void next() {
        queue.pollFirst();
        entryQueue.pollFirst();
    }

    PlaybackEntry skipTo(long queueItemId) {
        Log.d(LC, "skipTo id: " + queueItemId + " size: " + entryQueue.size());
        PlaybackEntry playbackEntry = null;
        for (int i = 0; i < queueItemId; i++) {
            playbackEntry = entryQueue.pollFirst();
            queue.pollFirst();
        }
        return playbackEntry;
    }
}
