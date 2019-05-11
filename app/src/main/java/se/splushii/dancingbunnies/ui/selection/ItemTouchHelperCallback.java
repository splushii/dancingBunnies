package se.splushii.dancingbunnies.ui.selection;

import android.util.Log;

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
    private final List<ID> selection;
    private TreeMap<Integer, ID> removedSelectedItems;
    private int secondarySelectionId;
    private int lastDragPos = -1;
    private boolean abort = false;
    private ViewHolder dragViewHolder;
    private Listener<ID, ViewHolder> listener;
    private SelectionTracker<ID> selectionTracker;

    public interface Listener<ID, ViewHolder> {
        void onDrop(List<ID> selection, int lastDragPos);
        void onAbort();
        void onUseViewHolderForDrag(ViewHolder dragViewHolder, List<ID> selection);
        void onResetDragViewHolder(ViewHolder dragViewHolder);
        void onDropMode();
        void onAbortMode();
    }

    ItemTouchHelperCallback(Adapter adapter, Listener<ID, ViewHolder> listener) {
        this.adapter = adapter;
        this.selection = new LinkedList<>();
        this.listener = listener;
    }

    void setSelectionTracker(SelectionTracker<ID> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    void prepareDrag(ViewHolder viewHolder) {
        MutableSelection<ID> mutableSelection = new MutableSelection<>();
        selectionTracker.copySelection(mutableSelection);
        selection.clear();
        ID initialSelectionID = viewHolder.getKey();
        List<ID> selectionToRemove = new LinkedList<>();
        mutableSelection.forEach(id -> {
            if (!id.equals(initialSelectionID)) {
                selectionToRemove.add(id);
            }
            selection.add(id);
        });
        Log.e(LC, "prepareDrag selection: " + selection);
        Log.e(LC, "prepareDrag toRemove: " + selectionToRemove);
        removedSelectedItems = adapter.removeItems(selectionToRemove);
        // The id of the draggable item when other items are removed
        lastDragPos = secondarySelectionId = viewHolder.getAdapterPosition();
        dragViewHolder = viewHolder;
        if (selection.size() > 1) {
            listener.onUseViewHolderForDrag(dragViewHolder, selection);
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
            adapter.moveItem(lastDragPos, secondarySelectionId);
            adapter.insertItems(removedSelectedItems);
        } else {
            TreeMap<Integer, ID> movedSelectionItems = new TreeMap<>();
            int newPos = lastDragPos + 1;
            for (int pos: removedSelectedItems.keySet()) {
                movedSelectionItems.put(newPos++, removedSelectedItems.get(pos));
            }
            adapter.insertItems(movedSelectionItems);
            adapter.insertItems(movedSelectionItems);
            listener.onDrop(selection, lastDragPos);
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