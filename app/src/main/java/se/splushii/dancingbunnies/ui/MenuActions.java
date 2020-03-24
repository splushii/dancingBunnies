package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryTree;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.meta.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.meta.MetaTag;
import se.splushii.dancingbunnies.util.Util;

public class MenuActions {
    private static final String LC = Util.getLogContext(MenuActions.class);

    static final int ACTION_MORE = View.generateViewId();
    public static final int ACTION_PLAY_MULTIPLE = View.generateViewId();
    public static final int ACTION_PLAY_MULTIPLE_QUERIES = View.generateViewId();
    public static final int ACTION_ADD_MULTIPLE_TO_QUEUE = View.generateViewId();
    public static final int ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE = View.generateViewId();
    public static final int ACTION_REMOVE_MULTIPLE_FROM_QUEUE = View.generateViewId();
    public static final int ACTION_ADD_MULTIPLE_TO_PLAYLIST = View.generateViewId();
    public static final int ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST = View.generateViewId();
    public static final int ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST = View.generateViewId();
    public static final int ACTION_CACHE_MULTIPLE = View.generateViewId();
    public static final int ACTION_CACHE_MULTIPLE_QUERIES = View.generateViewId();
    public static final int ACTION_CACHE_DELETE_MULTIPLE = View.generateViewId();
    public static final int ACTION_CACHE_DELETE_MULTIPLE_QUERIES = View.generateViewId();
    public static final int ACTION_HISTORY_DELETE_MULTIPLE = View.generateViewId();
    public static final int ACTION_PLAYLIST_DELETE_MULTIPLE = View.generateViewId();
    public static final int ACTION_SHUFFLE_MULTIPLE_IN_QUEUE = View.generateViewId();
    public static final int ACTION_SHUFFLE_MULTIPLE_IN_PLAYLIST_PLAYBACK = View.generateViewId();
    public static final int ACTION_SORT_MULTIPLE_IN_QUEUE = View.generateViewId();
    public static final int ACTION_SORT_MULTIPLE_IN_PLAYLIST_PLAYBACK = View.generateViewId();

    public static final int ACTION_PLAY = View.generateViewId();
    public static final int ACTION_ADD_TO_QUEUE = View.generateViewId();
    public static final int ACTION_REMOVE_FROM_QUEUE = View.generateViewId();
    public static final int ACTION_SET_CURRENT_PLAYLIST = View.generateViewId();
    public static final int ACTION_ADD_TO_PLAYLIST = View.generateViewId();
    public static final int ACTION_REMOVE_FROM_PLAYLIST = View.generateViewId();
    public static final int ACTION_CACHE = View.generateViewId();
    public static final int ACTION_CACHE_DELETE = View.generateViewId();
    public static final int ACTION_REMOVE_FROM_HISTORY = View.generateViewId();
    public static final int ACTION_EDIT_META = View.generateViewId();
    public static final int ACTION_DELETE_META = View.generateViewId();
    public static final int ACTION_GOTO_META = View.generateViewId();
    public static final int ACTION_INFO = View.generateViewId();

    public static final int ACTION_CLEAR_QUEUE = View.generateViewId();
    public static final int ACTION_CLEAR_HISTORY = View.generateViewId();

    static MenuItem addAction(Context context,
                              Menu menu,
                              int order,
                              int action,
                              int iconColorResource,
                              boolean enabled) {
        int stringResource = getStringResource(action);
        int iconResource = getIconResource(action);
        if (stringResource < 0 || iconResource < 0) {
            return null;
        }
        return menu.add(Menu.NONE, action, order, stringResource)
                .setIcon(iconResource)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                .setEnabled(enabled)
                .setIconTintList(ContextCompat.getColorStateList(context, iconColorResource));
    }

    static void addAction(Context context,
                          ViewGroup rootView,
                          BiConsumer<View, Integer> onAction,
                          int action,
                          int iconColorResource,
                          boolean enabled) {
        int stringResource = getStringResource(action);
        int iconResource = getIconResource(action);
        if (stringResource < 0 || iconResource < 0) {
            return;
        }
        ImageButton actionBtn = (ImageButton) LayoutInflater.from(context)
                .inflate(R.layout.track_item_actions_item, rootView, false);
        actionBtn.setContentDescription(context.getResources().getText(stringResource));
        actionBtn.setEnabled(enabled);
        actionBtn.setImageResource(iconResource);
        actionBtn.setImageTintList(ContextCompat.getColorStateList(context, iconColorResource));
        actionBtn.setOnClickListener(v -> onAction.accept(v, action));
        rootView.addView(actionBtn);
    }

