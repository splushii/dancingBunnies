package se.splushii.dancingbunnies.musiclibrary;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.export.SchemaValidator;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class StupidPlaylist extends Playlist {
    private static final String LC = Util.getLogContext(StupidPlaylist.class);

    private final List<PlaylistEntry> entries;
    public StupidPlaylist(PlaylistID playlistID, String name, List<PlaylistEntry> entries) {
        super(playlistID, name);
        this.entries = entries;
    }

    public static StupidPlaylist from(String src, Path playlistFile) {
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
        PlaylistID playlistID = new PlaylistID(
                src,
                jacksonPlaylistRoot.playlist.id,
                PlaylistID.TYPE_STUPID
        );
        List<PlaylistEntry> playlistEntries = new ArrayList<>();
        for (int i = 0; i < jacksonPlaylistRoot.entries.size(); i++) {
            JacksonPlaylistEntry jacksonPlaylistEntry = jacksonPlaylistRoot.entries.get(i);
            String playlistEntryID = jacksonPlaylistEntry.id;
            String type;
            switch (jacksonPlaylistEntry.entry.type) {
                case SchemaValidator.PLAYLIST_ENTRY_TYPE_TRACK:
                case Meta.FIELD_SPECIAL_MEDIA_ID:
                    type = Meta.FIELD_SPECIAL_MEDIA_ID;
                    break;
                case SchemaValidator.PLAYLIST_ENTRY_TYPE_PLAYLIST:
                    // TODO: Implement
                    throw new RuntimeException("Not implemented");
                default:
                    Log.e(LC, "Playlist entry type not supported: "
                            + jacksonPlaylistEntry.entry.type);
                    i--;
                    continue;
            }
            EntryID entryID = new EntryID(
                    jacksonPlaylistEntry.entry.src,
                    jacksonPlaylistEntry.entry.id,
                    type
            );
            playlistEntries.add(PlaylistEntry.from(playlistID, playlistEntryID, entryID, i));
        }
        return new StupidPlaylist(
                playlistID,
                jacksonPlaylistRoot.playlist.id,
                playlistEntries
        );
    }

    public List<PlaylistEntry> getEntries() {
        return entries;
    }

    public static class JacksonPlaylistRoot {
        public long schema_version;
        public JacksonPlaylistMeta playlist;
        public List<JacksonPlaylistEntry> entries;
    }

    public static class JacksonPlaylistMeta {
        public String type;
        public String id;
        public List<JacksonMeta> meta;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JacksonMeta {
        public LinkedHashMap<String, String> keyValue;
    }

    public static class JacksonPlaylistEntry {
        public String id;
        public JacksonEntry entry;
    }

    public static class JacksonEntry {
        public String type;
        public String src;
        public String id;
        public List<JacksonMeta> meta;
    }
}
