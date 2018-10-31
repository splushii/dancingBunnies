package se.splushii.dancingbunnies.ui.nowplaying;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;

public class NowPlayingKeyProvider extends ItemKeyProvider<Long> {

    NowPlayingKeyProvider() {
        super(ItemKeyProvider.SCOPE_MAPPED);
    }

    @Nullable
    @Override
    public Long getKey(int position) {
        return (long) position;
    }

    @Override
    public int getPosition(@NonNull Long key) {
        return key.intValue();
    }
}
