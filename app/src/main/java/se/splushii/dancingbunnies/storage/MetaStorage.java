package se.splushii.dancingbunnies.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.media.RatingCompat;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.LibraryEntry;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

public class MetaStorage {
    private static final String LC = Util.getLogContext(MetaStorage.class);

    private final DB dbHandler;
    private final SQLiteDatabase db;

    public MetaStorage(Context context) {
        dbHandler = DB.getInstance(context);
        db = dbHandler.getWritableDatabase();
    }

    public synchronized void close() {
        dbHandler.closeDB();
    }

    public void insertSongs(List<Meta> metaList) {
        long start = System.currentTimeMillis();
        Log.d(LC, "insertSongs start");
        db.beginTransaction();
        for (Meta meta: metaList) {
            insertSong(meta);
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        Log.d(LC, "insertSongs finish " + (System.currentTimeMillis() - start));
    }

    private void insertSong(Meta meta) {
        ContentValues c = new ContentValues();
        for (String key: meta.keySet()) {
            Meta.Type type = Meta.getType(key);
            switch (type) {
                case STRING:
                    c.put(DB.Keyify(key), meta.getString(key));
                    break;
                case LONG:
                    c.put(DB.Keyify(key), meta.getLong(key));
                    break;
                case BITMAP:
                    byte[] array = meta.getBitmap(key);
                    c.put(DB.Keyify(key), array);
                    break;
                case RATING:
                    RatingCompat rating = meta.getRating(key);
                    if (rating == null) {
                        break;
                    }
                    if (rating.getRatingStyle() != RatingCompat.RATING_5_STARS) {
                        Log.w(LC, "Rating style not RATING_5_STARS. Only RATING_5_STARTS supported.");
                        break;
                    }
                    c.put(DB.Keyify(key), rating.getStarRating());
                    break;
                default:
                    Log.w(LC, "Unhandled type: " + type);
                    break;
            }
        }
        db.replace(DB.TABLE_SONGS, null, c);
    }

    public Meta getMetadataEntry(EntryID entryID) {
        Cursor cursor = getBundleQueryCursor(entryID.toBundleQuery());
        List<Meta> list = getMetaFromCursor(cursor);
        cursor.close();
        if (list.isEmpty()) {
            return Meta.UNKNOWN_ENTRY;
        }
        if (list.size() > 1) {
            Log.e(LC, "getMetadataEntry returned more than one result!");
        }
        return list.get(0);
    }

    private Cursor getBundleQueryCursor(Bundle bundleQuery) {
        if (bundleQuery == null || bundleQuery.isEmpty()) {
            return db.rawQuery("SELECT * FROM " + DB.TABLE_SONGS, null);
        }
        StringBuilder query = new StringBuilder("SELECT * FROM " + DB.TABLE_SONGS + " WHERE ");
        String args[] = new String[bundleQuery.size()];
        int index = 0;
        for (String key: bundleQuery.keySet()) {
            switch (Meta.getType(key)) {
                case LONG:
                    args[index] = Long.toString(bundleQuery.getLong(key));
                    break;
                case STRING:
                    args[index] = bundleQuery.getString(key);
                    break;
                case BITMAP:
                case RATING:
                    Log.e(LC, "Unsupported bundleQuery type: " + Meta.getType(key).name());
                    continue;
            }
            if (index != 0) {
                query.append(" AND ");
            }
            query.append(DB.Keyify(key)).append("=?");
            index++;
        }
        return db.rawQuery(query.toString(), args);
    }

    public List<Meta> getMetadataEntries(Bundle bundleQuery) {
        long start = System.currentTimeMillis();
        Log.d(LC, "getMetadataEntries bundleQuery start");
        Cursor cursor = getBundleQueryCursor(bundleQuery);
        List<Meta> list = getMetaFromCursor(cursor);
        cursor.close();
        Log.d(LC, "getMetadataEntries bundleQuery finish " + (System.currentTimeMillis() - start));
        return list;
    }

    public List<LibraryEntry> getEntries(Bundle bundleQuery) {
        long start = System.currentTimeMillis();
        Log.d(LC, "getEntries metaKey start");
        String metaKey = Meta.METADATA_KEY_MEDIA_ID;
        if (bundleQuery.containsKey(Meta.METADATA_KEY_TYPE)) {
            String type = bundleQuery.getString(Meta.METADATA_KEY_TYPE);
            if (type != null) {
                metaKey = type;
            }
        }
        ArrayList<LibraryEntry> list = new ArrayList<>();
        Cursor cursor = getBundleQuerySelect(bundleQuery);
        Log.i(LC, "Entries in '" + metaKey + "' column: " + cursor.getCount());
        int srcIndex = cursor.getColumnIndex(DB.Keyify(Meta.METADATA_KEY_API));
        int metaKeyIndex = cursor.getColumnIndex(DB.Keyify(metaKey));
        int nameKeyIndex = Meta.METADATA_KEY_MEDIA_ID.equals(metaKey) ?
                cursor.getColumnIndex(DB.Keyify(Meta.METADATA_KEY_TITLE)) : metaKeyIndex;
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                String src = Meta.METADATA_KEY_MEDIA_ID.equals(metaKey) ?
                        cursor.getString(srcIndex) : MusicLibraryService.API_ID_DANCINGBUNNIES;
                String id = cursor.getString(metaKeyIndex);
                String name = cursor.getString(nameKeyIndex);
                list.add(new LibraryEntry(
                        new EntryID(
                                src,
                                id,
                                metaKey
                        ), name
                ));
                cursor.moveToNext();
            }
        }
        cursor.close();
        Log.d(LC, "getEntries metaKey finish " + (System.currentTimeMillis() - start));
        return list;
    }

    private Cursor getBundleQuerySelect(Bundle bundleQuery) {
        if (bundleQuery == null || bundleQuery.isEmpty()) {
            return db.rawQuery("SELECT * FROM " + DB.TABLE_SONGS, null);
        }
        String metaKey = Meta.METADATA_KEY_MEDIA_ID;
        if (bundleQuery.containsKey(Meta.METADATA_KEY_TYPE)) {
            String type = bundleQuery.getString(Meta.METADATA_KEY_TYPE);
            bundleQuery.remove(Meta.METADATA_KEY_TYPE);
            if (type != null) {
                metaKey = type;
            }
        }
        StringBuilder query = new StringBuilder();
        if (Meta.METADATA_KEY_MEDIA_ID.equals(metaKey)) {
            query.append("SELECT * FROM " + DB.TABLE_SONGS);
        } else {
            query.append("SELECT DISTINCT ");
            query.append(DB.Keyify(metaKey));
            query.append(" FROM ").append(DB.TABLE_SONGS);
        }
        ArrayList<String> args = new ArrayList<>();
        if (!bundleQuery.isEmpty()) {
            query.append(" WHERE ");
            int index = 0;
            for (String key : bundleQuery.keySet()) {
                if (Meta.METADATA_KEY_TYPE.equals(key)) {
                    continue;
                }
                switch (Meta.getType(key)) {
                    case LONG:
                        args.add(Long.toString(bundleQuery.getLong(key)));
                        break;
                    case STRING:
                        args.add(bundleQuery.getString(key));
                        break;
                    case BITMAP:
                    case RATING:
                        Log.e(LC, "Unsupported bundleQuery type: " + Meta.getType(key).name());
                        continue;
                }
                if (index != 0) {
                    query.append(" AND ");
                }
                query.append(DB.Keyify(key)).append(" GLOB ?");
                index++;
            }
        }
        return db.rawQuery(query.toString(), args.toArray(new String[0]));
    }

    private class ColumnIndexCache {
        private final ArrayMap<String, Integer> mMap = new ArrayMap<>();

        private int getColumnIndex(Cursor cursor, String columnName) {
            if (!mMap.containsKey(columnName))
                mMap.put(columnName, cursor.getColumnIndex(columnName));
            return mMap.get(columnName);
        }

        private void clear() {
            mMap.clear();
        }
    }

    private List<Meta> getMetaFromCursor(Cursor cursor) {
        ColumnIndexCache cache = new ColumnIndexCache();
        LinkedList<Meta> list = new LinkedList<>();
        Log.i(LC, "Cursor entries: " + cursor.getCount());
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                Bundle b = new Bundle();
                for (String key: Meta.db_keys) {
                    Meta.Type type = Meta.getType(key);
                    int index = cache.getColumnIndex(cursor, DB.Keyify(key));
                    if (index == -1 && (type == Meta.Type.STRING || type == Meta.Type.LONG)) {
                        Log.w(LC, "Could not find column: " + DB.Keyify(key));
                        continue;
                    }
                    switch (type) {
                        case STRING:
                            String sValue = cursor.getString(index);
                            if (sValue == null) {
                                break;
                            }
                            b.putString(key, sValue);
                            break;
                        case LONG:
                            // Fixme: May give '0' value when it in fact is null.
                            Long lValue = cursor.getLong(index);
                            b.putLong(key, lValue);
                            break;
                        case BITMAP:
                            byte[] array = cursor.getBlob(index);
                            if (array == null) {
                                break;
                            }
                            b.putByteArray(key, array);
                            break;
                        case RATING:
                            // Fixme: May give '0' value when it in fact is null.
                            float stars = cursor.getFloat(index);
                            RatingCompat rating = RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, stars);
                            b.putParcelable(key, rating);
                            break;
                        default:
                            Log.w(LC, "Unhandled type: " + type);
                            break;
                    }
                }
                b.putString(Meta.METADATA_KEY_TYPE, Meta.METADATA_KEY_MEDIA_ID);
                list.add(new Meta(b));
                cursor.moveToNext();
            }
        }
        cache.clear();
        cursor.close();
        return list;
    }

    public void clearAll() {
        db.delete(DB.TABLE_SONGS, null, null);
    }

    public void clearAll(String src) {
        db.delete(DB.TABLE_SONGS,
                DB.Keyify(Meta.METADATA_KEY_API) + "=?",
                new String[]{src});
    }
}
