package se.splushii.dancingbunnies.ui.transactions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.Selection;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

public class TransactionsDialogAdapter extends
        SmartDiffSelectionRecyclerViewAdapter<Transaction, TransactionsDialogAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(TransactionsDialogAdapter.class);

    private final TransactionsDialogFragment fragment;
    private TrackItemActionsView selectedActionView;

    TransactionsDialogAdapter(TransactionsDialogFragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
    }

    void setTransactions(List<Transaction> transactions) {
        setDataSet(
                transactions,
                (a, b) -> a.equals(b)
                        && a.getErrorCount() == b.getErrorCount()
                        && ((a.getErrorMessage() == null && b.getErrorMessage() == null)
                        || (a.getErrorMessage() != null && a.getErrorMessage().equals(b.getErrorMessage())))
        );
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.transactions_dialog_item, parent, false)
        );
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction transaction = getItem(position);
        boolean showErrorInfo = transaction.getErrorCount() > 0;
        if (showErrorInfo) {
            holder.item.setBackgroundResource(
                    position % 2 == 0
                            ? R.color.background_error_active_accent
                            : R.color.backgroundalternate_error_active_accent
            );
            holder.errorCountTextView.setText(String.valueOf(transaction.getErrorCount()));
            holder.errorMessageTextView.setText(transaction.getErrorMessage());
        } else {
            holder.item.setBackgroundResource(
                    position % 2 == 0
                            ? R.color.background_active_accent
                            : R.color.backgroundalternate_active_accent
            );
        }
        holder.item.setActivated(isSelected(holder.getKey()));
        holder.item.setOnClickListener(view -> holder.errorView.setVisibility(
                holder.errorView.getVisibility() != View.VISIBLE && showErrorInfo ?
                        View.VISIBLE : View.GONE
        ));
        holder.srcTextView.setText(transaction.getSrc());
        holder.typeTextView.setText(transaction.getDisplayableAction());
        holder.detailsTextView.setText(transaction.getDisplayableDetails());
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection() && selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
        }
    }

    @Override
    public void onSelectionDrop(Collection<Transaction> selection,
                                int targetPos,
                                Transaction idAfterTargetPos) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                       Collection<Transaction> selection) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onResetDragViewHolder(ViewHolder dragViewHolder) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<Transaction> selection) {
        fragment.onActionModeStarted(selection.size() + " transactions");
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<Transaction> selection) {
        fragment.onActionModeUpdated(selection.size() + " transactions");
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {
        fragment.onActionModeEnding();
    }

    @Override
    public boolean validDrag(Selection<Transaction> selection) {
        return false;
    }

    @Override
    public boolean validSelect(Transaction key) {
        return true;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return false;
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return false;
    }

    public class ViewHolder extends ItemDetailsViewHolder<Transaction> {
        private final View item;
        private final TextView srcTextView;
        private final TextView typeTextView;
        private final TextView detailsTextView;
        private final View errorView;
        private final TextView errorCountTextView;
        private final TextView errorMessageTextView;

        ViewHolder(@NonNull View v) {
            super(v);
            item = v.findViewById(R.id.transactions_dialog_item);
            srcTextView = v.findViewById(R.id.transactions_dialog_item_src);
            typeTextView = v.findViewById(R.id.transactions_dialog_item_type);
            detailsTextView = v.findViewById(R.id.transactions_dialog_item_details);
            errorView = v.findViewById(R.id.transactions_dialog_error);
            errorCountTextView = v.findViewById(R.id.transactions_dialog_error_count);
            errorMessageTextView = v.findViewById(R.id.transactions_dialog_error_msg);
        }

        @Override
        protected Transaction getSelectionKeyOf() {
            return getItem(getPos());
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }
}
