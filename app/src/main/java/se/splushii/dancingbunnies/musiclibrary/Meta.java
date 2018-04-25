package se.splushii.dancingbunnies.musiclibrary;

import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import com.google.android.gms.cast.MediaMetadata;

import java.util.HashMap;

import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.BITMAP;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.LONG;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.RATING;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.STRING;

public class Meta {
    private static String LC = Util.getLogContext(Meta.class);

    public static String getLongDescription(MediaMetadataCompat metadata) {
        String artist = metadata.getString(Meta.METADATA_KEY_ARTIST);
        String album = metadata.getString(Meta.METADATA_KEY_ALBUM);
        String title = metadata.getString(Meta.METADATA_KEY_TITLE);
        return title + " - " + artist + " [" + album + "]";
    }

    public static MediaMetadata from(MediaMetadataCompat meta) {
        MediaMetadata castMeta = new MediaMetadata();
        castMeta.putString(MediaMetadata.KEY_TITLE, meta.getString(Meta.METADATA_KEY_TITLE));
        castMeta.putString(MediaMetadata.KEY_ALBUM_TITLE, meta.getString(Meta.METADATA_KEY_ALBUM));
        castMeta.putString(MediaMetadata.KEY_ARTIST, meta.getString(Meta.METADATA_KEY_ARTIST));
        castMeta.putString(Meta.METADATA_KEY_API, meta.getString(Meta.METADATA_KEY_API));
        castMeta.putString(Meta.METADATA_KEY_MEDIA_ID, meta.getString(Meta.METADATA_KEY_MEDIA_ID));
        castMeta.putString(Meta.METADATA_KEY_TYPE, meta.getString(Meta.METADATA_KEY_TYPE));
        return castMeta;
    }

    public enum Type {
        STRING, BITMAP, RATING, LONG
    }

    public static final MediaMetadataCompat UNKNOWN_ENTRY = new MediaMetadataCompat.Builder()
            .putString(Meta.METADATA_KEY_ALBUM, Meta.METADATA_VALUE_UNKNOWN_ALBUM)
            .putString(Meta.METADATA_KEY_ARTIST, Meta.METADATA_VALUE_UNKNOWN_ARTIST)
            .putString(Meta.METADATA_KEY_TITLE, Meta.METADATA_VALUE_UNKNOWN_TITLE)
            .build();

    // Special keys/values
    public static final String METADATA_VALUE_UNKNOWN_ARTIST =
            "dancingbunnies.metadata.value.UNKNOWN_ARTIST";
    public static final String METADATA_VALUE_UNKNOWN_ALBUM =
            "dancingbunnies.metadata.value.UNKNOWN_ALBUM";
    public static final String METADATA_VALUE_UNKNOWN_TITLE =
            "dancingbunnies.metadata.value.UNKNOWN_TITLE";

    // All metadata keys
    // When adding a new key, do not forget to:
    // 1: Add a public static final String below.
    // 2: Add it to 'keys' below.
    // 3: Add it to 'typeMap' below.
    // 4: Think about a better way to maintain this...

