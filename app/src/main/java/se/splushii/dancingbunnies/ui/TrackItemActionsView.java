package se.splushii.dancingbunnies.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import java.util.function.Supplier;

import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class TrackItemActionsView extends LinearLayoutCompat implements PopupMenu.OnMenuItemClickListener {
    private static final String LC = Util.getLogContext(TrackItemActionsView.class);
    private View playAction;
    private View queueAction;
    private View removeAction;
    private View addToPlaylistAction;
    private View infoAction;
    private View playPlaylistAction;
    private View moreAction;
    private Supplier<EntryID> entryIDSupplier;

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

    @SuppressLint("RestrictedApi")
    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.track_item_actions_view_layout, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.TrackItemActionsView, defStyleAttr, defStyleRes);
        a.recycle();
        playAction = findViewById(R.id.item_action_play);
        queueAction = findViewById(R.id.item_action_queue);
        removeAction = findViewById(R.id.item_action_remove);
        addToPlaylistAction = findViewById(R.id.item_action_add_to_playlist);
        playPlaylistAction = findViewById(R.id.item_action_play_playlist);
        infoAction = findViewById(R.id.item_action_info);
        moreAction = findViewById(R.id.item_action_more);

        PopupMenu popup = new PopupMenu(getContext(), moreAction);
        popup.setOnMenuItemClickListener(this);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.track_item_actions_more_menu, popup.getMenu());
        // TODO: This is a restricted API
        // TODO: Use popup.setForceShowIcon(true) when API SDK 29 is live
        MenuPopupHelper menuPopupHelper = new MenuPopupHelper(
                getContext(),
                (MenuBuilder) popup.getMenu(),
                moreAction
        );
        menuPopupHelper.setForceShowIcon(true);
        moreAction.setOnClickListener(view -> {
            setMenuItemEnabled(popup, R.id.track_item_actions_more_menu_cache, entryIDSupplier != null);
            setMenuItemEnabled(popup, R.id.track_item_actions_more_menu_play, playAction.hasOnClickListeners());
            setMenuItemEnabled(popup, R.id.track_item_actions_more_menu_queue, queueAction.hasOnClickListeners());
            setMenuItemEnabled(popup, R.id.track_item_actions_more_menu_add_to_playlist, addToPlaylistAction.hasOnClickListeners());
            setMenuItemEnabled(popup, R.id.track_item_actions_more_menu_info, infoAction.hasOnClickListeners());
            animateShow(false);
            menuPopupHelper.show();
        });
    }

    private void setMenuItemEnabled(PopupMenu popupMenu, int resourceId, boolean enabled) {
        MenuItem menuItem = popupMenu.getMenu().findItem(resourceId);
        menuItem.setEnabled(enabled);
        menuItem.getIcon().setAlpha(enabled ? 255 : 64);
    }

    public void initialize() {
        setVisibility(View.INVISIBLE);
        setTranslationX(getTrans(false));
        setAlpha(0);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.track_item_actions_more_menu_cache:
                if (entryIDSupplier == null) {
                    return false;
                }
                MusicLibraryService.downloadAudioData(getContext(), entryIDSupplier.get());
                return true;
            case R.id.track_item_actions_more_menu_play:
                return playAction.performClick();
            case R.id.track_item_actions_more_menu_queue:
                return queueAction.performClick();
            case R.id.track_item_actions_more_menu_add_to_playlist:
                return addToPlaylistAction.performClick();
            case R.id.track_item_actions_more_menu_info:
                return infoAction.performClick();
        }
        return false;
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

    private void setListener(View view, Runnable r, boolean thenHideActions) {
        if (r == null) {
            view.setVisibility(GONE);
        } else {
            view.setOnClickListener(v -> {
                r.run();
                if (thenHideActions) {
                    animateShow(false);
                }
            });
            view.setVisibility(VISIBLE);
        }
    }

    public void setOnPlayListener(Runnable r) {
        setListener(playAction, r, true);
    }

    public void setOnQueueListener(Runnable r) {
        setListener(queueAction, r, true);
    }

    public void setOnAddToPlaylistListener(Runnable r) {
        setListener(addToPlaylistAction, r, true);
    }

    public void setOnPlayPlaylistListener(Runnable r) {
        setListener(playPlaylistAction, r, true);
    }

    public void setOnRemoveListener(Runnable r) {
        setListener(removeAction, r, true);
    }

    public void setOnInfoListener(Runnable r) {
        setListener(infoAction, r, false);
    }

    public void setEntryIDSupplier(Supplier<EntryID> entryIDSupplier) {
        this.entryIDSupplier = entryIDSupplier;
    }
}
