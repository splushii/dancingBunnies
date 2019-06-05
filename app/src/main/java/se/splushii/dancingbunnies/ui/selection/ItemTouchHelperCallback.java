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
    private boolean abort = false;
    private TreeMap<Integer, ID> initialSelection;
    private TreeMap<Integer, ID> removedSelectedItems;
    private SelectionTracker<ID> selectionTracker;
    private ViewHolder dragViewHolder;
    private ID dragID;
    private int initialDragPos;
    private int lastDragPos = -1;

    public interface Listener<ID, ViewHolder> {
        void onDrop(Collection<ID> selection, int lastDragPos);
        void onAbort();
        void onUseViewHolderForDrag(ViewHolder dragViewHolder, Collection<ID> selection);
        void onResetDragViewHolder(ViewHolder dragViewHolder);
        void onDropMode();
        void onAbortMode();
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
        lastDragPos = initialDragPos = dragViewHolder.getAdapterPosition();

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
        setDropMode();
    }

    private void setDropMode() {
        abort = false;
        listener.onDropMode();
    }

    private void setAbortMode() {
        abort = true;
        listener.onAbortMode();
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
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
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

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder current,
                          @NonNull RecyclerView.ViewHolder target) {
        int from = current.getAdapterPosition();
        int to = target.getAdapterPosition();
        Log.d(LC, "onMove from: " + from + " to " + to);
        if (to != lastDragPos && canDropOver(recyclerView, current, target)) {
            if (abort) {
                setDropMode();
            }
            adapter.moveItem(from, to);
            lastDragPos = to;
        }
        return true;
    }

    private boolean validMove(RecyclerView.ViewHolder current,
                              RecyclerView.ViewHolder target) {
        return true;
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (abort) {
            listener.onResetDragViewHolder(dragViewHolder);
            listener.onAbort();
            // Reset adapter items
            adapter.moveItem(lastDragPos, initialDragPos);
            adapter.insertItems(removedSelectedItems);
        } else {
            int newPos = lastDragPos;
            Log.e(LC, "newPos: " + newPos);
            adapter.removeItems(Collections.singletonList(dragID));
            TreeMap<Integer, ID> movedSelection = new TreeMap<>();
            for (ID id: initialSelection.values()) {
                movedSelection.put(newPos++, id);
            }
            adapter.insertItems(movedSelection);
            listener.onDrop(initialSelection.values(), lastDragPos);
        }
    }

    @Override
    public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView,
                                            int viewSize,
                                            int viewSizeOutOfBounds,
                                            int totalSize,
                                            long msSinceStartScroll) {
        if (!abort && Math.abs(viewSizeOutOfBounds) > viewSize) {
            setAbortMode();
        } else if (abort && Math.abs(viewSizeOutOfBounds) < viewSize) {
            setDropMode();
        }
        return super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                viewSizeOutOfBounds, totalSize, msSinceStartScroll);
    }
}