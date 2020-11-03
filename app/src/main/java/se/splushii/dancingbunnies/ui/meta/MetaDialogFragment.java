package se.splushii.dancingbunnies.ui.meta;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.ui.selection.RecyclerViewActionModeSelectionTracker;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_ADD;

public class MetaDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(MetaDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.meta_dialog";

    private EntryID entryID = EntryID.UNKOWN;
    private ArrayList<Pair<String, String>> data;

    private View addView;
    private AutoCompleteTextView addKeyEditText;
    private MutableLiveData<String> addValueKeyLiveData;
    private AutoCompleteTextView addValueEditText;
    private ImageButton addBtn;

    private MetaDialogFragmentAdapter recViewAdapter;
    private RecyclerViewActionModeSelectionTracker
            <MetaTag, MetaDialogFragmentAdapter.ViewHolder, MetaDialogFragmentAdapter>
            selectionTracker;
    private LiveData<Meta> metaLiveData;
    private SwitchCompat addLocalTagSwitch;

    private ArrayList<String> metaKeys;
    private ArrayAdapter<String> displayMetaKeysAdapter;

    public static void showDialog(FragmentManager fragmentManager, EntryID entryID) {
        if (entryID == null || entryID.isUnknown()) {
            return;
        }
        Util.showDialog(
                fragmentManager,
                null,
                TAG,
                MainActivity.REQUEST_CODE_NONE,
                new MetaDialogFragment(),
                entryID.toBundle()
        );
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setCancelable(false);
        Bundle args = getArguments();
        entryID = args == null ? EntryID.UNKOWN : EntryID.from(args);
        metaLiveData = MetaStorage.getInstance(requireContext()).getTrackMeta(entryID);
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

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.meta_dialog_fragment_layout, container, false);
        TextView title = rootView.findViewById(R.id.meta_dialog_title);
        addView = rootView.findViewById(R.id.meta_dialog_add_view);
        addLocalTagSwitch = rootView.findViewById(R.id.meta_dialog_add_local_switch);
        addKeyEditText = rootView.findViewById(R.id.meta_dialog_add_tag_key);
        metaKeys = new ArrayList<>();
        displayMetaKeysAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        addKeyEditText.setAdapter(displayMetaKeysAdapter);
        MetaStorage.getInstance(requireContext())
                .getTrackMetaKeys()
                .observe(getViewLifecycleOwner(), newFields -> {
                    metaKeys.clear();
                    metaKeys.addAll(newFields);
                    updateAddKeySuggestions();
                });

        addValueEditText = rootView.findViewById(R.id.meta_dialog_add_tag_value);
        ArrayAdapter<String> addValueAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item
        );
        addValueEditText.setAdapter(addValueAdapter);
        addValueKeyLiveData = new MutableLiveData<>();
        Transformations.switchMap(
                addValueKeyLiveData,
                key -> MetaStorage.getInstance(requireContext()).getTrackMetaValuesAsStrings(key)
        ).observe(getViewLifecycleOwner(), values -> {
            addValueAdapter.clear();
            addValueAdapter.addAll(values);
            addValueAdapter.notifyDataSetChanged();
        });
        addValueEditText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                updateAddValueSuggestions();
            }
        });
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

        boolean metaAddSupported = APIClient.getAPIClient(requireContext(), entryID.src)
                .supports(META_ADD, entryID.src);
        addLocalTagSwitch.setEnabled(metaAddSupported);
        addLocalTagSwitch.setChecked(!metaAddSupported);
        AtomicBoolean userSelectedLocalTag = new AtomicBoolean(false);
        addLocalTagSwitch.setOnTouchListener((v, event) -> {
            userSelectedLocalTag.set(true);
            return false;
        });
        addLocalTagSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (userSelectedLocalTag.get()) {
                userSelectedLocalTag.set(false);
                updateAddKeySuggestions();
                updateAddValueSuggestions();
            }
        });

        addBtn.setOnClickListener(v -> {
            boolean showAddView = addView.getVisibility() != View.VISIBLE;
            title.setText(showAddView ? R.string.metadata_context_add : R.string.metadata);
            if (showAddView) {
                addBtn.animate().rotation(45f).setInterpolator(new AccelerateDecelerateInterpolator());
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
                if (isLocalUserTag()) {
                    // TODO: Support adding other local user tag types (long, double)
                    String userLocalKey = Meta.constructLocalUserStringKey(key);
                    TransactionStorage.getInstance(getContext()).addMeta(
                            requireContext(),
                            MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                            entryID,
                            userLocalKey,
                            value
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

    private void updateAddValueSuggestions() {
        String key = addKeyEditText.getText().toString();
        if (isLocalUserTag()) {
            key = Meta.constructLocalUserStringKey(key);
        }
        if (!key.equals(addValueKeyLiveData.getValue())) {
            addValueKeyLiveData.setValue(key);
        }
    }

    private void updateAddKeySuggestions() {
        displayMetaKeysAdapter.clear();
        boolean isLocalUser = isLocalUserTag();
        displayMetaKeysAdapter.addAll(metaKeys.stream()
                .filter(key -> {
                    if (Meta.isSpecial(key)) {
                        return false;
                    }
                    if (Meta.isLocalUser(key)) {
                        return isLocalUser;
                    } else {
                        return !isLocalUser;
                    }
                })
                .map(key -> isLocalUser ? Meta.getUserLocalTagName(key) : key)
                .collect(Collectors.toList())
        );
        displayMetaKeysAdapter.notifyDataSetChanged();
    }

    private boolean isLocalUserTag() {
        return addLocalTagSwitch.isChecked();
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
        data.add(new Pair<>(Meta.FIELD_SPECIAL_ENTRY_SRC, entryID.src));
        data.add(new Pair<>(Meta.FIELD_SPECIAL_ENTRY_ID_TRACK, entryID.id));
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
