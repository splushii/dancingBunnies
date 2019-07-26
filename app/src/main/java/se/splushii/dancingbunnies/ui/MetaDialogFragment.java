package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class MetaDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(MetaDialogFragment.class);

    public static final String TAG = "dancingbunnies.splushii.se.fragment_tag.meta_dialog";

    private Meta meta = Meta.UNKNOWN_ENTRY;

    public static void showMeta(Fragment fragment, Meta meta) {
        if (meta == null) {
            return;
        }
        FragmentTransaction ft = fragment.getFragmentManager().beginTransaction();
        Fragment prev = fragment.getFragmentManager().findFragmentByTag(MetaDialogFragment.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment dialogFragment = new MetaDialogFragment();
        dialogFragment.setTargetFragment(fragment, MainActivity.REQUEST_CODE_META_DIALOG);
        dialogFragment.setArguments(meta.toBundle());
        dialogFragment.show(ft, MetaDialogFragment.TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        meta = args == null ? Meta.UNKNOWN_ENTRY : new Meta(args);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.meta_dialog_fragment_layout, container, false);
        ListView listView = v.findViewById(R.id.meta_dialog_list);
        ArrayList<Pair<String, String>> data = new ArrayList<>();
        data.add(new Pair<>(Meta.FIELD_SPECIAL_MEDIA_SRC, meta.entryID.src));
        data.add(new Pair<>(Meta.FIELD_SPECIAL_MEDIA_ID, meta.entryID.id));
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
        MetaDialogListAdapter adapter = new MetaDialogListAdapter(requireContext(), data);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Pair<String, String> metaItem = adapter.getItem(position);
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.putExtra(MainActivity.INTENT_EXTRA_FILTER_TYPE, metaItem.first);
            intent.putExtra(MainActivity.INTENT_EXTRA_FILTER_VALUE, metaItem.second);
            requireActivity().startActivity(intent);
            dismiss();
        });
        return v;
    }

    private class MetaDialogListAdapter extends ArrayAdapter<Pair<String, String>> {
        MetaDialogListAdapter(@NonNull Context context, @NonNull List<Pair<String, String>> objects) {
            super(context, 0, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.meta_dialog_list_item, parent, false);
            }
            convertView.setMinimumWidth(parent.getWidth());
            Pair<String, String> metaItem = getItem(position);
            String key = metaItem == null || metaItem.first == null ?
                    "<COULD NOT GET METADATA FIELD>" : metaItem.first;
            String value = metaItem == null || metaItem.second == null ?
                    "<COULD NOT GET METADATA VALUE>" : metaItem.second;
            TextView keyTextView = convertView.findViewById(R.id.meta_dialog_list_key);
            TextView valueTextView = convertView.findViewById(R.id.meta_dialog_list_value);
            keyTextView.setText(key);
            String displayValue = value;
            switch (key) {
                case Meta.FIELD_DURATION:
                    try {
                        displayValue = Util.getDurationString(Long.parseLong(value));
                    } catch (NumberFormatException ignored) {}
                    break;
                default:
                    break;
            }
            valueTextView.setText(displayValue);
            return convertView;
        }
    }
}
