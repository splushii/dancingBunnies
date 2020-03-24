package se.splushii.dancingbunnies.ui.selection;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.util.Util;

public class RecyclerViewActionModeSelectionTracker<ID,
        ViewHolder extends ItemDetailsViewHolder<ID>,
        Adapter extends SelectionRecyclerViewAdapter<ID, ViewHolder>> {
    private static final String LC = Util.getLogContext(RecyclerViewActionModeSelectionTracker.class);
    private final ItemTouchHelper itemTouchHelper;
    private final ItemTouchHelperCallback<ID, ViewHolder, Adapter> itemTouchHelperCallback;
    private final Adapter recViewAdapter;
    private SelectionTracker<ID> selectionTracker;
    private ActionModeCallback actionModeCallback;
    private int actionModeType;
    private boolean isDragging;

    public RecyclerViewActionModeSelectionTracker(
            FragmentActivity fragmentActivity,
            String selectionID,
            RecyclerView recView,
            Adapter recViewAdapter,
            StorageStrategy<ID> storageStrategy,
            Bundle savedInstanceState) {
        this.recViewAdapter = recViewAdapter;
        itemTouchHelperCallback = new ItemTouchHelperCallback<>(
                recViewAdapter,
                new ItemTouchHelperCallback.Listener<ID>() {
                    @Override
                    public void onDrop(
                            Collection<ID> selection,
                            int targetPos,
                            ID idAfterTargetPos
                    ) {
                        Log.d(LC, "ItemTouchHelper onDrop at " + targetPos
                                + " before " + idAfterTargetPos);
                        recViewAdapter.onSelectionDrop(selection, targetPos, idAfterTargetPos);
                        endDrag();
                    }

                    @Override
                    public void onAbort() {
                        Log.d(LC, "ItemTouchHelper onAbort");
                        endDrag();
                    }
                }
        );
        itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recView);
        ItemDetailsLookup<ID> itemDetailsLookup = new ItemDetailsLookup<ID>() {
            @Nullable
            @Override
            public ItemDetails<ID> getItemDetails(@NonNull MotionEvent e) {
                View view = recView.findChildViewUnder(e.getX(), e.getY());
                if (view != null) {
                    RecyclerView.ViewHolder viewHolder = recView.getChildViewHolder(view);
                    return ((ViewHolder) viewHolder).getItemDetails();
                }
                return null;
            }
        };
        selectionTracker = new SelectionTracker.Builder<>(
                selectionID,
                recView,
                recViewAdapter.keyProvider,
                itemDetailsLookup,
                storageStrategy
        ).withSelectionPredicate(
                new SelectionTracker.SelectionPredicate<ID>() {

                    @Override
                    public boolean canSetStateForKey(@NonNull ID key, boolean nextState) {
                        return canSetState(key, nextState);
                    }

                    @Override
                    public boolean canSetStateAtPosition(int position, boolean nextState) {
                        return canSetState(recViewAdapter.getKey(position), nextState);
                    }

                    private boolean canSetState(@NonNull ID key, boolean nextState) {
                        if (!isSelected(key) && nextState) {
                            if (isDragging) {
                                Log.w(LC, "Can not select while dragging: "
                                        + key.toString());
                                return false;
                            }
                            return recViewAdapter.validSelect(key);
                        }
                        // Always allow deselect
                        return true;
                    }

                    @Override
                    public boolean canSelectMultiple() {
                        return true;
                    }
                }
        ).withOnDragInitiatedListener(
                e -> {
                    // Add support for drag and drop.
                    View view = recView.findChildViewUnder(e.getX(), e.getY());
                    ViewHolder viewHolder = (ViewHolder) recView.findContainingViewHolder(view);
                    return startDrag(viewHolder);
                }
        ).build();
        recViewAdapter.setSelectionTracker(this);
        itemTouchHelperCallback.setSelectionTracker(this);
        if (savedInstanceState != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onItemStateChanged(@NonNull Object key, boolean selected) {
                if (actionModeCallback == null) {
                    Log.w(LC, "onItemStateChanged: there is no actionModeCallback set");
                    return;
                }
                if (actionModeCallback.isActionMode()) {
                    recViewAdapter.onActionModeSelectionChanged(
                            actionModeCallback,
                            selectionTracker.getSelection()
                    );
                }
            }

            @Override
            public void onSelectionChanged() {
                if (actionModeCallback == null) {
                    Log.w(LC, "onSelectionChanged: there is no actionModeCallback set");
                    return;
                }
                if (selectionTracker.hasSelection()) {
                    if (actionModeCallback.isActionMode()) {
                        recViewAdapter.onActionModeSelectionChanged(
                                actionModeCallback,
                                selectionTracker.getSelection()
                        );
                    } else if (actionModeCallback != null) {
                        fragmentActivity.startActionMode(actionModeCallback, actionModeType);
                        recViewAdapter.onActionModeStarted(
                                actionModeCallback,
                                selectionTracker.getSelection()
                        );
                    }
                } else if (actionModeCallback.isActionMode()) {
                    recViewAdapter.onActionModeEnding(actionModeCallback);
                    endDrag();
                }
            }
        });
    }

    public void setActionModeCallback(int actionModeType,
                                      ActionModeCallback callback) {
        this.actionModeType = actionModeType;
        this.actionModeCallback = callback;
    }

    public void clearSelection() {
        if (selectionTracker != null) {
            selectionTracker.clearSelection();
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        selectionTracker.onSaveInstanceState(outState);
    }

    public List<ID> getSelection() {
        MutableSelection<ID> selection = new MutableSelection<>();
        selectionTracker.copySelection(selection);
        List<ID> selectionList = new ArrayList<>();
        selection.forEach(selectionList::add);
        return selectionList;
    }

    public boolean hasSelection() {
        return selectionTracker.hasSelection();
    }

    public boolean startDrag(ViewHolder viewHolder) {
        if (isDragging) {
            Log.e(LC, "startDrag when already dragging");
            return false;
        }
        if (viewHolder == null) {
            Log.e(LC, "startDrag with null viewHolder");
            return false;
        }
        ID key = viewHolder.getKey();
        if (!selectionTracker.isSelected(key) && !selectionTracker.select(key)) {
            Log.e(LC, "startDrag could not select item: " + key.toString());
            return false;
        }
        if (!recViewAdapter.validDrag(selectionTracker.getSelection()) ) {
            Log.e(LC, "startDrag not allowed by recyclerview adapter");
            return false;
        }
        itemTouchHelperCallback.prepareDrag(viewHolder);
        itemTouchHelper.startDrag(viewHolder);
        isDragging = true;
        return true;
    }

    private void endDrag() {
        actionModeCallback.finish();
        isDragging = false;
    }

    void addObserver(SelectionTracker.SelectionObserver observer) {
        selectionTracker.addObserver(observer);
    }

    boolean isSelected(ID key) {
        return selectionTracker.isSelected(key);
    }

    void setItemsSelected(List<ID> keys, boolean selected) {
        selectionTracker.setItemsSelected(keys, selected);
    }

    void recalculateSelection() {
        itemTouchHelperCallback.recalculateSelection();
    }

    boolean isDragViewHolder(ViewHolder viewHolder) {
        return itemTouchHelperCallback.isDragViewHolder(viewHolder);
    }
}