    public static void showPopupMenu(Context context,
                              View anchor,
                              int[] moreActions,
                              HashSet<Integer> disabled,
                              PopupMenu.OnMenuItemClickListener onMenuItemClickListener) {
        PopupMenu popup = new PopupMenu(context, anchor);
        Menu menu = popup.getMenu();
        for (int i = 0; i < moreActions.length; i++) {
            MenuActions.addAction(
                    context,
                    menu,
                    i,
                    moreActions[i],
                    R.color.icon_on_background,
                    !disabled.contains(moreActions[i])
            );
        }
        MenuPopupHelper menuPopupHelper = new MenuPopupHelper(
                context,
                (MenuBuilder) popup.getMenu(),
                anchor
        );
        menuPopupHelper.setForceShowIcon(true);
        popup.setOnMenuItemClickListener(onMenuItemClickListener);
        menuPopupHelper.show();
    }

    public static boolean doAction(int action,
                                   AudioBrowser remote,
                                   Context context,
                                   FragmentManager fragmentManager,
                                   Supplier<EntryID> entryIDSupplier,
                                   Supplier<PlaybackEntry> playbackEntrySupplier,
                                   Supplier<PlaylistEntry> playlistEntrySupplier,
                                   Supplier<PlaylistID> playlistIDSupplier,
                                   Supplier<Long> playlistPositionSupplier,
                                   MetaDialogFragment metaDialogFragment,
                                   Supplier<MetaTag> metaTagSupplier
                                   ) {
        if (action == ACTION_PLAY) {
            remote.play(entryIDSupplier.get());
        } else if (action == ACTION_ADD_TO_QUEUE) {
            remote.queue(entryIDSupplier.get());
        } else if (action == ACTION_ADD_TO_PLAYLIST) {
            AddToPlaylistDialogFragment.showDialog(
                    fragmentManager,
                    Collections.singletonList(MusicLibraryQueryNode.fromEntryID(entryIDSupplier.get()))
            );
        } else if (action == ACTION_CACHE) {
            remote.downloadAudioData(
                    context,
                    Collections.singletonList(MusicLibraryQueryNode.fromEntryID(entryIDSupplier.get())),
                    AudioStorage.DOWNLOAD_PRIO_LOW
            );
        } else if (action == ACTION_CACHE_DELETE) {
            MusicLibraryService.deleteAudioData(context, entryIDSupplier.get());
        } else if (action == ACTION_REMOVE_FROM_QUEUE) {
            remote.dequeue(playbackEntrySupplier.get());
        } else if (action == ACTION_REMOVE_FROM_HISTORY) {
            PlaybackControllerStorage.getInstance(context)
                    .removeEntries(
                            PlaybackControllerStorage.QUEUE_ID_HISTORY,
                            Collections.singletonList(playbackEntrySupplier.get())
                    );
        } else if (action == ACTION_REMOVE_FROM_PLAYLIST) {
            PlaylistStorage.getInstance(context)
                    .removeFromPlaylist(
                            playlistIDSupplier.get(),
                            Collections.singletonList(playlistEntrySupplier.get())
                    );
        } else if (action == ACTION_SET_CURRENT_PLAYLIST) {
            remote.setCurrentPlaylist(
                    playlistIDSupplier.get(),
                    playlistPositionSupplier.get()
            );
        } else if (action == ACTION_INFO) {
            MetaDialogFragment.showMeta(fragmentManager, entryIDSupplier.get());
        } else if (action == ACTION_GOTO_META) {
            Intent intent = new Intent(metaDialogFragment.requireContext(), MainActivity.class);
            MetaTag metaTag = metaTagSupplier.get();
            intent.putExtra(MainActivity.INTENT_EXTRA_FILTER_TYPE, metaTag.key);
            intent.putExtra(MainActivity.INTENT_EXTRA_FILTER_VALUE, metaTag.value);
            metaDialogFragment.requireActivity().startActivity(intent);
        } else if (action == ACTION_EDIT_META) {
        } else if (action == ACTION_DELETE_META) {
        } else if (action == ACTION_CLEAR_QUEUE) {
            remote.clearQueue();
        } else if (action == ACTION_CLEAR_HISTORY) {
            PlaybackControllerStorage.getInstance(context)
                    .removeAll(PlaybackControllerStorage.QUEUE_ID_HISTORY);
        } else {
            return false;
        }
        return true;
    }

