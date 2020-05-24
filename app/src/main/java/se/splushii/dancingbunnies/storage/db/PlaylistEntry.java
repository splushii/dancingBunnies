package se.splushii.dancingbunnies.storage.db;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = DB.TABLE_PLAYLIST_ENTRIES,
        foreignKeys = @ForeignKey(
                parentColumns = {
                        DB.COLUMN_SRC,
                        DB.COLUMN_ID
                },
                childColumns = {
                        PlaylistEntry.COLUMN_PLAYLIST_SRC,
                        PlaylistEntry.COLUMN_PLAYLIST_ID
                },
                entity = Playlist.class,
                onDelete = CASCADE
        ),
        indices = @Index(value = {
                PlaylistEntry.COLUMN_PLAYLIST_SRC,
                PlaylistEntry.COLUMN_PLAYLIST_ID
        })
// Not possible to constrain the pos because of inserts, because incrementing COLUMN_POS
// needs to be done in a TEMP table, something not supported in Room as far as I know.
// See: https://stackoverflow.com/questions/22494148/incrementing-value-in-table-with-unique-key-causes-constraint-error
//        indices = @Index(value = {
//                PlaylistEntry.COLUMN_PLAYLIST_SRC,
//                PlaylistEntry.COLUMN_PLAYLIST_ID,
//                PlaylistEntry.COLUMN_POS
//        }, unique = true),
//        primaryKeys = {
//                PlaylistEntry.COLUMN_PLAYLIST_SRC,
//                PlaylistEntry.COLUMN_PLAYLIST_ID,
//                PlaylistEntry.COLUMN_POS
//        }
)
public class PlaylistEntry implements Parcelable {
    private static final String COLUMN_ROW_ID = "rowid";
    static final String COLUMN_PLAYLIST_SRC = "playlist_src";
    static final String COLUMN_PLAYLIST_ID = "playlist_id";
    static final String COLUMN_POS = "pos";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ROW_ID)
    public int rowId;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_SRC)
    public String playlist_src;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_ID)
    public String playlist_id;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = COLUMN_POS)
    public long pos;

    PlaylistEntry() {}

    protected PlaylistEntry(Parcel in) {
        playlist_src = Objects.requireNonNull(in.readString());
        playlist_id = Objects.requireNonNull(in.readString());
        src = Objects.requireNonNull(in.readString());
        id = Objects.requireNonNull(in.readString());
        pos = in.readLong();
    }

    public static final Creator<PlaylistEntry> CREATOR = new Creator<PlaylistEntry>() {
        @Override
        public PlaylistEntry createFromParcel(Parcel in) {
            return new PlaylistEntry(in);
        }

        @Override
        public PlaylistEntry[] newArray(int size) {
            return new PlaylistEntry[size];
        }
    };

    public static PlaylistEntry from(PlaylistID playlistID, EntryID entryID, int pos) {
        PlaylistEntry roomPlaylistEntry = new PlaylistEntry();
        roomPlaylistEntry.playlist_src = playlistID.src;
        roomPlaylistEntry.playlist_id = playlistID.id;
        roomPlaylistEntry.src = entryID.src;
        roomPlaylistEntry.id = entryID.id;
        roomPlaylistEntry.pos = pos;
        return roomPlaylistEntry;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(playlist_src);
        dest.writeString(playlist_id);
        dest.writeString(src);
        dest.writeString(id);
        dest.writeLong(pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistEntry entry = (PlaylistEntry) o;
        return pos == entry.pos &&
                Objects.equals(playlist_src, entry.playlist_src) &&
                Objects.equals(playlist_id, entry.playlist_id) &&
                Objects.equals(src, entry.src) &&
                Objects.equals(id, entry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlist_src, playlist_id, src, id, pos);
    }

    @Override
    public String toString() {
        return "PlaylistEntry{" +
                "rowId=" + rowId +
                ", playlist_src='" + playlist_src + '\'' +
                ", playlist_id='" + playlist_id + '\'' +
                ", src='" + src + '\'' +
                ", id='" + id + '\'' +
                ", pos=" + pos +
                '}';
    }
}