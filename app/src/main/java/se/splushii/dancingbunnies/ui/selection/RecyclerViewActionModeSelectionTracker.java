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
        Adapter extends SelectionRecyclerViewAdapter<ID, ViewHolder>,
        ViewHolder extends ItemDetailsViewHolder<ID>> {
    private static final String LC = Util.getLogContext(RecyclerViewActionModeSelectionTracker.class);
    private final ItemTouchHelper itemTouchHelper;
    private SelectionTracker<ID> selectionTracker;
    private ActionModeCallback actionModeCallback;

    public RecyclerViewActionModeSelectionTracker(
            FragmentActivity fragmentActivity,
            String selectionID,
            RecyclerView recView,
            Adapter recViewAdapter,
            StorageStrategy<ID> storageStrategy,
            Bundle savedInstanceState) {
        ItemTouchHelperCallback<ID, Adapter, ViewHolder> itemTouchHelperCallback = new ItemTouchHelperCallback<>(
                recViewAdapter,
                new ItemTouchHelperCallback.Listener<ID, ViewHolder>() {
                    @Override
                    public void onDrop(
                            Collection<ID> selection,
                            int targetPos,
                            ID idAfterTargetPos
                    ) {
                        Log.d(LC, "ItemTouchHelper onDrop at " + targetPos
                                + " before " + idAfterTargetPos);
                        recViewAdapter.onSelectionDrop(selection, targetPos, idAfterTargetPos);
                        actionModeCallback.finish();
                    }

                    @Override
                    public void onAbort() {
                        Log.d(LC, "ItemTouchHelper onAbort");
                        actionModeCallback.finish();
                    }

                    @Override
                    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                                       Collection<ID> selection) {
                        Log.d(LC, "ItemTouchHelper onUseViewHolderForDrag");
                        recViewAdapter.onUseViewHolderForDrag(dragViewHolder, selection);
                    }

                    @Override
                    public void onResetDragViewHolder(ViewHolder dragViewHolder) {
                        Log.d(LC, "ItemTouchHelper onResetDragViewHolder");
                        recViewAdapter.onResetDragViewHolder(dragViewHolder);
                    }

                    @Override
                    public boolean validMove(ViewHolder current, ViewHolder target) {
                        return recViewAdapter.validMove(current, target);
                    }

                    @Override
                    public boolean validDrag(ViewHolder viewHolder) {
                        return recViewAdapter.validDrag(viewHolder);
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
        ).withOnDragInitiatedListener(e -> {
            // Add support for drag and drop.
            View view = recView.findChildViewUnder(e.getX(), e.getY());
            ViewHolder viewHolder = (ViewHolder) recView.findContainingViewHolder(view);
            if (viewHolder != null
                    && recViewAdapter.onDragInitiated(selectionTracker.getSelection()) ) {
                itemTouchHelperCallback.prepareDrag(viewHolder);
                itemTouchHelper.startDrag(viewHolder);
                return true;
            }
            return false;
        }).build();
        recViewAdapter.setSelectionTracker(selectionTracker);
        itemTouchHelperCallback.setSelectionTracker(selectionTracker);
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
                        fragmentActivity.startActionMode(actionModeCallback);
                        recViewAdapter.onActionModeStarted(
                                actionModeCallback,
                                selectionTracker.getSelection()
                        );
                    }
                } else if (actionModeCallback.isActionMode()) {
                    recViewAdapter.onActionModeEnding(actionModeCallback);
                    actionModeCallback.finish();
                }
            }
        });
    }

    public void setActionModeCallback(ActionModeCallback callback) {
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
}
