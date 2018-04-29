package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.util.Util;

class PlayQueue {
    private static final String LC = Util.getLogContext(PlayQueue.class);
    private final MusicLibrary musicLibrary;
    private HashMap<EntryID, QueueItem> itemMap;
    private LinkedList<QueueItem> queue;
    private LinkedList<EntryID> entryQueue;
    private int currentPos;

    enum QueueOp {
        CURRENT,
        NEXT,
        LAST
    }

    PlayQueue(MusicLibrary musicLibrary) {
        this.musicLibrary = musicLibrary;
        itemMap = new HashMap<>();
        entryQueue = new LinkedList<>();
        queue = new LinkedList<>();
        currentPos = 0;
    }

    LinkedList<QueueItem> addToQueue(EntryID entryID, QueueOp op) {
        int pos;
        switch (op) {
            case CURRENT:
                pos = currentPos;
                break;
            case NEXT:
                pos = currentPos + 1;
                break;
            default:
            case LAST:
                pos = entryQueue.size();
                break;
        }
        MediaMetadataCompat meta = musicLibrary.getSongMetaData(entryID);
        MediaDescriptionCompat description = Meta.meta2desc(meta);
        QueueItem queueItem = new QueueItem(description, entryID.hashCode());
        itemMap.put(entryID, queueItem);
        queue.add(pos, queueItem);
        entryQueue.add(pos, entryID);
        Log.d(LC, queue.toString());
        Log.d(LC, "Added " + musicLibrary
                .getSongMetaData(entryID)
                .getDescription()
                .getTitle() + " to queue.");
        return queue;
    }

    LinkedList<QueueItem> removeFromQueue(EntryID entryID) {
        QueueItem queueItem = itemMap.remove(entryID);
        if (queueItem == null) {
            Log.w(LC, "Tried to remove queue item not in play queue.");
        }
        queue.remove(queueItem);
        entryQueue.remove(entryID);
        return queue;
    }

    EntryID current() {
        if (currentPos < 0 || currentPos >= entryQueue.size()) {
            return null;
        }
        return entryQueue.get(currentPos);
    }

    EntryID next() {
        int maxId = entryQueue.size() - 1;
        if (currentPos <= maxId) {
            currentPos++;
        }
        if (currentPos > maxId) {
            return null;
        }
        return entryQueue.get(currentPos);
    }

    EntryID skipTo(long queueItemId) {
        Log.d(LC, "id: " + queueItemId + " size: " + entryQueue.size());
        int qSize = entryQueue.size();
        if (qSize == 0 || queueItemId < 0 || queueItemId >= qSize) {
            return null;
        }
        currentPos = (int) queueItemId;
        return entryQueue.get(currentPos);
    }

    EntryID previous() {
        if (currentPos >= 0) {
            currentPos--;
        }
        if (entryQueue.size() == 0 || currentPos < 0) {
            return null;
        }
        return entryQueue.get(currentPos);
    }
}
