package se.splushii.dancingbunnies.ui.meta;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.Selection;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.MenuActions;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SmartDiffSelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_DELETE;
import static se.splushii.dancingbunnies.storage.transactions.Transaction.META_EDIT;

public class MetaDialogFragmentAdapter extends
        SmartDiffSelectionRecyclerViewAdapter<MetaTag, MetaDialogFragmentAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(MetaDialogFragmentAdapter.class);

    private final MetaDialogFragment fragment;

    private TrackItemActionsView selectedActionView;
    private ViewHolder currentEditViewHolder;

    MetaDialogFragmentAdapter(MetaDialogFragment fragment) {
        this.fragment = fragment;
        setHasStableIds(true);
    }

    void setTagEntries(List<MetaTag> entries) {
        Log.d(LC, "setTagEntries: "
                + "curSize: " + getSize()
                + " newSize " + entries.size());
        setDataSet(entries);
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection()) {
            hideTrackItemActions();
            disableCurrentEdit();
        }
    }

    @Override
    public void onSelectionDrop(Collection<MetaTag> selection,
                                int targetPos,
                                MetaTag idAfterTargetPos) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onUseViewHolderForDrag(ViewHolder dragViewHolder,
                                       Collection<MetaTag> selection) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void onResetDragViewHolder(ViewHolder dragViewHolder) {
        throw new RuntimeException("Not supported");
    }

    private void updateActionModeView(ActionModeCallback actionModeCallback,
                                      Selection<MetaTag> selection) {
        actionModeCallback.getActionMode().setTitle(selection.size() + " entries");
    }

    @Override
    public void onActionModeStarted(ActionModeCallback actionModeCallback,
                                    Selection<MetaTag> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeSelectionChanged(ActionModeCallback actionModeCallback,
                                             Selection<MetaTag> selection) {
        updateActionModeView(actionModeCallback, selection);
    }

    @Override
    public void onActionModeEnding(ActionModeCallback actionModeCallback) {
    }

    @Override
    public boolean validDrag(Selection<MetaTag> selection) {
        return false;
    }

    @Override
    public boolean validSelect(MetaTag key) {
        return false;
    }

    @Override
    public boolean validMove(ViewHolder current, ViewHolder target) {
        return false;
    }

    @Override
    public boolean validDrag(ViewHolder viewHolder) {
        return false;
    }

    boolean hideTrackItemActions() {
        if (selectedActionView != null) {
            selectedActionView.animateShow(false);
            selectedActionView = null;
            return true;
        }
        return false;
    }

    private boolean disableCurrentEdit() {
        if (currentEditViewHolder != null) {
            currentEditViewHolder.hideEdit();
            currentEditViewHolder = null;
            return true;
        }
        return false;
    }

    public class ViewHolder extends ItemDetailsViewHolder<MetaTag> {
        private final View item;
        private final TrackItemActionsView actionsView;
        private final TextView keyTextView;
        private final TextView valueTextView;
        private final AutoCompleteTextView editValueEditText;
        private final MutableLiveData<String> editValueKeyLiveData;

        private MetaTag tag;

        ViewHolder(View v) {
            super(v);
            item = v.findViewById(R.id.meta_dialog_item);
            actionsView = v.findViewById(R.id.meta_dialog_item_actions);
            keyTextView = v.findViewById(R.id.meta_dialog_list_key);
            valueTextView = v.findViewById(R.id.meta_dialog_list_value);
            editValueEditText = v.findViewById(R.id.meta_dialog_list_value_edit);
            item.setOnClickListener(view -> {
                if (hasSelection()) {
                    return;
                }
                if (selectedActionView != actionsView) {
                    hideTrackItemActions();
                }
                disableCurrentEdit();
                fragment.clearFocus();
                selectedActionView = actionsView;
                actionsView.animateShow(actionsView.getVisibility() != View.VISIBLE);
            });
            editValueEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String newValue = editValueEditText.getText().toString();
                    if (Meta.isLocalUser(tag.key)) {
                        TransactionStorage.getInstance(fragment.requireContext()).editMeta(
                                fragment.requireContext(),
                                MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                                tag.entryID,
                                tag.key,
                                tag.value,
                                newValue
                        );
                    } else {
                        // TODO: Implement
                        Log.e(LC, "Editing non-local user tag not implemented");
                        throw new RuntimeException("Editing non-local user tag not implemented");
                    }
                    hideEdit();
                    return true;
                }
                return false;
            });
            ArrayAdapter<String> editValueAdapter = new ArrayAdapter<>(
                    v.getContext(),
                    android.R.layout.simple_spinner_dropdown_item
            );
            editValueEditText.setAdapter(editValueAdapter);
            editValueKeyLiveData = new MutableLiveData<>();
            Transformations.switchMap(
                    editValueKeyLiveData,
                    key -> MetaStorage.getInstance(v.getContext()).getMetaValuesAsStrings(key)
            ).observe(fragment.getViewLifecycleOwner(), values -> {
                editValueAdapter.clear();
                editValueAdapter.addAll(values);
                editValueAdapter.notifyDataSetChanged();
            });
        }

        @Override
        protected MetaTag getSelectionKeyOf() {
            return getItem(getPos());
        }

        void setTag(MetaTag tag) {
            this.tag = tag;
            String displayKey = Meta.getDisplayKey(tag.key);
            String displayValue = Meta.getDisplayValue(tag.key, tag.value);
            editValueKeyLiveData.setValue(tag.key);
            keyTextView.setText(displayKey);
            valueTextView.setText(displayValue);

            APIClient apiClient = APIClient.getAPIClient(fragment.requireContext(), tag.entryID.src);
            boolean metaDelSupported = apiClient.supports(META_DELETE, null);
            boolean metaEditSupported = apiClient.supports(META_EDIT, null);
            boolean tagEditable = Meta.isLocalUser(tag.key) || metaEditSupported;
            boolean tagDeletable = Meta.isLocalUser(tag.key) || metaDelSupported;
            int[] disabledActions;
            if (tagEditable && tagDeletable) {
                disabledActions = new int[0];
            } else if (tagEditable) {
                disabledActions = new int[] {
                        MenuActions.ACTION_META_DELETE
                };
            } else if (tagDeletable) {
                disabledActions = new int[] {
                        MenuActions.ACTION_META_EDIT
                };
            } else {
                disabledActions = new int[]{
                        MenuActions.ACTION_META_EDIT,
                        MenuActions.ACTION_META_DELETE
                };
            }
            actionsView.setActions(
                    new int[] {
                            MenuActions.ACTION_META_GOTO,
                            MenuActions.ACTION_META_EDIT
                    },
                    new int[] {
                            MenuActions.ACTION_META_GOTO,
                            MenuActions.ACTION_META_EDIT,
                            MenuActions.ACTION_META_DELETE
                    },
                    disabledActions
            );
            actionsView.setPostAction(action -> {
                if (action == MenuActions.ACTION_META_GOTO) {
                    fragment.dismiss();
                } else if (action == MenuActions.ACTION_META_EDIT) {
                    showEdit();
                } else if (action == MenuActions.ACTION_META_DELETE) {
                    if (Meta.isLocalUser(tag.key)) {
                        TransactionStorage.getInstance(fragment.getContext()).deleteMeta(
                                fragment.requireContext(),
                                MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL,
                                tag.entryID,
                                tag.key,
                                tag.value
                        );
                    } else {
                        // TODO: Implement
                        Log.e(LC, "Adding non-local user tag not implemented");
                        throw new RuntimeException("Adding non-local user tag not implemented");
                    }
                }
            });
            actionsView.setMetaDialogFragment(fragment);
            actionsView.setMetaTagSupplier(() -> tag);
            actionsView.setEntryIDSupplier(() -> tag.entryID);
        }

        private void showEdit() {
            valueTextView.setVisibility(View.GONE);
            editValueEditText.setText(tag.value);
            editValueEditText.setVisibility(View.VISIBLE);
            editValueEditText.setActivated(true);
            editValueEditText.requestFocus();
            Util.showSoftInput(fragment.requireActivity(), editValueEditText);
            currentEditViewHolder = this;
        }

        private void hideEdit() {
            Util.hideSoftInput(fragment.requireActivity(), editValueEditText);
            editValueEditText.setVisibility(View.GONE);
            editValueEditText.setActivated(false);
            editValueEditText.setText("");
            valueTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ViewHolder holder = new ViewHolder(layoutInflater.inflate(
                R.layout.meta_dialog_list_item,
                parent,
                false
        ));
        holder.actionsView.initialize();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        MetaTag metaTag = getItem(position);
        holder.setTag(metaTag);
        holder.item.setActivated(isSelected(holder.getKey()));
    }

    boolean clearFocus() {
        boolean actionPerformed = false;
        if (hideTrackItemActions()) {
            actionPerformed = true;
        }
        if (disableCurrentEdit()) {
            actionPerformed = true;
        }
        return actionPerformed;
    }
}