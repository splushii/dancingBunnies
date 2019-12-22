package se.splushii.dancingbunnies.ui;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.HashSet;
import java.util.List;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_MORE;

public class ActionModeCallback implements ActionMode.Callback {
    private static final String LC = Util.getLogContext(ActionModeCallback.class);

    private final AudioBrowserFragment audioBrowserFragment;
    private final Callback callback;
    private ActionMode currentMode;
    private int[] visibleActions = {};
    private int[] moreActions = {};
    private HashSet<Integer> disabled = new HashSet<>();

    public ActionModeCallback(AudioBrowserFragment audioBrowserFragment, Callback callback) {
        this.audioBrowserFragment = audioBrowserFragment;
        this.callback = callback;
    }

    public interface Callback {
        List<EntryID> getEntryIDSelection();
        List<PlaybackEntry> getPlaybackEntrySelection();
        List<PlaylistEntry> getPlaylistEntrySelection();
        List<Playlist> getPlaylistSelection();
        PlaylistID getPlaylistID();
        MusicLibraryQueryNode getQueryNode();
        List<MusicLibraryQueryNode> getQueryNodes();
        void onDestroyActionMode(ActionMode actionMode);
    }

    public void setActions(int[] visible, int[] more, int[] disabled) {
        this.visibleActions = visible;
        this.moreActions = more;
        this.disabled.clear();
        for (int d: disabled) {
            this.disabled.add(d);
        }
        if (currentMode != null) {
            Menu menu = currentMode.getMenu();
            menu.clear();
            setActions(menu);
        }
    }

    private void setActions(Menu menu) {
        for (int i = 0; i < visibleActions.length; i++) {
            MenuActions.addAction(
                    audioBrowserFragment.requireContext(),
                    menu,
                    i,
                    visibleActions[i],
                    R.color.icon_on_primary,
                    !disabled.contains(visibleActions[i])
            );
        }
        if (moreActions.length > 0) {
            MenuActions.addAction(
                    audioBrowserFragment.requireContext(),
                    menu,
                    visibleActions.length,
                    ACTION_MORE,
                    R.color.icon_on_primary,
                    !disabled.contains(ACTION_MORE)
            );
        }
    }

    // Called when the action mode is created; startActionMode() was called
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        currentMode = mode;
        setActions(menu);
        return true;
    }

    // Called each time the action mode is shown. Always called after onCreateActionMode, but
    // may be called multiple times if the mode is invalidated.
    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false; // Return false if nothing is done
    }

    // Called when the user selects a contextual menu item
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == ACTION_MORE) {
            View anchor = audioBrowserFragment.requireActivity().findViewById(ACTION_MORE);
            MenuActions.showPopupMenu(
                    audioBrowserFragment.requireContext(),
                    anchor,
                    moreActions,
                    disabled,
                    popupItem -> onAction(mode, popupItem.getItemId())
            );
            return true;
        }
        return onAction(mode, item.getItemId());
    }

    private boolean onAction(ActionMode mode, int itemId) {
        if (!MenuActions.doSelectionAction(
                itemId,
                audioBrowserFragment,
                callback::getEntryIDSelection,
                callback::getQueryNode,
                callback::getPlaybackEntrySelection,
                callback::getPlaylistEntrySelection,
                callback::getPlaylistID,
                callback::getPlaylistSelection,
                callback::getQueryNodes
        )) {
            return false;
        }
        mode.finish();
        return true;
    }

    // Called when the user exits the action mode
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        callback.onDestroyActionMode(mode);
        currentMode = null;
    }

    public void finish() {
        if (currentMode != null) {
            currentMode.finish();
        }
    }

    public boolean isActionMode() {
        return currentMode != null;
    }

    public ActionMode getActionMode() {
        return currentMode;
    }
}
