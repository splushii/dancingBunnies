package se.splushii.dancingbunnies.storage.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = DB.TABLE_LIBRARY_TRANSACTIONS,
        indices = {
                @Index(value = { DB.COLUMN_SRC })
        }
)

public class LibraryTransaction {
    // Transactions
    //
    //
    // Playlist transactions
    //
    // PLAYLIST_DELETE(playlist_id[])
    public static final String PLAYLIST_DELETE = "playlist_delete";
    // PLAYLIST_META_ADD(playlist_id, key, value[])
    public static final String PLAYLIST_META_ADD = "playlist_meta_add";
    // PLAYLIST_META_DELETE(playlist_id, key, value[])
    public static final String PLAYLIST_META_DELETE = "playlist_meta_delete";
    // PLAYLIST_META_DELETE(playlist_id, key[]) [currently used? needed?]
    public static final String PLAYLIST_META_DELETE_ALL = "playlist_meta_delete_all";
    // PLAYLIST_META_EDIT(playlist_id, key, oldValue[], newValue[]) -> PLAYLIST_META_DELETE(playlist_id, key, oldValue[]) + PLAYLIST_META_ADD(playlist_id, key, newValue[])
    public static final String PLAYLIST_META_EDIT = "playlist_meta_edit";
    //
    // PLAYLIST_ENTRY_ADD(playlist_id, beforePlaylistEntryID, beforePos, entryID[], metaSnapshot[]) [entryID[] or PlaylistEntry[] as argument?]
    public static final String PLAYLIST_ENTRY_ADD = "playlist_entry_add";
    // PLAYLIST_ENTRY_DELETE(playlist_id, playlistEntryID[], playlistEntryPositions[])
    public static final String PLAYLIST_ENTRY_DELETE = "playlist_entry_delete";
    // PLAYLIST_ENTRY_MOVE(playlist_id, playlistEntryID[], beforePlaylistEntryID, beforePos)
    public static final String PLAYLIST_ENTRY_MOVE = "playlist_entry_move";
    //
    //
    // Entry transactions
    //
    // META_ADD(entry_id, key, value[])
    public static final String META_ADD = "meta_add";
    // META_DELETE(entry_id, key, value[])
    public static final String META_DELETE = "meta_delete";
    // META_DELETE_ALL(entry_id, key[])
    public static final String META_DELETE_ALL = "meta_delete_all";
    // META_EDIT(entry_id, key, oldValue[], newValue[]) -> META_DELETE(key, oldValue[]) + META_ADD(key, newValue[])
    public static final String META_EDIT = "meta_edit";

    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_SRC = "src";
    private static final String COLUMN_ACTION = "action";
    private static final String COLUMN_ARGS = "args";

    @ColumnInfo(name = COLUMN_DATE)
    public long date;
    @NonNull
    @ColumnInfo(name = COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = COLUMN_ACTION)
    public String action;
    @NonNull
    @ColumnInfo(name = COLUMN_ARGS)
    public String args;
}
