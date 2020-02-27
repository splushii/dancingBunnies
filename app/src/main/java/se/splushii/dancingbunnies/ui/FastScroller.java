package se.splushii.dancingbunnies.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.util.Util;

public class FastScroller extends LinearLayout {
    private static final String LC = Util.getLogContext(FastScroller.class);

    private RecyclerView.OnScrollListener scrollListener;
    private RecyclerView recyclerView;
    private View handle;
    private ViewColorer handleEnabler;
    private ViewColorer handleDisabler;
    private FastScrollerBubble bubble;
    private ViewAnimator bubbleEnabler;
    private ViewAnimator bubbleDisabler;

    private static final int ANIMATION_DURATION = 100;
    private static final int VIEW_HIDE_DELAY = 1000;
    private static final String SCALE_X = "scaleX";
    private static final String SCALE_Y = "scaleY";
    private static final String ALPHA = "alpha";
    private boolean touching = false;
    private float handleOffset = 0f;
    private boolean bubbleEnabled = true;
    private boolean recyclerViewReversed = false;

    public FastScroller(Context context) {
        super(context);
        init(context);
    }

    public FastScroller(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setClipChildren(false);
        inflate(context, R.layout.fastscroller, this);
        handle = findViewById(R.id.fastscroller_handle);
        handleEnabler = new ViewColorer(handle, R.color.colorPrimary, R.color.colorPrimary, 0);
        handleDisabler = new ViewColorer(handle, R.color.colorPrimary, R.color.grey500, 200);
        handleDisabler.run();
        handle.setVisibility(INVISIBLE);
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        scrollListener = new ScrollListener();
        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                if (hideIfNotNeeded()) {
                    return;
                }
                animateEnable(handleEnabler, handleDisabler);
                animateDisable(handleDisabler, VIEW_HIDE_DELAY);
            }
        });
    }

    public void setBubble(FastScrollerBubble bubble) {
        this.bubble = bubble;
        this.bubbleDisabler = new ViewAnimator(bubble, AnimationType.SCALE, true);
        this.bubbleEnabler = new ViewAnimator(bubble, AnimationType.SCALE, false);
        hideView(bubble, AnimationType.SCALE);
    }

    public void onDestroy() {
        recyclerView.removeOnScrollListener(scrollListener);
        scrollListener = null;
    }

    public void enableBubble(boolean enabled) {
        bubbleEnabled = enabled;
        if (!enabled) {
            animateDisable(bubbleDisabler, VIEW_HIDE_DELAY);
        }
    }

    public void setReversed(boolean reversed) {
        recyclerViewReversed = reversed;
    }

    private enum AnimationType {
        FADE,
        SCALE
    }

    private void showView(View v, AnimationType animationType) {
        AnimatorSet animatorSet = new AnimatorSet();
        v.setPivotX(v.getWidth());
        v.setPivotY(v.getHeight());
        v.setVisibility(VISIBLE);
        ArrayList<Animator> animators = new ArrayList<>();
        switch (animationType) {
            case SCALE:
                animators.add(ObjectAnimator.ofFloat(v, SCALE_X, 0f, 1f)
                        .setDuration(ANIMATION_DURATION));
                animators.add(ObjectAnimator.ofFloat(v, SCALE_Y, 0f, 1f)
                        .setDuration(ANIMATION_DURATION));
                /* FALLTHRU */
            case FADE:
                animators.add(ObjectAnimator.ofFloat(v, ALPHA, 0f, 1f)
                        .setDuration(ANIMATION_DURATION));
                break;
        }
        animatorSet.playTogether(animators);
        animatorSet.start();
    }

    private void hideView(View v, AnimationType animationType) {
        AnimatorSet animatorSet = new AnimatorSet();
        v.setPivotX(v.getWidth());
        v.setPivotY(v.getHeight());
        ArrayList<Animator> animators = new ArrayList<>();
        switch (animationType) {
            case SCALE:
                animators.add(ObjectAnimator.ofFloat(v, SCALE_X, 1f, 0f)
                        .setDuration(ANIMATION_DURATION));
                animators.add(ObjectAnimator.ofFloat(v, SCALE_Y, 1f, 0f)
                        .setDuration(ANIMATION_DURATION));
                /* FALLTHRU */
            case FADE:
                animators.add(ObjectAnimator.ofFloat(v, ALPHA, 1f, 0f)
                        .setDuration(ANIMATION_DURATION));
                break;
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                v.setVisibility(INVISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                v.setVisibility(INVISIBLE);
            }
        });
        animatorSet.start();
    }

    private void colorView(View v, int startColorResource, int endColorResource, long delay) {
        ValueAnimator colorAnim = ObjectAnimator.ofInt(
                v.getBackground(),
                "tint",
                ContextCompat.getColor(getContext(), startColorResource),
                ContextCompat.getColor(getContext(), endColorResource)
        );
        colorAnim.setDuration(delay);
        colorAnim.setEvaluator(new ArgbEvaluator());
        colorAnim.start();
    }

    private void setPosition(float position) {
        int height = getHeight();
        float proportion = position / height;
        int handleHeight = handle.getHeight();
        int handleRange = height - handleHeight;
        int handlePos = getValueInRange(0, handleRange, (int) (handleRange * proportion));
        handle.setY(handlePos);
        if (bubble != null) {
            int bubbleHeight = bubble.getHeight();
            int bubbleRange = height - bubbleHeight;
            int bubblePos = getValueInRange(0, bubbleRange, (int) position - bubbleHeight);
            bubble.setY(bubblePos);
        }
    }

    private int getValueInRange(int min, int max, int value) {
        int minimum = Math.max(min, value);
        return Math.min(minimum, max);
    }

    private boolean hideIfNotNeeded() {
        boolean hide = true;
        if (recyclerView != null) {
            RecyclerView.Adapter adapter = recyclerView.getAdapter();
            if (adapter != null && adapter.getItemCount() > 50) {
                hide = false;
            }
        }
        if (hide) {
            handle.setVisibility(INVISIBLE);
            if (bubble != null) {
                hideView(bubble, AnimationType.SCALE);
            }
        }
        return hide;
    }

    /**
     * Handling events from RecyclerView
     */
    private class ScrollListener extends RecyclerView.OnScrollListener {
        int lastPos = 0;
        boolean draggingOrSettling = false;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (hideIfNotNeeded()) {
                return;
            }
            switch (newState) {
                default:
                case RecyclerView.SCROLL_STATE_IDLE:
                    animateDisable(handleDisabler, VIEW_HIDE_DELAY);
                    if (bubble != null) {
                        animateDisable(bubbleDisabler, VIEW_HIDE_DELAY);
                    }
                    draggingOrSettling = false;
                    break;
                case RecyclerView.SCROLL_STATE_DRAGGING:
                case RecyclerView.SCROLL_STATE_SETTLING:
                    if (!draggingOrSettling) {
                        draggingOrSettling = true;
                        animateEnable(handleEnabler, handleDisabler);
                    }
                    break;
            }
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (bubble != null) {
                int pos = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                if (pos != lastPos) {
                    pos = Math.max(pos, 0);
                    lastPos = pos;
                    bubble.update(pos);
                }
            }
            if (!touching) {
                int scrollOffset = recyclerView.computeVerticalScrollOffset();
                int scrollRange = recyclerView.computeVerticalScrollRange();
                int visibleScrollRange = recyclerView.getHeight();
                int invisibleScrollRange = scrollRange - visibleScrollRange;
                float proportion = scrollOffset / (float) invisibleScrollRange;
                int height = getHeight();
                float handlePos = proportion * height;
                setPosition(handlePos);
            }
        }
    }

    /**
     * Handling events from FastScroller
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (hideIfNotNeeded()) {
            return super.onTouchEvent(event);
        }
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            animateEnable(handleEnabler, handleDisabler);
            if (bubble != null && bubbleEnabled) {
                animateEnable(bubbleEnabler, bubbleDisabler);
            }
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            touching = true;
            setPosition(event.getY());
            setRecyclerViewPosition();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            touching = false;
            animateDisable(handleDisabler, VIEW_HIDE_DELAY);
            if (bubble != null) {
                animateDisable(bubbleDisabler, VIEW_HIDE_DELAY);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void setRecyclerViewPosition() {
        if (recyclerView != null) {
            recyclerView.stopScroll();

            float newHandleOffset = handle.getY();
            if (newHandleOffset == handleOffset) {
                return;
            }
            handleOffset = newHandleOffset;
            int handleRange = getHeight() - handle.getHeight();
            int numItems = recyclerView.getAdapter().getItemCount();
            int position;
            if (newHandleOffset <= 0) {
                position = recyclerViewReversed ? numItems - 1 : 0;
            } else if (newHandleOffset >= handleRange) {
                position = recyclerViewReversed ? 0 : numItems - 1;
            } else {
                float offsetProportion = newHandleOffset / handleRange;
                int visibleItems = recyclerView.getChildCount();
                position = getValueInRange(
                        0,
                        numItems - 1,
                        (int) (offsetProportion * (numItems - visibleItems + 1))
                );
                position = recyclerViewReversed ? numItems - position : position;
            }
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            linearLayoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private void animateDisable(Runnable viewDisableAnimator, int hideDelay) {
        Handler handler = getHandler();
        if (handler == null) {
            return;
        }
        getHandler().postDelayed(viewDisableAnimator, hideDelay);
    }

    private void animateEnable(Runnable viewEnableAnimator, Runnable viewDisableAnimator) {
        Handler handler = getHandler();
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(viewDisableAnimator);
        viewEnableAnimator.run();
    }

    private class ViewAnimator implements Runnable {
        private final View view;
        private final AnimationType animationType;
        private final boolean hide;
        ViewAnimator(View v, AnimationType animationType, boolean hide) {
            this.view = v;
            this.animationType = animationType;
            this.hide = hide;
        }
        @Override
        public void run() {
            if (hide) {
                hideView(view, animationType);
            } else {
                showView(view, animationType);
            }
        }
    }

    private class ViewColorer implements Runnable {
        private final View view;
        private final int fromColorResource;
        private final int toColorResource;
        private final long delay;
        ViewColorer(View v, int fromColorResource, int toColorResource, long delay) {
            this.view = v;
            this.fromColorResource = fromColorResource;
            this.toColorResource = toColorResource;
            this.delay = delay;
        }
        @Override
        public void run() {
            if (view.getVisibility() != VISIBLE) {
                view.setVisibility(VISIBLE);
            }
            colorView(view, fromColorResource, toColorResource, delay);
        }
    }
}
