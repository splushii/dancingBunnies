package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.util.HashMap;
import java.util.LinkedList;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

class PlayQueue {
    private static final String LC = Util.getLogContext(PlayQueue.class);
    private final SessionManager sessionManager;
    private final SessionManagerListener<Session> sessionManagerListener =
            new SessionManagerListenerImpl();
    private HashMap<EntryID, QueueItem> itemMap;
    private LinkedList<QueueItem> queue;
    private LinkedList<PlaybackEntry> entryQueue;
    private int currentPos;

    enum QueueOp {
        CURRENT,
        NEXT,
        LAST
    }

    PlayQueue(CastContext castContext) {
        itemMap = new HashMap<>();
        entryQueue = new LinkedList<>();
        queue = new LinkedList<>();
        currentPos = 0;
        sessionManager = castContext.getSessionManager();
        updateQueue();
    }

    protected void onCreate() {
        sessionManager.addSessionManagerListener(sessionManagerListener);
    }

    protected void onDestroy() {
        sessionManager.removeSessionManagerListener(sessionManagerListener);
    }

    private void updateQueue() {
        CastSession castSession = sessionManager.getCurrentCastSession();
        if (castSession == null || !castSession.isConnected()) {
            return;
        }
        RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        MediaQueue mediaQueue = remoteMediaClient.getMediaQueue();
        Log.d(LC, "cast queue size: " + mediaQueue.getItemCount());
    }

    public int size() {
        return entryQueue.size();
    }

    LinkedList<QueueItem> addToQueue(PlaybackEntry playbackEntry, QueueOp op) {
        int pos;
        switch (op) {
            case CURRENT:
                pos = currentPos;
                break;
            case NEXT:
                pos = currentPos + 1;
                if (pos > entryQueue.size()) {
                    pos = entryQueue.size();
                }
                break;
            default:
            case LAST:
                pos = entryQueue.size();
                break;
        }
        MediaDescriptionCompat description = Meta.meta2desc(playbackEntry.meta);
        QueueItem queueItem = new QueueItem(description, playbackEntry.entryID.hashCode());
        itemMap.put(playbackEntry.entryID, queueItem);
        queue.add(pos, queueItem);
        entryQueue.add(pos, playbackEntry);
        Log.d(LC, queue.toString());
        Log.d(LC, "Added " + playbackEntry.meta.getDescription().getTitle() + " to queue.");
        return queue;
    }

    LinkedList<QueueItem> removeFromQueue(PlaybackEntry playbackEntry) {
        QueueItem queueItem = itemMap.remove(playbackEntry.entryID);
        if (queueItem == null) {
            Log.w(LC, "Tried to remove queue item not in play queue: " + playbackEntry.toString());
            for(EntryID e: itemMap.keySet()) {
                Log.w(LC, e.toString());
            }
        }
        queue.remove(queueItem);
        entryQueue.remove(playbackEntry);
        return queue;
    }

    PlaybackEntry current() {
        if (currentPos < 0 || currentPos >= entryQueue.size()) {
            return null;
        }
        return entryQueue.get(currentPos);
    }

    PlaybackEntry next() {
        int maxId = entryQueue.size() - 1;
        if (currentPos <= maxId) {
            currentPos++;
        }
        if (currentPos > maxId) {
            return null;
        }
        return entryQueue.get(currentPos);
    }

    PlaybackEntry skipTo(long queueItemId) {
        Log.d(LC, "id: " + queueItemId + " size: " + entryQueue.size());
        int qSize = entryQueue.size();
        if (qSize == 0 || queueItemId < 0 || queueItemId >= qSize) {
            return null;
        }
        currentPos = (int) queueItemId;
        return entryQueue.get(currentPos);
    }

    PlaybackEntry previous() {
        if (currentPos >= 0) {
            currentPos--;
        }
        if (entryQueue.size() == 0 || currentPos < 0) {
            return null;
        }
        return entryQueue.get(currentPos);
    }

    private class SessionManagerListenerImpl implements SessionManagerListener<Session> {
        @Override
        public void onSessionStarting(Session session) {
            Log.d(LC, "CastSession starting");
        }

        @Override
        public void onSessionStarted(Session session, String s) {
            Log.d(LC, "CastSession started");
            onConnect((CastSession) session);
        }

        @Override
        public void onSessionStartFailed(Session session, int i) {
            Log.d(LC, "CastSession start failed");
            onDisconnect();
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(LC, "CastSession ending");
            onDisconnect();
        }

        @Override
        public void onSessionEnded(Session session, int i) {
            Log.d(LC, "CastSession ended");
        }

        @Override
        public void onSessionResuming(Session session, String s) {
            Log.d(LC, "CastSession resuming");
        }

        @Override
        public void onSessionResumed(Session session, boolean b) {
            Log.d(LC, "CastSession resumed");
            onConnect((CastSession) session);
        }

        @Override
        public void onSessionResumeFailed(Session session, int i) {
            Log.d(LC, "CastSession resume failed");
            onDisconnect();
        }

        @Override
        public void onSessionSuspended(Session session, int i) {
            Log.d(LC, "CastSession suspended");
        }

        private void onConnect(CastSession session) {
            // TODO
        }

        void onDisconnect() {
            // TODO
        }
    }
}
