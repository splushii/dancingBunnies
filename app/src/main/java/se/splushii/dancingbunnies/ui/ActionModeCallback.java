package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class ActionModeCallback implements ActionMode.Callback {
    private static final String LC = Util.getLogContext(ActionModeCallback.class);

    private static final int ACTIONMODE_ACTION_MORE = View.generateViewId();
    public static final int ACTIONMODE_ACTION_PLAY = View.generateViewId();
    public static final int ACTIONMODE_ACTION_ADD_TO_QUEUE = View.generateViewId();
    public static final int ACTIONMODE_ACTION_REMOVE_FROM_QUEUE = View.generateViewId();
    public static final int ACTIONMODE_ACTION_ADD_TO_PLAYLIST = View.generateViewId();
    public static final int ACTIONMODE_ACTION_REMOVE_FROM_PLAYLIST = View.generateViewId();
    public static final int ACTIONMODE_ACTION_CACHE = View.generateViewId();
    public static final int ACTIONMODE_ACTION_CACHE_DELETE = View.generateViewId();
    public static final int ACTIONMODE_ACTION_HISTORY_DELETE = View.generateViewId();
    public static final int ACTIONMODE_ACTION_PLAYLIST_DELETE = View.generateViewId();

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
        Bundle getQueryBundle();
        PlaylistID getPlaylistID();
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
            addAction(menu, false, i, visibleActions[i]);
        }
        if (moreActions.length > 0) {
            addAction(menu, false, visibleActions.length, ACTIONMODE_ACTION_MORE);
        }
    }

    // Called when the action mode is created; startActionMode() was called
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        currentMode = mode;
        setActions(menu);
        return true;
    }

    private void addAction(Menu menu, boolean more, int order, int action) {
        int stringResource;
        int iconResource;
        if (action == ACTIONMODE_ACTION_ADD_TO_QUEUE) {
            stringResource = R.string.item_action_queue;
            iconResource = R.drawable.ic_queue_black_24dp;
        } else if (action == ACTIONMODE_ACTION_ADD_TO_PLAYLIST) {
            stringResource = R.string.item_action_add_to_playlist;
            iconResource = R.drawable.ic_playlist_add_black_24dp;
        } else if (action == ACTIONMODE_ACTION_PLAY) {
            stringResource = R.string.item_action_play;
            iconResource = R.drawable.ic_play_arrow_black_24dp;
        } else if (action == ACTIONMODE_ACTION_CACHE) {
            stringResource = R.string.item_action_cache;
            iconResource = R.drawable.ic_offline_pin_black_24dp;
        } else if (action == ACTIONMODE_ACTION_CACHE_DELETE) {
            stringResource = R.string.item_action_cache_delete;
            iconResource = R.drawable.ic_remove_circle_black_24dp;
        } else if (action == ACTIONMODE_ACTION_REMOVE_FROM_QUEUE) {
            stringResource = R.string.item_action_queue_delete;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTIONMODE_ACTION_HISTORY_DELETE) {
            stringResource = R.string.item_action_history_delete;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTIONMODE_ACTION_REMOVE_FROM_PLAYLIST) {
            stringResource = R.string.item_action_remove_from_playlist;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTIONMODE_ACTION_PLAYLIST_DELETE) {
            stringResource = R.string.item_action_delete_playlist;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTIONMODE_ACTION_MORE) {
            stringResource = R.string.item_action_more;
            iconResource = R.drawable.ic_more_vert_black_24dp;
        } else {
            return;
        }
        menu.add(Menu.NONE, action, order, stringResource)
                .setIcon(iconResource)
                .setEnabled(!disabled.contains(action))
                .setIconTintList(ContextCompat.getColorStateList(
                        audioBrowserFragment.requireContext(),
                        more ? R.color.icon_on_white : R.color.icon_on_primary
                ));
    }

    // Called each time the action mode is shown. Always called after onCreateActionMode, but
    // may be called multiple times if the mode is invalidated.
    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false; // Return false if nothing is done
    }

    // Called when the user selects a contextual menu item
    @Override
    @SuppressWarnings("RestrictedAPI")
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == ACTIONMODE_ACTION_MORE) {
            View anchor = audioBrowserFragment.requireActivity().findViewById(ACTIONMODE_ACTION_MORE);
            PopupMenu popup = new PopupMenu(audioBrowserFragment.requireContext(), anchor);
            Menu menu = popup.getMenu();
            for (int i = 0; i < moreActions.length; i++) {
                addAction(menu, true, i, moreActions[i]);
            }
            // TODO: This is a restricted API
            // TODO: Use popup.setForceShowIcon(true) when API SDK 29 is live
            MenuPopupHelper menuPopupHelper = new MenuPopupHelper(
                    audioBrowserFragment.requireContext(),
                    (MenuBuilder) popup.getMenu(),
                    anchor
            );
            menuPopupHelper.setForceShowIcon(true);
            popup.setOnMenuItemClickListener(popupItem -> onAction(mode, popupItem.getItemId()));
            menuPopupHelper.show();
            return true;
        }
        return onAction(mode, item.getItemId());
    }

    private boolean onAction(ActionMode mode, int itemId) {
        if (itemId == ACTIONMODE_ACTION_PLAY) {
            audioBrowserFragment.play(callback.getEntryIDSelection(), callback.getQueryBundle());
        } else if (itemId == ACTIONMODE_ACTION_ADD_TO_QUEUE) {
            audioBrowserFragment.queue(callback.getEntryIDSelection(), callback.getQueryBundle());
        } else if (itemId == ACTIONMODE_ACTION_ADD_TO_PLAYLIST) {
            AddToPlaylistDialogFragment.showDialog(
                    audioBrowserFragment,
                    new ArrayList<>(callback.getEntryIDSelection()),
                    callback.getQueryBundle()
            );
        } else if (itemId == ACTIONMODE_ACTION_CACHE) {
            audioBrowserFragment.downloadAudioData(
                    callback.getEntryIDSelection(),
                    callback.getQueryBundle()
            );
        } else if (itemId == ACTIONMODE_ACTION_CACHE_DELETE) {
            MusicLibraryService.deleteAudioData(
                    audioBrowserFragment.requireContext(),
                    callback.getEntryIDSelection()
            );
        } else if (itemId == ACTIONMODE_ACTION_REMOVE_FROM_QUEUE) {
            audioBrowserFragment.dequeue(callback.getPlaybackEntrySelection());
        } else if (itemId == ACTIONMODE_ACTION_HISTORY_DELETE) {
            PlaybackControllerStorage.getInstance(audioBrowserFragment.requireContext())
                    .removeEntries(
                            PlaybackControllerStorage.QUEUE_ID_HISTORY,
                            callback.getPlaybackEntrySelection()
                    );
        } else if (itemId == ACTIONMODE_ACTION_REMOVE_FROM_PLAYLIST) {
            PlaylistStorage.getInstance(audioBrowserFragment.requireContext())
                    .removeFromPlaylist(
                            callback.getPlaylistID(),
                            callback.getPlaylistEntrySelection()
                    );
        } else if (itemId == ACTIONMODE_ACTION_PLAYLIST_DELETE) {
            PlaylistStorage.getInstance(audioBrowserFragment.requireContext())
                    .deletePlaylists(callback.getPlaylistSelection());
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
