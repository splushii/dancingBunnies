package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.MainActivity;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.QueryLeaf;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.TransactionStorage;
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

    private List<QueryNode> queryNodes;

    static void showDialog(FragmentManager fragmentManager, List<QueryNode> queryNodes) {
        if (queryNodes == null || queryNodes.isEmpty()) {
            return;
        }
        Bundle args = new Bundle();
        args.putStringArray(BUNDLE_KEY_QUERY_NODES, QueryNode.toJSONStringArray(queryNodes));
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
        MetaStorage.getInstance(requireContext()).getTracksOnce(queryNodes)
                .thenAcceptAsync(
                        tracks -> filterPlaylists(model, tracks),
                        Util.getMainThreadExecutor()
                );
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        queryNodes = QueryNode.fromJSONStringArray(
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
        recyclerView.setAdapter(recViewAdapter);
        View addToNewPlaylistView = rootView.findViewById(R.id.add_to_playlist_dialog_new);
        addToNewPlaylistView.setOnClickListener(v ->
                AddToNewPlaylistDialogFragment.showDialog(this, queryNodes)
        );
        return rootView;
    }

    private void filterPlaylists(PlaylistFragmentModel model, List<EntryID> tracks) {
        if (model == null || tracks == null || tracks.isEmpty()) {
            return;
        }
        numEntriesTextView.setText(String.format(Locale.getDefault(), "(%d entries)", tracks.size()));
        recViewAdapter.setOnItemClickListener(playlistID -> {
            TransactionStorage.getInstance(requireContext())
                    .addPlaylistEntries(
                            requireContext(),
                            playlistID.src,
                            playlistID,
                            tracks,
                            null
                    )
                    .thenRun(this::dismiss);
        });
        recViewAdapter.setModel(
                model,
                new QueryLeaf(Meta.FIELD_QUERY, QueryLeaf.Op.EXISTS, null, false),
                playlistIDs -> {
                    List<EntryID> applicablePlaylists = new ArrayList<>();
                    for (EntryID playlistID: playlistIDs) {
                        boolean applicable = true;
                        for (EntryID entryID: tracks) {
                            if (!canBeAdded(entryID, playlistID)) {
                                applicable = false;
                                break;
                            }
                        }
                        if (applicable) {
                            applicablePlaylists.add(playlistID);
                        }
                    }
                    return applicablePlaylists;
                }
        );
    }

    private boolean canBeAdded(EntryID entryID, EntryID playlistID) {
        return APIClient.getAPIClient(requireContext(), playlistID.src)
                .supports(PLAYLIST_ENTRY_ADD, entryID.src);
    }

    @Override
    public void onNewPlaylistCreated() {
        dismiss();
    }
}
