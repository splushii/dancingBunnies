package se.splushii.dancingbunnies.ui.selection;

import android.util.Log;
import android.view.ActionMode;

import java.util.List;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.util.Util;

public abstract class SelectionRecyclerViewAdapter<
        ID,
        ViewHolder extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<ViewHolder> {
    private static final String LC = Util.getLogContext(SelectionRecyclerViewAdapter.class);
    private SelectionTracker<ID> selectionTracker;

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
        for (ID item: items) {
            int pos = getPosition(item);
            removedItemsMap.put(pos, item);
            removeItemFromDataset(pos);
            notifyItemRemoved(pos);
        }
        return removedItemsMap;
    }

    void setSelectionTracker(SelectionTracker<ID> selectionTracker) {
        this.selectionTracker = selectionTracker;
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                Log.e(LC, "onSelectionChanged: " + selectionTracker.getSelection());
                SelectionRecyclerViewAdapter.this.onSelectionChanged();
            }
        });
    }
    protected abstract void onSelectionChanged();
    protected boolean hasSelection() {
        return selectionTracker != null && selectionTracker.hasSelection();
    }
    protected boolean isSelected(ID key) {
        return selectionTracker != null && selectionTracker.isSelected(key);
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
    public abstract void onSelectionDrop(List<ID> selection, int lastDragPos);
    public abstract void onUseViewHolderForDrag(ViewHolder dragViewHolder, List<ID> selection);
    public abstract void onResetDragViewHolder(ViewHolder dragViewHolder);
    public abstract boolean onActionItemClicked(int menuItemID, List<ID> selectionList);
    public abstract void onActionModeStarted(ActionMode actionMode, Selection<ID> selection);
    public abstract void onActionModeSelectionChanged(ActionMode actionMode, Selection<ID> selection);
    public abstract void onActionModeEnding(ActionMode actionMode);
    public abstract boolean onDragInitiated(Selection<ID> selection);
}