    static boolean doSelectionAction(int action,
                                     AudioBrowser remote,
                                     Context context,
                                     FragmentManager fragmentManager,
                                     Supplier<List<EntryID>> entryIDSupplier,
                                     Supplier<MusicLibraryQueryNode> queryNodeSupplier,
                                     Supplier<List<PlaybackEntry>> playbackEntrySupplier,
                                     Supplier<List<PlaylistEntry>> playlistEntrySupplier,
                                     Supplier<PlaylistID> playlistIDSupplier,
                                     Supplier<List<Playlist>> playlistSupplier,
                                     Supplier<List<MusicLibraryQueryNode>> queryNodesSupplier
    ) {
        if (action == ACTION_PLAY_MULTIPLE) {
            remote.play(entryIDSupplier.get(), queryNodeSupplier.get());
        } else if (action == ACTION_PLAY_MULTIPLE_QUERIES) {
            remote.playQueries(queryNodesSupplier.get());
        } else if (action == ACTION_ADD_MULTIPLE_TO_QUEUE) {
            remote.queue(entryIDSupplier.get(), queryNodeSupplier.get());
        } else if (action == ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE) {
            remote.queueQueryBundles(queryNodesSupplier.get());
        } else if (action == ACTION_ADD_MULTIPLE_TO_PLAYLIST) {
            AddToPlaylistDialogFragment.showDialog(
                    fragmentManager,
                    getQueryNodeOrDefault(
                            queryNodeSupplier.get()).withEntryIDs(entryIDSupplier.get()
                    )
            );
        } else if (action == ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST) {
            AddToPlaylistDialogFragment.showDialog(
                    fragmentManager,
                    queryNodesSupplier.get()
            );
        } else if (action == ACTION_CACHE_MULTIPLE) {
            remote.downloadAudioData(
                    context,
                    new ArrayList<>(
                            getQueryNodeOrDefault(queryNodeSupplier.get())
                                    .withEntryIDs(entryIDSupplier.get())
                    ),
                    AudioStorage.DOWNLOAD_PRIO_LOW
            );
        } else if (action == ACTION_CACHE_MULTIPLE_QUERIES) {
            remote.downloadAudioData(
                    context,
                    new ArrayList<>(queryNodesSupplier.get()),
                    AudioStorage.DOWNLOAD_PRIO_LOW
            );
        } else if (action == ACTION_CACHE_DELETE_MULTIPLE) {
            MusicLibraryService.deleteAudioData(
                    context,
                    new ArrayList<>(
                            getQueryNodeOrDefault(queryNodeSupplier.get())
                                    .withEntryIDs(entryIDSupplier.get())
                    )
            );
        } else if (action == ACTION_CACHE_DELETE_MULTIPLE_QUERIES) {
            MusicLibraryService.deleteAudioData(context, new ArrayList<>(queryNodesSupplier.get())
            );
        } else if (action == ACTION_REMOVE_MULTIPLE_FROM_QUEUE) {
            remote.dequeue(playbackEntrySupplier.get());
        } else if (action == ACTION_HISTORY_DELETE_MULTIPLE) {
            PlaybackControllerStorage.getInstance(context)
                    .removeEntries(
                            PlaybackControllerStorage.QUEUE_ID_HISTORY,
                            playbackEntrySupplier.get()
                    );
        } else if (action == ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST) {
            PlaylistStorage.getInstance(context)
                    .removeFromPlaylist(
                            playlistIDSupplier.get(),
                            playlistEntrySupplier.get()
                    );
        } else if (action == ACTION_PLAYLIST_DELETE_MULTIPLE) {
            PlaylistStorage.getInstance(context)
                    .deletePlaylists(playlistSupplier.get());
        } else if (action == ACTION_SHUFFLE_MULTIPLE_IN_QUEUE) {
            remote.shuffleQueueItems(playbackEntrySupplier.get());
        } else if (action == ACTION_SHUFFLE_MULTIPLE_IN_PLAYLIST_PLAYBACK) {
            PlaybackControllerStorage.getInstance(context)
                    .shuffle(
                            PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK,
                            playbackEntrySupplier.get()
                    );
        } else if (action == ACTION_SORT_MULTIPLE_IN_QUEUE) {
            EntryTypeSelectionDialogFragment.showDialogToSort(
                    fragmentManager,
                    new ArrayList<>(playbackEntrySupplier.get()),
                    EntryTypeSelectionDialogFragment.SORT_QUEUE
            );
        } else if (action == ACTION_SORT_MULTIPLE_IN_PLAYLIST_PLAYBACK) {
            EntryTypeSelectionDialogFragment.showDialogToSort(
                    fragmentManager,
                    new ArrayList<>(playbackEntrySupplier.get()),
                    EntryTypeSelectionDialogFragment.SORT_PLAYLIST_PLAYBACK
            );
        } else {
            return false;
        }
        return true;
    }

