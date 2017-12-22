package se.splushii.dancingbunnies.ui;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import se.splushii.dancingbunnies.MusicLibraryFragment;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.events.PlaySongEvent;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

public class MusicLibraryAdapter extends RecyclerView.Adapter<MusicLibraryAdapter.SongViewHolder> {
    private static String LC = Util.getLogContext(MusicLibraryAdapter.class);
    private List<MediaBrowserCompat.MediaItem> dataset;
    private MusicLibraryFragment fragment;
    private String currentSrc;
    private String currentParentId;
    private LibraryEntry.EntryType currentType;

    public MusicLibraryAdapter(MusicLibraryFragment fragment) {
        this.dataset = new ArrayList<>();
        this.fragment = fragment;
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        Button butt;
        SongViewHolder(View v) {
            super(v);
            butt = (Button) v.findViewById(R.id.song_title);
        }
    }

    @Override
    public SongViewHolder onCreateViewHolder(ViewGroup parent,
                                             int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.musiclibrary_item, parent, false);
        return new SongViewHolder(v);
    }

    public void setDataset(List<MediaBrowserCompat.MediaItem> items, String src, String parentId, LibraryEntry.EntryType type) {
        this.dataset = items;
        this.currentSrc = src;
        this.currentParentId = parentId;
        this.currentType = type;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(final SongViewHolder holder, int position) {
        final int n = position;
        RecyclerView rv =
                (RecyclerView) fragment.getView().findViewById(R.id.musiclibrary_recyclerview);
        final LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        final MediaBrowserCompat.MediaItem item = dataset.get(position);
        final String title = item.getDescription().getTitle() + "";
        Bundle b = item.getDescription().getExtras();
        final String src = b.getString(Meta.METADATA_KEY_API);
        final LibraryEntry.EntryType type =
                (LibraryEntry.EntryType) b.getSerializable(Meta.METADATA_KEY_TYPE);
        final String id = item.getMediaId();
        final boolean browsable = item.isBrowsable();
        holder.butt.setText(title);
        holder.butt.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Log.d(LC, "Long click on " + title);
                // TODO: implement popup with queueing, related entries, etc.
                return true;
            }
        });
        holder.butt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (browsable) {
                    fragment.addBackButtonHistory(getCurrentView());
                    fragment.refreshView(new LibraryView(src, id, type, 0, 0));
                } else {
                    Log.d(LC, "Sending play song event with src: " + src + ", : " + id);
                    EventBus.getDefault().post(new PlaySongEvent(src, id));
                }
            }
        });
    }

    public LibraryView getCurrentView() {
        RecyclerView rv = fragment.getView().findViewById(R.id.musiclibrary_recyclerview);
        LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
        int hPos = llm.findFirstCompletelyVisibleItemPosition();
        View v = llm.getChildAt(0);
        int hPad = v == null ? 0 : v.getTop() - llm.getPaddingTop();
        if (hPad < 0 && hPos > 0) {
            hPos--;
        }
        return new LibraryView(currentSrc, currentParentId, currentType, hPos, hPad);
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }
}
