package se.splushii.dancingbunnies.ui.nowplaying;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.SelectionTracker;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.util.Util;

class NowPlayingSelectionPredicate extends SelectionTracker.SelectionPredicate<Long> {
    private static final String LC = Util.getLogContext(NowPlayingSelectionPredicate.class);
    private final NowPlayingEntriesAdapter adapter;
    private NowPlayingKeyProvider keyProvider;
    private String currentSelectionType = null;

    NowPlayingSelectionPredicate(NowPlayingEntriesAdapter adapter,
                                 NowPlayingKeyProvider nowPlayingSelectionKeyProvider) {
        this.adapter = adapter;
        this.keyProvider = nowPlayingSelectionKeyProvider;
    }

    @Override
    public boolean canSetStateForKey(@NonNull Long key, boolean nextState) {
        PlaybackEntry playbackEntry = adapter.getPlaybackEntry(key);
        if (playbackEntry == null) {
            return false;
        }
        String playbackType = playbackEntry.playbackType;
        Log.d(LC, "currentType: " + currentSelectionType);
        if (currentSelectionType == null) {
            currentSelectionType = playbackType;
        }
        Log.d(LC, "canSetStateForKey: " + key + " " + playbackEntry.toString() + ", " + playbackType);
        return playbackType.equals(currentSelectionType);
    }

    @Override
    public boolean canSetStateAtPosition(int position, boolean nextState) {
        return canSetStateForKey(keyProvider.getKey(position), nextState);
    }

    @Override
    public boolean canSelectMultiple() {
        return true;
    }

    void reset() {
        currentSelectionType = null;
    }

    String getCurrentType() {
        return currentSelectionType;
    }
}