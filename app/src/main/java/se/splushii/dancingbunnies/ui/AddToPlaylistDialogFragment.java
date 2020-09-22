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
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQueryNode;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.storage.db.Playlist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.ui.playlist.PlaylistAdapter;
import se.splushii.dancingbunnies.ui.playlist.PlaylistFragmentModel;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.storage.transactions.Transaction.PLAYLIST_ENTRY_ADD;

public class AddToPlaylistDialogFragment
        extends DialogFragment
        implements AddToNewPlaylistDialogFragment.Handler {
    private static final String LC = Util.getLogContext(AddToPlaylistDialogFragment.class);

    private static final String TAG = "dancingbunnies.splushii.se.fragment_tag.add_to_playlist_dialog";
    private static final String BUNDLE_KEY_QUERY_NODES = "dancingbunnies.bundle.key.addtoplaylistdialog.query_nodes";

    private TextView numEntriesTextView;

    private PlaylistFragmentModel model;
    private PlaylistAdapter recViewAdapter;

    private List<MusicLibraryQueryNode> queryNodes;
    private CompletableFuture<List<EntryID>> songEntries;

    static void showDialog(FragmentManager fragmentManager, List<MusicLibraryQueryNode> queryNodes) {
        if (queryNodes == null || queryNodes.isEmpty()) {
            return;
        }
        Bundle args = new Bundle();
        args.putStringArray(BUNDLE_KEY_QUERY_NODES, MusicLibraryQueryNode.toJSONStringArray(queryNodes));
        Util.showDialog(
                fragmentManager,
                null,
                TAG,
                MainActivity.REQUEST_CODE_ADD_TO_PLAYLIST_DIALOG,
                new AddToPlaylistDialogFragment(),
                args
        );
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        model = new ViewModelProvider(requireActivity()).get(PlaylistFragmentModel.class);
        filterPlaylists(model, songEntries.getNow(null));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        queryNodes = MusicLibraryQueryNode.fromJSONStringArray(
                args.getStringArray(BUNDLE_KEY_QUERY_NODES)
        );
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
        songEntries = MetaStorage.getInstance(requireContext()).getSongEntriesOnce(queryNodes);
        songEntries.thenAccept(songEntries -> filterPlaylists(model, songEntries));
        recViewAdapter.setOnItemClickListener(playlist ->
                songEntries.thenAccept(songEntryIDs -> {
                    List<PlaylistEntry> playlistEntries = PlaylistEntry.generatePlaylistEntries(
                            playlist.playlistID(),
                            songEntryIDs.toArray(new EntryID[0])
                    );
                    PlaylistStorage.getInstance(requireContext())
                            .addToPlaylist(playlist.playlistID(), playlistEntries);
                }).thenRun(this::dismiss)
        );
        recyclerView.setAdapter(recViewAdapter);
        View addToNewPlaylistView = rootView.findViewById(R.id.add_to_playlist_dialog_new);
        addToNewPlaylistView.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, queryNodes)
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
        PlaylistID playlistID = playlist.playlistID();
        if (playlistID.type != PlaylistID.TYPE_STUPID) {
            return false;
        }
        if (MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL.equals(playlistID.src)) {
            return true;
        }
        return playlistID.src.equals(entryID.src) &&
                MusicLibraryService.checkAPISupport(playlistID.src, PLAYLIST_ENTRY_ADD);
    }

    @Override
    public void onNewPlaylistCreated() {
        dismiss();
    }
}
