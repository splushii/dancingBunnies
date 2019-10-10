package se.splushii.dancingbunnies.ui.meta;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

public class MetaDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(MetaDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.meta_dialog";

    private EntryID entryID = EntryID.UNKOWN;
    private ArrayList<Pair<String, String>> data;

    private View addView;
    private EditText addValueEditText;
    private ImageButton addBtn;

    private MetaDialogFragmentAdapter recViewAdapter;
    private RecyclerViewActionModeSelectionTracker<MetaTag, MetaDialogFragmentAdapter, MetaDialogFragmentAdapter.ViewHolder> selectionTracker;
    private LiveData<Meta> metaLiveData;

    public static void showMeta(AudioBrowserFragment audioBrowserFragment, EntryID entryID) {
        if (entryID == null || entryID.isUnknown()) {
            return;
        }
        FragmentTransaction ft = audioBrowserFragment.getFragmentManager().beginTransaction();
        Fragment prev = audioBrowserFragment.getFragmentManager().findFragmentByTag(MetaDialogFragment.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment dialogFragment = new MetaDialogFragment();
        dialogFragment.setTargetFragment(audioBrowserFragment, MainActivity.REQUEST_CODE_META_DIALOG);
        dialogFragment.setArguments(entryID.toBundle());
        dialogFragment.show(ft, MetaDialogFragment.TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setCancelable(false);
        Bundle args = getArguments();
        entryID = args == null ? EntryID.UNKOWN : EntryID.from(args);
        metaLiveData = MetaStorage.getInstance(requireContext()).getMeta(entryID);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnKeyListener((dialogInterface, keyCode, keyEvent) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                return onBackPressed();
            }
            return false;
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.meta_dialog_fragment_layout, container, false);
        TextView title = rootView.findViewById(R.id.meta_dialog_title);
        addView = rootView.findViewById(R.id.meta_dialog_add_view);
        SwitchCompat addLocalTagSwitch = rootView.findViewById(R.id.meta_dialog_add_local_switch);
        EditText addKeyEditText = rootView.findViewById(R.id.meta_dialog_add_tag_key);
        addValueEditText = rootView.findViewById(R.id.meta_dialog_add_tag_value);
        addBtn = rootView.findViewById(R.id.meta_dialog_add_btn);
        RecyclerView recView = rootView.findViewById(R.id.meta_dialog_recyclerview);
        LinearLayoutManager recViewLayoutManager = new LinearLayoutManager(this.getContext());
        recView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new MetaDialogFragmentAdapter(this);
        recView.setAdapter(recViewAdapter);
        selectionTracker = new RecyclerViewActionModeSelectionTracker<>(
                requireActivity(),
                MainActivity.SELECTION_ID_META_DIALOG,
                recView,
                recViewAdapter,
                StorageStrategy.createParcelableStorage(MetaTag.class),
                savedInstanceState
        );
        // TODO: selectionTracker.setActionModeCallback
        recView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    recViewAdapter.hideTrackItemActions();
                }
            }
        });


        boolean metaAddSupported = MusicLibraryService
                .checkAPISupport(entryID.src, MusicLibraryService.META_ADD);
        addLocalTagSwitch.setEnabled(metaAddSupported);
        addLocalTagSwitch.setChecked(!metaAddSupported);

        addBtn.setOnClickListener(v -> {
            addBtn.animate().rotation(45f).setInterpolator(new AccelerateDecelerateInterpolator());
            boolean showAddView = addView.getVisibility() != View.VISIBLE;
            title.setText(showAddView ? R.string.metadata_context_add : R.string.metadata);
            if (showAddView) {
                addKeyEditText.requestFocus();
                Util.showSoftInput(requireActivity(), addKeyEditText);
                addView.setVisibility(View.VISIBLE);
            } else {
                hideAddView();
            }
        });
        addValueEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String key = addKeyEditText.getText().toString();
                String value = addValueEditText.getText().toString();
                boolean isLocalUserTag = addLocalTagSwitch.isChecked();
                if (isLocalUserTag) {
                    // TODO: Support adding other local user tag types (long, double)
                    String userLocalKey = Meta.constructLocalUserStringKey(key);
                    CompletableFuture.runAsync(() ->
                            MetaStorage.getInstance(getContext()).insertLocalUserMeta(
                                    entryID,
                                    userLocalKey,
                                    value
                            )
                    );
                } else {
                    // TODO: Implement
                    Log.e(LC, "Adding non-local user tag not implemented");
                    throw new RuntimeException("Adding non-local user tag not implemented");
                }
                hideAddView();
                addKeyEditText.setText("");
                addValueEditText.setText("");
                title.setText(R.string.metadata);
                return true;
            }
            return false;
        });

        metaLiveData.observe(getViewLifecycleOwner(), meta -> {
            populateData(meta);
            initMetaRecView();
        });
        return rootView;
    }

    private boolean hideAddView() {
        if (addView.getVisibility() == View.VISIBLE) {
            Util.hideSoftInput(requireActivity(), addValueEditText);
            addView.setVisibility(View.GONE);
            addBtn.animate().rotation(0f).setInterpolator(new AccelerateDecelerateInterpolator());
            return true;
        }
        return false;
    }

    private void populateData(Meta meta) {
        data = new ArrayList<>();
        data.add(new Pair<>(Meta.FIELD_SPECIAL_MEDIA_SRC, entryID.src));
        data.add(new Pair<>(Meta.FIELD_SPECIAL_MEDIA_ID, entryID.id));
        for (String key: meta.keySet()) {
            switch (Meta.getType(key)) {
                case STRING:
                    meta.getStrings(key).forEach(s -> data.add(new Pair<>(key, s)));
                    break;
                case LONG:
                    meta.getLongs(key).forEach(l -> data.add(new Pair<>(key, String.valueOf(l))));
                    break;
                case DOUBLE:
                    meta.getDoubles(key).forEach(d -> data.add(new Pair<>(key, String.valueOf(d))));
                    break;
                default:
                    Log.e(LC, "Unhandled key type: " + key);
            }
        }
        Collections.sort(data, (left, right) -> {
            for (String key : Meta.FIELD_ORDER) {
                if (key.equals(left.first)) {
                    return -1;
                }
                if (key.equals(right.first)) {
                    return 1;
                }
            }
            return 0;
        });
    }

    private void initMetaRecView() {
        List<MetaTag> tagEntries = new ArrayList<>();
        for (Pair<String, String> tagData: data) {
            MetaTag tag = new MetaTag(entryID, tagData.first, tagData.second);
            tagEntries.add(tag);
        }
        recViewAdapter.setTagEntries(tagEntries);
    }

    boolean clearFocus() {
        boolean actionPerformed = false;
        if (selectionTracker != null && selectionTracker.hasSelection()) {
            selectionTracker.clearSelection();
            actionPerformed = true;
        }
        if (hideAddView()) {
            actionPerformed = true;
        }
        return actionPerformed;
    }

    private boolean onBackPressed() {
        boolean actionPerformed = false;
        if (recViewAdapter.clearFocus()) {
            actionPerformed = true;
        }
        if (clearFocus()) {
            actionPerformed = true;
        }
        if (actionPerformed) {
            return true;
        }
        dismiss();
        return true;
    }
}
