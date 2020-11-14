package se.splushii.dancingbunnies.musiclibrary;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.text.InputType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.cast.MediaMetadata;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.DOUBLE;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.LONG;
import static se.splushii.dancingbunnies.musiclibrary.Meta.Type.STRING;

public class Meta {
    private static final String LC = Util.getLogContext(Meta.class);

    public static final Meta UNKNOWN_ENTRY = new Meta(EntryID.UNKOWN);
    private static final HashMap<String, Type> typeMap = new HashMap<>();
    private static final HashSet<String> localOriginSet = new HashSet<>();

    private static final String FIELD_DANCINGBUNNIES_PREFIX = "se.splushii.dancingbunnies.meta.field.";
    public static final String FIELD_SPECIAL_ENTRY_SRC = FIELD_DANCINGBUNNIES_PREFIX + "entry.src";
    public static final String FIELD_SPECIAL_ENTRY_ID_TRACK = FIELD_DANCINGBUNNIES_PREFIX + "entry.id.track";
    public static final String FIELD_SPECIAL_ENTRY_ID_PLAYLIST = FIELD_DANCINGBUNNIES_PREFIX + "entry.id.playlist";

    public static final String FIELD_LOCAL_CACHED = FIELD_DANCINGBUNNIES_PREFIX + "cached"; static{localOriginSet.add(FIELD_LOCAL_CACHED);}
    public static final String FIELD_LOCAL_CACHED_VALUE_YES = "yes";

    private static final String FIELD_LOCAL_USER_PREFIX = FIELD_DANCINGBUNNIES_PREFIX + "user.";
    private static final String FIELD_LOCAL_USER_TYPE_STRING = "string";
    private static final String FIELD_LOCAL_USER_TYPE_LONG = "long";
    private static final String FIELD_LOCAL_USER_TYPE_DOUBLE = "double";

    public static final String FIELD_DURATION = "duration"; static{typeMap.put(FIELD_DURATION, LONG);}
    public static final String FIELD_CONTENT_TYPE = "content type";
    public static final String FIELD_FILE_SUFFIX = "suffix";
    public static final String FIELD_BITRATE = "bitrate"; static {typeMap.put(FIELD_BITRATE, LONG);}
    public static final String FIELD_MEDIA_ROOT = "media root";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ARTIST = "artist";
    public static final String FIELD_ALBUM = "album";
    public static final String FIELD_FILE_SIZE = "file size"; static {typeMap.put(FIELD_FILE_SIZE, LONG);}
    public static final String FIELD_BOOKMARK_POSITION = "bookmark position"; static{typeMap.put(FIELD_BOOKMARK_POSITION, LONG);}
    public static final String FIELD_AVERAGE_RATING = "avarage rating"; static{typeMap.put(FIELD_AVERAGE_RATING, DOUBLE);}
    public static final String FIELD_DISCNUMBER = "discnumber"; static {typeMap.put(FIELD_DISCNUMBER, LONG);}
    public static final String FIELD_TOTALTRACKS = "totaltracks"; static{typeMap.put(FIELD_TOTALTRACKS, LONG);}
    public static final String FIELD_RATING = "rating"; static{typeMap.put(FIELD_RATING, LONG);}
    public static final String FIELD_TRACKNUMBER = "tracknumber"; static{typeMap.put(FIELD_TRACKNUMBER, LONG);}
    public static final String FIELD_USER_RATING = "user rating"; static{typeMap.put(FIELD_USER_RATING, LONG);}
    public static final String FIELD_YEAR = "year"; static{typeMap.put(FIELD_YEAR, LONG);}
    public static final String FIELD_GENRE = "genre";
    public static final String FIELD_PARENT_ID = "parent id";
    public static final String FIELD_TRANSCODED_TYPE = "transcoded type";
    public static final String FIELD_TRANSCODED_SUFFIX = "transcoded suffix";
    public static final String FIELD_MEDIA_URI = "media uri";
    public static final String FIELD_ALBUM_ART_URI = "album art uri";
    public static final String FIELD_DATE_ADDED = "date added";
    public static final String FIELD_DATE_CHANGED = "date changed";
    public static final String FIELD_DATE_STARRED = "date starred";
    public static final String FIELD_ARTIST_ID = "artist id";
    public static final String FIELD_ALBUM_ID = "album id";
    public static final String FIELD_PLAY_COUNT = "play count"; static{typeMap.put(FIELD_PLAY_COUNT, LONG);}
    public static final String FIELD_SONG_COUNT = "song count"; static{typeMap.put(FIELD_SONG_COUNT, LONG);}
    public static final String FIELD_COMMENT = "comment";
    public static final String FIELD_OWNER = "owner";
    public static final String FIELD_QUERY = "query";

