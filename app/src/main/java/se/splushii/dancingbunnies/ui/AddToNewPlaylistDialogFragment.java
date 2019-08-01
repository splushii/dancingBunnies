package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.SmartPlaylist;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.util.Util;

public class AddToNewPlaylistDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(AddToNewPlaylistDialogFragment.class);

    private static final String TAG =
            "dancingbunnies.splushii.se.fragment_tag.add_to_new_playlist_dialog";
    private static final String BUNDLE_KEY_QUERY =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.query";
    private static final String BUNDLE_KEY_ENTRY_IDS =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.entryids";

    private Bundle query;
    private List<EntryID> entryIDs;
    private EditText addToNewPlaylistEditText;

    public static void showDialog(Fragment fragment, Bundle query) {
        Bundle args = new Bundle();
        args.remove(BUNDLE_KEY_ENTRY_IDS);
        args.putBundle(BUNDLE_KEY_QUERY, query);
        _showDialog(fragment, args);
    }

    static void showDialog(Fragment fragment, List<EntryID> entryIDs, Bundle query) {
        Bundle args = new Bundle();
        args.putParcelableArrayList(BUNDLE_KEY_ENTRY_IDS, new ArrayList<>(entryIDs));
        args.putBundle(BUNDLE_KEY_QUERY, query);
        _showDialog(fragment, args);
    }

    private static void _showDialog(Fragment fragment, Bundle args) {
        FragmentTransaction ft = fragment.getFragmentManager().beginTransaction();
        Fragment prev = fragment.getFragmentManager().findFragmentByTag(AddToNewPlaylistDialogFragment.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment dialogFragment = new AddToNewPlaylistDialogFragment();
        dialogFragment.setTargetFragment(fragment, MainActivity.REQUEST_CODE_ADD_TO_NEW_PLAYLIST_DIALOG);
        dialogFragment.setArguments(args);
        dialogFragment.show(ft, AddToNewPlaylistDialogFragment.TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        this.entryIDs = args.getParcelableArrayList(BUNDLE_KEY_ENTRY_IDS);
        this.query = args.getBundle(BUNDLE_KEY_QUERY);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.add_to_new_playlist_dialog_fragment_layout, container, false);
        addToNewPlaylistEditText = rootView.findViewById(R.id.add_to_new_playlist_dialog_input);
        ImageButton addToNewPlaylistBtn = rootView.findViewById(R.id.add_to_new_playlist_dialog_btn);
        addToNewPlaylistBtn.setOnClickListener(v -> {
            String name = addToNewPlaylistEditText.getText().toString();
            if (entryIDs != null) {
                // Create a StupidPlaylist
                MetaStorage.getInstance(requireContext())
                        .getSongEntriesOnce(entryIDs, query)
                        .thenCompose(songEntryIDs ->
                                PlaylistStorage.getInstance(requireContext()).insertPlaylists(
                                        0,
                                        Collections.singletonList(new StupidPlaylist(
                                                PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_STUPID),
                                                name,
                                                songEntryIDs
                                        ))
                                ))
                        .thenRun(this::dismiss);
            } else {
                // Create a SmartPlaylist
                PlaylistStorage.getInstance(requireContext()).insertPlaylists(
                        0,
                        Collections.singletonList(new SmartPlaylist(
                                PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_SMART),
                                name,
                                query
                        )))
                        .thenRunAsync(this::dismiss, Util.getMainThreadExecutor());
            }
        });
        return rootView;
    }
}
