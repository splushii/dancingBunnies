package se.splushii.dancingbunnies.storage.db;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.SmartPlaylist;

@Entity(tableName = DB.TABLE_PLAYLISTS,
// Not possible to constrain the pos because of inserts, because incrementing COLUMN_POS
// needs to be done in a TEMP table, something not supported in Room as far as I know.
// See: https://stackoverflow.com/questions/22494148/incrementing-value-in-table-with-unique-key-causes-constraint-error
        indices = @Index(value = {
                Playlist.COLUMN_SRC,
                Playlist.COLUMN_ID,
                Playlist.COLUMN_TYPE
        }, unique = true),
        primaryKeys = {
                Playlist.COLUMN_SRC,
                Playlist.COLUMN_ID,
                Playlist.COLUMN_TYPE
        }
)
public class Playlist implements Parcelable {
    static final String COLUMN_SRC = "src";
    static final String COLUMN_ID = "id";
    static final String COLUMN_TYPE = "type";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_QUERY = "query";
    static final String COLUMN_POS = "pos";

    @NonNull
    @ColumnInfo(name = COLUMN_SRC)
    String src;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    String id;
    @NonNull
    @ColumnInfo(name = COLUMN_TYPE)
    String type;
    @NonNull
    @ColumnInfo(name = COLUMN_NAME)
    String name;
    @ColumnInfo(name = COLUMN_QUERY)
    String query;
    @NonNull
    @ColumnInfo(name = COLUMN_POS)
    long pos;

    public Playlist() {}

    protected Playlist(Parcel in) {
        init(
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readLong()
        );
    }

    private void init(String src, String id, String type, String name, String query, long pos) {
        this.src = src;
        this.id = id;
        this.type = type;
        this.name = name;
        this.query = query;
        this.pos = pos;
    }

    public PlaylistID playlistID() {
        return new PlaylistID(src, id, type);
    }

    public String query() {
        return query;
    }

    public String name() {
        return name;
    }

    public long pos() {
        return pos;
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

    private static Playlist from(se.splushii.dancingbunnies.musiclibrary.Playlist playlist,
                                 long pos,
                                 String query) {
        Playlist roomPlaylist = new Playlist();
        roomPlaylist.init(
                playlist.id.src,
                playlist.id.id,
                playlist.id.type,
                playlist.name,
                query,
                pos
        );
        return roomPlaylist;
    }

    public static Playlist from(Playlist playlist, long pos) {
        Playlist roomPlaylist = new Playlist();
        roomPlaylist.init(
                playlist.src,
                playlist.id,
                playlist.type,
                playlist.name,
                playlist.query,
                pos
        );
        return roomPlaylist;
    }

    public static Playlist from(se.splushii.dancingbunnies.musiclibrary.Playlist playlist, long pos) {
        if (playlist instanceof SmartPlaylist) {
            return from(
                    playlist,
                    pos,
                    ((SmartPlaylist)playlist).getJSONQueryString());
        }
        return from(playlist, pos, null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(src);
        dest.writeString(id);
        dest.writeString(type);
        dest.writeString(name);
        dest.writeString(query);
        dest.writeLong(pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Playlist playlist = (Playlist) o;
        return src.equals(playlist.src)
                && id.equals(playlist.id)
                && type.equals(playlist.type);
    }

    @Override
    public String toString() {
        return "Playlist{" +
                "pos=" + pos +
                ", name='" + name + '\'' +
                ", query='" + query + '\'' +
                ", src='" + src + '\'' +
                ", id='" + id + '\'' +
                ", type='" + type + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, id, type);
    }
}