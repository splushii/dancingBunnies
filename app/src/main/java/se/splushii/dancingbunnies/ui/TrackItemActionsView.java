package se.splushii.dancingbunnies.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentManager;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.meta.MetaDialogFragment;
import se.splushii.dancingbunnies.ui.meta.MetaTag;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.ui.MenuActions.ACTION_MORE;

public class TrackItemActionsView extends LinearLayoutCompat {
    private static final String LC = Util.getLogContext(TrackItemActionsView.class);

    private AudioBrowser remote;
    private FragmentManager fragmentManager;
    private MetaDialogFragment metaDialogFragment;

    private Supplier<EntryID> entryIDSupplier;
    private Supplier<PlaybackEntry> playbackEntrySupplier;
    private Supplier<EntryID> playlistIDSupplier;
    private Supplier<PlaylistEntry> playlistEntrySupplier;
    private Supplier<Long> playlistPositionSupplier;
    private Supplier<MetaTag> metaTagSupplier;

    private int[] visibleActions = {};
    private int[] moreActions = {};
    private HashSet<Integer> disabled = new HashSet<>();

    private LinearLayout rootView;
    private Consumer<Integer> postAction = action -> {};

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
        for (int visibleAction: visibleActions) {
            MenuActions.addAction(
                    getContext(),
                    rootView,
                    this::onAction,
                    visibleAction,
                    R.color.icon_on_secondary,
                    !disabled.contains(visibleAction)
            );
        }
        if (moreActions.length > 0) {
            MenuActions.addAction(
                    getContext(),
                    rootView,
                    this::onAction,
                    ACTION_MORE,
                    R.color.icon_on_secondary,
                    !disabled.contains(ACTION_MORE)
            );
        }
    }

    private boolean onAction(View v, int action) {
        if (action == ACTION_MORE) {
            MenuActions.showPopupMenu(
                    getContext(),
                    v,
                    moreActions,
                    disabled,
                    popupItem -> onAction(popupItem.getActionView(), popupItem.getItemId())
            );
            return true;
        }
        if (!MenuActions.doAction(
                action,
                remote,
                getContext(),
                fragmentManager,
                entryIDSupplier,
                playbackEntrySupplier,
                playlistEntrySupplier,
                playlistIDSupplier,
                playlistPositionSupplier,
                metaDialogFragment,
                metaTagSupplier
        )) {
            return false;
        }
        animateShow(false);
        postAction.accept(action);
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

    public void setPlaylistIDSupplier(Supplier<EntryID> playlistIDSupplier) {
        this.playlistIDSupplier = playlistIDSupplier;
    }

    public void setPlaylistEntrySupplier(Supplier<PlaylistEntry> playlistEntrySupplier) {
        this.playlistEntrySupplier = playlistEntrySupplier;
    }

    public void setPlaylistPositionSupplier(Supplier<Long> playlistPositionSupplier) {
        this.playlistPositionSupplier = playlistPositionSupplier;
    }

    public void setMetaTagSupplier(Supplier<MetaTag> metaTagSupplier) {
        this.metaTagSupplier = metaTagSupplier;
    }

    public void setAudioBrowser(AudioBrowser remote) {
        this.remote = remote;
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    public void setPostAction(Consumer<Integer> postAction) {
        this.postAction = postAction;
    }

    public void setMetaDialogFragment(MetaDialogFragment metaDialogFragment) {
        this.metaDialogFragment = metaDialogFragment;
    }
}
