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

public class ItemActionsView extends LinearLayout {
    private static final String LC = Util.getLogContext(ItemActionsView.class);
    private View playAction;
    private View queueAction;
    private View addToPlaylistAction;
    private View infoAction;

    public ItemActionsView(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public ItemActionsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public ItemActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    public ItemActionsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.item_actions_view_layout, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.ItemActionsView, defStyleAttr, defStyleRes);
        a.recycle();
        playAction = findViewById(R.id.item_action_play);
        queueAction = findViewById(R.id.item_action_queue);
        addToPlaylistAction = findViewById(R.id.item_action_add_to_playlist);
        infoAction = findViewById(R.id.item_action_info);
    }

    public void initialize() {
        setVisibility(View.INVISIBLE);
        setTranslationX(getWidth());
        setAlpha(0);
    }

    public void animateShow(boolean show) {
        int translation = ((View)getParent()).getWidth() - getWidth();
        if (show) {
            animate()
                    .translationX(translation)
                    .setDuration(200)
                    .alpha(1)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            setVisibility(View.VISIBLE);
                        }
                    })
                    .start();
        } else {
            animate()
                    .translationX(getWidth())
                    .alpha(0)
                    .setDuration(200)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            setVisibility(View.INVISIBLE);
                        }
                    })
                    .start();
        }
    }

    public void setOnPlayListener(Runnable r) {
        playAction.setOnClickListener(v -> {
            r.run();
            animateShow(false);
        });
    }

    public void setOnQueueListener(Runnable r) {
        queueAction.setOnClickListener(v -> {
            r.run();
            animateShow(false);
        });
    }

    public void setOnAddToPlaylistListener(Runnable r) {
        addToPlaylistAction.setOnClickListener(v -> {
            r.run();
            animateShow(false);
        });
    }

    public void setOnInfoListener(Runnable r) {
        infoAction.setOnClickListener(v -> {
            r.run();
        });
    }
}
