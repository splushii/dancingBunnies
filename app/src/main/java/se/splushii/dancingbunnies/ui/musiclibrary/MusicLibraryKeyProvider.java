package se.splushii.dancingbunnies.ui.musiclibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;
import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class MusicLibraryKeyProvider extends ItemKeyProvider<EntryID> {
    private final MusicLibraryAdapter adapter;

    MusicLibraryKeyProvider(MusicLibraryAdapter adapter) {
        super(ItemKeyProvider.SCOPE_MAPPED);
        this.adapter = adapter;
    }

    @Nullable
    @Override
    public EntryID getKey(int position) {
        return adapter.getEntryId(position);
    }

    @Override
    public int getPosition(@NonNull EntryID key) {
        return adapter.getEntryIdPosition(key);
    }
}
