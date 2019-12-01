package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.AudioBrowserFragment;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.PlaybackControllerStorage;
import se.splushii.dancingbunnies.util.Util;

public class SortDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(SortDialogFragment.class);

    public static final String SORT_QUEUE = "sort_queue";
    public static final String SORT_PLAYLIST_PLAYBACK = "sort_playback";

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.sort_dialog";
    private static final String BUNDLE_KEY_PLAYBACK_ENTRIES = "dancingbunnies.bundle.key.sortdialog.entries";
    private static final String BUNDLE_KEY_SORT_TARGET = "dancingbunnies.bundle.key.sortdialog.sort_target";
    private static final String BUNDLE_KEY_ONLY_RETURN_CONFIG = "dancingbunnies.bundle.key.sortdialog.only_return_config";

    private ArrayList<PlaybackEntry> entries;
    private String sortTarget;
    private boolean onlyReturnConfig;

    public static void showDialogForSortConfig(Fragment fragment) {
        showDialog(fragment, new Bundle(), true);
    }

    static void showDialogToSort(Fragment fragment,
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
        showDialog(fragment, args, false);
    }

    private static void showDialog(Fragment fragment, Bundle args, boolean onlyReturnConfig) {
        args.putBoolean(BUNDLE_KEY_ONLY_RETURN_CONFIG, onlyReturnConfig);
        FragmentTransaction ft = fragment.getFragmentManager().beginTransaction();
        Fragment prev = fragment.getFragmentManager().findFragmentByTag(SortDialogFragment.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment dialogFragment = new SortDialogFragment();
        dialogFragment.setTargetFragment(fragment, MainActivity.REQUEST_CODE_SORT_DIALOG);
        dialogFragment.setArguments(args);
        dialogFragment.show(ft, SortDialogFragment.TAG);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        onlyReturnConfig = args.getBoolean(BUNDLE_KEY_ONLY_RETURN_CONFIG, true);
        if (!onlyReturnConfig) {
            ArrayList<PlaybackEntry> entries = args.getParcelableArrayList(BUNDLE_KEY_PLAYBACK_ENTRIES);
            this.entries = entries == null ? new ArrayList<>() : entries;
            sortTarget = args.getString(BUNDLE_KEY_SORT_TARGET);
        }
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.sort_dialog_fragment_layout, container, false);
        View defaultSortView = rootView.findViewById(R.id.sort_dialog_default_sort);
        defaultSortView.setOnClickListener(view -> {
            List<String> sortBy = new ArrayList<>();
            sortBy.add(Meta.FIELD_ARTIST);
            sortBy.add(Meta.FIELD_ALBUM);
            sortBy.add(Meta.FIELD_DISCNUMBER);
            sortBy.add(Meta.FIELD_TRACKNUMBER);
            sort(sortBy);
        });
        View artistAlbumSortView = rootView.findViewById(R.id.sort_dialog_artist_album_sort);
        artistAlbumSortView.setOnClickListener(view -> {
            List<String> sortBy = new ArrayList<>();
            sortBy.add(Meta.FIELD_ARTIST);
            sortBy.add(Meta.FIELD_ALBUM);
            sort(sortBy);
        });
        View discTrackSortView = rootView.findViewById(R.id.sort_dialog_disc_track_sort);
        discTrackSortView.setOnClickListener(view -> {
            List<String> sortBy = new ArrayList<>();
            sortBy.add(Meta.FIELD_DISCNUMBER);
            sortBy.add(Meta.FIELD_TRACKNUMBER);
            sort(sortBy);
        });
        // TODO: Implement custom sort
        // TODO: Cap number of sortBy fields to MetaValueEntry.NUM_SORT_VALUES
//        View customSortView = rootView.findViewById(R.id.sort_dialog_custom_sort);
//        customSortView.setOnClickListener(view -> {
//            List<String> sortBy = new ArrayList<>();
//            sortBy.add(Meta.FIELD_DURATION);
//            sort(sortBy);
//        });
        return rootView;
    }

    private void sort(List<String> sortBy) {
        Fragment targetFragment = getTargetFragment();
        if (onlyReturnConfig) {
            if (targetFragment instanceof SortDialogFragment.ConfigHandler) {
                ((SortDialogFragment.ConfigHandler) targetFragment).onSortConfig(sortBy);
                dismiss();
                return;
            }
        }
        if (targetFragment instanceof AudioBrowserFragment) {
            switch (sortTarget) {
                case SORT_QUEUE:
                    AudioBrowserFragment audioBrowserFragment = (AudioBrowserFragment) targetFragment;
                    audioBrowserFragment.sortQueueItems(entries, sortBy);
                    break;
                case SORT_PLAYLIST_PLAYBACK:
                    PlaybackControllerStorage.getInstance(requireContext()).sort(
                            PlaybackControllerStorage.QUEUE_ID_CURRENT_PLAYLIST_PLAYBACK,
                            entries,
                            sortBy
                    );
                    break;
            }
            dismiss();
        }
    }

    public interface ConfigHandler {
        void onSortConfig(List<String> sortBy);
    }
}