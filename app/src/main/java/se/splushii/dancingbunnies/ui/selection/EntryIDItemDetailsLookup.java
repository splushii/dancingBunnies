package se.splushii.dancingbunnies.ui.selection;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.ui.musiclibrary.MusicLibraryAdapter;

public class EntryIDItemDetailsLookup extends ItemDetailsLookup<EntryID> {
    private final RecyclerView recyclerView;

    public EntryIDItemDetailsLookup(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<EntryID> getItemDetails(@NonNull MotionEvent e) {
        View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (view != null) {
            RecyclerView.ViewHolder viewHolder = recyclerView.getChildViewHolder(view);
            if (viewHolder instanceof MusicLibraryAdapter.SongViewHolder) {
                return ((MusicLibraryAdapter.SongViewHolder) viewHolder).getItemDetails();
            }
        }
        return null;
    }
}
