package se.splushii.dancingbunnies.ui.nowplaying;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class NowPlayingDetailsLookup extends ItemDetailsLookup<Long> {
    private final RecyclerView recyclerView;

    NowPlayingDetailsLookup(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
        View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (view != null) {
            RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
            if (viewHolder instanceof NowPlayingEntriesAdapter.SongViewHolder) {
                return ((NowPlayingEntriesAdapter.SongViewHolder) viewHolder).getItemDetails();
            }
        }
        return null;
    }
}
