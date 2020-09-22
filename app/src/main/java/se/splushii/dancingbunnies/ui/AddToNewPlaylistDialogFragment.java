package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.SmartPlaylist;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class AddToNewPlaylistDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(AddToNewPlaylistDialogFragment.class);

    private static final String TAG =
            "dancingbunnies.splushii.se.fragment_tag.add_to_new_playlist_dialog";
    private static final String BUNDLE_KEY_QUERY =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.query";
    private static final String BUNDLE_KEY_QUERY_BUNDLES =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.entryids";

    private MusicLibraryQueryNode query;
    private List<MusicLibraryQueryNode> queries;
    private EditText addToNewPlaylistEditText;

    public static void showDialog(Fragment fragment, MusicLibraryQueryNode queryTree) {
        Bundle args = new Bundle();
        args.putString(BUNDLE_KEY_QUERY, queryTree.toJSON().toString());
        showDialog(fragment, args);
    }

    static void showDialog(Fragment fragment, List<MusicLibraryQueryNode> queryTrees) {
        Bundle args = new Bundle();
        args.putStringArray(
                BUNDLE_KEY_QUERY_BUNDLES,
                MusicLibraryQueryNode.toJSONStringArray(queryTrees)
        );
        showDialog(fragment, args);
    }

    private static void showDialog(Fragment fragment, Bundle args) {
        Util.showDialog(
                fragment,
                TAG,
                MainActivity.REQUEST_CODE_ADD_TO_NEW_PLAYLIST_DIALOG,
                new AddToNewPlaylistDialogFragment(),
                args
        );
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        this.queries = MusicLibraryQueryNode.fromJSONStringArray(args.getStringArray(BUNDLE_KEY_QUERY_BUNDLES));
        this.query = MusicLibraryQueryNode.fromJSON(args.getString(BUNDLE_KEY_QUERY));
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

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        addToNewPlaylistEditText.requestFocus();
        Util.showSoftInput(requireContext(), addToNewPlaylistEditText);
        super.onResume();
    }

    private void createPlaylist(String name) {
        CompletableFuture<Void> completableFuture;
        if (queries != null && !queries.isEmpty()) {
            // Create a StupidPlaylist
            completableFuture = MetaStorage.getInstance(requireContext())
                    .getSongEntriesOnce(queries)
                    .thenCompose(songEntryIDs -> {
                        PlaylistID playlistID = PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_STUPID);
                        List<PlaylistEntry> playlistEntries = PlaylistEntry.generatePlaylistEntries(
                                playlistID,
                                songEntryIDs.toArray(new EntryID[0])
                        );
                        return PlaylistStorage.getInstance(requireContext()).insertPlaylists(
                                0,
                                Collections.singletonList(new StupidPlaylist(
                                        playlistID,
                                        name,
                                        playlistEntries
                                ))
                        );
                    });
        } else if (query != null) {
            // Create a SmartPlaylist
            completableFuture = PlaylistStorage.getInstance(requireContext()).insertPlaylists(
                    0,
                    Collections.singletonList(new SmartPlaylist(
                            PlaylistStorage.generatePlaylistID(PlaylistID.TYPE_SMART),
                            name,
                            query
                    )));
        } else {
            completableFuture = CompletableFuture.completedFuture(null);
        }
        completableFuture
                .thenRunAsync(this::dismiss, Util.getMainThreadExecutor())
                .thenRun(this::finish);
    }

    private void finish() {
        clearFocus();
        Fragment fragment = getTargetFragment();
        if (fragment instanceof AddToNewPlaylistDialogFragment.Handler) {
            ((AddToNewPlaylistDialogFragment.Handler) fragment).onNewPlaylistCreated();
        }
    }

    private void clearFocus() {
        addToNewPlaylistEditText.clearFocus();
        Util.hideSoftInput(requireContext(), addToNewPlaylistEditText);
    }

    interface Handler {
        void onNewPlaylistCreated();
    }
}
