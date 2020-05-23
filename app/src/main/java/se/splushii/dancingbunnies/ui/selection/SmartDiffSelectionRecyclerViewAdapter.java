package se.splushii.dancingbunnies.ui.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.util.Util;

public abstract class SmartDiffSelectionRecyclerViewAdapter<ID, ViewHolder extends ItemDetailsViewHolder<ID>>
        extends SelectionRecyclerViewAdapter<ID, ViewHolder> {
    private static final String LC = Util.getLogContext(SmartDiffSelectionRecyclerViewAdapter.class);

    private List<ID> dataSet = new ArrayList<>();

    protected void setDataSet(List<ID> items) {
        setDataSet(items, Objects::equals);
    }

    protected void setDataSet(List<ID> items,
                              BiFunction<ID, ID, Boolean> contentComparator) {
        if (items.size() > 1000) {
            // Do fast diff
            boolean changed = Util.fastDiff(dataSet, items, contentComparator);
            if (changed) {
                dataSet.clear();
                dataSet.addAll(items);
                notifyDataSetChanged();
            }
        } else {
            // Do thorough diff
            Util.Diff diff = Util.calculateDiff(dataSet, items, contentComparator);
            if (diff.changed) {
                if (hasSelection() && !diff.deleted.isEmpty()) {
                    removeSelection(
                            diff.deleted.stream()
                                    .map(dataSet::get)
                                    .collect(Collectors.toList())
                    );
                }
                dataSet.clear();
                dataSet.addAll(items);
                diff.diffUtilResult.dispatchUpdatesTo(this);
                diff.moved.forEach(p -> notifyItemChanged(p.second));
                recalculateSelection();
            }
        }
    }

    protected ID getItem(int position) {
        if (position >=0 && position < dataSet.size()) {
            return dataSet.get(position);
        }
        return null;
    }

    protected void forEachItem(Consumer<ID> fun) {
        dataSet.forEach(fun::accept);
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    protected int getSize() {
        return dataSet.size();
    }

    protected boolean isEmpty() {
        return dataSet.isEmpty();
    }

    @Override
    protected void moveItemInDataset(int from, int to) {
        dataSet.add(to, dataSet.remove(from));
    }

    @Override
    protected void addItemToDataset(int pos, ID item) {
        dataSet.add(pos, item);
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        dataSet.remove(pos);
    }

    @Override
    protected ID getKey(int pos) {
        if (pos < 0 || pos >= dataSet.size()) {
            return null;
        }
        return dataSet.get(pos);
    }

    @Override
    protected int getPosition(@NonNull ID item) {
        int index = dataSet.indexOf(item);
        return index < 0 ? RecyclerView.NO_POSITION : index;
    }
}
