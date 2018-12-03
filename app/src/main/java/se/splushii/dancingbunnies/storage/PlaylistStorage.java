package se.splushii.dancingbunnies.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.musiclibrary.SmartPlaylist;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.util.Util;

public class PlaylistStorage {
    private static final String LC = Util.getLogContext(PlaylistStorage.class);

    private final DB dbHandler;
    private final SQLiteDatabase db;

    public PlaylistStorage(Context context) {
        dbHandler = DB.getInstance(context);
        db = dbHandler.getWritableDatabase();
    }

    public synchronized void close() {
        dbHandler.closeDB();
    }

    private boolean playlistExist(PlaylistID playlistID) {
        String selection = DB.COLUMN_PLAYLISTS_PLAYLIST_SRC + "=?"
                + " AND " + DB.COLUMN_PLAYLISTS_PLAYLIST_ID + "=?";
        String[] selectionArgs = {playlistID.src, playlistID.id};
        String[] columns = {DB.COLUMN_PLAYLISTS_TABLE_ID};
        Cursor cursor = db.query(
                DB.TABLE_PLAYLISTS,
                columns,
                selection,
                selectionArgs,
                null, null, null, null
        );
        boolean exist = cursor.getCount() > 0;
        cursor.close();
        return exist;
    }

    private int getPlaylistTableID(PlaylistID playlistID) {
        String selection = DB.COLUMN_PLAYLISTS_PLAYLIST_SRC + "=?"
                + " AND " + DB.COLUMN_PLAYLISTS_PLAYLIST_ID + "=?";
        String[] selectionArgs = {playlistID.src, playlistID.id};
        String[] columns = {DB.COLUMN_PLAYLISTS_TABLE_ID};
        Cursor cursor = db.query(
                DB.TABLE_PLAYLISTS,
                columns,
                selection,
                selectionArgs,
                null, null, null, null
        );
        if (!cursor.moveToFirst() || cursor.getCount() < 1) {
            cursor.close();
            return -1;
        }
        int tableID = cursor.getInt(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_TABLE_ID));
        cursor.close();
        return tableID;
    }

    public void clearAll(String src) {
        String selection = DB.COLUMN_PLAYLISTS_PLAYLIST_SRC + "=?";
        String[] selectionArgs = {src};
        String[] columns = {DB.COLUMN_PLAYLISTS_TABLE_ID};
        Cursor cursor = db.query(
                DB.TABLE_PLAYLISTS,
                columns,
                selection,
                selectionArgs,
                null, null, null, null
        );
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                int playlistsTableId = cursor.getInt(0);
                db.delete(
                        DB.TABLE_PLAYLIST_ENTRIES,
                        DB.COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID + "=" + playlistsTableId,
                        null
                );
                cursor.moveToNext();
            }
        }
        cursor.close();
        int num = db.delete(
                DB.TABLE_PLAYLISTS,
                selection,
                selectionArgs
        );
        Log.d(LC, "clearAll dropped " + num + " playlist tables.");
    }

    public void insertPlaylists(List<Playlist> playlists) {
        db.beginTransaction();
        for (Playlist playlist: playlists) {
            ContentValues c = new ContentValues();
            c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_SRC, playlist.id.src);
            c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_ID, playlist.id.id);
            c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_TYPE, playlist.id.type);
            c.put(DB.COLUMN_PLAYLISTS_PLAYLIST_NAME, playlist.name);
            if (PlaylistID.TYPE_SMART.equals(playlist.id.type)) {
                SmartPlaylist smartPlaylist = (SmartPlaylist) playlist;
                c.put(DB.COLUMN_PLAYLISTS_SMART_QUERY, smartPlaylist.getQuery());
            }
            long rowId = db.replaceOrThrow(DB.TABLE_PLAYLISTS, null, c);
            if (PlaylistID.TYPE_STUPID.equals(playlist.id.type)) {
                StupidPlaylist stupidPlaylist = (StupidPlaylist) playlist;
                int index = 0;
                for (EntryID entryID: stupidPlaylist.getEntries()) {
                    c = new ContentValues();
                    c.put(DB.COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID, rowId);
                    c.put(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC, entryID.src);
                    c.put(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_ID, entryID.id);
                    c.put(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION, index++);
                    db.insertOrThrow(DB.TABLE_PLAYLIST_ENTRIES, null, c);
                }
            }
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public List<PlaylistItem> getPlaylists() {
        List<PlaylistItem> playlistEntries = new LinkedList<>();
        String[] columns = {
                DB.COLUMN_PLAYLISTS_PLAYLIST_SRC,
                DB.COLUMN_PLAYLISTS_PLAYLIST_ID,
                DB.COLUMN_PLAYLISTS_PLAYLIST_TYPE,
                DB.COLUMN_PLAYLISTS_PLAYLIST_NAME
        };
        Cursor cursor = db.query(
                DB.TABLE_PLAYLISTS,
                columns,
                null, null, null, null, null
        );
        if (cursor.moveToFirst()) {
            int srcIndex = cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_SRC);
            int idIndex = cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_ID);
            int typeIndex = cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_TYPE);
            int nameIndex = cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_NAME);
            while (!cursor.isAfterLast()) {
                String src = cursor.getString(srcIndex);
                String id = cursor.getString(idIndex);
                String type = cursor.getString(typeIndex);
                String name = cursor.getString(nameIndex);
                playlistEntries.add(new PlaylistItem(new PlaylistID(src, id ,type), name));
                cursor.moveToNext();
            }
        }
        cursor.close();
        return playlistEntries;
    }

    public Playlist getPlaylist(PlaylistID playlistID) {
        String selection = DB.COLUMN_PLAYLISTS_PLAYLIST_SRC + "=?"
                + " AND " + DB.COLUMN_PLAYLISTS_PLAYLIST_ID + "=?";
        String[] selectionArgs = {playlistID.src, playlistID.id};
        String[] columns = {
                DB.COLUMN_PLAYLISTS_TABLE_ID,
                DB.COLUMN_PLAYLISTS_PLAYLIST_SRC,
                DB.COLUMN_PLAYLISTS_PLAYLIST_ID,
                DB.COLUMN_PLAYLISTS_PLAYLIST_TYPE,
                DB.COLUMN_PLAYLISTS_PLAYLIST_NAME,
                DB.COLUMN_PLAYLISTS_SMART_QUERY
        };
        Cursor cursor = db.query(
                DB.TABLE_PLAYLISTS,
                columns,
                selection,
                selectionArgs,
                null, null, null, null
        );
        if (!cursor.moveToFirst()) {
            return null;
        }
        if (cursor.isAfterLast()) {
            return null;
        }
        String src = cursor.getString(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_SRC));
        String id = cursor.getString(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_ID));
        String type = cursor.getString(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_TYPE));
        String name = cursor.getString(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_PLAYLIST_NAME));
        Playlist playlist = null;
        switch (type) {
            case PlaylistID.TYPE_SMART:
                String query = cursor.getString(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_SMART_QUERY));
                playlist = new SmartPlaylist(new PlaylistID(src, id ,type), name, query);
                break;
            case PlaylistID.TYPE_STUPID:
                int tableID = cursor.getInt(cursor.getColumnIndex(DB.COLUMN_PLAYLISTS_TABLE_ID));
                List<EntryID> playlistEntries = getStupidPlaylistEntries(tableID);
                playlist = new StupidPlaylist(new PlaylistID(src, id, type), name, playlistEntries);
                break;
            default:
                break;
        }
        cursor.close();
        return playlist;
    }

    private List<EntryID> getStupidPlaylistEntries(int tableId) {
        List<EntryID> playlistEntries = new LinkedList<>();
        String selection = DB.COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID + "=" + tableId;
        String[] columns = {
                DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC,
                DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_ID,
        };
        Cursor cursor = db.query(
                DB.TABLE_PLAYLIST_ENTRIES,
                columns,
                selection,
                null, null, null,
                DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION+ " ASC",
                null
        );
        if (cursor.moveToFirst()) {
            int srcIndex = cursor.getColumnIndex(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC);
            int idIndex = cursor.getColumnIndex(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_ID);
            while (!cursor.isAfterLast()) {
                String src = cursor.getString(srcIndex);
                String id = cursor.getString(idIndex);
                playlistEntries.add(new EntryID(src, id, Meta.METADATA_KEY_MEDIA_ID));
                cursor.moveToNext();
            }
        }
        cursor.close();
        return playlistEntries;
    }

    public void addToPlaylist(PlaylistID playlistID, List<EntryID> entryIDs) {
        if (!playlistID.type.equals(PlaylistID.TYPE_STUPID)) {
            Log.w(LC, "addToPlaylist tried to add entry to non-stupid playlist");
            return;
        }
        int tableID = getPlaylistTableID(playlistID);
        if (tableID == -1) {
            Log.w(LC, "addToPlaylist tried to add entry to unknown playlist");
            return;
        }
        int nextPlaylistEntryPos = getStupidPlaylistEntries(tableID).size();
        db.beginTransaction();
        for (EntryID entryID: entryIDs) {
            ContentValues c = new ContentValues();
            c.put(DB.COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID, tableID);
            c.put(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_SRC, entryID.src);
            c.put(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_ID, entryID.id);
            c.put(DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION, nextPlaylistEntryPos);
            db.replaceOrThrow(DB.TABLE_PLAYLIST_ENTRIES, null, c);
            nextPlaylistEntryPos++;
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public void removeFromPlaylist(PlaylistID playlistID, int position) {
        if (!playlistID.type.equals(PlaylistID.TYPE_STUPID)) {
            Log.w(LC, "removeFromPlaylist tried to remove entry from non-stupid playlist");
            return;
        }
        int tableID = getPlaylistTableID(playlistID);
        String selection = DB.COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID + "=" + tableID
                + " AND " + DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION + "=" + position;
        int num = db.delete(DB.TABLE_PLAYLIST_ENTRIES, selection, null);
        // Reorder entries positioned after the deleted entry
        db.execSQL("UPDATE " + DB.TABLE_PLAYLIST_ENTRIES
                + " SET " + DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION + " = "
                + DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION + " - 1"
                + " where " + DB.COLUMN_PLAYLIST_ENTRIES_PLAYLISTS_TABLE_ID + " = " + tableID
                + " and " + DB.COLUMN_PLAYLIST_ENTRIES_ENTRY_POSITION + " > " + position);
        Log.d(LC, "Deleted " + num + " playlist entries from "
                + playlistID.src + " " + playlistID.id);
    }
}
