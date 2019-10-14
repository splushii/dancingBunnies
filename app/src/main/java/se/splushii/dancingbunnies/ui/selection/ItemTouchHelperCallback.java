package se.splushii.dancingbunnies.ui.selection;

import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.util.Util;

public class ItemTouchHelperCallback<
        ID,
        Adapter extends SelectionRecyclerViewAdapter<ID, ViewHolder>,
        ViewHolder extends ItemDetailsViewHolder<ID>>
        extends ItemTouchHelper.Callback {
    private static final String LC = Util.getLogContext(ItemTouchHelperCallback.class);
    private final Adapter adapter;
    private Listener<ID, ViewHolder> listener;
    private TreeMap<Integer, ID> initialSelection;
    private TreeMap<Integer, ID> removedSelectedItems;
    private SelectionTracker<ID> selectionTracker;
    private ViewHolder dragViewHolder;
    private ID dragID;
    private int initialDragPos;
    private int targetDragPos = -1;
    private boolean aborted = false;

    public interface Listener<ID, ViewHolder> {
        void onDrop(Collection<ID> selection, int targetPos, ID idAfterTargetPos);
        void onAbort();
        void onUseViewHolderForDrag(ViewHolder dragViewHolder, Collection<ID> selection);
        void onResetDragViewHolder(ViewHolder dragViewHolder);
        boolean validMove(ViewHolder current, ViewHolder target);
        boolean validDrag(ViewHolder v);
    }

    ItemTouchHelperCallback(Adapter adapter, Listener<ID, ViewHolder> listener) {
        this.adapter = adapter;
        this.listener = listener;
    }

    void setSelectionTracker(SelectionTracker<ID> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    void prepareDrag(ViewHolder viewHolder) {
        dragViewHolder = viewHolder;
        targetDragPos = initialDragPos = dragViewHolder.getAdapterPosition();

        MutableSelection<ID> mutableSelection = new MutableSelection<>();
        selectionTracker.copySelection(mutableSelection);
        dragID = dragViewHolder.getKey();
        List<ID> selectionToRemove = new LinkedList<>();
        mutableSelection.forEach(id -> {
            if (!id.equals(dragID)) {
                selectionToRemove.add(id);
            }
        });
        removedSelectedItems = adapter.removeItems(selectionToRemove);
        initialSelection = new TreeMap<>(removedSelectedItems);
        initialSelection.put(initialDragPos, dragID);
        // The id of the draggable item when other items are removed
        if (initialSelection.size() > 1) {
            listener.onUseViewHolderForDrag(dragViewHolder, initialSelection.values());
        }
        aborted = false;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
        ViewHolder v = (ViewHolder) viewHolder;
        return makeMovementFlags(
                listener.validDrag(v) ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0,
                0
        );
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
    }

    @Override
    public boolean canDropOver(@NonNull RecyclerView recyclerView,
                               @NonNull RecyclerView.ViewHolder current,
                               @NonNull RecyclerView.ViewHolder target) {
        return validMove(current, target);
    }

    private boolean validMove(RecyclerView.ViewHolder current,
                              RecyclerView.ViewHolder target) {
        return !aborted && listener.validMove((ViewHolder) current, (ViewHolder) target);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder current,
                          @NonNull RecyclerView.ViewHolder target) {
        if (aborted) {
            return false;
        }
        int from = current.getAdapterPosition();
        int to = target.getAdapterPosition();
        Log.d(LC, "onMove from: " + from + " to " + to);
        if (to != targetDragPos && canDropOver(recyclerView, current, target)) {
            adapter.moveItem(from, to);
            targetDragPos = to;
            return true;
        }
        return false;
    }

    @Override
    public void onMoved(@NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        int fromPos,
                        @NonNull RecyclerView.ViewHolder target,
                        int toPos,
                        int x,
                        int y) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        RecyclerView.LayoutParams targetParams = (RecyclerView.LayoutParams) target.itemView.getLayoutParams();
        boolean isMovedHigher = fromPos < toPos;
        if (linearLayoutManager.getReverseLayout()) {
            if (isMovedHigher) {
                linearLayoutManager.scrollToPositionWithOffset(
                        toPos,
                        (linearLayoutManager.getHeight() - linearLayoutManager.getPaddingBottom())
                                - ((linearLayoutManager.getDecoratedTop(target.itemView) - targetParams.topMargin)
                                + (linearLayoutManager.getDecoratedMeasuredHeight(target.itemView) + targetParams.topMargin + targetParams.bottomMargin))
                );
            } else {
                linearLayoutManager.scrollToPositionWithOffset(
                        toPos,
                        (linearLayoutManager.getHeight() - linearLayoutManager.getPaddingBottom())
                                - (linearLayoutManager.getDecoratedBottom(target.itemView) + targetParams.bottomMargin)
                );
            }
        } else {
            if (isMovedHigher) {
                linearLayoutManager.scrollToPositionWithOffset(
                        toPos,
                        (linearLayoutManager.getDecoratedBottom(target.itemView) + targetParams.bottomMargin)
                                - (linearLayoutManager.getDecoratedMeasuredHeight(target.itemView) + targetParams.topMargin + targetParams.bottomMargin)
                                - recyclerView.getPaddingTop()
                );
            } else {
                linearLayoutManager.scrollToPositionWithOffset(
                        toPos,
                        linearLayoutManager.getDecoratedTop(target.itemView) - targetParams.topMargin - recyclerView.getPaddingTop()
                );
            }
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder) {
        Log.d(LC, "clearView");
        super.clearView(recyclerView, viewHolder);
        if (aborted) {
            return;
        }
        if (targetDragPos == initialDragPos) {
            reset();
        } else {
            int newPos = targetDragPos;
            ID idAfterTargetPos = adapter.getKey(newPos + 1);
            adapter.removeItems(Collections.singletonList(dragID));
            TreeMap<Integer, ID> movedSelection = new TreeMap<>();
            for (ID id: initialSelection.values()) {
                movedSelection.put(newPos++, id);
            }
            adapter.insertItems(movedSelection);
            listener.onDrop(
                    initialSelection.values(),
                    targetDragPos,
                    idAfterTargetPos
            );
        }
    }

    private void reset() {
        // Reset drag view
        listener.onResetDragViewHolder(dragViewHolder);
        // Reset adapter items
        if (targetDragPos != initialDragPos) {
            adapter.moveItem(targetDragPos, initialDragPos);
        }
        adapter.insertItems(removedSelectedItems);
        listener.onAbort();
    }

    @Override
    public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView,
                                            int viewSize,
                                            int viewSizeOutOfBounds,
                                            int totalSize,
                                            long msSinceStartScroll) {
        if (aborted) {
            return 0;
        }
        if (Math.abs(viewSizeOutOfBounds) > viewSize * 1.5) {
            // FIXME: This is an ugly fix because of the two problems below
            // viewHolder is detached when dragged out of bounds (and thus calling clearView())
            // ItemTouchHelper does not fire onMove when dragging view out of bounds
            reset();
            aborted = true;
            return 0;
        }
        return super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                viewSizeOutOfBounds, totalSize, msSinceStartScroll);
    }
}