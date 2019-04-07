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
import java.util.Arrays;
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

    public static final int REQUEST_CODE = 1337;
    public static final String TAG = "dancingbunnies.splushii.se.meta_dialog_tag";

    private Meta meta = Meta.UNKNOWN_ENTRY;
    private List<String> metaOrder = Arrays.asList(
            Meta.METADATA_KEY_TITLE,
            Meta.METADATA_KEY_ALBUM,
            Meta.METADATA_KEY_ARTIST,
            Meta.METADATA_KEY_YEAR,
            Meta.METADATA_KEY_GENRE,
            Meta.METADATA_KEY_DURATION,
            Meta.METADATA_KEY_TRACK_NUMBER,
            Meta.METADATA_KEY_DISC_NUMBER,
            Meta.METADATA_KEY_CONTENT_TYPE,
            Meta.METADATA_KEY_BITRATE,
            Meta.METADATA_KEY_API,
            Meta.METADATA_KEY_MEDIA_ROOT,
            Meta.METADATA_KEY_MEDIA_ID
    );

    public static void showMeta(Fragment fragment, Meta meta) {
        FragmentTransaction ft = fragment.getFragmentManager().beginTransaction();
        Fragment prev = fragment.getFragmentManager().findFragmentByTag(MetaDialogFragment.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment dialogFragment = new MetaDialogFragment();
        dialogFragment.setTargetFragment(fragment, MetaDialogFragment.REQUEST_CODE);
        if (meta != null) {
            dialogFragment.setArguments(meta.getBundle());
        }
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
        for (String key: meta.keySet()) {
            if (Meta.METADATA_KEY_TYPE.equals(key)) {
                continue;
            }
            switch (Meta.getType(key)) {
                case STRING:
                    String value = meta.getString(key);
                    if (value != null) {
                        data.add(new Pair<>(key, meta.getString(key)));
                    }
                    break;
                case LONG:
                    data.add(new Pair<>(key, String.valueOf(meta.getLong(key))));
                    break;
                case DOUBLE:
                    data.add(new Pair<>(key, String.valueOf(meta.getDouble(key))));
                    break;
                case BOOLEAN:
                    data.add(new Pair<>(key, String.valueOf(meta.getBoolean(key))));
                    break;
                default:
                case BITMAP:
                    Log.e(LC, "Unhandled key type: " + key);
            }
        }
        Collections.sort(data, (left, right) -> {
            for (String key : metaOrder) {
                if (key.equals(left.first)) {
                    return -1;
                }
                if (key.equals(right.first)) {
                    return 1;
                }
            }
            return 1;
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
            Pair<String, String> metaItem = getItem(position);
            String key = metaItem.first;
            String value = metaItem.second;
            TextView keyTextView = convertView.findViewById(R.id.meta_dialog_list_key);
            TextView valueTextView = convertView.findViewById(R.id.meta_dialog_list_value);
            keyTextView.setText(Meta.getHumanReadable(key));
            String displayValue = value;
            switch (key) {
                case Meta.METADATA_KEY_DURATION:
                    displayValue = Util.getDurationString(Long.parseLong(value));
                    break;
                default:
                    break;
            }
            valueTextView.setText(displayValue);
            return convertView;
        }
    }
}
