package se.splushii.dancingbunnies.ui.meta;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.ActionModeCallback;
import se.splushii.dancingbunnies.ui.MenuActions;
import se.splushii.dancingbunnies.ui.TrackItemActionsView;
import se.splushii.dancingbunnies.ui.selection.ItemDetailsViewHolder;
import se.splushii.dancingbunnies.ui.selection.SelectionRecyclerViewAdapter;
import se.splushii.dancingbunnies.util.Util;

public class MetaDialogFragmentAdapter extends
        SelectionRecyclerViewAdapter<MetaTag, MetaDialogFragmentAdapter.ViewHolder> {
    private static final String LC = Util.getLogContext(MetaDialogFragmentAdapter.class);

    private final MetaDialogFragment fragment;

    private List<MetaTag> tagEntries;
    private TrackItemActionsView selectedActionView;
    private ViewHolder currentEditViewHolder;

    MetaDialogFragmentAdapter(MetaDialogFragment fragment) {
        this.fragment = fragment;
        tagEntries = new ArrayList<>();
        setHasStableIds(true);
    }

    void setTagEntries(List<MetaTag> entries) {
        Log.d(LC, "setTagEntries: "
                + "curSize: " + tagEntries.size()
                + " newSize " + entries.size());
        boolean changed = !tagEntries.equals(entries);
        if (changed) {
            tagEntries = entries;
            notifyDataSetChanged();
        }
    }

    @Override
    protected void onSelectionChanged() {
        if (hasSelection()) {
            hideTrackItemActions();
            disableCurrentEdit();
        }
    }

    @Override
    protected MetaTag getKey(int pos) {
        if (pos < 0 || pos >= tagEntries.size()) {
            return null;
        }
        return tagEntries.get(pos);
    }

    @Override
    protected int getPosition(@NonNull MetaTag key) {
        int index = tagEntries.indexOf(key);
        return index < 0 ? RecyclerView.NO_POSITION : index;
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
    public boolean onDragInitiated(Selection<MetaTag> selection) {
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

    @Override
    protected void moveItemInDataset(int from, int to) {
        throw new RuntimeException("Not supported");
    }

    @Override
    protected void addItemToDataset(int pos, MetaTag item) {
        throw new RuntimeException("Not supported");
    }

    @Override
    protected void removeItemFromDataset(int pos) {
        throw new RuntimeException("Not supported");
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
        private final EditText editValueEditText;

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
                boolean showActionsView = !hasSelection()
                        && actionsView.getVisibility() != View.VISIBLE;
                actionsView.animateShow(showActionsView);
            });
            editValueEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String newValue = editValueEditText.getText().toString();
                    if (Meta.isLocalUser(tag.key)) {
                        MetaStorage.getInstance(fragment.getContext()).replaceLocalUserMeta(
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

        }

        @Override
        protected int getPositionOf() {
            return getAdapterPosition();
        }

        @Override
        protected MetaTag getSelectionKeyOf() {
            return tagEntries.get(getPositionOf());
        }

        void setTag(MetaTag tag) {
            this.tag = tag;
            String displayKey = Meta.getDisplayKey(tag.key);
            String displayValue = Meta.getDisplayValue(tag.key, tag.value);
            keyTextView.setText(displayKey);
            valueTextView.setText(displayValue);

            boolean metaDelSupported = MusicLibraryService
                    .checkAPISupport(tag.entryID.src, MusicLibraryService.META_DELETE);
            boolean metaEditSupported = MusicLibraryService
                    .checkAPISupport(tag.entryID.src, MusicLibraryService.META_EDIT);
            boolean tagEditable = Meta.isLocalUser(tag.key) || metaEditSupported;
            boolean tagDeletable = Meta.isLocalUser(tag.key) || metaDelSupported;
            int[] disabledActions;
            if (tagEditable && tagDeletable) {
                disabledActions = new int[0];
            } else if (tagEditable) {
                disabledActions = new int[] {
                        MenuActions.ACTION_DELETE_META
                };
            } else if (tagDeletable) {
                disabledActions = new int[] {
                        MenuActions.ACTION_EDIT_META
                };
            } else {
                disabledActions = new int[]{
                        MenuActions.ACTION_EDIT_META,
                        MenuActions.ACTION_DELETE_META
                };
            }
            actionsView.setActions(
                    new int[] {
                            MenuActions.ACTION_GOTO_META,
                            MenuActions.ACTION_EDIT_META
                    },
                    new int[] {
                            MenuActions.ACTION_GOTO_META,
                            MenuActions.ACTION_EDIT_META,
                            MenuActions.ACTION_DELETE_META
                    },
                    disabledActions
            );
            actionsView.initialize();
            actionsView.setPostAction(action -> {
                if (action == MenuActions.ACTION_GOTO_META) {
                    fragment.dismiss();
                } else if (action == MenuActions.ACTION_EDIT_META) {
                    showEdit();
                } else if (action == MenuActions.ACTION_DELETE_META) {
                    if (Meta.isLocalUser(tag.key)) {
                        MetaStorage.getInstance(fragment.getContext()).deleteLocalUserMeta(
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
        return tagEntries.get(position).hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(layoutInflater.inflate(
                R.layout.meta_dialog_list_item,
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        MetaTag metaTag = tagEntries.get(position);
        holder.setTag(metaTag);
        holder.item.setActivated(isSelected(holder.getKey()));
    }

    @Override
    public int getItemCount() {
        return tagEntries.size();
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