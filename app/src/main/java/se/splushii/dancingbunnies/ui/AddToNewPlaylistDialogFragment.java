package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
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
    private static final String BUNDLE_KEY_QUERY_BUNDLES =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.entryids";

    private Bundle query;
    private ArrayList<Bundle> queryBundles;
    private EditText addToNewPlaylistEditText;

    public static void showDialog(Fragment fragment, Bundle query) {
        Bundle args = new Bundle();
        args.putBundle(BUNDLE_KEY_QUERY, query);
        _showDialog(fragment, args);
    }

    static void showDialog(Fragment fragment, List<Bundle> bundleQueries) {
        Bundle args = new Bundle();
        args.putParcelableArrayList(BUNDLE_KEY_QUERY_BUNDLES, new ArrayList<>(bundleQueries));
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
        this.queryBundles = args.getParcelableArrayList(BUNDLE_KEY_QUERY_BUNDLES);
        this.query = args.getBundle(BUNDLE_KEY_QUERY);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.add_to_new_playlist_dialog_fragment_layout, container, false);
        addToNewPlaylistEditText = rootView.findViewById(R.id.add_to_new_playlist_dialog_input);
        addToNewPlaylistEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String name = addToNewPlaylistEditText.getText().toString();
                createPlaylist(name);
                return true;
            }
            return false;
        });
        return rootView;
    }

    private void createPlaylist(String name) {
        CompletableFuture<Void> completableFuture;
        if (queryBundles != null) {
            // Create a StupidPlaylist
            completableFuture = MetaStorage.getInstance(requireContext())
                    .getSongEntriesOnce(queryBundles)
                    .thenCompose(songEntryIDs ->
                            PlaylistStorage.getInstance(requireContext()).insertPlaylists(
                                    0,
                                    Collections.singletonList(new StupidPlaylist(
                                            PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_STUPID),
                                            name,
                                            songEntryIDs
                                    ))
                            ));
        } else {
            // Create a SmartPlaylist
            completableFuture = PlaylistStorage.getInstance(requireContext()).insertPlaylists(
                    0,
                    Collections.singletonList(new SmartPlaylist(
                            PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_SMART),
                            name,
                            query
                    )));
        }
        completableFuture
                .thenRunAsync(this::dismiss, Util.getMainThreadExecutor())
                .thenRun(this::finish);

    }

    private void finish() {
        Fragment fragment = getTargetFragment();
        if (fragment instanceof AddToNewPlaylistDialogFragment.Handler) {
            ((AddToNewPlaylistDialogFragment.Handler) fragment).onNewPlaylistCreated();
        }
    }

    interface Handler {
        void onNewPlaylistCreated();
    }
}
