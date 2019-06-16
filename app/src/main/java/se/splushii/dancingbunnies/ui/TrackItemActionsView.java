package se.splushii.dancingbunnies.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.util.Util;

public class TrackItemActionsView extends LinearLayout {
    private static final String LC = Util.getLogContext(TrackItemActionsView.class);
    private View playAction;
    private View queueAction;
    private View removeAction;
    private View addToPlaylistAction;
    private View infoAction;
    private View playPlaylistAction;

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

    public TrackItemActionsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.item_actions_view_layout, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.TrackItemActionsView, defStyleAttr, defStyleRes);
        a.recycle();
        playAction = findViewById(R.id.item_action_play);
        queueAction = findViewById(R.id.item_action_queue);
        removeAction = findViewById(R.id.item_action_remove);
        addToPlaylistAction = findViewById(R.id.item_action_add_to_playlist);
        playPlaylistAction = findViewById(R.id.item_action_play_playlist);
        infoAction = findViewById(R.id.item_action_info);
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
}