    public static final String METADATA_KEY_ADVERTISEMENT =
            MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT;
    public static final String METADATA_KEY_ALBUM =
            MediaMetadataCompat.METADATA_KEY_ALBUM;
    public static final String METADATA_KEY_ALBUM_ART =
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
    public static final String METADATA_KEY_ALBUM_ARTIST =
            MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST;
    public static final String METADATA_KEY_ALBUM_ART_URI =
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
    public static final String METADATA_KEY_ART =
            MediaMetadataCompat.METADATA_KEY_ART;
    public static final String METADATA_KEY_ARTIST =
            MediaMetadataCompat.METADATA_KEY_ARTIST;
    public static final String METADATA_KEY_ART_URI =
            MediaMetadataCompat.METADATA_KEY_ART_URI;
    public static final String METADATA_KEY_AUTHOR =
            MediaMetadataCompat.METADATA_KEY_AUTHOR;
    public static final String METADATA_KEY_BT_FOLDER_TYPE =
            MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE;
    public static final String METADATA_KEY_COMPILATION =
            MediaMetadataCompat.METADATA_KEY_COMPILATION;
    public static final String METADATA_KEY_COMPOSER =
            MediaMetadataCompat.METADATA_KEY_COMPOSER;
    public static final String METADATA_KEY_DATE =
            MediaMetadataCompat.METADATA_KEY_DATE;
    public static final String METADATA_KEY_DISC_NUMBER =
            MediaMetadataCompat.METADATA_KEY_DISC_NUMBER;
    public static final String METADATA_KEY_DISPLAY_DESCRIPTION =
            MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
    public static final String METADATA_KEY_DISPLAY_ICON =
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON;
    public static final String METADATA_KEY_DISPLAY_ICON_URI =
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI;
    public static final String METADATA_KEY_DISPLAY_SUBTITLE =
            MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
    public static final String METADATA_KEY_DISPLAY_TITLE =
            MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
    public static final String METADATA_KEY_DOWNLOAD_STATUS =
            MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS;
    public static final String METADATA_KEY_DURATION =
            MediaMetadataCompat.METADATA_KEY_DURATION;
    public static final String METADATA_KEY_GENRE =
            MediaMetadataCompat.METADATA_KEY_GENRE;
    public static final String METADATA_KEY_MEDIA_ID =
            MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
    public static final String METADATA_KEY_MEDIA_URI =
            MediaMetadataCompat.METADATA_KEY_MEDIA_URI;
    public static final String METADATA_KEY_NUM_TRACKS =
            MediaMetadataCompat.METADATA_KEY_NUM_TRACKS;
    public static final String METADATA_KEY_RATING =
            MediaMetadataCompat.METADATA_KEY_RATING;
    public static final String METADATA_KEY_TITLE =
            MediaMetadataCompat.METADATA_KEY_TITLE;
    public static final String METADATA_KEY_TRACK_NUMBER =
            MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER;
    public static final String METADATA_KEY_USER_RATING =
            MediaMetadataCompat.METADATA_KEY_USER_RATING;
    public static final String METADATA_KEY_WRITER =
            MediaMetadataCompat.METADATA_KEY_WRITER;
    public static final String METADATA_KEY_YEAR =
            MediaMetadataCompat.METADATA_KEY_YEAR;
    public static final String METADATA_KEY_TYPE =
            "dancingbunnies.metadata.TYPE";
    public static final String METADATA_KEY_PARENT_ID =
            "dancingbunnies.metadata.PARENT_ID";
    public static final String METADATA_KEY_API =
            "dancingbunnies.metadata.API";
    public static final String METADATA_KEY_MEDIA_ROOT =
            "dancingbunnies.metadata.MEDIA_ROOT";
    public static final String METADATA_KEY_FILE_SIZE =
            "dancingbunnies.metadata.FILE_SIZE";
    public static final String METADATA_KEY_CONTENT_TYPE =
            "dancingbunnies.metadata.CONTENT_TYPE";
    public static final String METADATA_KEY_FILE_SUFFIX =
            "dancingbunnies.metadata.FILE_SUFFIX";
    public static final String METADATA_KEY_TRANSCODED_TYPE =
            "dancingbunnies.metadata.TRANSCODED_TYPE";
    public static final String METADATA_KEY_TRANSCODED_SUFFIX =
            "dancingbunnies.metadata.TRANSCODED_SUFFIX";
    public static final String METADATA_KEY_BITRATE =
            "dancingbunnies.metadata.BITRATE";
    public static final String METADATA_KEY_DATE_ADDED =
            "dancingbunnies.metadata.DATE_ADDED";
    public static final String METADATA_KEY_DATE_STARRED =
            "dancingbunnies.metadata.DATE_STARRED";
    public static final String METADATA_KEY_HEART_RATING =
            "dancingbunnies.metadata.HEART_RATING";
    public static final String METADATA_KEY_ALBUM_ID =
            "dancingbunnies.metadata.ALBUM_ID";
    public static final String METADATA_KEY_ARTIST_ID =
            "dancingbunnies.metadata.ARTIST_ID";
    public static final String METADATA_KEY_BOOKMARK_POSITION =
            "dancingbunnies.metadata.BOOKMARK_POSITION";
    public static final String METADATA_KEY_AVERAGE_RATING =
            "dancingbunnies.metadata.AVERAGE_RATING";

    public static final String[] db_keys = {
        // DancingBunnies keys
        METADATA_KEY_ALBUM_ID,
        METADATA_KEY_API,
        METADATA_KEY_ARTIST_ID,
        METADATA_KEY_AVERAGE_RATING,
        METADATA_KEY_BITRATE,
        METADATA_KEY_BOOKMARK_POSITION,
        METADATA_KEY_CONTENT_TYPE,
        METADATA_KEY_DATE_ADDED,
        METADATA_KEY_DATE_STARRED,
        METADATA_KEY_FILE_SIZE,
        METADATA_KEY_FILE_SUFFIX,
        METADATA_KEY_HEART_RATING,
        METADATA_KEY_MEDIA_ROOT,
        METADATA_KEY_PARENT_ID,
        METADATA_KEY_TYPE,
        METADATA_KEY_TRANSCODED_SUFFIX,
        METADATA_KEY_TRANSCODED_TYPE,
        // Android MediaMetadataCompat keys
        METADATA_KEY_ADVERTISEMENT,
        METADATA_KEY_ALBUM,
        METADATA_KEY_ALBUM_ART,
        METADATA_KEY_ALBUM_ARTIST,
        METADATA_KEY_ALBUM_ART_URI,
        METADATA_KEY_ART,
        METADATA_KEY_ARTIST,
        METADATA_KEY_ART_URI,
        METADATA_KEY_AUTHOR,
        METADATA_KEY_BT_FOLDER_TYPE,
        METADATA_KEY_COMPILATION,
        METADATA_KEY_COMPOSER,
        METADATA_KEY_DATE,
        METADATA_KEY_DISC_NUMBER,
        METADATA_KEY_DISPLAY_DESCRIPTION,
        METADATA_KEY_DISPLAY_ICON,
        METADATA_KEY_DISPLAY_ICON_URI,
        METADATA_KEY_DISPLAY_SUBTITLE,
        METADATA_KEY_DISPLAY_TITLE,
        METADATA_KEY_DOWNLOAD_STATUS,
        METADATA_KEY_DURATION,
        METADATA_KEY_GENRE,
        METADATA_KEY_MEDIA_ID,
        METADATA_KEY_MEDIA_URI,
        METADATA_KEY_NUM_TRACKS,
        METADATA_KEY_RATING,
        METADATA_KEY_TITLE,
        METADATA_KEY_TRACK_NUMBER,
        METADATA_KEY_USER_RATING,
        METADATA_KEY_WRITER,
        METADATA_KEY_YEAR
    };

