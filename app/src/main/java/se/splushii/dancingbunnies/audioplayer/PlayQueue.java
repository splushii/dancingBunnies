package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import java.util.LinkedList;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.util.Util;

class PlayQueue {
    private static final String LC = Util.getLogContext(PlayQueue.class);
    private final MusicLibrary musicLibrary;
    private LinkedList<EntryID> entryQueue;
    private int currentId;

    enum QueueOp {
        CURRENT,
        NEXT,
        LAST
    }

    PlayQueue(MusicLibrary musicLibrary) {
        this.musicLibrary = musicLibrary;
        entryQueue = new LinkedList<>();
        currentId = 0;
    }

    LinkedList<QueueItem> addToQueue(EntryID entryID, QueueOp op) {
        long id;
        switch (op) {
            case CURRENT:
                id = currentId;
                break;
            case NEXT:
                id = currentId + 1;
                break;
            default:
            case LAST:
                id = entryQueue.size();
                break;
        }
        entryQueue.add((int) id, entryID);
        Log.d(LC, entryQueue.toString());
        Log.d(LC, "Added " + musicLibrary
                .getSongMetaData(entryID)
                .getDescription()
                .getTitle() + " to queue.");
        LinkedList<QueueItem> mediaSessionQueue = new LinkedList<>();
        for (int i = 0; i < entryQueue.size(); i++) {
            MediaMetadataCompat meta = musicLibrary.getSongMetaData(entryQueue.get(i));
            MediaDescriptionCompat description = meta.getDescription();
            QueueItem queueItem = new QueueItem(description, i);
            mediaSessionQueue.add(queueItem);
        }
        return mediaSessionQueue;
    }

    EntryID current() {
        if (currentId < 0 || currentId >= entryQueue.size()) {
            return null;
        }
        return entryQueue.get(currentId);
    }

    EntryID next() {
        int maxId = entryQueue.size() - 1;
        if (currentId <= maxId) {
            currentId++;
        }
        if (currentId > maxId) {
            return null;
        }
        return entryQueue.get(currentId);
    }

    EntryID skipTo(long queueItemId) {
        Log.d(LC, "id: " + queueItemId + " size: " + entryQueue.size());
        int qSize = entryQueue.size();
        if (qSize == 0 || queueItemId < 0 || queueItemId >= qSize) {
            return null;
        }
        currentId = (int) queueItemId;
        return entryQueue.get(currentId);
    }

    EntryID previous() {
        if (currentId >= 0) {
            currentId--;
        }
        if (entryQueue.size() == 0 || currentId < 0) {
            return null;
        }
        return entryQueue.get(currentId);
    }
}
