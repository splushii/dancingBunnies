package se.splushii.dancingbunnies.ui.selection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.util.Util;

public abstract class SelectionRecyclerViewAdapter<ID, ViewHolder extends ItemDetailsViewHolder<ID>>
        extends RecyclerView.Adapter<ViewHolder> {
    private static final String LC = Util.getLogContext(SelectionRecyclerViewAdapter.class);

    private RecyclerViewActionModeSelectionTracker
            <ID, ViewHolder, ? extends SelectionRecyclerViewAdapter<ID, ViewHolder>>
            selectionTracker;

    protected abstract void moveItemInDataset(int from, int to);
    void moveItem(int from, int to) {
        moveItemInDataset(from, to);
        notifyItemMoved(from, to);
    }
    protected abstract void addItemToDataset(int pos, ID item);
    void insertItems(TreeMap<Integer, ID> itemsMap) {
        itemsMap.forEach((pos, item) -> {
            addItemToDataset(pos, item);
            notifyItemInserted(pos);
        });
    }
    protected abstract void removeItemFromDataset(int pos);
    TreeMap<Integer, ID> removeItems(List<ID> items) {
        TreeMap<Integer, ID> removedItemsMap = new TreeMap<>();
        List<Integer> positionsToRemove = new ArrayList<>();
        for (ID item: items) {
            int pos = getPosition(item);
            if (pos >= 0) {
                positionsToRemove.add(pos);
                removedItemsMap.put(pos, item);
            }
        }
        // Remove in reverse order preserve higher positions
        Collections.sort(positionsToRemove, Collections.reverseOrder());
        for (int pos: positionsToRemove) {
            removeItemFromDataset(pos);
            notifyItemRemoved(pos);
        }
        return removedItemsMap;
    }

    protected void recalculateSelection() {
        selectionTracker.recalculateSelection();
    }

    void setSelectionTracker(
            RecyclerViewActionModeSelectionTracker
                    <ID, ViewHolder, ? extends SelectionRecyclerViewAdapter<ID, ViewHolder>>
                    selectionTracker
    ) {
        this.selectionTracker = selectionTracker;
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                SelectionRecyclerViewAdapter.this.onSelectionChanged();
            }
        });
    }
    protected abstract void onSelectionChanged();
    protected boolean hasSelection() {
        return selectionTracker != null && selectionTracker.hasSelection();
    }
    protected List<ID> getSelection() {
        return selectionTracker == null
                ? Collections.emptyList()
                : selectionTracker.getSelection();
    }
    protected boolean isSelected(ID key) {
        return selectionTracker != null && selectionTracker.isSelected(key);
    }
    protected void removeSelection(List<ID> keys) {
        if (selectionTracker == null) {
            return;
        }
        selectionTracker.setItemsSelected(keys, false);
    }

    protected void startDrag(ViewHolder viewHolder) {
        selectionTracker.startDrag(viewHolder);
    }

    final ItemKeyProvider<ID> keyProvider = new ItemKeyProvider<ID>(ItemKeyProvider.SCOPE_MAPPED) {
        @Nullable
        @Override
        public ID getKey(int position) {
            return SelectionRecyclerViewAdapter.this.getKey(position);
        }

        @Override
        public int getPosition(@NonNull ID key) {
            return SelectionRecyclerViewAdapter.this.getPosition(key);
        }
    };
    protected abstract ID getKey(int pos);
    protected abstract int getPosition(@NonNull ID key);
    public abstract void onSelectionDrop(Collection<ID> selection,
                                         int targetPos,
                                         ID idAfterTargetPos);
    public abstract void onUseViewHolderForDrag(ViewHolder dragViewHolder, Collection<ID> selection);
    public abstract void onResetDragViewHolder(ViewHolder dragViewHolder);
    protected boolean isDragViewHolder(ViewHolder viewHolder) {
        return selectionTracker.isDragViewHolder(viewHolder);
    }
    public abstract void onActionModeStarted(ActionModeCallback actionModeCallback, Selection<ID> selection);
    public abstract void onActionModeSelectionChanged(ActionModeCallback actionModeCallback, Selection<ID> selection);
    public abstract void onActionModeEnding(ActionModeCallback actionModeCallback);
    public abstract boolean validSelect(ID key);
    public abstract boolean validMove(ViewHolder current, ViewHolder target);
    public abstract boolean validDrag(ViewHolder viewHolder);
    public abstract boolean validDrag(Selection<ID> selection);
}