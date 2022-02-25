package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowser;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.storage.db.MetaValueEntry;
import se.splushii.dancingbunnies.util.Util;

public class EntryTypeSelectionDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(EntryTypeSelectionDialogFragment.class);

    static final String SORT_QUEUE = "sort_queue";
    static final String SORT_PLAYLIST_PLAYBACK = "sort_playback";

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.entry_type_selection_dialog";
    private static final String BUNDLE_KEY_PLAYBACK_ENTRIES = "dancingbunnies.bundle.key.entrytypeselection.entries";
    private static final String BUNDLE_KEY_SORT_TARGET = "dancingbunnies.bundle.key.entrytypeselection.sort_target";
    private static final String BUNDLE_KEY_ONLY_RETURN_CONFIG = "dancingbunnies.bundle.key.entrytypeselection.only_return_config";
    private static final String BUNDLE_KEY_INITIAL_SELECTION = "dancingbunnies.bundle.key.entrytypeselection.initial_selection";
    private static final String BUNDLE_KEY_ENTRY_TYPE = "dancingbunnies.bundle.key.entrytypeselection.entry_type";

    private ArrayList<PlaybackEntry> entries;
    private String sortTarget;
    private boolean onlyReturnConfig;

    private EntryTypeSelectionDialogAdapter selectedItemsRecyclerViewAdapter;
    private EntryTypeSelectionDialogAdapter availableItemsRecyclerViewAdapter;
    private ItemTouchHelper touchHelper;
    private HashSet<String> chosenKeys;
    private String entryType;

    public static void showDialogForSortConfig(Fragment targetFragment,
                                               List<String> initialSelection,
                                               String entryType) {
        Bundle args = new Bundle();
        args.putStringArrayList(BUNDLE_KEY_INITIAL_SELECTION, new ArrayList<>(initialSelection));
        args.putString(BUNDLE_KEY_ENTRY_TYPE, entryType);
        showDialog(
                targetFragment.requireActivity().getSupportFragmentManager(),
                targetFragment,
                args,
                true
        );
    }

    static void showDialogToSort(FragmentManager fragmentManager,
                                 ArrayList<PlaybackEntry> entries,
                                 String sortTarget) {
        if (entries == null
                || entries.isEmpty()
                || sortTarget == null
                || (!sortTarget.equals(SORT_QUEUE) && !sortTarget.equals(SORT_PLAYLIST_PLAYBACK))) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelableArrayList(BUNDLE_KEY_PLAYBACK_ENTRIES, entries);
        args.putString(BUNDLE_KEY_SORT_TARGET, sortTarget);
        showDialog(fragmentManager, null, args, false);
    }

    private static void showDialog(FragmentManager fragmentManager,
                                   Fragment targetFragment,
                                   Bundle args,
                                   boolean onlyReturnConfig) {
        args.putBoolean(BUNDLE_KEY_ONLY_RETURN_CONFIG, onlyReturnConfig);
        Util.showDialog(
                fragmentManager,
                targetFragment,
                TAG,
                MainActivity.REQUEST_CODE_SORT_DIALOG,
                new EntryTypeSelectionDialogFragment(),
                args
        );
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        chosenKeys = new HashSet<>();
        Bundle args = getArguments();
        onlyReturnConfig = args.getBoolean(BUNDLE_KEY_ONLY_RETURN_CONFIG, true);
        if (!onlyReturnConfig) {
            ArrayList<PlaybackEntry> entries = args.getParcelableArrayList(BUNDLE_KEY_PLAYBACK_ENTRIES);
            this.entries = entries == null ? new ArrayList<>() : entries;
            sortTarget = args.getString(BUNDLE_KEY_SORT_TARGET);
        }
        entryType = args.getString(BUNDLE_KEY_ENTRY_TYPE, EntryID.TYPE_TRACK);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.entry_type_selection_dialog_fragment_layout, container, false);
        RecyclerView selectedItemsRecyclerView = rootView.findViewById(R.id.entry_type_selection_dialog_selected_items);
        selectedItemsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        selectedItemsRecyclerViewAdapter = new EntryTypeSelectionDialogAdapter(
                this,
                true,
                new EntryTypeSelectionDialogAdapter.Callback() {
                    @Override
                    public void onClick(String key) {
                        removeItem(key);
                    }

                    @Override
                    public void startDrag(EntryTypeSelectionDialogAdapter.ViewHolder holder) {
                        EntryTypeSelectionDialogFragment.this.startDrag(holder);
                    }
                }
        );
        ItemTouchHelper.Callback callback =
                new EntryTypeSelectionDialogAdapter.ItemTouchHelperCallback(selectedItemsRecyclerViewAdapter);
        touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(selectedItemsRecyclerView);
        selectedItemsRecyclerView.setAdapter(selectedItemsRecyclerViewAdapter);

        RecyclerView availableItemsRecyclerView = rootView.findViewById(R.id.entry_type_selection_dialog_available_items);
        availableItemsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        availableItemsRecyclerViewAdapter = new EntryTypeSelectionDialogAdapter(
                this,
                false,
                new EntryTypeSelectionDialogAdapter.Callback() {
                    @Override
                    public void onClick(String key) {
                        addItem(key);
                    }

                    @Override
                    public void startDrag(EntryTypeSelectionDialogAdapter.ViewHolder holder) {}
                }
        );
        availableItemsRecyclerView.setAdapter(availableItemsRecyclerViewAdapter);

        LiveData<List<String>> metaKeysLiveData;
        switch (entryType) {
            default:
            case EntryID.TYPE_TRACK:
                metaKeysLiveData = MetaStorage.getInstance(requireContext()).getTrackMetaKeys();
                break;
            case EntryID.TYPE_PLAYLIST:
                metaKeysLiveData = MetaStorage.getInstance(requireContext()).getPlaylistMetaKeys();
                break;
        }
        metaKeysLiveData.observe(getViewLifecycleOwner(), keys ->
                        availableItemsRecyclerViewAdapter.setDataset(keys)
        );

        rootView.findViewById(R.id.entry_type_selection_dialog_submit).setOnClickListener(view -> {
            processSelection(selectedItemsRecyclerViewAdapter.getSelection());
        });

        List<String> initialConfig = getArguments().getStringArrayList(BUNDLE_KEY_INITIAL_SELECTION);
        if (initialConfig != null && !initialConfig.isEmpty()) {
            initialConfig.forEach(this::addItem);
        }
        return rootView;
    }

    private void startDrag(EntryTypeSelectionDialogAdapter.ViewHolder holder) {
        touchHelper.startDrag(holder);
    }

    private void addItem(String key) {
        if (chosenKeys.contains(key) || chosenKeys.size() >= MetaValueEntry.NUM_MAX_EXTRA_VALUES) {
            return;
        }
        chosenKeys.add(key);
        availableItemsRecyclerViewAdapter.disableItem(key);
        selectedItemsRecyclerViewAdapter.addItem(key);
    }

    private void removeItem(String key) {
        chosenKeys.remove(key);
        selectedItemsRecyclerViewAdapter.removeItem(key);
        availableItemsRecyclerViewAdapter.enableItem(key);
    }

    private void processSelection(List<String> keys) {
        Fragment targetFragment = getTargetFragment();
        if (onlyReturnConfig) {
            if (targetFragment instanceof EntryTypeSelectionDialogFragment.ConfigHandler) {
                ((EntryTypeSelectionDialogFragment.ConfigHandler) targetFragment).onEntryTypeSelection(keys);
                dismiss();
                return;
            }
            return;
        }
        switch (sortTarget) {
            case SORT_QUEUE:
                AudioBrowser.getInstance(requireActivity()).sortQueueItems(entries, keys);
                break;
            case SORT_PLAYLIST_PLAYBACK:
                PlaybackControllerStorage.getInstance(requireContext()).sort(
                        PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK,
                        entries,
                        keys
                );
                break;
        }
        dismiss();
    }

    public interface ConfigHandler {
        void onEntryTypeSelection(List<String> keys);
    }
}