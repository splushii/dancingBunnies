package se.splushii.dancingbunnies.ui;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class EntryTypeSelectionDialogAdapter extends RecyclerView.Adapter<EntryTypeSelectionDialogAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(EntryTypeSelectionDialogAdapter.class);
    private final Fragment fragment;
    private final boolean withHandle;
    private final Callback callback;
    private final List<String> dataset;
    private final MutableLiveData<HashSet<String>> disabledKeys;

    EntryTypeSelectionDialogAdapter(Fragment fragment,
                                    boolean withHandle,
                                    Callback callback) {
        this.fragment = fragment;
        this.withHandle = withHandle;
        this.callback = callback;
        disabledKeys = new MutableLiveData<>();
        disabledKeys.setValue(new HashSet<>());
        dataset = new ArrayList<>();
    }

    interface Callback {
        void onClick(String key);
        void startDrag(ViewHolder holder);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.entry_type_selection_item, parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String key = dataset.get(position);
        holder.value.setText(Meta.getDisplayKey(key));
        holder.itemView.setEnabled(!disabledKeys.getValue().contains(key));
        holder.value.setEnabled(!disabledKeys.getValue().contains(key));
        holder.itemView.setOnClickListener(view -> callback.onClick(key));
        holder.handle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                callback.startDrag(holder);
            }
            return v.performClick();
        });
        disabledKeys.observe(fragment.getViewLifecycleOwner(), disabledKeys -> {
            holder.itemView.setEnabled(!disabledKeys.contains(key));
            holder.value.setEnabled(!disabledKeys.contains(key));
        });
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    private boolean onMove(int from, int to) {
        if (from == to) {
            return false;
        }
        if (from < 0 || from > dataset.size()) {
            return false;
        }
        if (to < 0 || to > dataset.size() - 1) {
            return false;
        }
        dataset.add(to, dataset.remove(from));
        notifyItemMoved(from, to);
        return true;
    }

    void addItem(String key) {
        dataset.add(key);
        notifyItemInserted(dataset.size() - 1);
    }

    void removeItem(String key) {
        int index = dataset.indexOf(key);
        if (index >= 0) {
            dataset.remove(index);
            notifyItemRemoved(index);
        }
    }

    void enableItem(String key) {
        HashSet<String> currentlyDisabledKeys = disabledKeys.getValue();
        currentlyDisabledKeys = new HashSet<>(
                currentlyDisabledKeys == null ? Collections.emptyList() : currentlyDisabledKeys
        );
        currentlyDisabledKeys.remove(key);
        disabledKeys.setValue(currentlyDisabledKeys);
    }

    void disableItem(String key) {
        HashSet<String> currentlyDisabledKeys = disabledKeys.getValue();
        currentlyDisabledKeys = new HashSet<>(
                currentlyDisabledKeys == null ? Collections.emptyList() : currentlyDisabledKeys
        );
        currentlyDisabledKeys.add(key);
        disabledKeys.setValue(currentlyDisabledKeys);
    }

    void setDataset(List<String> newFields) {
        dataset.clear();
        dataset.addAll(newFields);
        notifyDataSetChanged();
    }

    List<String> getSelection() {
        return new ArrayList<>(dataset);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final View root;
        final TextView value;
        final View handle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.entry_type_selection_item);
            value = itemView.findViewById(R.id.entry_type_selection_item_value);
            handle = itemView.findViewById(R.id.entry_type_selection_item_handle);
            handle.setVisibility(withHandle ? View.VISIBLE : View.GONE);
        }
    }

    public static class ItemTouchHelperCallback extends ItemTouchHelper.Callback {
        private final EntryTypeSelectionDialogAdapter adapter;

        ItemTouchHelperCallback(EntryTypeSelectionDialogAdapter entryTypeSelectionDialogAdapter) {
            this.adapter = entryTypeSelectionDialogAdapter;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            return adapter.onMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

        }
    }
}
