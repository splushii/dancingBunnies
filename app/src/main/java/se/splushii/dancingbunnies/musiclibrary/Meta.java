package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import com.google.android.gms.cast.MediaMetadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.BITMAP;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.LONG;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.RATING;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.STRING;

// TODO: Use Meta throughout app. To convert, do for example "new Meta(metaCompat).toCastMeta()".
public class Meta {
    private static String LC = Util.getLogContext(Meta.class);

    public static String getLongDescription(MediaMetadataCompat metadata) {
        String artist = metadata.getString(METADATA_KEY_ARTIST);
        String album = metadata.getString(METADATA_KEY_ALBUM);
        String title = metadata.getString(METADATA_KEY_TITLE);
        return title + " - " + artist + " [" + album + "]";
    }

    public static MediaMetadataCompat from(MediaMetadata castMeta) {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
        b.putString(METADATA_KEY_TITLE, castMeta.getString(MediaMetadata.KEY_TITLE));
        b.putString(METADATA_KEY_ALBUM, castMeta.getString(MediaMetadata.KEY_ALBUM_TITLE));
        b.putString(METADATA_KEY_ARTIST, castMeta.getString(MediaMetadata.KEY_ARTIST));
        b.putString(METADATA_KEY_API, castMeta.getString(METADATA_KEY_API));
        b.putString(METADATA_KEY_MEDIA_ID, castMeta.getString(METADATA_KEY_MEDIA_ID));
        b.putString(METADATA_KEY_TYPE, castMeta.getString(METADATA_KEY_TYPE));
        b.putString(METADATA_KEY_PLAYBACK_TYPE, castMeta.getString(METADATA_KEY_PLAYBACK_TYPE));
        // TODO: Put all metadata in there ^
        return b.build();
    }

    public static MediaMetadata from(MediaMetadataCompat meta, String playbackType) {
        MediaMetadata castMeta = new MediaMetadata();
        castMeta.putString(MediaMetadata.KEY_TITLE, meta.getString(METADATA_KEY_TITLE));
        castMeta.putString(MediaMetadata.KEY_ALBUM_TITLE, meta.getString(METADATA_KEY_ALBUM));
        castMeta.putString(MediaMetadata.KEY_ARTIST, meta.getString(METADATA_KEY_ARTIST));
        castMeta.putString(METADATA_KEY_API, meta.getString(METADATA_KEY_API));
        castMeta.putString(METADATA_KEY_MEDIA_ID, meta.getString(METADATA_KEY_MEDIA_ID));
        castMeta.putString(METADATA_KEY_TYPE, meta.getString(METADATA_KEY_TYPE));
        castMeta.putString(METADATA_KEY_TITLE, meta.getString(METADATA_KEY_TITLE));
        castMeta.putString(METADATA_KEY_PLAYBACK_TYPE, playbackType);
        // TODO: Put all metadata in there ^
        return castMeta;
    }

    public static MediaDescriptionCompat meta2desc(MediaMetadataCompat meta) {
        EntryID entryID = EntryID.from(meta);
        Bundle extras = new Bundle();
        extras.putAll(meta.getBundle());
        return new MediaDescriptionCompat.Builder()
                .setMediaId(entryID.key())
                .setExtras(extras)
                .setTitle(extras.getString(Meta.METADATA_KEY_TITLE))
                .build();
    }

    public static MediaMetadataCompat desc2meta(MediaDescriptionCompat desc) {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
        Bundle descExtras = desc.getExtras();
        for (String key: descExtras.keySet()) {
            switch (typeMap.get(key)) {
                case STRING:
                    b.putString(key, descExtras.getString(key));
                    break;
                case LONG:
                    b.putLong(key, descExtras.getLong(key));
                    break;
                case RATING:
                case BITMAP:
                default:
                    Log.e(LC, "desc2meta: " + typeMap.get(key).name() + " not handled");
                    break;
            }
        }
        return b.build();
    }

    public enum Type {
        STRING, BITMAP, RATING, LONG
    }

    public static final MediaMetadataCompat UNKNOWN_ENTRY = new MediaMetadataCompat.Builder()
            .putString(Meta.METADATA_KEY_API, Meta.METADATA_VALUE_UNKNOWN_SRC)
            .putString(Meta.METADATA_KEY_MEDIA_ID, Meta.METADATA_VALUE_UNKNOWN_ID)
            .putString(Meta.METADATA_KEY_TYPE, Meta.METADATA_VALUE_UNKNOWN_TYPE)
            .putString(Meta.METADATA_KEY_ALBUM, Meta.METADATA_VALUE_UNKNOWN_ALBUM)
            .putString(Meta.METADATA_KEY_ARTIST, Meta.METADATA_VALUE_UNKNOWN_ARTIST)
            .putString(Meta.METADATA_KEY_TITLE, Meta.METADATA_VALUE_UNKNOWN_TITLE)
            .putString(Meta.METADATA_KEY_PLAYBACK_TYPE, PlaybackEntry.USER_TYPE_EXTERNAL)
            .build();

