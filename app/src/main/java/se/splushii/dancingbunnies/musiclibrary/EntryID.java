package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;

import com.google.android.gms.cast.MediaMetadata;

import org.apache.lucene.document.Document;

import se.splushii.dancingbunnies.search.Indexer;

public class EntryID implements Parcelable {
    static final EntryID UnknownEntryID = EntryID.from(Meta.UNKNOWN_ENTRY);
    public final String src;
    public final String id;
    public final String type;

    public EntryID(String src, String id, String type) {
        this.src = src;
        this.id = id;
        this.type = type;
    }

    protected EntryID(Parcel in) {
        src = in.readString();
        id = in.readString();
        type = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(src);
        dest.writeString(id);
        dest.writeString(type);
    }

    public static final Creator<EntryID> CREATOR = new Creator<EntryID>() {
        @Override
        public EntryID createFromParcel(Parcel in) {
            return new EntryID(in);
        }

        @Override
        public EntryID[] newArray(int size) {
            return new EntryID[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "{src: " + src + ", id: " + id + ", type: " + type + "}";
    }

    @Override
    public int hashCode() {
        return key().hashCode();
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
        return src.equals(e.src) && id.equals(e.id) && type.equals(e.type);
    }

    public String key() {
        return src + id + type;
    }

    public MediaDescriptionCompat toMediaDescriptionCompat() {
        Bundle b = toBundle();
        return new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setExtras(b)
                .build();
    }

    public static EntryID from(Meta meta) {
        return from(meta.getBundle());
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
        String src = b.getString(Meta.METADATA_KEY_API);
        String id = b.getString(Meta.METADATA_KEY_MEDIA_ID);
        String type = b.getString(Meta.METADATA_KEY_TYPE);
        return new EntryID(src, id, type);
    }

    public Bundle toBundle() {
        Bundle b = toBundleQuery();
        b.putString(Meta.METADATA_KEY_TYPE, type);
        return b;
    }

    public Bundle toBundleQuery() {
        Bundle b = new Bundle();
        b.putString(Meta.METADATA_KEY_API, src);
        b.putString(Meta.METADATA_KEY_MEDIA_ID, id);
        return b;
    }

    public static EntryID from(LibraryEntry e) {
        return new EntryID(e.src(), e.id(), e.type());
    }

    public static EntryID from(Document doc) {
        String src = doc.get(Indexer.meta2fieldNameMap.get(Meta.METADATA_KEY_API));
        String id = doc.get(Indexer.meta2fieldNameMap.get(Meta.METADATA_KEY_MEDIA_ID));
        String type = Meta.METADATA_KEY_MEDIA_ID;
        return new EntryID(src, id, type);
    }

    public static EntryID from(MediaMetadata castMeta) {
        String src = castMeta.getString(Meta.METADATA_KEY_API);
        String id = castMeta.getString(Meta.METADATA_KEY_MEDIA_ID);
        String type = castMeta.getString(Meta.METADATA_KEY_TYPE);
        return new EntryID(src, id, type);
    }

    public static EntryID from(MediaMetadataCompat meta) {
        return EntryID.from(meta.getBundle());
    }
}
