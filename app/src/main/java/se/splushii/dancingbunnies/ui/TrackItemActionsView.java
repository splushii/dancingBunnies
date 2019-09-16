package se.splushii.dancingbunnies.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Supplier;

import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class TrackItemActionsView extends LinearLayoutCompat {
    private static final String LC = Util.getLogContext(TrackItemActionsView.class);

    private static final int ACTION_MORE = View.generateViewId();
    public static final int ACTION_PLAY = View.generateViewId();
    public static final int ACTION_ADD_TO_QUEUE = View.generateViewId();
    public static final int ACTION_REMOVE_FROM_QUEUE = View.generateViewId();
    public static final int ACTION_SET_CURRENT_PLAYLIST = View.generateViewId();
    public static final int ACTION_ADD_TO_PLAYLIST = View.generateViewId();
    public static final int ACTION_REMOVE_FROM_PLAYLIST = View.generateViewId();
    public static final int ACTION_CACHE = View.generateViewId();
    public static final int ACTION_CACHE_DELETE = View.generateViewId();
    public static final int ACTION_REMOVE_FROM_HISTORY = View.generateViewId();
    public static final int ACTION_INFO = View.generateViewId();

    private AudioBrowserFragment audioBrowserFragment;

    private Supplier<EntryID> entryIDSupplier;
    private Supplier<PlaybackEntry> playbackEntrySupplier;
    private Supplier<PlaylistID> playlistIDSupplier;
    private Supplier<PlaylistEntry> playlistEntrySupplier;
    private Supplier<Long> playlistPositionSupplier;

    private int[] visibleActions = {};
    private int[] moreActions = {};
    private HashSet<Integer> disabled = new HashSet<>();

    private LinearLayout rootView;

    public TrackItemActionsView(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public TrackItemActionsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public TrackItemActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.track_item_actions_view_layout, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.TrackItemActionsView, defStyleAttr, defStyleRes);
        a.recycle();

        rootView = findViewById(R.id.item_action_root);
        setActions();
    }

    public void setActions(int[] visible, int[] more, int[] disabled) {
        this.visibleActions = visible;
        this.moreActions = more;
        this.disabled.clear();
        for (int d: disabled) {
            this.disabled.add(d);
        }
        rootView.removeAllViews();
        setActions();
    }

    private void setActions() {
        for (int i = 0; i < visibleActions.length; i++) {
            addAction(null,false, i, visibleActions[i]);
        }
        if (moreActions.length > 0) {
            addAction(null, false, visibleActions.length, ACTION_MORE);
        }
    }

    private void addAction(Menu menu, boolean more, int order, int action) {
        int stringResource;
        int iconResource;
        if (action == ACTION_ADD_TO_QUEUE) {
            stringResource = R.string.item_action_queue;
            iconResource = R.drawable.ic_queue_black_24dp;
        } else if (action == ACTION_ADD_TO_PLAYLIST) {
            stringResource = R.string.item_action_add_to_playlist;
            iconResource = R.drawable.ic_playlist_add_black_24dp;
        } else if (action == ACTION_PLAY) {
            stringResource = R.string.item_action_play;
            iconResource = R.drawable.ic_play_arrow_black_24dp;
        } else if (action == ACTION_CACHE) {
            stringResource = R.string.item_action_cache;
            iconResource = R.drawable.ic_offline_pin_black_24dp;
        } else if (action == ACTION_CACHE_DELETE) {
            stringResource = R.string.item_action_cache_delete;
            iconResource = R.drawable.ic_remove_circle_black_24dp;
        } else if (action == ACTION_REMOVE_FROM_QUEUE) {
            stringResource = R.string.item_action_queue_delete;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_REMOVE_FROM_HISTORY) {
            stringResource = R.string.item_action_history_delete;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_REMOVE_FROM_PLAYLIST) {
            stringResource = R.string.item_action_remove_from_playlist;
            iconResource = R.drawable.ic_delete_black_24dp;
        } else if (action == ACTION_SET_CURRENT_PLAYLIST) {
            stringResource = R.string.item_action_play_playlist;
            iconResource = R.drawable.ic_playlist_play_white_24dp;
        } else if (action == ACTION_INFO) {
            stringResource = R.string.item_action_info;
            iconResource = R.drawable.ic_info_black_24dp;
        } else if (action == ACTION_MORE) {
            stringResource = R.string.item_action_more;
            iconResource = R.drawable.ic_more_vert_black_24dp;
        } else {
            return;
        }
        if (menu != null) {
            menu.add(Menu.NONE, action, order, stringResource)
                    .setIcon(iconResource)
                    .setEnabled(!disabled.contains(action))
                    .setIconTintList(ContextCompat.getColorStateList(
                            getContext(),
                            more ? R.color.icon_on_white : R.color.icon_on_secondary
                    ));
        } else {
            ImageButton actionBtn = (ImageButton) LayoutInflater.from(getContext())
                    .inflate(R.layout.track_item_actions_item, rootView, false);
            actionBtn.setContentDescription(getResources().getText(stringResource));
            actionBtn.setEnabled(!disabled.contains(action));
            actionBtn.setImageResource(iconResource);
            actionBtn.setImageTintList(ContextCompat.getColorStateList(
                    getContext(),
                    more ? R.color.icon_on_white : R.color.icon_on_primary
            ));
            actionBtn.setOnClickListener(v -> onAction(v, action));
            rootView.addView(actionBtn);
        }
    }

    @SuppressWarnings("RestrictedAPI")
    private boolean onAction(View v, int action) {
        if (action == ACTION_MORE) {
            PopupMenu popup = new PopupMenu(getContext(), v);
            Menu menu = popup.getMenu();
            for (int i = 0; i < moreActions.length; i++) {
                addAction(menu, true, i, moreActions[i]);
            }
            // TODO: This is a restricted API
            // TODO: Use popup.setForceShowIcon(true) when API SDK 29 is live
            MenuPopupHelper menuPopupHelper = new MenuPopupHelper(
                    getContext(),
                    (MenuBuilder) popup.getMenu(),
                    v
            );
            menuPopupHelper.setForceShowIcon(true);
            popup.setOnMenuItemClickListener(popupItem -> onAction(
                    popupItem.getActionView(),
                    popupItem.getItemId()
            ));
            menuPopupHelper.show();
            return true;
        } else if (action == ACTION_PLAY) {
            audioBrowserFragment.play(entryIDSupplier.get());
        } else if (action == ACTION_ADD_TO_QUEUE) {
            audioBrowserFragment.queue(entryIDSupplier.get());
        } else if (action == ACTION_ADD_TO_PLAYLIST) {
            AddToPlaylistDialogFragment.showDialog(
                    audioBrowserFragment,
                    new ArrayList<>(Collections.singletonList(entryIDSupplier.get())),
                    null
            );
        } else if (action == ACTION_CACHE) {
            audioBrowserFragment.downloadAudioData(
                    Collections.singletonList(entryIDSupplier.get()),
                    null
            );
        } else if (action == ACTION_CACHE_DELETE) {
            MusicLibraryService.deleteAudioData(
                    audioBrowserFragment.requireContext(),
                    entryIDSupplier.get()
            );
        } else if (action == ACTION_REMOVE_FROM_QUEUE) {
                audioBrowserFragment.dequeue(playbackEntrySupplier.get());
        } else if (action == ACTION_REMOVE_FROM_HISTORY) {
            PlaybackControllerStorage.getInstance(getContext()).removeEntries(
                    PlaybackControllerStorage.QUEUE_ID_HISTORY,
                    Collections.singletonList(playbackEntrySupplier.get())
            );
        } else if (action == ACTION_REMOVE_FROM_PLAYLIST) {
            PlaylistStorage.getInstance(getContext()).removeFromPlaylist(
                    playlistIDSupplier.get(),
                    Collections.singletonList(playlistEntrySupplier.get())
            );
        } else if (action == ACTION_SET_CURRENT_PLAYLIST) {
            audioBrowserFragment.setCurrentPlaylist(
                    playlistIDSupplier.get(),
                    playlistPositionSupplier.get()
            );
        } else if (action == ACTION_INFO) {
            MetaStorage.getInstance(getContext()).getMetaOnce(entryIDSupplier.get())
                    .thenAccept(meta -> MetaDialogFragment.showMeta(audioBrowserFragment, meta));
        }
        animateShow(false);
        return true;
    }

    public void initialize() {
        setVisibility(View.INVISIBLE);
        setTranslationX(getTrans(false));
        setAlpha(0);
    }

    private float getTrans(boolean show) {
        return show ? ((View)getParent()).getWidth() - getWidth() :
                ((View)getParent()).getWidth() - ((float) getWidth() * 3 / 4);
    }

    public void animateShow(boolean show) {
        if (show) {
            initialize();
        }
        animate()
                .translationX(getTrans(show))
                .setDuration(200)
                .alpha(show ? 1 : 0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (show) {
                            setVisibility(VISIBLE);
                        }
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!show) {
                            setVisibility(View.INVISIBLE);
                        }
                    }
                })
                .start();
    }

    public void setEntryIDSupplier(Supplier<EntryID> entryIDSupplier) {
        this.entryIDSupplier = entryIDSupplier;
    }

    public void setPlaybackEntrySupplier(Supplier<PlaybackEntry> playbackEntrySupplier) {
        this.playbackEntrySupplier = playbackEntrySupplier;
    }

    public void setPlaylistIDSupplier(Supplier<PlaylistID> playlistIDSupplier) {
        this.playlistIDSupplier = playlistIDSupplier;
    }

    public void setPlaylistEntrySupplier(Supplier<PlaylistEntry> playlistEntrySupplier) {
        this.playlistEntrySupplier = playlistEntrySupplier;
    }

    public void setPlaylistPositionSupplier(Supplier<Long> playlistPositionSupplier) {
        this.playlistPositionSupplier = playlistPositionSupplier;
    }

    public void setAudioBrowserFragment(AudioBrowserFragment fragment) {
        audioBrowserFragment = fragment;
    }
}
