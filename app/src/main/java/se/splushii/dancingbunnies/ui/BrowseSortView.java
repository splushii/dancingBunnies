package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class BrowseSortView extends LinearLayout {
    private static final String LC = Util.getLogContext(BrowseSortView.class);

    private static final int MENU_ITEM_ID_SORT_BY_HEADER = 1;
    private static final int MENU_GROUP_ID_SORT_BY_HEADER = 0;
    private static final int MENU_GROUP_ID_SORT_BY_CUSTOM = 1;
    private static final int MENU_GROUP_ID_SORT_BY_SINGLE = 2;
    private static final int MENU_GROUP_ORDER_SORT_BY_HEADER = Menu.FIRST;
    private static final int MENU_GROUP_ORDER_SORT_BY_CUSTOM = Menu.FIRST + 1;
    private static final int MENU_GROUP_ORDER_SORT_BY_SINGLE = Menu.FIRST + 2;

    private LinearLayout rootView;
    private LinearLayout browseHeaderSortedByKeysView;
    private ImageView browseHeaderSortedByOrderView;
    private Menu browseHeaderSortedByMenu;

    private Callback callback;

    public BrowseSortView(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public BrowseSortView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public BrowseSortView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.browse_sort_view_layout, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs,
                R.styleable.BrowseSortView,
                defStyleAttr,
                defStyleRes
        );
        a.recycle();

        rootView = findViewById(R.id.browse_sort_root);
        browseHeaderSortedByKeysView = findViewById(R.id.browse_sort_keys);
        browseHeaderSortedByOrderView = findViewById(R.id.browse_sort_order);

        final PopupMenu browseHeaderSortedByPopup = new PopupMenu(getContext(), rootView);
        browseHeaderSortedByMenu = browseHeaderSortedByPopup.getMenu();
        browseHeaderSortedByMenu.add(
                MENU_GROUP_ID_SORT_BY_CUSTOM,
                Menu.NONE,
                MENU_GROUP_ORDER_SORT_BY_CUSTOM,
                "Custom sort"
        ).setIcon(
                R.drawable.ic_edit_black_24dp
        ).setIconTintList(
                ContextCompat.getColorStateList(getContext(), R.color.text_active_on_accent)
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            browseHeaderSortedByMenu.setGroupDividerEnabled(true);
        }
        browseHeaderSortedByPopup.setForceShowIcon(true);
        browseHeaderSortedByPopup.setOnMenuItemClickListener(this::onSortedBySelected);
        rootView.setOnClickListener(view -> browseHeaderSortedByPopup.show());
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private boolean onSortedBySelected(MenuItem item) {
        int groupId = item.getGroupId();
        int itemId = item.getItemId();
        if (groupId == MENU_GROUP_ID_SORT_BY_HEADER && itemId == MENU_ITEM_ID_SORT_BY_HEADER) {
            item.setIcon(!callback.isSortedAscending() ?
                    R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp
            ).setIconTintList(
                    ContextCompat.getColorStateList(getContext(), R.color.text_active_on_accent)
            );
            callback.setSortOrder(!callback.isSortedAscending());
            return true;
        }
        if (groupId == MENU_GROUP_ID_SORT_BY_CUSTOM) {
            EntryTypeSelectionDialogFragment.showDialogForSortConfig(
                    callback.getTargetFragment(),
                    callback.getSortByMetaKeys(),
                    callback.getEntryType()
            );
            return true;
        }
        if (groupId != MENU_GROUP_ID_SORT_BY_SINGLE) {
            return false;
        }
        String metaKey = callback.getMetaKey(itemId);
        String metaKeyForDisplay = callback.getMetaKeyForDisplay(itemId);
        Log.e(LC, "itemId: " + itemId + " key: " + metaKey + " oman: " + metaKeyForDisplay);
        Log.d(LC, "Sorting by: " + metaKeyForDisplay);
        Toast.makeText(
                getContext(),
                "Sorting by: " + metaKeyForDisplay,
                Toast.LENGTH_SHORT
        ).show();
        callback.sortBy(Collections.singletonList(metaKey));
        return true;
    }

    public void onMetaKeyForDisplayChanged(List<String> metaKeysForDisplay) {
        browseHeaderSortedByMenu.removeGroup(MENU_GROUP_ID_SORT_BY_SINGLE);
        for (int i = 0; i < metaKeysForDisplay.size(); i++) {
            browseHeaderSortedByMenu.add(
                    MENU_GROUP_ID_SORT_BY_SINGLE,
                    i,
                    MENU_GROUP_ORDER_SORT_BY_SINGLE,
                    metaKeysForDisplay.get(i)
            );
        }
    }

    public void onSortedBy(List<String> metaKeys) {
        setSortByMenuHeader(getSortedByDisplayString(
                metaKeys,
                null,
                callback.getShowMetaKey(),
                false
        ));
        setSortedByColumns(metaKeys);
    }

    public void onSortedByAscending(boolean ascending) {
        int sortOrderResource = ascending ?
                R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp;
        browseHeaderSortedByOrderView.setImageResource(sortOrderResource);
    }

    public static String getSortedByDisplayString(List<String> metaKeys,
                                                  List<String> metaValues,
                                                  String showMetaKey,
                                                  boolean excludeShowMetaKey) {
        if (metaKeys == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < metaKeys.size(); i++) {
            String key = metaKeys.get(i);
            if (excludeShowMetaKey && showMetaKey.equals(key)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                sb.append("; ");
            }
            if (metaValues == null) {
                // Show keys
                sb.append(Meta.getDisplayKey(key));
            } else {
                // Show values
                String value = i >= metaValues.size() ? "" : metaValues.get(i);
                sb.append(value == null ? "" : Meta.getDisplayValue(key, value));
            }
        }
        return sb.toString();
    }

    private void setSortByMenuHeader(String header) {
        browseHeaderSortedByMenu.removeGroup(MENU_GROUP_ID_SORT_BY_HEADER);
        browseHeaderSortedByMenu.add(
                MENU_GROUP_ID_SORT_BY_HEADER,
                Menu.NONE,
                MENU_GROUP_ORDER_SORT_BY_HEADER,
                "Sorting shown entries by:"
        ).setEnabled(false);
        browseHeaderSortedByMenu.add(
                MENU_GROUP_ID_SORT_BY_HEADER,
                MENU_ITEM_ID_SORT_BY_HEADER,
                MENU_GROUP_ORDER_SORT_BY_HEADER,
                header
        ).setIcon(callback.isSortedAscending() ?
                R.drawable.ic_arrow_drop_down_black_24dp : R.drawable.ic_arrow_drop_up_black_24dp
        ).setIconTintList(
                ContextCompat.getColorStateList(getContext(), R.color.text_active_on_accent)
        );
    }

    public static void setSortedByColumns(Context context,
                                          LinearLayout rootView,
                                          LinearLayout columnRootView,
                                          List<String> metaKeys,
                                          List<String> metaValues,
                                          String showMetaKey,
                                          boolean isHeader) {
        columnRootView.removeAllViews();
        TableLayout.LayoutParams textViewLayoutParams = new TableLayout.LayoutParams(
                0,
                TableLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        int columnCount = 0;
        for (int i = 0; i < metaKeys.size(); i++) {
            String key = metaKeys.get(i);
            if (showMetaKey.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_TRACK.equals(showMetaKey)
                    && Meta.FIELD_TITLE.equals(key)
                    || Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(showMetaKey)
                    && Meta.FIELD_TITLE.equals(key)) {
                continue;
            }
            TextView tv = new TextView(context);
            tv.setLayoutParams(textViewLayoutParams);
            switch (Meta.getType(key)) {
                default:
                case STRING:
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    break;
                case LONG:
                case DOUBLE:
                    tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    break;
            }
            tv.setTextColor(ContextCompat.getColorStateList(context, R.color.text_active_on_accent));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isHeader ? 14 : 12);
            tv.setSingleLine();
            tv.setEllipsize(TextUtils.TruncateAt.END);
            if (isHeader) {
                tv.setText(Meta.getDisplayKey(key));
            } else {
                String value = metaValues == null || i >= metaValues.size() ? "" : metaValues.get(i);
                tv.setText(value == null ? "" : Meta.getDisplayValue(key, value));
            }
            int padding = Util.dpToPixels(context, 4);
            tv.setPadding(padding, 0, padding, 0);
            columnRootView.addView(tv);
            columnCount++;
        }
        LinearLayout.LayoutParams rootLayoutParams =
                (LinearLayout.LayoutParams) rootView.getLayoutParams();
        rootLayoutParams.weight = columnCount;
        rootLayoutParams.width = columnCount == 0 && isHeader ? Util.dpToPixels(context, 32) : 0;
        rootView.setLayoutParams(rootLayoutParams);
    }

    public void setSortedByColumns(List<String> metaKeys) {
        setSortedByColumns(
                getContext(),
                this,
                browseHeaderSortedByKeysView,
                metaKeys,
                null,
                callback.getShowMetaKey(),
                true
        );
    }

    public interface Callback {
        void setSortOrder(boolean ascending);
        void sortBy(List<String> metaKeys);
        Fragment getTargetFragment();
        String getShowMetaKey();
        List<String> getSortByMetaKeys();
        boolean isSortedAscending();
        String getMetaKey(int i);
        String getMetaKeyForDisplay(int i);
        String getEntryType();
    }
}