    private static final String DELIM = "; ";

    private static final String JSON_KEY_ENTRY_ID = "entry_id";
    private static final String JSON_KEY_STRINGS = "strings";
    private static final String JSON_KEY_LONGS = "longs";
    private static final String JSON_KEY_DOUBLES = "doubles";
    private static final String BUNDLE_KEY_STRINGS = "dancingbunnies.bundle.key.meta.strings";
    private static final String BUNDLE_KEY_LONGS = "dancingbunnies.bundle.key.meta.longs";
    private static final String BUNDLE_KEY_DOUBLES = "dancingbunnies.bundle.key.meta.doubles";

    public static final List<String> FIELD_ORDER = Arrays.asList(
            Meta.FIELD_TITLE,
            Meta.FIELD_ALBUM,
            Meta.FIELD_ARTIST,
            Meta.FIELD_YEAR,
            Meta.FIELD_GENRE,
            Meta.FIELD_DURATION,
            Meta.FIELD_TRACKNUMBER,
            Meta.FIELD_DISCNUMBER,
            Meta.FIELD_CONTENT_TYPE,
            Meta.FIELD_BITRATE,
            Meta.FIELD_SPECIAL_ENTRY_SRC,
            Meta.FIELD_MEDIA_ROOT,
            Meta.FIELD_SPECIAL_ENTRY_ID_TRACK
    );

    public enum Type {
        STRING,
        LONG,
        DOUBLE
    }

    public final EntryID entryID;
    private final HashMap<String, List<String>> stringMap;
    private final HashMap<String, List<Long>> longMap;
    private final HashMap<String, List<Double>> doubleMap;
    private String tagDelimiter;

    public Meta(EntryID entryID) {
        this.entryID = entryID;
        stringMap = new HashMap<>();
        longMap = new HashMap<>();
        doubleMap = new HashMap<>();
        tagDelimiter = null;
    }

    private Meta(EntryID entryID,
                 HashMap<String, List<String>> stringMap,
                 HashMap<String, List<Long>> longMap,
                 HashMap<String, List<Double>> doubleMap) {
        this.entryID = entryID;
        this.stringMap = stringMap;
        this.longMap = longMap;
        this.doubleMap = doubleMap;
        tagDelimiter = null;
    }

    @SuppressWarnings("unchecked")
    public Meta(Bundle b) {
        entryID = EntryID.from(b);
        stringMap = (HashMap<String, List<String>>) b.getSerializable(BUNDLE_KEY_STRINGS);
        longMap = (HashMap<String, List<Long>>) b.getSerializable(BUNDLE_KEY_LONGS);
        doubleMap = (HashMap<String, List<Double>>) b.getSerializable(BUNDLE_KEY_DOUBLES);
        tagDelimiter = null;
    }

