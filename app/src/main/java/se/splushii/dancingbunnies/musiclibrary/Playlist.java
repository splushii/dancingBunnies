package se.splushii.dancingbunnies.musiclibrary;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import se.splushii.dancingbunnies.musiclibrary.export.SchemaValidator;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public abstract class Playlist {
    private static final String LC = Util.getLogContext(Playlist.class);

    public static final String TYPE_STUPID = "static";
    public static final String TYPE_SMART = "smart";

    public final Meta meta;
    Playlist(Meta meta) {
        this.meta = meta;
    }

    public static Playlist from(Context context, String src, Path playlistFile) {
        JacksonPlaylistRoot jRoot = getJackson(context, playlistFile);
        if (jRoot == null) {
            return null;
        }
        String id = jRoot.playlist.id;
        String type;
        type = Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST;
        Meta meta = new Meta(new EntryID(src, id, type));
        for (Map<String, String> metaEntry: jRoot.playlist.meta) {
            Set<String> keySet = metaEntry.keySet();
            if (keySet.isEmpty()) {
                continue;
            }
            if (keySet.size() > 1) {
                throw new RuntimeException("This should never happen");
            }
            String key = keySet.toArray(new String[0])[0];
            String value = metaEntry.get(key);
            switch (Meta.getType(key)) {
                case STRING:
                    meta.addString(key, value);
                    break;
                case LONG:
                    if (value != null) {
                        try {
                            meta.addLong(key, Long.parseLong(value));
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                case DOUBLE:
                    if (value != null) {
                        try {
                            meta.addDouble(key, Double.parseDouble(value));
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
            }
        }
        switch (jRoot.playlist.type) {
            case Playlist.TYPE_STUPID:
                List<PlaylistEntry> playlistEntries = new ArrayList<>();
                for (int i = 0; i < jRoot.entries.size(); i++) {
                    JacksonPlaylistEntry jacksonPlaylistEntry = jRoot.entries.get(i);
                    String playlistEntryID = jacksonPlaylistEntry.id;
                    String entryType;
                    switch (jacksonPlaylistEntry.entry.type) {
                        case EntryID.TYPE_TRACK:
                        case Meta.FIELD_SPECIAL_ENTRY_ID_TRACK:
                            entryType = Meta.FIELD_SPECIAL_ENTRY_ID_TRACK;
                            break;
                        case EntryID.TYPE_PLAYLIST:
                        case Meta.FIELD_SPECIAL_ENTRY_ID_PLAYLIST:
                            // TODO: Implement
                            throw new RuntimeException("Not implemented");
                        default:
                            Log.e(LC, "Playlist entry type not supported: "
                                    + jacksonPlaylistEntry.entry.type);
                            continue;
                    }
                    EntryID entryID = new EntryID(
                            jacksonPlaylistEntry.entry.src,
                            jacksonPlaylistEntry.entry.id,
                            entryType
                    );
                    playlistEntries.add(PlaylistEntry.from(
                            meta.entryID,
                            playlistEntryID,
                            entryID,
                            i
                    ));
                }
                return new StupidPlaylist(meta, playlistEntries);
            case Playlist.TYPE_SMART:
                return new SmartPlaylist(meta, QueryNode.fromJSON(jRoot.query));
            default:
                Log.e(LC, "Playlist type not supported: " + jRoot.playlist.type);
                return null;
        }
    }

    private static String writeToFile(Path path, JacksonPlaylistRoot jRoot) {
        ObjectMapper objMapper = new ObjectMapper(new YAMLFactory());
        try {
            objMapper.writeValue(Files.newOutputStream(path), jRoot);
        } catch (IOException e) {
            e.printStackTrace();
            return "Could not write changes to local playlist file: " + e.getMessage();
        }
        return null;
    }

    public static String addPlaylistInFile(Context context,
                                           Path path,
                                           EntryID playlistID,
                                           String name,
                                           String query) {
        JacksonPlaylistRoot jRoot = new JacksonPlaylistRoot();
        JacksonPlaylistMeta jPlaylistMeta = new JacksonPlaylistMeta();
        String type;
        if (query == null) {
            type = Playlist.TYPE_STUPID;
            jRoot.entries = new ArrayList<>();
        } else {
            type = Playlist.TYPE_SMART;
            jRoot.query = query;
        }
        jPlaylistMeta.id = playlistID.id;
        jPlaylistMeta.type = type;
        jPlaylistMeta.meta = new ArrayList<>();
        jPlaylistMeta.meta.add(Collections.singletonMap(Meta.FIELD_TITLE, name));
        jRoot.schema_version = 1L;
        jRoot.playlist = jPlaylistMeta;
        return writeToFile(path, jRoot);
    }

    private static JacksonPlaylistRoot getJackson(Context context, Path playlistFile) {
        try {
            if (!SchemaValidator.validatePlaylist(
                    context,
                    new FileInputStream(playlistFile.toFile())
            )) {
                Log.e(LC, "Playlist schema invalid");
                return null;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        ObjectMapper objMapper = new ObjectMapper(new YAMLFactory());
        JacksonPlaylistRoot jacksonPlaylistRoot;
        try {
            jacksonPlaylistRoot = objMapper.readValue(
                    Files.newInputStream(playlistFile),
                    JacksonPlaylistRoot.class
            );
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        long schemaVersion = jacksonPlaylistRoot.schema_version;
        if (schemaVersion != 1) {
            return null;
        }
        return jacksonPlaylistRoot;
    }

    public static String addEntryInFile(Context context,
                                        Path path,
                                        EntryID entryID,
                                        String beforePlaylistEntryID,
                                        Meta metaSnapshot) {
        JacksonPlaylistRoot jRoot = getJackson(context, path);
        if (jRoot == null) {
            return "Could not parse playlist json";
        }
        JacksonPlaylistEntry jPlaylistEntry = new JacksonPlaylistEntry();
        jPlaylistEntry.id = PlaylistEntry.generatePlaylistEntryID();
        JacksonEntry jEntry = new JacksonEntry();
        jEntry.type = EntryID.TYPE_TRACK; // TODO: Support TYPE_PLAYLIST
        jEntry.src = entryID.src;
        jEntry.id = entryID.id;
        Set<String> includedMetaKeys = new HashSet<>(Arrays.asList(
                Meta.FIELD_ARTIST,
                Meta.FIELD_YEAR,
                Meta.FIELD_ALBUM,
                Meta.FIELD_DISCNUMBER,
                Meta.FIELD_TRACKNUMBER,
                Meta.FIELD_TITLE
        ));
        jEntry.meta = metaSnapshot.toStringMapList(includedMetaKeys);
        jPlaylistEntry.entry = jEntry;

        boolean found = false;
        for (int i = 0; i < jRoot.entries.size(); i++) {
            if (jRoot.entries.get(i).id.equals(beforePlaylistEntryID)) {
                jRoot.entries.add(i, jPlaylistEntry);
                found = true;
                break;
            }
        }
        if (!found) {
            jRoot.entries.add(jPlaylistEntry);
        }
        return writeToFile(path, jRoot);
    }

    public static String deleteEntryInFile(Context context,
                                           Path path,
                                           String playlistEntryID) {
        JacksonPlaylistRoot jRoot = getJackson(context, path);
        if (jRoot == null) {
            return "Could not parse playlist json";
        }
        for (int i = 0; i < jRoot.entries.size(); i++) {
            if (jRoot.entries.get(i).id.equals(playlistEntryID)) {
                jRoot.entries.remove(i);
                break;
            }
        }
        return writeToFile(path, jRoot);
    }

    public static String moveEntryInFile(Context context,
                                         Path path,
                                         String playlistEntryID,
                                         String beforePlaylistEntryID) {
        JacksonPlaylistRoot jRoot = getJackson(context, path);
        if (jRoot == null) {
            return "Could not parse playlist json";
        }
        JacksonPlaylistEntry jPlaylistEntry = null;
        for (int i = 0; i < jRoot.entries.size(); i++) {
            if (jRoot.entries.get(i).id.equals(playlistEntryID)) {
                jPlaylistEntry = jRoot.entries.remove(i);
                break;
            }
        }
        if (jPlaylistEntry == null) {
            // Could not find entry to move. Assume this is ok.
            return null;
        }
        boolean found = false;
        for (int i = 0; i < jRoot.entries.size(); i++) {
            if (jRoot.entries.get(i).id.equals(beforePlaylistEntryID)) {
                jRoot.entries.add(i, jPlaylistEntry);
                found = true;
                break;
            }
        }
        if (!found) {
            jRoot.entries.add(jPlaylistEntry);
        }
        return writeToFile(path, jRoot);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JacksonPlaylistRoot {
        public long schema_version;
        public JacksonPlaylistMeta playlist;
        public List<JacksonPlaylistEntry> entries;
        public String query;
    }

    public static class JacksonPlaylistMeta {
        public String type;
        public String id;
        public List<Map<String, String>> meta;
    }

    public static class JacksonPlaylistEntry {
        public String id;
        public JacksonEntry entry;
    }

    public static class JacksonEntry {
        public String type;
        public String src;
        public String id;
        public List<Map<String, String>> meta;
    }
}
