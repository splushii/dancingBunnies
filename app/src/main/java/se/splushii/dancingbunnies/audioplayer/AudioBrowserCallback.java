package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.session.PlaybackStateCompat;

import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public interface AudioBrowserCallback {
    void onMediaBrowserConnected();
    void onPlaybackStateChanged(PlaybackStateCompat state);
    void onMetadataChanged(EntryID entryID);
    void onCurrentEntryChanged(PlaybackEntry entry);
    void onSessionDestroyed();
    void onSessionReady();
    void onQueueChanged(List<PlaybackEntry> queue);
    void onPlaylistSelectionChanged(EntryID playlistID, long pos);
    void onPlaylistPlaybackOrderModeChanged(int playbackOrderMode);
    void onRepeatModeChanged(boolean repeat);
}
