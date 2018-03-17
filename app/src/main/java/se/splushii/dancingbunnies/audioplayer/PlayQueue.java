package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import java.util.LinkedList;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

class PlayQueue {
    private static final String LC = Util.getLogContext(PlayQueue.class);
    private LinkedList<QueueItem> mediaSessionQueue;
    private LinkedList<EntryID> entryQueue;
    private int currentId;
    PlayQueue() {
        mediaSessionQueue = new LinkedList<>();
        entryQueue = new LinkedList<>();
        currentId = 0;
    }

    void addToQueue(EntryID entryID, MediaDescriptionCompat description, MediaSessionCompat mediaSession) {
        Log.d(LC, "Added " + description.getTitle() + " to mediaSessionQueue.");
        long id = mediaSessionQueue.size();
        QueueItem queueItem = new QueueItem(description, id);
        mediaSessionQueue.add(queueItem);
        entryQueue.add(entryID);
        mediaSession.setQueue(mediaSessionQueue);
    }

    EntryID next() {
        if (currentId + 1 >= entryQueue.size()) {
            return null;
        }
        return entryQueue.get(currentId++);
    }

    EntryID skipTo(long queueItemId) {
        Log.d(LC, "id: " + queueItemId + " size: " + entryQueue.size());
        if (queueItemId >= entryQueue.size()) {
            return null;
        }
        currentId = (int) queueItemId;
        return entryQueue.get(currentId);
    }

    EntryID previous() {
        if (currentId - 1 < 0) {
            return null;
        }
        return entryQueue.get(currentId--);
    }
}
