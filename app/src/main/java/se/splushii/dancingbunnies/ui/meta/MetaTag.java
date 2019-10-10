package se.splushii.dancingbunnies.ui.meta;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import se.splushii.dancingbunnies.musiclibrary.EntryID;

public class MetaTag implements Parcelable {
    final EntryID entryID;
    public final String key;
    public final String value;

    MetaTag(EntryID entryID, String key, String value) {
        this.entryID = entryID;
        this.key = key;
        this.value = value;
    }

    private MetaTag(Parcel in) {
        entryID = in.readParcelable(EntryID.class.getClassLoader());
        key = in.readString();
        value = in.readString();
    }

    public static final Creator<MetaTag> CREATOR = new Creator<MetaTag>() {
        @Override
        public MetaTag createFromParcel(Parcel in) {
            return new MetaTag(in);
        }

        @Override
        public MetaTag[] newArray(int size) {
            return new MetaTag[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(entryID, i);
        parcel.writeString(key);
        parcel.writeString(value);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetaTag that = (MetaTag) o;
        return entryID.equals(that.entryID)
                && key.equals(that.key)
                && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryID, key, value);
    }
}
