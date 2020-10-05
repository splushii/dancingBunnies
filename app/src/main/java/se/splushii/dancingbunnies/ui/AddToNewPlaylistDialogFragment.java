package se.splushii.dancingbunnies.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.storage.transactions.Transaction;
import se.splushii.dancingbunnies.ui.settings.SettingsActivityFragment;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.PlaylistID.TYPE_SMART;
import static se.splushii.dancingbunnies.musiclibrary.PlaylistID.TYPE_STUPID;

public class AddToNewPlaylistDialogFragment extends DialogFragment {
    private static final String LC = Util.getLogContext(AddToNewPlaylistDialogFragment.class);

    private static final String TAG =
            "dancingbunnies.splushii.se.fragment_tag.add_to_new_playlist_dialog";
    private static final String BUNDLE_KEY_QUERY =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.query";
    private static final String BUNDLE_KEY_QUERY_BUNDLES =
            "dancingbunnies.bundle.key.add_to_new_playlist_dialog.entryids";

    private static final int MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_HEADER = 0;
    private static final int MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_SINGLE = 1;
    private static final int MENU_GROUP_ORDER_NEW_PLAYLIST_BACKEND_HEADER = Menu.FIRST;
    private static final int MENU_GROUP_ORDER_NEW_PLAYLIST_BACKEND_SINGLE = Menu.FIRST + 1;

    private MusicLibraryQueryNode query;
    private List<MusicLibraryQueryNode> queries;
    private EditText addToNewPlaylistEditText;

    private TextView newPlaylistBackendId;
    private ImageView newPlaylistBackendIcon;
    private Menu newPlaylistBackendMenu;

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

        String playlistType;
        if (queries != null && !queries.isEmpty()) {
            playlistType = TYPE_STUPID;
        } else if (query != null) {
            playlistType = TYPE_SMART;
        } else {
            playlistType = null;
        }

        addToNewPlaylistEditText = rootView.findViewById(R.id.add_to_new_playlist_dialog_input);
        addToNewPlaylistEditText.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String name = addToNewPlaylistEditText.getText().toString();
                createPlaylist(name, playlistType);
                return true;
            }
            return false;
        });

        View newPlaylistBackend = rootView.findViewById(R.id.add_to_new_playlist_backend);
        newPlaylistBackendId = rootView.findViewById(R.id.add_to_new_playlist_backend_id);
        newPlaylistBackendIcon = rootView.findViewById(R.id.add_to_new_playlist_backend_icon);
        final PopupMenu newPlaylistBackendPopup = new PopupMenu(requireContext(), newPlaylistBackend);
        newPlaylistBackendMenu = newPlaylistBackendPopup.getMenu();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            newPlaylistBackendMenu.setGroupDividerEnabled(true);
        }
        MenuPopupHelper newPlaylistBackendPopupHelper = new MenuPopupHelper(
                requireContext(),
                (MenuBuilder) newPlaylistBackendMenu,
                newPlaylistBackend
        );
        newPlaylistBackendPopupHelper.setForceShowIcon(true);
        List<String> sources = new ArrayList<>();
        String defaultNewPlaylistBackendSource = MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL;
        newPlaylistBackendId.setText(defaultNewPlaylistBackendSource);
        newPlaylistBackendIcon.setImageResource(
                MusicLibraryService.getAPIIconResourceFromSource(defaultNewPlaylistBackendSource)
        );
        setNewPlaylistBackendMenuHeader(defaultNewPlaylistBackendSource);
        newPlaylistBackendPopup.setOnMenuItemClickListener(item ->
                onNewPlaylistBackendMenuSelected(item, sources)
        );
        newPlaylistBackend.setOnClickListener(view -> {
            sources.clear();
            sources.addAll(
                    SettingsActivityFragment.getSources(requireContext())
                            .stream()
                            .filter(src -> {
                                APIClient apiClient = APIClient.getAPIClient(requireContext(), src);
                                return apiClient.supports(Transaction.PLAYLIST_ADD, src)
                                        && (TYPE_STUPID.equals(playlistType)
                                        || apiClient.supportsDancingBunniesSmartPlaylist());
                            })
                            .collect(Collectors.toList())
            );
            newPlaylistBackendMenu.removeGroup(MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_SINGLE);
            for (int i = 0; i < sources.size(); i++) {
                String src = sources.get(i);
                MenuItem menuItem = newPlaylistBackendMenu.add(
                        MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_SINGLE,
                        i,
                        MENU_GROUP_ORDER_NEW_PLAYLIST_BACKEND_SINGLE,
                        src
                );
                menuItem.setIcon(MusicLibraryService.getAPIIconResourceFromSource(src));
            }
            newPlaylistBackendPopupHelper.show();
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

    private boolean onNewPlaylistBackendMenuSelected(MenuItem item, List<String> sources) {
        int position = item.getItemId();
        int groupId = item.getGroupId();
        if (groupId != MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_SINGLE) {
            return false;
        }
        String src = sources.get(position);
        newPlaylistBackendIcon.setImageResource(
                MusicLibraryService.getAPIIconResourceFromSource(src)
        );
        newPlaylistBackendId.setText(src);
        setNewPlaylistBackendMenuHeader(src);
        return true;
    }

    private void setNewPlaylistBackendMenuHeader(String src) {
        newPlaylistBackendMenu.removeGroup(MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_HEADER);
        newPlaylistBackendMenu.add(
                MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_HEADER,
                Menu.NONE,
                MENU_GROUP_ORDER_NEW_PLAYLIST_BACKEND_HEADER,
                "Creating playlist on backend:"
        ).setEnabled(false);
        newPlaylistBackendMenu.add(
                MENU_GROUP_ID_NEW_PLAYLIST_BACKEND_HEADER,
                Menu.NONE,
                MENU_GROUP_ORDER_NEW_PLAYLIST_BACKEND_HEADER,
                src
        ).setEnabled(false);
    }

    private void createPlaylist(String name, String playlistType) {
        CompletableFuture<Void> completableFuture;
        String src = newPlaylistBackendId.getText().toString();
        switch (playlistType) {
            case TYPE_STUPID:
                PlaylistID playlistID = PlaylistID.generate(src, TYPE_STUPID);
                completableFuture = TransactionStorage.getInstance(requireContext())
                        .addPlaylist(requireContext(), playlistID, name, null, null)
                        .thenCompose(aVoid -> MetaStorage.getInstance(requireContext())
                                .getSongEntriesOnce(queries)
                        )
                        .thenCompose(songEntryIDs -> TransactionStorage.getInstance(requireContext())
                                .addPlaylistEntries(
                                        requireContext(),
                                        playlistID.src,
                                        playlistID,
                                        songEntryIDs,
                                        null
                                )
                        );
                break;
            case TYPE_SMART:
                PlaylistID smartPlaylistID = PlaylistID.generate(src, TYPE_SMART);
                completableFuture = TransactionStorage.getInstance(requireContext())
                        .addPlaylist(
                                requireContext(),
                                smartPlaylistID,
                                name,
                                query.toString(),
                                null
                        );
                break;
            default:
                completableFuture = CompletableFuture.completedFuture(null);
                break;
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
