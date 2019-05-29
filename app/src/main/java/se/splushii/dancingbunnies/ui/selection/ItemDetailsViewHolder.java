package se.splushii.dancingbunnies.ui.selection;

import android.content.Context;
import android.view.View;

import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.storage.MetaStorage;

public abstract class ItemDetailsViewHolder<ID> extends RecyclerView.ViewHolder {
    private MutableLiveData<EntryID> entryIDLiveData;
    private LiveData<Meta> metaLiveData;

    public ItemDetailsViewHolder(@NonNull View itemView) {
        super(itemView);
        entryIDLiveData = new MutableLiveData<>();
    }

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
    protected abstract int getPositionOf();
    protected abstract ID getSelectionKeyOf();

    ItemDetailsLookup.ItemDetails<ID> getItemDetails() {
        return itemDetails;
    }

    public ID getKey() {
        return itemDetails.getSelectionKey();
    }

    public void initMetaObserver(Context context) {
        metaLiveData = Transformations.switchMap(entryIDLiveData, entryID -> {
            if (!Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
                MutableLiveData<Meta> nullMeta = new MutableLiveData<>();
                nullMeta.setValue(null);
                return nullMeta;
            }
            return MetaStorage.getInstance(context).getMeta(entryID);
        });
    }

    public void setEntryID(EntryID entryID) {
        entryIDLiveData.setValue(entryID);
    }

    public void observeMeta(LifecycleOwner lifecycleOwner, Consumer<Meta> metaConsumer) {
        metaLiveData.observe(lifecycleOwner, metaConsumer::accept);
    }
}
