package se.splushii.dancingbunnies.ui.selection;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public abstract class ItemDetailsViewHolder<ID> extends RecyclerView.ViewHolder {
    private final ItemDetailsLookup.ItemDetails<ID> itemDetails = new ItemDetailsLookup.ItemDetails<ID>() {
        @Override
        public int getPosition() {
            return getPositionOf();
        }

        @Nullable
        @Override
        public ID getSelectionKey() {
            return getSelectionKeyOf();
        }
    };

    public ItemDetailsViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    ItemDetailsLookup.ItemDetails<ID> getItemDetails() {
        return itemDetails;
    }

    public ID getKey() {
        return itemDetails.getSelectionKey();
    }

    public int getPos() {
        return itemDetails.getPosition();
    }

    protected int getPositionOf() {
        return getAdapterPosition();
    }

    protected abstract ID getSelectionKeyOf();
}