    public static Meta from(JSONObject json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JSONObject jsonEntryIDJSON = json.getJSONObject(JSON_KEY_ENTRY_ID);
            EntryID entryID = EntryID.from(jsonEntryIDJSON);
            JSONObject jsonStringsJSON = json.getJSONObject(JSON_KEY_STRINGS);
            HashMap<String, List<String>> stringMap = mapper.readValue(
                    jsonStringsJSON.toString(),
                    new TypeReference<HashMap<String, List<String>>>() {}
            );
            JSONObject jsonLongsJSON = json.getJSONObject(JSON_KEY_LONGS);
            HashMap<String, List<Long>> longMap = mapper.readValue(
                    jsonLongsJSON.toString(),
                    new TypeReference<HashMap<String, List<Long>>>() {}
            );
            JSONObject jsonDoublesJSON = json.getJSONObject(JSON_KEY_DOUBLES);
            HashMap<String, List<Double>> doubleMap = mapper.readValue(
                    jsonDoublesJSON.toString(),
                    new TypeReference<HashMap<String, List<Double>>>() {}
            );
            return new Meta(entryID, stringMap, longMap, doubleMap);
        } catch (JSONException | JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isSpecial(String key) {
        return FIELD_SPECIAL_ENTRY_ID_TRACK.equals(key)
                || FIELD_SPECIAL_ENTRY_ID_PLAYLIST.equals(key)
                || FIELD_SPECIAL_ENTRY_SRC.equals(key);
    }

    public static boolean isLocal(String key) {
        return isLocalUser(key) || localOriginSet.contains(key);
    }

    public static boolean isLocalUser(String key) {
        return key.startsWith(FIELD_LOCAL_USER_PREFIX);
    }

    public static String constructLocalUserStringKey(String key) {
        return constructLocalUserKey(key, Meta.FIELD_LOCAL_USER_TYPE_STRING);
    }

    public static String constructLocalUserLongKey(String key) {
        return constructLocalUserKey(key, Meta.FIELD_LOCAL_USER_TYPE_LONG);
    }

    public static String constructLocalUserDoubleKey(String key) {
        return constructLocalUserKey(key, Meta.FIELD_LOCAL_USER_TYPE_DOUBLE);
    }

    private static String constructLocalUserKey(String key, String type) {
        return Meta.FIELD_LOCAL_USER_PREFIX + type + "." + key;
    }

    public static String getDurationString(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds %= 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    public static String getDisplayValue(String key, String value) {
        long longValue;
        try {
            switch (key) {
                case Meta.FIELD_DURATION:
                    return getDurationString(Long.parseLong(value));
                case Meta.FIELD_FILE_SIZE:
                    longValue = Long.parseLong(value);
                    if (longValue >= 1_000_000L) {
                        return String.format(Locale.getDefault(),"%.1f MB", longValue / 1_000_000d);
                    }
                    if (longValue >= 1_000L) {
                        return String.format(Locale.getDefault(),"%.1f KB", longValue / 1_000d);
                    }
                    return String.format(Locale.getDefault(),"%d B", longValue);
                case Meta.FIELD_BITRATE:
                    return String.format(Locale.getDefault(), "%d kbps", Long.parseLong(value));
                default:
                    break;
            }
        } catch (NumberFormatException ignored) {}
        return value;
    }

    public static String getDisplayValue(String key, long value) {
        return getDisplayValue(key, Long.toString(value));
    }

    public static String getUserLocalTagName(String key) {
        if (isLocalUser(key)) {
            String keySuffix = key.substring(FIELD_LOCAL_USER_PREFIX.length());
            return keySuffix.split("\\.", 2)[1];
        }
        return null;
    }

    public static String getDisplayKey(String key) {
        if (isLocalUser(key)) {
            return "dB " + getUserLocalTagName(key);
        }
        if (isLocal(key) || isSpecial(key)) {
            String tagName = key.substring(FIELD_DANCINGBUNNIES_PREFIX.length());
            return "dB " + tagName;
        }
        return key;
    }

    public static Type getType(String key) {
        if (isLocalUser(key)) {
            String keySuffix = key.substring(FIELD_LOCAL_USER_PREFIX.length());
            String type = keySuffix.split("\\.", 2)[0];
            switch (type) {
                default:
                case FIELD_LOCAL_USER_TYPE_STRING:
                    return STRING;
                case FIELD_LOCAL_USER_TYPE_LONG:
                    return LONG;
                case FIELD_LOCAL_USER_TYPE_DOUBLE:
                    return DOUBLE;
            }
        }
        return typeMap.getOrDefault(key, STRING);
    }

    public static int getInputType(String key) {
        switch (Meta.getType(key)) {
            default:
            case STRING:
                return InputType.TYPE_CLASS_TEXT;
            case LONG:
                return InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_SIGNED;
            case DOUBLE:
                return InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        InputType.TYPE_NUMBER_FLAG_SIGNED;
        }
    }

    public boolean has(String key) {
        switch (getType(key)) {
            case STRING:
                return stringMap.containsKey(key);
            case LONG:
                return longMap.containsKey(key);
            case DOUBLE:
                return doubleMap.containsKey(key);
        }
        return false;
    }

    public void setTagDelimiter(String tagDelimiter) {
        this.tagDelimiter = tagDelimiter;
    }

    public void addLong(String key, long value) {
        List<Long> longs = longMap.getOrDefault(key, new ArrayList<>());
        longs.add(value);
        longMap.put(key, longs);
    }

    public List<Long> getLongs(String key) {
        return longMap.get(key);
    }

    public long getFirstLong(String key, long defaultValue) {
        List<Long> longs = longMap.get(key);
        if (longs != null && longs.size() > 0) {
            return longs.get(0);
        }
        return defaultValue;
    }

    public void addString(String key, String value) {
        addString(key, value, tagDelimiter);
    }

    public void addString(String key, String value, String delimiter) {
        List<String> strings = stringMap.getOrDefault(key, new ArrayList<>());
        if (delimiter != null && !delimiter.isEmpty() && Util.isValidRegex(delimiter)) {
            strings.addAll(Arrays.asList(value.split(delimiter)));
        } else {
            strings.add(value);
        }
        stringMap.put(key, strings);
    }

    public List<String> getStrings(String key) {
        return stringMap.get(key);
    }

    public String getFirstString(String key) {
        List<String> strings = stringMap.get(key);
        if (strings != null && strings.size() > 0) {
            return strings.get(0);
        }
        return "";
    }

    public void addDouble(String key, double value) {
        List<Double> doubles = doubleMap.getOrDefault(key, new ArrayList<>());
        doubles.add(value);
        doubleMap.put(key, doubles);
    }

    public List<Double> getDoubles(String key) {
        return doubleMap.get(key);
    }

    public String getAsString(String key) {
        switch (getType(key)) {
            case STRING:
                List<String> strings = stringMap.get(key);
                return strings == null ? "" : getAsString(strings);
            case LONG:
                List<Long> longs = longMap.get(key);
                return longs == null ? "" : longs.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(DELIM));
            case DOUBLE:
                List<Double> doubles = doubleMap.get(key);
                return doubles == null ? "" : doubles.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(DELIM));
        }
        return "";
    }