    private static final HashMap<String, Type> typeMap;
    static {
        typeMap = new HashMap<>();
        typeMap.put(METADATA_KEY_TYPE, STRING);
        typeMap.put(METADATA_KEY_PARENT_ID, STRING);
        typeMap.put(METADATA_KEY_API, STRING);
        typeMap.put(METADATA_KEY_MEDIA_ROOT, STRING);
        typeMap.put(METADATA_KEY_FILE_SIZE, LONG);
        typeMap.put(METADATA_KEY_CONTENT_TYPE, STRING);
        typeMap.put(METADATA_KEY_FILE_SUFFIX, STRING);
        typeMap.put(METADATA_KEY_TRANSCODED_TYPE, STRING);
        typeMap.put(METADATA_KEY_TRANSCODED_SUFFIX, STRING);
        typeMap.put(METADATA_KEY_BITRATE, LONG);
        typeMap.put(METADATA_KEY_DATE_ADDED, STRING);
        typeMap.put(METADATA_KEY_DATE_STARRED, STRING);
        typeMap.put(METADATA_KEY_HEART_RATING, STRING);
        typeMap.put(METADATA_KEY_ALBUM_ID, STRING);
        typeMap.put(METADATA_KEY_ARTIST_ID, STRING);
        typeMap.put(METADATA_KEY_BOOKMARK_POSITION, LONG);
        typeMap.put(METADATA_KEY_AVERAGE_RATING, STRING);
        // Android keys
        typeMap.put(METADATA_KEY_ADVERTISEMENT, LONG);
        typeMap.put(METADATA_KEY_ALBUM, STRING);
        typeMap.put(METADATA_KEY_ALBUM_ART, BITMAP);
        typeMap.put(METADATA_KEY_ALBUM_ARTIST, STRING);
        typeMap.put(METADATA_KEY_ALBUM_ART_URI, STRING);
        typeMap.put(METADATA_KEY_ART, BITMAP);
        typeMap.put(METADATA_KEY_ARTIST, STRING);
        typeMap.put(METADATA_KEY_ART_URI, STRING);
        typeMap.put(METADATA_KEY_AUTHOR, STRING);
        typeMap.put(METADATA_KEY_BT_FOLDER_TYPE, LONG);
        typeMap.put(METADATA_KEY_COMPILATION, STRING);
        typeMap.put(METADATA_KEY_COMPOSER, STRING);
        typeMap.put(METADATA_KEY_DATE, STRING);
        typeMap.put(METADATA_KEY_DISC_NUMBER, LONG);
        typeMap.put(METADATA_KEY_DISPLAY_DESCRIPTION, STRING);
        typeMap.put(METADATA_KEY_DISPLAY_ICON, BITMAP);
        typeMap.put(METADATA_KEY_DISPLAY_ICON_URI, STRING);
        typeMap.put(METADATA_KEY_DISPLAY_SUBTITLE, STRING);
        typeMap.put(METADATA_KEY_DISPLAY_TITLE, STRING);
        typeMap.put(METADATA_KEY_DOWNLOAD_STATUS, LONG);
        typeMap.put(METADATA_KEY_DURATION, LONG);
        typeMap.put(METADATA_KEY_GENRE, STRING);
        typeMap.put(METADATA_KEY_MEDIA_ID, STRING);
        typeMap.put(METADATA_KEY_MEDIA_URI, STRING);
        typeMap.put(METADATA_KEY_NUM_TRACKS, LONG);
        typeMap.put(METADATA_KEY_RATING, RATING);
        typeMap.put(METADATA_KEY_TITLE, STRING);
        typeMap.put(METADATA_KEY_TRACK_NUMBER, LONG);
        typeMap.put(METADATA_KEY_USER_RATING, RATING);
        typeMap.put(METADATA_KEY_WRITER, STRING);
        typeMap.put(METADATA_KEY_YEAR, LONG);
    }

    public static Type getType(String key) {
        if (!typeMap.containsKey(key)) {
            Log.d(LC, METADATA_KEY_DOWNLOAD_STATUS);
            Log.d(LC, key);
            Log.d(LC, "compare: " + METADATA_KEY_DOWNLOAD_STATUS.equals(key));
            Log.d(LC, "key: " + key);
        }
        return typeMap.get(key);
    }
}
