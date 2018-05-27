package se.splushii.dancingbunnies.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;

import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.util.Util;

public class FastScroller extends LinearLayout {
    private static final String LC = Util.getLogContext(FastScroller.class);

    private View handle;
    private RecyclerView.OnScrollListener scrollListener;
    private RecyclerView recyclerView;
    private ViewHider handleHider;
    private FastScrollerBubble bubble;
    private ViewHider bubbleHider;

    private static final int ANIMATION_DURATION = 100;
    private static final int VIEW_HIDE_DELAY = 1000;
    private static final String SCALE_X = "scaleX";
    private static final String SCALE_Y = "scaleY";
    private static final String ALPHA = "alpha";
    private float handleOffset = 0f;

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
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.fastscroller, this);
        handle = findViewById(R.id.fastscroller_handle);
        handleHider = new ViewHider(handle, AnimationType.FADE);
        hideView(handle, AnimationType.FADE);
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        scrollListener = new ScrollListener();
        recyclerView.addOnScrollListener(scrollListener);
    }

    public void setBubble(FastScrollerBubble bubble) {
        this.bubble = bubble;
        this.bubbleHider = new ViewHider(bubble, AnimationType.SCALE);
        hideView(bubble, AnimationType.SCALE);
    }

    public void onDestroy() {
        recyclerView.removeOnScrollListener(scrollListener);
        scrollListener = null;
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

    private boolean iAmNeeded() {
        int scrollRange = recyclerView.computeVerticalScrollRange();
        int visibleScrollRange = recyclerView.getHeight();
        return scrollRange > 2 * visibleScrollRange;
    }

    /**
     * Handling events from RecyclerView
     */
    private class ScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (!iAmNeeded()) {
                hideView(handle, AnimationType.FADE);
                if (bubble != null) {
                    hideView(bubble, AnimationType.SCALE);
                }
                return;
            }
            if (bubble != null) {
                MusicLibraryAdapter libAdapter = (MusicLibraryAdapter) recyclerView.getAdapter();
                MediaBrowserCompat.MediaItem item =
                        libAdapter.getItemData(recyclerView.getChildAdapterPosition(recyclerView.getChildAt(0)));
                String title = String.valueOf(item.getDescription().getTitle());
                char firstChar = title.charAt(0);
                bubble.setText(String.valueOf(firstChar));
            }

            animateShow(handle, handleHider, AnimationType.FADE);
            animateHide(handleHider, VIEW_HIDE_DELAY);

            int scrollOffset = recyclerView.computeVerticalScrollOffset();
            int scrollRange = recyclerView.computeVerticalScrollRange();
            int visibleScrollRange = recyclerView.getHeight();
            int invisibleScrollRange = scrollRange - visibleScrollRange;
            float proportion = scrollOffset / (float) invisibleScrollRange;
            int height = getHeight();
            float handlePos = proportion * height;
            setPosition(handlePos);
            handleOffset = handle.getY();
        }
    }

    /**
     * Handling events from FastScroller
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!iAmNeeded()) {
            hideView(handle, AnimationType.FADE);
            if (bubble != null) {
                hideView(bubble, AnimationType.SCALE);
            }
            return super.onTouchEvent(event);
        }
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            setPosition(event.getY());
            animateShow(handle, handleHider, AnimationType.FADE);
            if (bubble != null) {
                animateShow(bubble, bubbleHider, AnimationType.SCALE);
            }
            setRecyclerViewPosition();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            animateHide(handleHider, VIEW_HIDE_DELAY);
            if (bubble != null) {
                animateHide(bubbleHider, VIEW_HIDE_DELAY);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }


    private void animateHide(ViewHider viewHider, int handleHideDelay) {
        getHandler().postDelayed(viewHider, handleHideDelay);
    }

    private void animateShow(View v, ViewHider viewHider, AnimationType animationType) {
        getHandler().removeCallbacks(viewHider);
        if (v.getVisibility() == INVISIBLE) {
            showView(v, animationType);
        }
    }

    private class ViewHider implements Runnable {
        private final View view;
        private final AnimationType animationType;
        ViewHider(View v, AnimationType animationType) {
            this.view = v;
            this.animationType = animationType;
        }
        @Override
        public void run() {
            hideView(view, animationType);
        }
    }

    private void setRecyclerViewPosition() {
        if (recyclerView != null) {
            recyclerView.stopScroll();

            // Calculate FastScroll move proportion
            float newHandleOffset = handle.getY();
            float oldHandleOffset = handleOffset;
            handleOffset = newHandleOffset;
            int handleRange = getHeight() - handle.getHeight();
            if (newHandleOffset == 0f) {
                recyclerView.scrollToPosition(0);
                return;
            } else if (newHandleOffset >= handleRange) {
                int lastItem = recyclerView.getAdapter().getItemCount() - 1;
                recyclerView.scrollToPosition(lastItem);
                return;
            }
            float moveProportion = (newHandleOffset - oldHandleOffset) / handleRange;

            // Apply move proportion to RecyclerView
            int scrollRange = recyclerView.computeVerticalScrollRange();
            int visibleScrollRange = recyclerView.getHeight();
            int invisibleScrollRange = scrollRange - visibleScrollRange;
            int recViewMove = (int) (moveProportion * invisibleScrollRange);
            recyclerView.scrollBy(0, recViewMove);
        }
    }

}