    private static MusicLibraryQueryNode getQueryNodeOrDefault(MusicLibraryQueryNode queryNode) {
        return queryNode == null ?
                new MusicLibraryQueryTree(MusicLibraryQueryTree.Op.AND, false) : queryNode;
    }

    private static int getStringResource(int action) {
        if (action == ACTION_ADD_TO_QUEUE) {
            return R.string.item_action_queue;
        } else if (action == ACTION_ADD_TO_PLAYLIST) {
            return R.string.item_action_add_to_playlist;
        } else if (action == ACTION_PLAY) {
            return R.string.item_action_play;
        } else if (action == ACTION_CACHE) {
            return R.string.item_action_cache;
        } else if (action == ACTION_CACHE_DELETE) {
            return R.string.item_action_cache_delete;
        } else if (action == ACTION_REMOVE_FROM_QUEUE) {
            return R.string.item_action_queue_delete;
        } else if (action == ACTION_REMOVE_FROM_HISTORY) {
            return R.string.item_action_history_delete;
        } else if (action == ACTION_REMOVE_FROM_PLAYLIST) {
            return R.string.item_action_remove_from_playlist;
        } else if (action == ACTION_SET_CURRENT_PLAYLIST) {
            return R.string.item_action_play_playlist;
        } else if (action == ACTION_INFO) {
            return R.string.item_action_info;
        } else if (action == ACTION_MORE) {
            return R.string.item_action_more;
        } else if (action == ACTION_ADD_MULTIPLE_TO_QUEUE) {
            return R.string.item_action_queue;
        } else if (action == ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE) {
            return R.string.item_action_queue;
        } else if (action == ACTION_ADD_MULTIPLE_TO_PLAYLIST) {
            return R.string.item_action_add_to_playlist;
        } else if (action == ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST) {
            return R.string.item_action_add_to_playlist;
        } else if (action == ACTION_PLAY_MULTIPLE) {
            return R.string.item_action_play;
        } else if (action == ACTION_PLAY_MULTIPLE_QUERIES) {
            return R.string.item_action_play;
        } else if (action == ACTION_CACHE_MULTIPLE) {
            return R.string.item_action_cache;
        } else if (action == ACTION_CACHE_MULTIPLE_QUERIES) {
            return R.string.item_action_cache;
        } else if (action == ACTION_CACHE_DELETE_MULTIPLE) {
            return R.string.item_action_cache_delete;
        } else if (action == ACTION_CACHE_DELETE_MULTIPLE_QUERIES) {
            return R.string.item_action_cache_delete;
        } else if (action == ACTION_REMOVE_MULTIPLE_FROM_QUEUE) {
            return R.string.item_action_queue_delete;
        } else if (action == ACTION_HISTORY_DELETE_MULTIPLE) {
            return R.string.item_action_history_delete;
        } else if (action == ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST) {
            return R.string.item_action_remove_from_playlist;
        } else if (action == ACTION_PLAYLIST_DELETE_MULTIPLE) {
            return R.string.item_action_delete_playlist;
        } else if (action == ACTION_EDIT_META) {
            return R.string.item_action_edit_meta;
        } else if (action == ACTION_DELETE_META) {
            return R.string.item_action_delete_meta;
        } else if (action == ACTION_GOTO_META) {
            return R.string.item_action_goto_meta;
        } else if (action == ACTION_SHUFFLE_MULTIPLE_IN_QUEUE) {
            return R.string.item_action_shuffle_selection;
        } else if (action == ACTION_SHUFFLE_MULTIPLE_IN_PLAYLIST_PLAYBACK) {
            return R.string.item_action_shuffle_selection;
        } else if (action == ACTION_SORT_MULTIPLE_IN_QUEUE) {
            return R.string.item_action_sort_selection;
        } else if (action == ACTION_SORT_MULTIPLE_IN_PLAYLIST_PLAYBACK) {
            return R.string.item_action_sort_selection;
        } else if (action == ACTION_CLEAR_QUEUE) {
            return R.string.item_action_queue_clear;
        } else if (action == ACTION_CLEAR_HISTORY) {
            return R.string.item_action_history_clear;
        }
        return -1;
    }

