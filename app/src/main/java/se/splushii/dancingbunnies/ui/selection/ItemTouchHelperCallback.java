package se.splushii.dancingbunnies.ui.selection;

import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.util.Util;

public class ItemTouchHelperCallback<
        ID,
        ViewHolder extends ItemDetailsViewHolder<ID>,
        Adapter extends SelectionRecyclerViewAdapter<ID, ViewHolder>>
        extends ItemTouchHelper.Callback {
    private static final String LC = Util.getLogContext(ItemTouchHelperCallback.class);
    private final Adapter adapter;
    private Listener<ID> listener;
    private TreeMap<Integer, ID> selection;
    private RecyclerViewActionModeSelectionTracker
            <ID, ViewHolder, ? extends SelectionRecyclerViewAdapter<ID, ViewHolder>>
            selectionTracker;
    private ViewHolder dragViewHolder;
    private ID dragID;
    private int targetDragPos = -1;
    private boolean aborted = false;

    public interface Listener<ID> {
        void onDrop(Collection<ID> selection, int targetPos, ID idAfterTargetPos);
        void onAbort();
    }

    ItemTouchHelperCallback(Adapter adapter, Listener<ID> listener) {
        this.adapter = adapter;
        this.listener = listener;
    }

    void setSelectionTracker(
            RecyclerViewActionModeSelectionTracker
                    <ID, ViewHolder, ? extends SelectionRecyclerViewAdapter<ID, ViewHolder>>
                    selectionTracker
    ) {
        this.selectionTracker = selectionTracker;
        selection = new TreeMap<>();
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
                adapter.validDrag(v) ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0,
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
        return !aborted && adapter.validMove((ViewHolder) current, (ViewHolder) target);
    }


    void prepareDrag(ViewHolder viewHolder) {
        Log.d(LC, "prepareDrag");
        targetDragPos = -1;
        aborted = false;
        dragViewHolder = viewHolder;
        dragID = dragViewHolder.getKey();
        initializeSelection();
    }

    void recalculateSelection() {
        if (dragViewHolder == null) {
            return;
        }
        Log.d(LC, "recalculateSelection");
        initializeSelection();
    }

    private void initializeSelection() {
        Log.d(LC, "initializeSelection");
        selection.clear();
        // Save viewHolder pos
        int dragPos = adapter.getPosition(dragID);
        selection.put(dragPos, dragID);
        // Remove and save all selection positions except viewHolder's from adapter
        List<ID> selectionToRemove = selectionTracker.getSelection().stream()
                .filter(key -> !key.equals(dragID))
                .collect(Collectors.toList());
        selection.putAll(adapter.removeItems(selectionToRemove));
        // Use viewHolder as dragViewHolder
        adapter.onUseViewHolderForDrag(dragViewHolder, selection.values());
    }

    boolean isDragViewHolder(ViewHolder viewHolder) {
        return viewHolder == dragViewHolder;
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
            adapter.notifyItemChanged(from);
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
        if (targetDragPos < 0) {
            resetDrag();
        } else {
            dropDrag();
        }
    }

    private void dropDrag() {
        ID idAfterTargetPos = adapter.getKey(targetDragPos + 1);
        // Reset and remove dragViewHolder
        adapter.onResetDragViewHolder(dragViewHolder);
        adapter.removeItems(Collections.singletonList(dragID));
        // Move dropped selection
        TreeMap<Integer, ID> movedSelection = new TreeMap<>();
        int newPos = targetDragPos;
        for (ID id: selection.values()) {
            movedSelection.put(newPos++, id);
        }
        adapter.insertItems(movedSelection);
        dragViewHolder = null;
        dragID = null;
        listener.onDrop(
                selection.values(),
                targetDragPos,
                idAfterTargetPos
        );
    }

    private void resetDrag() {
        // Reset and remove dragViewHolder
        adapter.onResetDragViewHolder(dragViewHolder);
        adapter.removeItems(Collections.singletonList(dragID));
        // Reset selection
        adapter.insertItems(selection);
        dragViewHolder = null;
        dragID = null;
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
            resetDrag();
            aborted = true;
            return 0;
        }
        return super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                viewSizeOutOfBounds, totalSize, msSinceStartScroll);
    }
}