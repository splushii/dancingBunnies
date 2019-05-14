package se.splushii.dancingbunnies.storage.db;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;

@Entity(tableName = DB.TABLE_PLAYLISTS,
// Not possible to constrain the pos because of inserts, because incrementing COLUMN_POS
// needs to be done in a TEMP table, something not supported in Room as far as I know.
// See: https://stackoverflow.com/questions/22494148/incrementing-value-in-table-with-unique-key-causes-constraint-error?noredirect=1&lq=1
        indices = @Index(value = {
                Playlist.COLUMN_API,
                Playlist.COLUMN_ID
        }, unique = true),
        primaryKeys = {
                Playlist.COLUMN_API,
                Playlist.COLUMN_ID
        }
)
public class Playlist implements Parcelable {
    static final String COLUMN_API = "api";
    static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    static final String COLUMN_POS = "pos";

    @NonNull
    @ColumnInfo(name = COLUMN_API)
    public String api;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = COLUMN_NAME)
    public String name;
    @NonNull
    @ColumnInfo(name = COLUMN_POS)
    public long pos;

    public Playlist() {}

    protected Playlist(Parcel in) {
        api = in.readString();
        id = in.readString();
        name = in.readString();
        pos = in.readLong();
    }

    public static final Creator<Playlist> CREATOR = new Creator<Playlist>() {
        @Override
        public Playlist createFromParcel(Parcel in) {
            return new Playlist(in);
        }

        @Override
        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };

    public static Playlist from(StupidPlaylist playlist, int pos) {
        Playlist roomPlaylist = new Playlist();
        roomPlaylist.api = playlist.id.src;
        roomPlaylist.id = playlist.id.id;
        roomPlaylist.name = playlist.name;
        roomPlaylist.pos = pos;
        return roomPlaylist;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(api);
        dest.writeString(id);
        dest.writeString(name);
        dest.writeLong(pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Playlist playlist = (Playlist) o;
        return api.equals(playlist.api) &&
                id.equals(playlist.id);
    }

    @Override
    public String toString() {
        return "Playlist{" +
                "pos=" + pos +
                ", name='" + name + '\'' +
                ", api='" + api + '\'' +
                ", id='" + id + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(api, id);
    }
}