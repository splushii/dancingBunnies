package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.ui.playlist.PlaylistAdapter;
import se.splushii.dancingbunnies.ui.playlist.PlaylistFragmentModel;
import se.splushii.dancingbunnies.util.Util;

public class AddToPlaylistDialogFragment
        extends DialogFragment
        implements AddToNewPlaylistDialogFragment.Handler {
    private static final String LC = Util.getLogContext(AddToPlaylistDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.add_to_playlist_dialog";
    private static final String BUNDLE_KEY_QUERY_BUNDLES = "dancingbunnies.bundle.key.addtoplaylistdialog.query_bundles";

    private TextView numEntriesTextView;

    private PlaylistFragmentModel model;
    private PlaylistAdapter recViewAdapter;

    private ArrayList<Bundle> queryBundles;
    private CompletableFuture<List<EntryID>> songEntries;

    static void showDialog(Fragment fragment, List<Bundle> queryBundles) {
        if (queryBundles == null || queryBundles.isEmpty()) {
            return;
        }
        Bundle args = new Bundle();
        args.putParcelableArrayList(BUNDLE_KEY_QUERY_BUNDLES, new ArrayList<>(queryBundles));
        showDialog(fragment, args);
    }

    private static void showDialog(Fragment fragment, Bundle args) {
        FragmentTransaction ft = fragment.getFragmentManager().beginTransaction();
        Fragment prev = fragment.getFragmentManager().findFragmentByTag(AddToPlaylistDialogFragment.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        DialogFragment dialogFragment = new AddToPlaylistDialogFragment();
        dialogFragment.setTargetFragment(fragment, MainActivity.REQUEST_CODE_ADD_TO_PLAYLIST_DIALOG);
        dialogFragment.setArguments(args);
        dialogFragment.show(ft, AddToPlaylistDialogFragment.TAG);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        model = ViewModelProviders.of(requireActivity()).get(PlaylistFragmentModel.class);
        filterPlaylists(model, songEntries.getNow(null));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        queryBundles = args.getParcelableArrayList(BUNDLE_KEY_QUERY_BUNDLES);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.add_to_playlist_dialog_fragment_layout, container, false);
        numEntriesTextView = rootView.findViewById(R.id.add_to_playlist_dialog_num_entries);
        RecyclerView recyclerView = rootView.findViewById(R.id.add_to_playlist_dialog_recyclerview);
        LinearLayoutManager recViewLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(recViewLayoutManager);
        recViewAdapter = new PlaylistAdapter(this);
        songEntries = MetaStorage.getInstance(requireContext()).getSongEntriesOnce(queryBundles);
        songEntries.thenAccept(songEntries -> filterPlaylists(model, songEntries));
        recViewAdapter.setOnItemClickListener(playlist ->
                songEntries.thenAccept(songEntryIDs ->
                        PlaylistStorage.getInstance(requireContext())
                                .addToPlaylist(new PlaylistID(playlist), songEntryIDs)
                ).thenRun(this::dismiss)
        );
        recyclerView.setAdapter(recViewAdapter);
        View addToNewPlaylistView = rootView.findViewById(R.id.add_to_playlist_dialog_new);
        addToNewPlaylistView.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, queryBundles)
        );
        return rootView;
    }

    private void filterPlaylists(PlaylistFragmentModel model, List<EntryID> songEntries) {
        if (model == null || songEntries == null || songEntries.isEmpty()) {
            return;
        }
        numEntriesTextView.setText(String.format(Locale.getDefault(), "(%d entries)", songEntries.size()));
        recViewAdapter.setModel(model, playlists -> {
            List<Playlist> applicablePlaylists = new ArrayList<>();
            for (Playlist playlist: playlists) {
                boolean applicable = true;
                for (EntryID entryID: songEntries) {
                    if (!canBeAdded(entryID, playlist)) {
                        applicable = false;
                        break;
                    }
                }
                if (applicable) {
                    applicablePlaylists.add(playlist);
                }
            }
            return applicablePlaylists;
        });
    }

    private boolean canBeAdded(EntryID entryID, Playlist playlist) {
        if (playlist.type != PlaylistID.TYPE_STUPID) {
            return false;
        }
        if (MusicLibraryService.API_ID_DANCINGBUNNIES.equals(playlist.api)) {
            return true;
        }
        return playlist.api.equals(entryID.src) &&
                MusicLibraryService.checkAPISupport(
                        playlist.api,
                        MusicLibraryService.PLAYLIST_ENTRY_ADD);
    }

    @Override
    public void onNewPlaylistCreated() {
        dismiss();
    }
}
