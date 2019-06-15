package se.splushii.dancingbunnies.ui.selection;

import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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
import se.splushii.dancingbunnies.util.Util;

public class RecyclerViewActionModeSelectionTracker<ID,
        Adapter extends SelectionRecyclerViewAdapter<ID, ViewHolder>,
        ViewHolder extends ItemDetailsViewHolder<ID>> {
    private static final String LC = Util.getLogContext(RecyclerViewActionModeSelectionTracker.class);
    private SelectionTracker<ID> selectionTracker;
    private ActionMode actionMode;

    public RecyclerViewActionModeSelectionTracker(
            FragmentActivity activity,
            int actionModeMenuResource,
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
                        actionMode.finish();
                    }

                    @Override
                    public void onAbort() {
                        Log.d(LC, "ItemTouchHelper onAbort");
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
                    public void onDropMode() {
                        Log.d(LC, "ItemTouchHelper onDropMode");
                        // TODO: Implement
                    }

                    @Override
                    public void onAbortMode() {
                        Log.d(LC, "ItemTouchHelper onAbortMode");
                        // TODO: Implement
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
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
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

        ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
            // Called when the action mode is created; startActionMode() was called
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate a menu resource providing context menu items
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(actionModeMenuResource, menu);
                return true;
            }

            // Called each time the action mode is shown. Always called after onCreateActionMode, but
            // may be called multiple times if the mode is invalidated.
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false; // Return false if nothing is done
            }

            // Called when the user selects a contextual menu item
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                MutableSelection<ID> selection = new MutableSelection<>();
                selectionTracker.copySelection(selection);
                List<ID> selectionList = new ArrayList<>();
                selection.forEach(selectionList::add);
                if (recViewAdapter.onActionItemClicked(item.getItemId(), selectionList)) {
                    mode.finish();
                    return true;
                }
                return false;
            }

            // Called when the user exits the action mode
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                clearSelection();
                actionMode = null;
            }
        };
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection()) {
                    if (actionMode == null) {
                        actionMode = activity.startActionMode(actionModeCallback);
                        recViewAdapter.onActionModeStarted(
                                actionMode,
                                selectionTracker.getSelection()
                        );
                    } else {
                        recViewAdapter.onActionModeSelectionChanged(
                                actionMode,
                                selectionTracker.getSelection()
                        );
                    }
                } else if (actionMode != null) {
                    recViewAdapter.onActionModeEnding(actionMode);
                    actionMode.finish();
                }
            }
        });
    }

    public void clearSelection() {
        if (selectionTracker != null) {
            selectionTracker.clearSelection();
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        selectionTracker.onSaveInstanceState(outState);
    }
}
