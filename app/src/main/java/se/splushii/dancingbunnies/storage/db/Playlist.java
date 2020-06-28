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
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;

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
    int type;
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
                in.readInt(),
                in.readString(),
                in.readString(),
                in.readLong()
        );
    }

    private void init(String src, String id, int type, String name, String query, long pos) {
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
                                 int pos,
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

    public static Playlist from(StupidPlaylist playlist, int pos) {
        return from((se.splushii.dancingbunnies.musiclibrary.Playlist) playlist, pos, null);
    }

    public static Playlist from(SmartPlaylist playlist, int pos) {
        return from(
                (se.splushii.dancingbunnies.musiclibrary.Playlist) playlist,
                pos,
                playlist.getJSONQueryString()
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(src);
        dest.writeString(id);
        dest.writeInt(type);
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
                && type == playlist.type;
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