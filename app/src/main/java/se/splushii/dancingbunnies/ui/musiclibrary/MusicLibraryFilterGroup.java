package se.splushii.dancingbunnies.ui.musiclibrary;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.function.Consumer;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryLeaf;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryTree;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryFilterGroup extends LinearLayout {
    private static final String LC = Util.getLogContext(MusicLibraryFilterGroup.class);
    private TextView operatorView;
    private Chip newBtn;
    private ChipGroup filters;
    private Chip activeChip;

    private View newSelectionView;

    private View newView;
    private Spinner newType;
    private Spinner newOp;
    private AutoCompleteTextView newInput;
    private View editView;
    private TextView editType;
    private Spinner editOp;
    private AutoCompleteTextView editInput;

    private NewListener newListener;
    private Consumer<MusicLibraryQueryTree.Op> onOpChangedCb;

    private MusicLibraryQueryTree.Op operator;

    public MusicLibraryFilterGroup(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public MusicLibraryFilterGroup(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public MusicLibraryFilterGroup(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    public MusicLibraryFilterGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.musiclibrary_filter_group, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.MusicLibraryFilterGroup, defStyleAttr, defStyleRes);
        a.recycle();
        operatorView = findViewById(R.id.musiclibrary_filter_group_operator);
        newBtn = findViewById(R.id.musiclibrary_filter_group_new_btn);
        filters = findViewById(R.id.musiclibrary_filter_group_filters);
        newSelectionView = findViewById(R.id.musiclibrary_filter_group_new_selection);
        View newSelectionAll = findViewById(R.id.musiclibrary_filter_group_new_selection_all);
        View newSelectionAny = findViewById(R.id.musiclibrary_filter_group_new_selection_any);
        View newSelectionFilter = findViewById(R.id.musiclibrary_filter_group_new_selection_filter);
        newView = findViewById(R.id.musiclibrary_filter_group_new);
        newType = findViewById(R.id.musiclibrary_filter_group_new_type);
        newOp = findViewById(R.id.musiclibrary_filter_group_new_operator);
        newInput = findViewById(R.id.musiclibrary_filter_group_new_text);
        editView = findViewById(R.id.musiclibrary_filter_group_edit);
        editType = findViewById(R.id.musiclibrary_filter_group_edit_type);
        editOp = findViewById(R.id.musiclibrary_filter_group_edit_operator);
        editInput = findViewById(R.id.musiclibrary_filter_group_edit_input);

        operatorView.setOnClickListener(view -> {
            setOperator(operator == MusicLibraryQueryTree.Op.AND ?
                    MusicLibraryQueryTree.Op.OR : MusicLibraryQueryTree.Op.AND);
            onOpChangedCb.accept(operator);
        });

        newBtn.setOnClickListener(view -> {
            deactivate(false, false, true, false, true);
            boolean activate = !newBtn.isActivated();
            activateNewBtn(activate);
            if (newView.getVisibility() != VISIBLE
                     && newSelectionView.getVisibility() != VISIBLE) {
                newSelectionView.setVisibility(VISIBLE);
                newListener.onActivated();
            } else {
                deactivate();
            }
        });

        newSelectionAll.setOnClickListener(view -> {
            newListener.onSubQuerySubmit(MusicLibraryQueryTree.Op.AND);
            deactivate();
        });

        newSelectionAny.setOnClickListener(view -> {
            newListener.onSubQuerySubmit(MusicLibraryQueryTree.Op.OR);
            deactivate();
        });

        newSelectionFilter.setOnClickListener(view -> {
            deactivate(false, true, true, false, true);
            newView.setVisibility(VISIBLE);
            newInput.requestFocus();
            Util.showSoftInput(getContext(), newInput);
        });

        newType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                int pos = newType.getSelectedItemPosition();
                String key = MusicLibraryFilterGroup.this.onNewTypeSelected(pos);
                newInput.setInputType(Meta.getInputType(key));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        newInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                int typePos = newType.getSelectedItemPosition();
                int opPos = newOp.getSelectedItemPosition();
                String input = newInput.getText().toString();
                newListener.onFilterSubmit(typePos, opPos, input);
                deactivate();
                return true;
            }
            return false;
        });
    }

    private Chip addChip(String text, int iconResource) {
        Chip newChip = new Chip(getContext());
        newChip.setEllipsize(TextUtils.TruncateAt.END);
        newChip.setChipBackgroundColorResource(R.color.accent_active_accentdark);
        newChip.setTextColor(ContextCompat.getColorStateList(
                getContext(),
                R.color.white_active_accent_extra_light
        ));
        newChip.setChipIconResource(iconResource);
        newChip.setText(text);
        newChip.setCloseIconVisible(true);
        filters.addView(newChip, filters.getChildCount());
        return newChip;
    }

    public void addLeafFilter(String key,
                              MusicLibraryQueryLeaf.Op op,
                              String value,
                              ArrayAdapter<String> autoCompleteValuesAdapter,
                              LeafFilterListener listener) {
        editInput.setAdapter(autoCompleteValuesAdapter);
        Chip newChip = addChip(String.format(
                "%s %s %s",
                Meta.getDisplayKey(key),
                MusicLibraryQueryLeaf.getDisplayableOp(op),
                Meta.getDisplayValue(key, value)
        ), R.drawable.ic_filter_tilt_shift_black_24dp);
        newChip.setOnClickListener(v -> {
            deactivate(true, true, false, true, false);
            if (toggleFilterChip(newChip)) {
                editOp.setSelection(MusicLibraryQueryLeaf.getOps(key).indexOf(op));
                editInput.setText(value);
                editInput.setInputType(Meta.getInputType(key));
                editInput.setOnEditorActionListener((v1, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        String filterString = editInput.getText().toString();
                        int opPos = editOp.getSelectedItemPosition();
                        deactivate();
                        listener.onSubmit(opPos, filterString);
                        return true;
                    }
                    return false;
                });
                String filterEditTypeText = Meta.getDisplayKey(key) + ':';
                editType.setText(filterEditTypeText);
                editView.setVisibility(VISIBLE);
                editInput.requestFocus();
                Util.showSoftInput(getContext(), editInput);
                listener.onActivated();
            } else {
                deactivate();
                listener.onDeactivated();
            }
        });
        newChip.setOnCloseIconClickListener(v -> {
            deactivate();
            listener.onClose();
        });
    }

    public void addTreeFilter(MusicLibraryQueryTree.Op operator,
                              TreeFilterListener listener) {
        Chip newChip = addChip(
                operator == MusicLibraryQueryTree.Op.AND ? "All of" : "Any of",
                operator == MusicLibraryQueryTree.Op.AND ?
                        R.drawable.ic_keyboard_arrow_up_black_24dp
                        : R.drawable.ic_keyboard_arrow_down_black_24dp
        );
        newChip.setOnClickListener(v -> {
            deactivate(true, true, true, true, false);
            if (toggleFilterChip(newChip)) {
                listener.onActivated();
            } else {
                deactivate();
                listener.onDeactivated();
            }
        });
        newChip.setOnCloseIconClickListener(v -> {
            deactivate();
            listener.onClosed();
        });
    }

    private boolean toggleFilterChip(Chip chip) {
        if (activeChip != null && activeChip != chip) {
            activeChip.setActivated(false);
        }
        boolean activate = !chip.isActivated();
        chip.setActivated(activate);
        activeChip = activate ? chip : activeChip;
        return activate;
    }

    public void activateNewBtn(boolean activate) {
        newBtn.setActivated(activate);
        newBtn.animate().rotation(activate ? 45f : 0f).setInterpolator(new AccelerateDecelerateInterpolator());
    }

    public void activate(int index) {
        if (index >= filters.getChildCount()) {
            return;
        }
        toggleFilterChip((Chip)filters.getChildAt(index));
    }

    public void deactivate() {
        deactivate(true, true, true, true, true);
    }

    public void deactivate(boolean deactivateNewBtn,
                           boolean deactivateNewSelection,
                           boolean deactivateEdit,
                           boolean deactivateNew,
                           boolean deactivateChip) {
        if (deactivateNewBtn) {
            activateNewBtn(false);
        }
        if (deactivateNewSelection && newSelectionView.getVisibility() == VISIBLE) {
            newSelectionView.setVisibility(GONE);
        }
        if (deactivateNew && newView.getVisibility() == VISIBLE) {
            Util.hideSoftInput(getContext(), newView);
            newView.setVisibility(GONE);
        }
        if (deactivateEdit && editView.getVisibility() == VISIBLE) {
            Util.hideSoftInput(getContext(), editInput);
            editView.setVisibility(GONE);
        }
        if (deactivateChip && activeChip != null) {
            activeChip.setActivated(false);
        }
    }

    public void setOperator(MusicLibraryQueryTree.Op operator) {
        this.operator = operator;
        switch (operator) {
            default:
            case AND:
                operatorView.setText("All of:");
                break;
            case OR:
                operatorView.setText("Any of:");
                break;
        }
    }

    public void setOnOperatorChanged(Consumer<MusicLibraryQueryTree.Op> onOpChanged) {
        onOpChangedCb = onOpChanged;
    }

    public void setAdapters(ArrayAdapter<String> autoCompleteValuesAdapter,
                            ArrayAdapter<String> typeAdapter,
                            ArrayAdapter<String> operatorAdapter) {
        newInput.setAdapter(autoCompleteValuesAdapter);
        newType.setAdapter(typeAdapter);
        newType.setSelection(0);
        newOp.setAdapter(operatorAdapter);
        newOp.setSelection(0);
        editOp.setAdapter(operatorAdapter);
        editOp.setSelection(0);
    }

    public void setNewListener(NewListener listener) {
        newListener = listener;
    }

    private String onNewTypeSelected(int pos) {
        if (newListener != null) {
            return newListener.onTypeSelected(pos);
        }
        return null;
    }

    public interface NewListener {
        void onActivated();
        String onTypeSelected(int typePos);
        void onFilterSubmit(int typePos, int opPos, String input);
        void onSubQuerySubmit(MusicLibraryQueryTree.Op op);
    }

    public interface LeafFilterListener {
        void onActivated();
        void onDeactivated();
        void onSubmit(int opPos, String input);
        void onClose();
    }

    public interface TreeFilterListener {
        void onActivated();
        void onDeactivated();
        void onClosed();
    }
}
