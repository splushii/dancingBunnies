package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.util.Log;

import java.util.Objects;

public class EntryID {
    private static final String LC = "EntryID";
    public final String src;
    public final String id;
    public final LibraryEntry.EntryType type;

    public EntryID(String src, String id, LibraryEntry.EntryType type) {
        this.src = src;
        this.id = id;
        this.type = type;
    }

    @Override
    public String toString() {
        return "{src: " + src + ", id: " + id + ", type: " + type.name() + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        EntryID e = (EntryID) obj;
        return Objects.equals(this.src, e.src)
                && Objects.equals(this.id, e.id)
                && this.type == e.type;
    }

    public String key() {
        return src + id + type.name();
    }

    public MediaDescriptionCompat toMediaDescriptionCompat() {
        Bundle b = toBundle();
        return new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setExtras(b)
                .build();
    }

    public static EntryID from(MediaItem item) {
        Bundle b = item.getDescription().getExtras();
        return from(b);
    }

    public static EntryID from(MediaDescriptionCompat description) {
        Bundle b = description.getExtras();
        return from(b);
    }

    public static EntryID from(QueueItem queueItem) {
        Bundle b = queueItem.getDescription().getExtras();
        return from(b);
    }

    public static EntryID from(Bundle b) {
        Log.d(LC, b.toString());
        String src = b.getString(Meta.METADATA_KEY_API);
        String id = b.getString(Meta.METADATA_KEY_MEDIA_ID);
        LibraryEntry.EntryType type =
                LibraryEntry.EntryType.valueOf(b.getString(Meta.METADATA_KEY_TYPE));
        return new EntryID(src, id, type);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(Meta.METADATA_KEY_API, src);
        b.putString(Meta.METADATA_KEY_MEDIA_ID, id);
        b.putString(Meta.METADATA_KEY_TYPE, type.name());
        return b;
    }

    public static EntryID from(LibraryEntry e) {
        return new EntryID(e.src(), e.id(), e.type());
    }
}