    public static String getAsString(List<String> strings) {
        return String.join(DELIM, strings);
    }

    public static class ComparableValue implements Comparable<ComparableValue> {
        Meta.Type type;
        private List<String> stringValues;
        private List<Long> longValues;
        private List<Double> doubleValues;

        private ComparableValue() {}

        private static ComparableValue withStrings(List<String> values) {
            ComparableValue comparableValue = new ComparableValue();
            comparableValue.type = STRING;
            comparableValue.stringValues = values;
            return comparableValue;
        }

        private static ComparableValue withLongs(List<Long> values) {
            ComparableValue comparableValue = new ComparableValue();
            comparableValue.type = LONG;
            comparableValue.longValues = values;
            return comparableValue;
        }

        private static ComparableValue withDoubles(List<Double> values) {
            ComparableValue comparableValue = new ComparableValue();
            comparableValue.type = DOUBLE;
            comparableValue.doubleValues = values;
            return comparableValue;
        }

        @Override
        public int compareTo(ComparableValue that) {
            if (type != that.type) {
                return type.ordinal() - that.type.ordinal();
            }
            switch (type) {
                default:
                case STRING:
                    return compare(stringValues, that.stringValues);
                case LONG:
                    return compare(longValues, that.longValues);
                case DOUBLE:
                    return compare(doubleValues, that.doubleValues);
            }
        }

        private <T extends Comparable<T>>int compare(List<T> list1, List<T> list2) {
            if (list1 == null || list1.isEmpty()) {
                if (list2 == null || list2.isEmpty()) {
                    return 0;
                }
                return -1;
            } else if (list2 == null || list2.isEmpty()) {
                return 1;
            }
            return list1.get(0).compareTo(list2.get(0));
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComparableValue that = (ComparableValue) o;
            if (type != that.type) return false;
            switch (type) {
                default:
                case STRING:
                    return stringValues.equals(that.stringValues);
                case LONG:
                    return longValues.equals(that.longValues);
                case DOUBLE:
                    return doubleValues.equals(that.doubleValues);
            }
        }
    }

    public ComparableValue getAsComparable(String key) {
        switch (getType(key)) {
            default:
            case STRING:
                return ComparableValue.withStrings(stringMap.get(key));
            case LONG:
                return ComparableValue.withLongs(longMap.get(key));
            case DOUBLE:
                return ComparableValue.withDoubles(doubleMap.get(key));
        }
    }

    public Bundle toBundle() {
        Bundle b = entryID.toBundle();
        b.putSerializable(BUNDLE_KEY_STRINGS, stringMap);
        b.putSerializable(BUNDLE_KEY_LONGS, longMap);
        b.putSerializable(BUNDLE_KEY_DOUBLES, doubleMap);
        return b;
    }

