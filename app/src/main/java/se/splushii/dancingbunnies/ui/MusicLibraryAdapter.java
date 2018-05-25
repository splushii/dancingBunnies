package se.splushii.dancingbunnies.ui;

import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import se.splushii.dancingbunnies.MusicLibraryFragment;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private List<MediaBrowserCompat.MediaItem> dataset;
    private MusicLibraryFragment fragment;
    private RecyclerView.ViewHolder contextMenuHolder;

    public MusicLibraryAdapter(MusicLibraryFragment fragment) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
    }

    public MediaBrowserCompat.MediaItem getItemData(int childPosition) {
        return dataset.get(childPosition);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        View queueAction;
        TextView songTitleView;
        ImageButton overflowMenu;
        View moreActions;
        SongViewHolder(View view) {
            super(view);
            songTitleView = view.findViewById(R.id.song_title);
            overflowMenu = view.findViewById(R.id.overflow_menu);
            moreActions = view.findViewById(R.id.more_actions);
            queueAction = view.findViewById(R.id.action_queue);
        }
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.musiclibrary_item_browsable, parent, false);
        return new SongViewHolder(v);
    }

    public void setDataset(List<MediaBrowserCompat.MediaItem> items) {
        this.dataset = items;
        notifyDataSetChanged();
    }

    public RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        final MediaBrowserCompat.MediaItem item = dataset.get(position);
        final String title = item.getDescription().getTitle() + "";
        EntryID entryID = EntryID.from(item);
        final boolean browsable = item.isBrowsable();
        holder.moreActions.setVisibility(View.GONE);
        holder.songTitleView.setText(title);
        holder.songTitleView.setOnLongClickListener(view -> {
            Log.d(LC, "Long click on " + title);
            if (holder.moreActions.getVisibility() == View.VISIBLE) {
                holder.moreActions.setVisibility(View.GONE);
            } else {
                holder.moreActions.setVisibility(View.VISIBLE);
            }
//            if (browsable) {
//                // TODO: Support context menu for browsable items
//            } else {
//                fragment.play(entryID);
//            }
            return true;
        });
        holder.songTitleView.setOnClickListener(view -> {
            if (browsable) {
                fragment.browse(entryID);
            } else {
                contextMenuHolder = holder;
                view.showContextMenu();
            }
        });
        holder.overflowMenu.setOnClickListener(View::showContextMenu);
        holder.queueAction.setOnClickListener(v -> fragment.queue(entryID));
    }

    public Pair<Integer, Integer> getCurrentPosition() {
        RecyclerView rv = fragment.getView().findViewById(R.id.musiclibrary_recyclerview);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        int hPos = llm.findFirstCompletelyVisibleItemPosition();
        View v = llm.getChildAt(0);
        int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        if (hPad < 0 && hPos > 0) {
            hPos--;
        }
        return new Pair<>(hPos, hPad);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
