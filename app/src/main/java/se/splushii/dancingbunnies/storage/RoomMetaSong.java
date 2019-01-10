package se.splushii.dancingbunnies.storage;

import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.util.Util;

@Entity(tableName = RoomDB.SONG_TABLE,
        indices = {
                @Index(value = {RoomMetaSong.COLUMN_API, RoomMetaSong.COLUMN_ID}, unique = true)
        },
        primaryKeys = {RoomMetaSong.COLUMN_API, RoomMetaSong.COLUMN_ID}
)
public class RoomMetaSong {
    private static final String LC = Util.getLogContext(RoomMetaSong.class);
    // TODO: Reenable some keys
    public static final String[] db_keys = {
            // DancingBunnies keys
//        METADATA_KEY_ALBUM_ID,
            Meta.METADATA_KEY_API,
//        METADATA_KEY_ARTIST_ID,
//        METADATA_KEY_AVERAGE_RATING,
            Meta.METADATA_KEY_BITRATE,
//        METADATA_KEY_BOOKMARK_POSITION,
            Meta.METADATA_KEY_CONTENT_TYPE,
//        METADATA_KEY_DATE_ADDED,
//        METADATA_KEY_DATE_STARRED,
//        METADATA_KEY_FILE_SIZE,
//        METADATA_KEY_FILE_SUFFIX,
//        METADATA_KEY_HEART_RATING,
            Meta.METADATA_KEY_MEDIA_ROOT,
//        METADATA_KEY_PARENT_ID,
//        METADATA_KEY_TYPE,
//        METADATA_KEY_TRANSCODED_SUFFIX,
//        METADATA_KEY_TRANSCODED_TYPE,
            // Android MediaMetadataCompat keys
//        METADATA_KEY_ADVERTISEMENT,
            Meta.METADATA_KEY_ALBUM,
//        METADATA_KEY_ALBUM_ART,
            Meta.METADATA_KEY_ALBUM_ARTIST,
//        METADATA_KEY_ALBUM_ART_URI,
//        METADATA_KEY_ART,
            Meta.METADATA_KEY_ARTIST,
//        METADATA_KEY_ART_URI,
//        METADATA_KEY_AUTHOR,
//        METADATA_KEY_BT_FOLDER_TYPE,
//        METADATA_KEY_COMPILATION,
//        METADATA_KEY_COMPOSER,
//        METADATA_KEY_DATE,
            Meta.METADATA_KEY_DISC_NUMBER,
//        METADATA_KEY_DISPLAY_DESCRIPTION,
//        METADATA_KEY_DISPLAY_ICON,
//        METADATA_KEY_DISPLAY_ICON_URI,
//        METADATA_KEY_DISPLAY_SUBTITLE,
//        METADATA_KEY_DISPLAY_TITLE,
//        METADATA_KEY_DOWNLOAD_STATUS,
            Meta.METADATA_KEY_DURATION,
            Meta.METADATA_KEY_GENRE,
            Meta.METADATA_KEY_MEDIA_ID,
//        METADATA_KEY_MEDIA_URI,
//        METADATA_KEY_NUM_TRACKS,
//        METADATA_KEY_RATING,
            Meta.METADATA_KEY_TITLE,
            Meta.METADATA_KEY_TRACK_NUMBER,
//        METADATA_KEY_USER_RATING,
//        METADATA_KEY_WRITER,
            Meta.METADATA_KEY_YEAR
    };
    public static final HashSet DBKeysSet = new HashSet<>(Arrays.asList(db_keys));

    public static final String COLUMN_API = "api";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_ROOT = "root";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_ALBUM = "album";
    public static final String COLUMN_ARTIST = "artist";
    public static final String COLUMN_ALBUM_ARTIST = "albumartist";
    public static final String COLUMN_GENRE = "genre";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_CONTENT_TYPE = "contenttype";
    public static final String COLUMN_YEAR = "year";
    public static final String COLUMN_TRACK_NUMBER = "tracknum";
    public static final String COLUMN_TRACK_DISCNUMBER = "discnum";
    public static final String COLUMN_BITRATE = "bitrate";

