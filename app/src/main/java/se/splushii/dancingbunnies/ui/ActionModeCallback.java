package se.splushii.dancingbunnies.ui;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.HashSet;
import java.util.List;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_MORE;

public class ActionModeCallback implements ActionMode.Callback, MenuItem.OnMenuItemClickListener {
    private static final String LC = Util.getLogContext(ActionModeCallback.class);

    private final FragmentActivity activity;
    private final AudioBrowser remote;
    private final Callback callback;
    private ActionMode currentMode;
    private final Toolbar customToolbar;
    private int[] visibleActions = {};
    private int[] moreActions = {};
    private HashSet<Integer> disabled = new HashSet<>();
    private MenuItem moreActionsMenuItem;

    public ActionModeCallback(FragmentActivity activity, AudioBrowser remote, Callback callback) {
        this.activity = activity;
        this.remote = remote;
        this.callback = callback;
        this.customToolbar = null;
    }

    public ActionModeCallback(FragmentActivity activity,
                              AudioBrowser remote,
                              Toolbar customToolbar,
                              Callback callback) {
        this.activity = activity;
        this.remote = remote;
        this.callback = callback;
        this.customToolbar = customToolbar;
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
        Menu menu = null;
        if (customToolbar != null) {
            menu = customToolbar.getMenu();
        } else if (currentMode != null) {
            menu = currentMode.getMenu();
        }
        if (menu != null) {
            menu.clear();
            setActions(menu);
        }
    }

    private void setActions(Menu menu) {
        for (int i = 0; i < visibleActions.length; i++) {
            MenuItem menuItem = MenuActions.addAction(
                    activity,
                    menu,
                    i,
                    visibleActions[i],
                    R.color.icon_on_primary,
                    !disabled.contains(visibleActions[i])
            );
            if (customToolbar != null && menuItem != null) {
                // Add a click listener as onActionItemClicked will not be triggered
                // when using a custom toolbar
                menuItem.setOnMenuItemClickListener(this);
            }
        }
        if (moreActions.length > 0) {
            moreActionsMenuItem = MenuActions.addAction(
                    activity,
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
        if (customToolbar != null) {
            menu = customToolbar.getMenu();
        }
        menu.clear();
        setActions(menu);
        return true;
    }

    // Called each time the action mode is shown. Always called after onCreateActionMode, but
    // may be called multiple times if the mode is invalidated.
    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        currentMode = mode;
        return false; // Return false if nothing is done
    }

    // Called when the user selects a contextual menu item
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        currentMode = mode;
        return handleItemClick(item);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return handleItemClick(item);
    }

    private boolean handleItemClick(MenuItem item) {
        if (onMenuItemClicked(item.getItemId())) {
            return true;
        }
        return onAction(item.getItemId());
    }

    public boolean onMenuItemClicked(int itemId) {
        if (itemId == ACTION_MORE && moreActionsMenuItem != null) {
            View anchor = customToolbar != null ?
                    customToolbar.findViewById(ACTION_MORE) : activity.findViewById(ACTION_MORE);
            MenuActions.showPopupMenu(
                    activity,
                    anchor,
                    moreActions,
                    disabled,
                    popupItem -> onAction(popupItem.getItemId())
            );
            return true;
        }
        return false;
    }

    private boolean onAction(int itemId) {
        if (!MenuActions.doSelectionAction(
                itemId,
                remote,
                activity,
                activity.getSupportFragmentManager(),
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
        if (currentMode != null) {
            currentMode.finish();
        }
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
