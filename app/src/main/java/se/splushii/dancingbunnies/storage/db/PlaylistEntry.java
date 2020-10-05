package se.splushii.dancingbunnies.storage.db;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.util.Util;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = DB.TABLE_PLAYLIST_ENTRIES,
        foreignKeys = @ForeignKey(
                parentColumns = {
                        Playlist.COLUMN_SRC,
                        Playlist.COLUMN_ID,
                        Playlist.COLUMN_TYPE
                },
                childColumns = {
                        PlaylistEntry.COLUMN_PLAYLIST_SRC,
                        PlaylistEntry.COLUMN_PLAYLIST_ID,
                        PlaylistEntry.COLUMN_PLAYLIST_TYPE
                },
                entity = Playlist.class,
                onDelete = CASCADE
        ),
        indices = @Index(value = {
                PlaylistEntry.COLUMN_PLAYLIST_SRC,
                PlaylistEntry.COLUMN_PLAYLIST_ID,
                PlaylistEntry.COLUMN_PLAYLIST_TYPE,
                PlaylistEntry.COLUMN_ID
        }, unique = true),
        primaryKeys = {
                PlaylistEntry.COLUMN_PLAYLIST_SRC,
                PlaylistEntry.COLUMN_PLAYLIST_ID,
                PlaylistEntry.COLUMN_PLAYLIST_TYPE,
                PlaylistEntry.COLUMN_ID
        }
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
    static final String COLUMN_PLAYLIST_SRC = "playlist_src";
    static final String COLUMN_PLAYLIST_ID = "playlist_id";
    static final String COLUMN_PLAYLIST_TYPE = "playlist_type";
    static final String COLUMN_ID = "id";
    static final String COLUMN_ENTRY_SRC = "entry_src";
    static final String COLUMN_ENTRY_ID = "entry_id";
    static final String COLUMN_ENTRY_TYPE = "entry_type";
    static final String COLUMN_POS = "pos";

    public static final String TYPE_TRACK = "track";

    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_SRC)
    String playlist_src;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_ID)
    String playlist_id;
    @NonNull
    @ColumnInfo(name = COLUMN_PLAYLIST_TYPE)
    String playlist_type;
    @NonNull
    @ColumnInfo(name = COLUMN_ID)
    String playlist_entry_id;
    @NonNull
    @ColumnInfo(name = COLUMN_ENTRY_SRC)
    String entry_src;
    @NonNull
    @ColumnInfo(name = COLUMN_ENTRY_ID)
    String entry_id;
    @NonNull
    @ColumnInfo(name = COLUMN_ENTRY_TYPE)
    String entry_type;
    @NonNull
    @ColumnInfo(name = COLUMN_POS)
    long pos;

    PlaylistEntry() {}

    protected PlaylistEntry(Parcel in) {
        init(
                Objects.requireNonNull(in.readString()),
                Objects.requireNonNull(in.readString()),
                Objects.requireNonNull(in.readString()),
                Objects.requireNonNull(in.readString()),
                Objects.requireNonNull(in.readString()),
                Objects.requireNonNull(in.readString()),
                Objects.requireNonNull(in.readString()),
                in.readLong()
        );
    }

    public static String generatePlaylistEntryID() {
        return Util.generateID();
    }

    private void init(String playlist_src,
                      String playlist_id,
                      String playlist_type,
                      String playlist_entry_id,
                      String entry_src,
                      String entry_id,
                      String entry_type,
                      long position) {
        this.playlist_src = playlist_src;
        this.playlist_id = playlist_id;
        this.playlist_type = playlist_type;
        this.playlist_entry_id = playlist_entry_id;
        this.entry_type = entry_type;
        this.entry_src = entry_src;
        this.entry_id = entry_id;
        this.pos = position;
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

    public static PlaylistEntry from(String playlistSrc,
                                     String playlistID,
                                     String playlistType,
                                     String playlistEntryID,
                                     String entryIDSrc,
                                     String entryIDID,
                                     String entryIDType,
                                     long pos) {
        PlaylistEntry roomPlaylistEntry = new PlaylistEntry();
        roomPlaylistEntry.init(
                playlistSrc,
                playlistID,
                playlistType,
                playlistEntryID,
                entryIDSrc,
                entryIDID,
                entryIDType,
                pos
        );
        return roomPlaylistEntry;
    }

    public static PlaylistEntry from(PlaylistID playlistID,
                                     String playlistEntryID,
                                     EntryID entryID,
                                     int pos) {
        return from(
                playlistID.src,
                playlistID.id,
                playlistID.type,
                playlistEntryID,
                entryID.src,
                entryID.id,
                entryID.type,
                pos
        );
    }

    public static PlaylistEntry from(PlaylistEntry playlistEntry, long pos) {
        return from(
                playlistEntry.playlist_src,
                playlistEntry.playlist_id,
                playlistEntry.playlist_type,
                playlistEntry.playlist_entry_id,
                playlistEntry.entry_src,
                playlistEntry.entry_id,
                playlistEntry.entry_type,
                pos
        );
    }

    public String playlistEntryID() {
        return playlist_entry_id;
    }

    public EntryID entryID() {
        return new EntryID(entry_src, entry_id, entry_type);
    }

    public static List<PlaylistEntry> generatePlaylistEntries(PlaylistID playlistID,
                                                              EntryID[] entryIDs) {
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        for (int index = 0; index < entryIDs.length; index++) {
            EntryID entryID = entryIDs[index];
            playlistEntries.add(PlaylistEntry.from(
                    playlistID,
                    generatePlaylistEntryID(),
                    entryID,
                    index
            ));
        }
        return playlistEntries;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(playlist_src);
        dest.writeString(playlist_id);
        dest.writeString(playlist_type);
        dest.writeString(playlist_entry_id);
        dest.writeString(entry_src);
        dest.writeString(entry_id);
        dest.writeString(entry_type);
        dest.writeLong(pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistEntry entry = (PlaylistEntry) o;
        return Objects.equals(playlist_src, entry.playlist_src) &&
                Objects.equals(playlist_id, entry.playlist_id) &&
                Objects.equals(playlist_type, entry.playlist_type) &&
                Objects.equals(playlist_entry_id, entry.playlist_entry_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                playlist_src,
                playlist_id,
                playlist_type,
                playlist_entry_id
        );
    }

    @NonNull
    @Override
    public String toString() {
        return "PlaylistEntry{" +
                "playlist_src='" + playlist_src + '\'' +
                ", playlist_id='" + playlist_id + '\'' +
                ", playlist_type='" + playlist_type + '\'' +
                ", playlist_entry_id='" + playlist_entry_id + '\'' +
                ", entry_src='" + entry_src + '\'' +
                ", entry_id='" + entry_id + '\'' +
                ", entry_type='" + entry_type + '\'' +
                ", pos=" + pos +
                '}';
    }

    public boolean samePos(PlaylistEntry b) {
        return pos == b.pos;
    }
}