    public JSONObject toJSON() {
        JSONObject root = new JSONObject();
        ObjectMapper mapper = new ObjectMapper();
        try {
            root.put(JSON_KEY_ENTRY_ID, entryID.toJSON());
            root.put(JSON_KEY_STRINGS, new JSONObject(mapper.writeValueAsString(stringMap)));
            root.put(JSON_KEY_LONGS, new JSONObject(mapper.writeValueAsString(longMap)));
            root.put(JSON_KEY_DOUBLES, new JSONObject(mapper.writeValueAsString(doubleMap)));
        } catch (JSONException | JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
        return root;
    }

    public List<Map<String, String>> toStringMapList(Set<String> includedMetaKeys) {
        List<Map<String, String>> jMetas = new ArrayList<>();
        for (String key: keySet()) {
            if (includedMetaKeys == null || !includedMetaKeys.contains(key)) {
                continue;
            }
            List<String> values = new ArrayList<>();
            switch (Meta.getType(key)) {
                case STRING:
                    values = getStrings(key);
                    break;
                case LONG:
                    values = getLongs(key).stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList());
                    break;
                case DOUBLE:
                    values = getDoubles(key).stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList());
                    break;
            }
            jMetas.addAll(values.stream()
                    .map(v -> new LinkedHashMap<>(Collections.singletonMap(key, v)))
                    .collect(Collectors.toList())
            );
        }
        return jMetas;
    }

    @SuppressLint("WrongConstant")
    public MediaMetadataCompat toMediaMetadataCompat() {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
        b.putString(EntryID.BUNDLE_KEY_SRC, entryID.src);
        b.putString(EntryID.BUNDLE_KEY_ID, entryID.id);
        b.putString(EntryID.BUNDLE_KEY_TYPE, entryID.type);
        b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getAsString(FIELD_TITLE));
        b.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, getAsString(FIELD_ALBUM));
        b.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getAsString(FIELD_ARTIST));
        String albumArtURI = getAsString(FIELD_ALBUM_ART_URI);
        if (albumArtURI != null) {
            b.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, albumArtURI);
        }
        long duration = getFirstLong(FIELD_DURATION, -1);
        if (duration > 0) {
            b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        }
        return b.build();
    }

    public MediaMetadata toCastMediaMetadata() {
        MediaMetadata castMeta = new MediaMetadata();
        castMeta.putString(EntryID.BUNDLE_KEY_SRC, entryID.src);
        castMeta.putString(EntryID.BUNDLE_KEY_ID, entryID.id);
        castMeta.putString(EntryID.BUNDLE_KEY_TYPE, entryID.type);
        castMeta.putString(MediaMetadata.KEY_TITLE, getAsString(FIELD_TITLE));
        castMeta.putString(MediaMetadata.KEY_ALBUM_TITLE, getAsString(FIELD_ALBUM));
        castMeta.putString(MediaMetadata.KEY_ARTIST, getAsString(FIELD_ARTIST));
        return castMeta;
    }

    public Set<String> keySet() {
        HashSet<String> keySet = new HashSet<>();
        keySet.addAll(stringMap.keySet());
        keySet.addAll(longMap.keySet());
        keySet.addAll(doubleMap.keySet());
        return keySet;
    }

    public static String getTitle(MediaMetadataCompat metadata) {
        return metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    }

    public static String getArtistAlbum(MediaMetadataCompat metadata) {
        String artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
        String album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
        String value = "";
        if (artist != null && !artist.isEmpty()) {
            value += artist;
        }
        if (album != null && !album.isEmpty()) {
            if (!value.isEmpty()) {
                value += " ";
            }
            value += "[" + album + "]";
        }
        return value;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Meta:");
        for (String key: keySet()) {
            sb.append("\n").append(key);
            switch (getType(key)) {
                case STRING:
                    sb.append(" (string):\t").append(getAsString(key));
                    break;
                case LONG:
                    sb.append(" (long):\t").append(getAsString(key));
                    break;
                case DOUBLE:
                    sb.append(" (double):\t").append(getAsString(key));
                    break;
            }
        }
        return sb.toString();
    }

    public String getFormattedFileSize() {
        long size = getFirstLong(Meta.FIELD_FILE_SIZE, -1);
        return size < 0 ? null : Meta.getDisplayValue(Meta.FIELD_FILE_SIZE, size);
    }
}