    @NonNull
    String api;
    String root;
    @NonNull
    String id;
    String title;
    String album;
    String artist;
    String albumartist;
    String genre;
    long duration;
    String contenttype;
    long year;
    long tracknum;
    long discnum;
    long bitrate;


    public Meta getMeta() {
        Bundle b = new Bundle();
        b.putString(Meta.METADATA_KEY_API, api);
        b.putString(Meta.METADATA_KEY_MEDIA_ROOT, root);
        b.putString(Meta.METADATA_KEY_MEDIA_ID, id);
        b.putString(Meta.METADATA_KEY_TITLE, title);
        b.putString(Meta.METADATA_KEY_ALBUM, album);
        b.putString(Meta.METADATA_KEY_ARTIST, artist);
        b.putString(Meta.METADATA_KEY_ALBUM_ARTIST, albumartist);
        b.putString(Meta.METADATA_KEY_GENRE, genre);
        b.putLong(Meta.METADATA_KEY_DURATION, duration);
        b.putString(Meta.METADATA_KEY_CONTENT_TYPE, contenttype);
        b.putLong(Meta.METADATA_KEY_YEAR, year);
        b.putLong(Meta.METADATA_KEY_TRACK_NUMBER, tracknum);
        b.putLong(Meta.METADATA_KEY_DISC_NUMBER, discnum);
        b.putLong(Meta.METADATA_KEY_BITRATE, bitrate);
        return new Meta(b);
    }

    static String columnName(String metaType) {
        switch(metaType) {
            case Meta.METADATA_KEY_API:
                return COLUMN_API;
            case Meta.METADATA_KEY_MEDIA_ROOT:
                return COLUMN_ROOT;
            case Meta.METADATA_KEY_MEDIA_ID:
                return COLUMN_ID;
            case Meta.METADATA_KEY_TITLE:
                return COLUMN_TITLE;
            case Meta.METADATA_KEY_ALBUM:
                return COLUMN_ALBUM;
            case Meta.METADATA_KEY_ARTIST:
                return COLUMN_ARTIST;
            case Meta.METADATA_KEY_ALBUM_ARTIST:
                return COLUMN_ALBUM_ARTIST;
            case Meta.METADATA_KEY_GENRE:
                return COLUMN_GENRE;
            case Meta.METADATA_KEY_DURATION:
                return COLUMN_DURATION;
            case Meta.METADATA_KEY_CONTENT_TYPE:
                return COLUMN_CONTENT_TYPE;
            case Meta.METADATA_KEY_YEAR:
                return COLUMN_YEAR;
            case Meta.METADATA_KEY_TRACK_NUMBER:
                return COLUMN_TRACK_NUMBER;
            case Meta.METADATA_KEY_DISC_NUMBER:
                return COLUMN_TRACK_DISCNUMBER;
            case Meta.METADATA_KEY_BITRATE:
                return COLUMN_BITRATE;
            default:
                return null;
        }
    }

    public void from(Meta meta) {
        for (String key: meta.keySet()) {
            switch (key) {
                case Meta.METADATA_KEY_API:
                    api = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_MEDIA_ROOT:
                    root = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_MEDIA_ID:
                    id = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_TITLE:
                    title = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_ALBUM:
                    album = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_ARTIST:
                    artist = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_ALBUM_ARTIST:
                    albumartist = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_GENRE:
                    genre = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_DURATION:
                    duration = meta.getLong(key);
                    break;
                case Meta.METADATA_KEY_CONTENT_TYPE:
                    contenttype = meta.getString(key);
                    break;
                case Meta.METADATA_KEY_YEAR:
                    year = meta.getLong(key);
                    break;
                case Meta.METADATA_KEY_TRACK_NUMBER:
                    tracknum = meta.getLong(key);
                    break;
                case Meta.METADATA_KEY_DISC_NUMBER:
                    discnum = meta.getLong(key);
                    break;
                case Meta.METADATA_KEY_BITRATE:
                    bitrate = meta.getLong(key);
                    break;
                default:
                    Log.e(LC, "Unhandled key: " + key);
            }
        }
    }
}