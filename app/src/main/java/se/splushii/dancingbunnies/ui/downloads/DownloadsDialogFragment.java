package se.splushii.dancingbunnies.ui.downloads;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.stream.Collectors;

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
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.DownloadEntry;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.FastScroller;
import se.splushii.dancingbunnies.ui.MenuActions;
import se.splushii.dancingbunnies.ui.downloads.DownloadsDialogAdapter.ViewHolder;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

public class DownloadsDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(DownloadsDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.downloads_dialog";

    private View header;
    private Toolbar toolbar;
    private View contentView;
    private View contentEmptyView;
    private DownloadsDialogAdapter recyclerViewAdapter;
    private RecyclerViewActionModeSelectionTracker
            <DownloadEntry, ViewHolder, DownloadsDialogAdapter>
            selectionTracker;

    public static void showDialog(Fragment fragment) {
        Util.showDialog(
                fragment,
                TAG,
                MainActivity.REQUEST_CODE_NONE,
                new DownloadsDialogFragment(),
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
        View rootView = inflater.inflate(R.layout.downloads_dialog_fragment_layout, container, false);
        header = rootView.findViewById(R.id.downloads_dialog_header);
        toolbar = rootView.findViewById(R.id.downloads_dialog_toolbar);
        contentEmptyView = rootView.findViewById(R.id.downloads_dialog_entries_empty);
        contentView = rootView.findViewById(R.id.downloads_dialog_content);
        RecyclerView recyclerView = rootView.findViewById(R.id.downloads_dialog_content_entries);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewAdapter = new DownloadsDialogAdapter(this);
        recyclerView.setAdapter(recyclerViewAdapter);
        selectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_DOWNLOADS_DIALOG,
                recyclerView,
                recyclerViewAdapter,
                StorageStrategy.createParcelableStorage(DownloadEntry.class),
                savedInstanceState
        );
        ActionModeCallback actionModeCallback = new ActionModeCallback(
                requireActivity(),
                AudioBrowser.getInstance(requireActivity()),
                toolbar,
                new ActionModeCallback.Callback() {
                    @Override
                    public List<EntryID> getEntryIDSelection() {
                        return selectionTracker.getSelection()
                                .stream()
                                .map(d -> d.entryID)
                                .collect(Collectors.toList());
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
                        return null;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode actionMode) {
                        selectionTracker.clearSelection();
                    }
                }
        );
        actionModeCallback.setActions(
                new int[] {MenuActions.ACTION_CACHE_DELETE_MULTIPLE},
                new int[] {MenuActions.ACTION_CACHE_DELETE_MULTIPLE},
                new int[] {}
        );
        toolbar.setOnMenuItemClickListener(item ->
                actionModeCallback.onMenuItemClicked(item.getItemId())
        );
        selectionTracker.setActionModeCallback(ActionMode.TYPE_PRIMARY, actionModeCallback);
        toolbar.setNavigationOnClickListener(view -> selectionTracker.clearSelection());
        FastScroller fastScroller = rootView.findViewById(R.id.downloads_dialog_content_fastscroller);
        fastScroller.setRecyclerView(recyclerView);
        contentEmptyView.setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.downloads_dialog_clear).setOnClickListener(view ->
                recyclerViewAdapter.clearAll()
        );
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    recyclerViewAdapter.hideTrackItemActions();
                }
            }
        });
        return rootView;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setContentView(R.layout.downloads_dialog_fragment_layout);
        return dialog;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        AudioStorage.getInstance(requireContext())
                .getDownloads()
                .observe(getViewLifecycleOwner(), downloads -> {
                    recyclerViewAdapter.setDownloads(downloads);
                    contentEmptyView.setVisibility(downloads.isEmpty() ? View.VISIBLE : View.GONE);
                    contentView.setVisibility(downloads.isEmpty() ? View.GONE : View.VISIBLE);
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