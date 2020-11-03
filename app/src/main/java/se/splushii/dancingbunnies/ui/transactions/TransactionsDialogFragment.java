package se.splushii.dancingbunnies.ui.transactions;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.jobs.TransactionsWorker;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.MenuActions;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

public class TransactionsDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(TransactionsDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.transactions_dialog";

    private View header;
    private Toolbar toolbar;
    private View contentView;
    private View contentEmptyView;
    private TransactionsDialogAdapter recyclerViewAdapter;
    private RecyclerViewActionModeSelectionTracker
            <Transaction, TransactionsDialogAdapter.ViewHolder, TransactionsDialogAdapter>
            selectionTracker;

    public static void showDialog(Fragment fragment) {
        Util.showDialog(
                fragment,
                TAG,
                MainActivity.REQUEST_CODE_NONE,
                new TransactionsDialogFragment(),
                null
        );
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.transactions_dialog_fragment_layout, container, false);
        header = rootView.findViewById(R.id.transactions_dialog_header);
        toolbar = rootView.findViewById(R.id.transactions_dialog_toolbar);
        contentEmptyView = rootView.findViewById(R.id.transactions_dialog_entries_empty);
        contentView = rootView.findViewById(R.id.transactions_dialog_content);
        RecyclerView recyclerView = rootView.findViewById(R.id.transactions_dialog_content_entries);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewAdapter = new TransactionsDialogAdapter(this);
        recyclerView.setAdapter(recyclerViewAdapter);
        selectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_TRANSACTIONS_DIALOG,
                recyclerView,
                recyclerViewAdapter,
                StorageStrategy.createParcelableStorage(Transaction.class),
                savedInstanceState
        );
        ActionModeCallback actionModeCallback = new ActionModeCallback(
                requireActivity(),
                AudioBrowser.getInstance(requireActivity()),
                toolbar,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return null;
                    }

                    @Override
                    public List<PlaybackEntry> getPlaybackEntrySelection() {
                        return null;
                    }

                    @Override
                    public List<PlaylistEntry> getPlaylistEntrySelection() {
                        return null;
                    }

                    @Override
                    public List<EntryID> getPlaylistSelection() {
                        return null;
                    }

                    @Override
                    public EntryID getPlaylistID() {
                        return null;
                    }

                    @Override
                    public QueryNode getQueryNode() {
                        return null;
                    }

                    @Override
                    public List<QueryNode> getQueryNodes() {
                        return null;
                    }

                    @Override
                    public List<Transaction> getTransactions() {
                        return selectionTracker.getSelection();
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        selectionTracker.clearSelection();
                    }
                }
        );
        actionModeCallback.setActions(
                new int[] { MenuActions.ACTION_TRANSACTION_DELETE_MULTIPLE },
                new int[] { MenuActions.ACTION_TRANSACTION_DELETE_MULTIPLE },
                new int[] {}
        );
        toolbar.setOnMenuItemClickListener(item ->
                actionModeCallback.onMenuItemClicked(item.getItemId())
        );
        selectionTracker.setActionModeCallback(ActionMode.TYPE_PRIMARY, actionModeCallback);
        toolbar.setNavigationOnClickListener(view -> selectionTracker.clearSelection());
        FastScroller fastScroller = rootView.findViewById(R.id.transactions_dialog_content_fastscroller);
        fastScroller.setRecyclerView(recyclerView);
        contentEmptyView.setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.transactions_dialog_apply).setOnClickListener(view -> {
            TransactionsWorker.requeue(requireContext(), true);
        });
        return rootView;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setContentView(R.layout.transactions_dialog_fragment_layout);
        return dialog;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        TransactionStorage.getInstance(requireContext())
                .getTransactions()
                .observe(getViewLifecycleOwner(), transactions -> {
                    recyclerViewAdapter.setTransactions(transactions);
                    contentEmptyView.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
                    contentView.setVisibility(transactions.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private void resetLayout() {
        getDialog().getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        resetLayout();
    }

    void onActionModeStarted(String s) {
        header.setVisibility(View.GONE);
        toolbar.setVisibility(View.VISIBLE);
        toolbar.setTitle(s);
    }

    void onActionModeUpdated(String s) {
        header.setVisibility(View.GONE);
        toolbar.setVisibility(View.VISIBLE);
        toolbar.setTitle(s);
    }

    void onActionModeEnding() {
        toolbar.setVisibility(View.GONE);
        header.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        selectionTracker.clearSelection();
        super.onDismiss(dialog);
    }
}
