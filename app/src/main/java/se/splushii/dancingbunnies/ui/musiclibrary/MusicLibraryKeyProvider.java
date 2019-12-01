package se.splushii.dancingbunnies.ui.musiclibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;

class MusicLibraryKeyProvider extends ItemKeyProvider<LibraryEntry> {
    private final MusicLibraryAdapter adapter;

    MusicLibraryKeyProvider(MusicLibraryAdapter adapter) {
        super(ItemKeyProvider.SCOPE_MAPPED);
        this.adapter = adapter;
    }

    @Nullable
    @Override
    public LibraryEntry getKey(int position) {
        return adapter.getEntryId(position);
    }

    @Override
    public int getPosition(@NonNull LibraryEntry key) {
        return adapter.getEntryIdPosition(key);
    }
}