    // Special keys/values
    public static final String METADATA_VALUE_UNKNOWN_SRC =
            "dancingbunnies.metadata.value.UNKNOWN_SRC";
    public static final String METADATA_VALUE_UNKNOWN_ID =
            "dancingbunnies.metadata.value.UNKNOWN_ID";
    public static final String METADATA_VALUE_UNKNOWN_TYPE =
            "dancingbunnies.metadata.value.UNKNOWN_TYPE";
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
    // Only used to identify queue position when de-queueing using onRemoveQueueItem
    public static final String METADATA_KEY_QUEUE_POS =
            "dancingbunnies.metadata.QUEUE_POS";
    // Only used to identify if playback entry is in queue or in playlist
    public static final String METADATA_KEY_PLAYBACK_TYPE =
            "dancingbunnies.metadata.PLAYBACK_TYPE";

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
    public static final HashSet DBKeysSet = new HashSet<>(Arrays.asList(db_keys));

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
        typeMap.put(METADATA_KEY_QUEUE_POS, LONG);
        typeMap.put(METADATA_KEY_PLAYBACK_TYPE, LONG);
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

    public static final HashMap<String, String> humanMap;
    static {
        humanMap = new HashMap<>();
        humanMap.put(METADATA_KEY_TYPE, "type");
        humanMap.put(METADATA_KEY_PARENT_ID, "parent");
        humanMap.put(METADATA_KEY_API, "api");
        humanMap.put(METADATA_KEY_MEDIA_ROOT, "media root");
        humanMap.put(METADATA_KEY_FILE_SIZE, "file size");
        humanMap.put(METADATA_KEY_CONTENT_TYPE, "content type");
        humanMap.put(METADATA_KEY_FILE_SUFFIX, "file suffix");
        humanMap.put(METADATA_KEY_TRANSCODED_TYPE, "transcoded type");
        humanMap.put(METADATA_KEY_TRANSCODED_SUFFIX, "transcoded suffix");
        humanMap.put(METADATA_KEY_BITRATE, "bitrate");
        humanMap.put(METADATA_KEY_DATE_ADDED, "date added");
        humanMap.put(METADATA_KEY_DATE_STARRED, "date starred");
        humanMap.put(METADATA_KEY_HEART_RATING, "heart rating");
        humanMap.put(METADATA_KEY_ALBUM_ID, "album id");
        humanMap.put(METADATA_KEY_ARTIST_ID, "artist id");
        humanMap.put(METADATA_KEY_BOOKMARK_POSITION, "bookmark position");
        humanMap.put(METADATA_KEY_AVERAGE_RATING, "average rating");
        humanMap.put(METADATA_KEY_QUEUE_POS, "current queue position");
        humanMap.put(METADATA_KEY_PLAYBACK_TYPE, "entry playback type");
        // Android keys
        humanMap.put(METADATA_KEY_ADVERTISEMENT, "advertisement");
        humanMap.put(METADATA_KEY_ALBUM, "album");
        humanMap.put(METADATA_KEY_ALBUM_ART, "album art");
        humanMap.put(METADATA_KEY_ALBUM_ARTIST, "album artist");
        humanMap.put(METADATA_KEY_ALBUM_ART_URI, "album art URI");
        humanMap.put(METADATA_KEY_ART, "art");
        humanMap.put(METADATA_KEY_ARTIST, "artist");
        humanMap.put(METADATA_KEY_ART_URI, "art URI");
        humanMap.put(METADATA_KEY_AUTHOR, "author");
        humanMap.put(METADATA_KEY_BT_FOLDER_TYPE, "Bluetooth folder type");
        humanMap.put(METADATA_KEY_COMPILATION, "compilation");
        humanMap.put(METADATA_KEY_COMPOSER, "composer");
        humanMap.put(METADATA_KEY_DATE, "date");
        humanMap.put(METADATA_KEY_DISC_NUMBER, "disc number");
        humanMap.put(METADATA_KEY_DISPLAY_DESCRIPTION, "display description");
        humanMap.put(METADATA_KEY_DISPLAY_ICON, "display icon");
        humanMap.put(METADATA_KEY_DISPLAY_ICON_URI, "display icon URI");
        humanMap.put(METADATA_KEY_DISPLAY_SUBTITLE, "display subtitle");
        humanMap.put(METADATA_KEY_DISPLAY_TITLE, "display title");
        humanMap.put(METADATA_KEY_DOWNLOAD_STATUS, "download status");
        humanMap.put(METADATA_KEY_DURATION, "duration");
        humanMap.put(METADATA_KEY_GENRE, "genre");
        humanMap.put(METADATA_KEY_MEDIA_ID, "media ID");
        humanMap.put(METADATA_KEY_MEDIA_URI, "media URI");
        humanMap.put(METADATA_KEY_NUM_TRACKS, "number of tracks");
        humanMap.put(METADATA_KEY_RATING, "rating");
        humanMap.put(METADATA_KEY_TITLE, "title");
        humanMap.put(METADATA_KEY_TRACK_NUMBER, "track number");
        humanMap.put(METADATA_KEY_USER_RATING, "user rating");
        humanMap.put(METADATA_KEY_WRITER, "writer");
        humanMap.put(METADATA_KEY_YEAR, "year");
    }

    public static String getHumanReadable(String string) {
        return humanMap.get(string);
    }
}