    private static int getIconResource(int action) {
        if (action == ACTION_ADD_TO_QUEUE) {
            return R.drawable.ic_queue_black_24dp;
        } else if (action == ACTION_ADD_TO_PLAYLIST) {
            return R.drawable.ic_playlist_add_black_24dp;
        } else if (action == ACTION_PLAY) {
            return R.drawable.ic_play_arrow_black_24dp;
        } else if (action == ACTION_CACHE) {
            return R.drawable.ic_offline_pin_black_24dp;
        } else if (action == ACTION_CACHE_DELETE) {
            return R.drawable.ic_remove_circle_black_24dp;
        } else if (action == ACTION_REMOVE_FROM_QUEUE) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_REMOVE_FROM_HISTORY) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_REMOVE_FROM_PLAYLIST) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_SET_CURRENT_PLAYLIST) {
            return R.drawable.ic_playlist_play_white_24dp;
        } else if (action == ACTION_INFO) {
            return R.drawable.ic_info_black_24dp;
        } else if (action == ACTION_MORE) {
            return R.drawable.ic_more_vert_black_24dp;
        } else if (action == ACTION_ADD_MULTIPLE_TO_QUEUE) {
            return R.drawable.ic_queue_black_24dp;
        } else if (action == ACTION_ADD_MULTIPLE_QUERIES_TO_QUEUE) {
            return R.drawable.ic_queue_black_24dp;
        } else if (action == ACTION_ADD_MULTIPLE_TO_PLAYLIST) {
            return R.drawable.ic_playlist_add_black_24dp;
        } else if (action == ACTION_ADD_MULTIPLE_QUERIES_TO_PLAYLIST) {
            return R.drawable.ic_playlist_add_black_24dp;
        } else if (action == ACTION_PLAY_MULTIPLE) {
            return R.drawable.ic_play_arrow_black_24dp;
        } else if (action == ACTION_PLAY_MULTIPLE_QUERIES) {
            return R.drawable.ic_play_arrow_black_24dp;
        } else if (action == ACTION_CACHE_MULTIPLE) {
            return R.drawable.ic_offline_pin_black_24dp;
        } else if (action == ACTION_CACHE_MULTIPLE_QUERIES) {
            return R.drawable.ic_offline_pin_black_24dp;
        } else if (action == ACTION_CACHE_DELETE_MULTIPLE) {
            return R.drawable.ic_remove_circle_black_24dp;
        } else if (action == ACTION_CACHE_DELETE_MULTIPLE_QUERIES) {
            return R.drawable.ic_remove_circle_black_24dp;
        } else if (action == ACTION_REMOVE_MULTIPLE_FROM_QUEUE) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_HISTORY_DELETE_MULTIPLE) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_REMOVE_MULTIPLE_FROM_PLAYLIST) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_PLAYLIST_DELETE_MULTIPLE) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_EDIT_META) {
            return R.drawable.ic_edit_black_24dp;
        } else if (action == ACTION_DELETE_META) {
            return R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_GOTO_META) {
            return R.drawable.ic_open_in_new_black_24dp;
        } else if (action == ACTION_SHUFFLE_MULTIPLE_IN_QUEUE) {
            return R.drawable.ic_shuffle_black_24dp;
        } else if (action == ACTION_SHUFFLE_MULTIPLE_IN_PLAYLIST_PLAYBACK) {
            return R.drawable.ic_shuffle_black_24dp;
        } else if (action == ACTION_SORT_MULTIPLE_IN_QUEUE) {
            return R.drawable.ic_sort_black_24dp;
        } else if (action == ACTION_SORT_MULTIPLE_IN_PLAYLIST_PLAYBACK) {
            return R.drawable.ic_sort_black_24dp;
        } else if (action == ACTION_CLEAR_QUEUE) {
            return 0;
        } else if (action == ACTION_CLEAR_HISTORY) {
            return 0;
        }
        return -1;
    }
}
