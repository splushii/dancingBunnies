package se.splushii.dancingbunnies.ui;

import android.app.Activity;
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
        Bundle b = meta.getBundle();
        for (String key: b.keySet()) {
            if (Meta.METADATA_KEY_TYPE.equals(key)) {
                continue;
            }
            switch (Meta.getType(key)) {
                case STRING:
                    String value = b.getString(key);
                    if (value != null) {
                        data.add(new Pair<>(key, b.getString(key)));
                    }
                    break;
                case LONG:
                    data.add(new Pair<>(key, String.valueOf(b.getLong(key))));
                    break;
                default:
                case BITMAP:
                case RATING:
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
            sendResult(metaItem.first, metaItem.second);
        });
        return v;
    }

    private void sendResult(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        Intent intent = new Intent().putExtras(bundle);
        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);
        dismiss();
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
