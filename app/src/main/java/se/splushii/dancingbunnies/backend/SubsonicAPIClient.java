package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import androidx.core.util.Pair;
import se.splushii.dancingbunnies.musiclibrary.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Playlist;
import se.splushii.dancingbunnies.musiclibrary.PlaylistID;
import se.splushii.dancingbunnies.musiclibrary.StupidPlaylist;
import se.splushii.dancingbunnies.storage.db.PlaylistEntry;
import se.splushii.dancingbunnies.util.Util;

public class SubsonicAPIClient extends APIClient {
    private static final String LC = Util.getLogContext(SubsonicAPIClient.class);

    private static final Integer REQ_RETRY_COUNT = 3;

    public enum RequestType {
        GET_INDEXES,
        GET_MUSIC_DIRECTORY,
        GET_MUSIC_FOLDERS,
        GET_PLAYLIST,
        GET_PLAYLISTS
    }

    private static final String API_BASE_PATH = "/rest/";

    private static final String JSON_RESP = "subsonic-response";
    private static final String JSON_STATUS = "status";
    private static final String JSON_VERSION = "version";
    private static final String JSON_INDEX = "index";
    private static final String JSON_INDICES = "indexes";
    private static final String JSON_CHILD = "child";
    private static final String JSON_ARTIST = "artist";
    private static final String JSON_ALBUM = "album";
    private static final String JSON_ID = "id";
    private static final String JSON_PARENT = "parent";
    private static final String JSON_NAME = "name";
    private static final String JSON_ERROR = "error";
    private static final String JSON_MESSAGE = "message";
    private static final String JSON_CODE = "code";
    private static final String JSON_TITLE = "title";
    private static final String JSON_IS_DIR = "isDir";
    private static final String JSON_TRACK = "track";
    private static final String JSON_YEAR = "year";
    private static final String JSON_GENRE = "genre";
    private static final String JSON_COVER_ART = "coverArt";
    private static final String JSON_SIZE = "size";
    private static final String JSON_CONTENT_TYPE = "contentType";
    private static final String JSON_SUFFIX = "suffix";
    private static final String JSON_TRANSCODED_CONTENT_TYPE = "transcodedContentType";
    private static final String JSON_TRANSCODED_SUFFIX = "transcodedSuffix";
    private static final String JSON_DURATION = "duration";
    private static final String JSON_BITRATE = "bitRate";
    private static final String JSON_PATH = "path";
    private static final String JSON_IS_VIDEO = "isVideo";
    private static final String JSON_USER_RATING = "userRating";
    private static final String JSON_AVERAGE_RATING = "averageRating";
    private static final String JSON_PLAY_COUNT = "playCount";
    private static final String JSON_DISC_NUMBER = "discNumber";
    private static final String JSON_CREATED = "created";
    private static final String JSON_STARRED = "starred";
    private static final String JSON_ALBUM_ID = "albumId";
    private static final String JSON_ARTIST_ID = "artistId";
    private static final String JSON_TYPE = "type";
    private static final String JSON_TYPE_MUSIC = "music";
    private static final String JSON_BOOKMARK_POSITION = "bookmarkPosition";
    private static final String JSON_MUSIC_FOLDERS = "musicFolders";
    private static final String JSON_MUSIC_FOLDER = "musicFolder";
    private static final String JSON_DIRECTORY = "directory";
    private static final String JSON_PLAYLISTS = "playlists";
    private static final String JSON_PLAYLIST = "playlist";
    private static final String JSON_ENTRY = "entry";
    private static final String STATUS_OK = "ok";

    private static final String VERSION = "1.15.0";
    private static final String[] SUPPORTED_VERSIONS = {"1.15.0", "1.16.0"};
    private static final String CLIENT_ID = "dancingbunnies";
    private static final String FORMAT = "json";

    private final Context context;
    private final SecureRandom rand;
    private final HashMap<String, Integer> retries;
    private final HTTPRequestQueue httpRequestQueue;

    private String username = "";
    private String password = "";
    private String baseURL = "";
    private String tagDelimiter = null;


    public SubsonicAPIClient(String src, Context context) {
        super(src);
        this.context = context;
        retries = new HashMap<>();
        SecureRandom tmpRand;
        try {
            tmpRand = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            Log.d(LC, "SHA1PRNG not supported");
            e.printStackTrace();
            tmpRand = null;
            // TODO(kringlan): do stuff here.
        }
        rand = tmpRand;
        this.httpRequestQueue = HTTPRequestQueue.getInstance(context);
    }

    private void onRequestFail(RequestType type,
                               final CompletableFuture<Void> pastReq,
                               String query,
                               String musicFolder,
                               String errorMsg,
                               ConcurrentLinkedQueue<Meta> metaList,
                               ConcurrentLinkedQueue<Playlist> playlists,
                               ConcurrentLinkedQueue<EntryID> playlistEntries,
                               APIClientRequestHandler handler) {
        int retryCount = retries.getOrDefault(query, 1);
        String status = "Request failed:\ntype: " + type + "\nquery: " + query;
        if (retryCount > REQ_RETRY_COUNT) {
            Log.d(LC,  status + " No more retries.");
            retries.remove(query);
            pastReq.complete(null);
            return;
        }
        retries.put(query, retryCount + 1);
        Log.d(LC, status + "\nRetry " + retryCount + "/" + REQ_RETRY_COUNT);
        CompletableFuture<Void> newReq;
        switch(type) {
            case GET_INDEXES:
                newReq = getIndexesQuery(query, musicFolder, metaList, handler);
                break;
            case GET_MUSIC_FOLDERS:
                newReq = getMusicFoldersQuery(query, metaList, handler);
                break;
            case GET_MUSIC_DIRECTORY:
                newReq = getMusicDirectoryQuery(query, musicFolder, metaList, handler);
                break;
            case GET_PLAYLISTS:
                newReq = getPlaylistsQuery(query, playlists, handler);
                break;
            case GET_PLAYLIST:
                newReq = getPlaylistQuery(query, playlistEntries, handler);
                break;
            default:
                newReq = new CompletableFuture<>();
                newReq.complete(null);
                break;
        }
        newReq.thenRun(() -> pastReq.complete(null));
    }

    @Override
    public boolean hasLibrary() {
        return true;
    }

    @Override
    public CompletableFuture<Optional<String>> heartbeat() {
        String query = baseURL + "ping" + getBaseQuery();
        final CompletableFuture<Optional<String>> req = new CompletableFuture<>();
        httpRequestQueue.addToRequestQueue(new StringRequest(
                query,
                response -> {
                    String status = statusOK(response);
                    if (status.isEmpty()) {
                        req.complete(Optional.empty());
                    } else {
                        Log.e(LC, "heartbeat: " + status);
                        req.complete(Optional.of(status));
                    }
                },
                error -> {
                    String errMsg = HTTPRequestQueue.getHTTPErrorMessage(error);
                    Log.e(LC, "heartbeat: " + errMsg);
                    // TODO: Can errMsg null? (app crash)
                    req.complete(Optional.of(errMsg));
                }
        ));
        return req;
    }

    private CompletableFuture<Void> getMusicFolders(final ConcurrentLinkedQueue<Meta> metaList,
                                                    final APIClientRequestHandler handler) {
        String query = baseURL + "getMusicFolders" + getBaseQuery();
        return getMusicFoldersQuery(query, metaList, handler);
    }

    private CompletableFuture<Void> getMusicFoldersQuery(final String query,
                                                         final ConcurrentLinkedQueue<Meta> metaList,
                                                         final APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        final CompletableFuture<Optional<List<Pair<String, String>>>> req = new CompletableFuture<>();
        httpRequestQueue.addToRequestQueue(new StringRequest(
                query,
                response -> {
                    String status = statusOK(response);
                    if (status.isEmpty()) {
                        List<Pair<String, String>> folders = new ArrayList<>();
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONObject jResp = json.getJSONObject(JSON_RESP);
                            JSONObject jFolders = jResp.getJSONObject(JSON_MUSIC_FOLDERS);
                            JSONArray jFolderArray = jFolders.getJSONArray(JSON_MUSIC_FOLDER);
                            for (int i = 0; i < jFolderArray.length(); i++) {
                                JSONObject jFolder = jFolderArray.getJSONObject(i);
                                String id = jFolder.getString(JSON_ID);
                                String name = jFolder.getString(JSON_NAME);
                                folders.add(new Pair<>(id, name));
                            }
                        } catch (JSONException e) {
                            Log.e(LC, "JSON error in getMusicFolders: " + e.getMessage());
                        }
                        req.complete(Optional.of(folders));
                    } else {
                        Log.e(LC, "getMusicFolders: " + status);
                        req.complete(Optional.empty());
                    }
                },
                error -> onRequestFail(
                        RequestType.GET_MUSIC_FOLDERS,
                        ret,
                        query,
                        "",
                        HTTPRequestQueue.getHTTPErrorMessage(error),
                        metaList,
                        null,
                        null,
                        handler
                )
        ));
        req.thenAccept(musicFolders -> {
            if (!musicFolders.isPresent()) {
                handler.onFailure("Could not fetch Subsonic music folders.");
                ret.complete(null);
                return;
            }
            handler.onProgress("Fetching music folder indexes...");
            List<CompletableFuture<String>> indexReqList = new ArrayList<>();
            for (Pair<String, String> musicFolder: musicFolders.get()) {
                final CompletableFuture<String> indexReq = new CompletableFuture<>();
                indexReqList.add(indexReq);
                getIndexes(musicFolder.first, musicFolder.second, metaList, handler)
                        .thenRun(() -> indexReq.complete(null));
            }
            // TODO: how to handle errors? Send with handler
            // (and collected by MusicLibraryService or higher)?
            CompletableFuture<Void> allOf = CompletableFuture
                    .allOf(indexReqList.toArray(new CompletableFuture[0]));
            allOf.thenRun(() -> ret.complete(null));
        });
        return ret;
    }

    private CompletableFuture<Void> getIndexes(final String musicFolderId,
                                               final String musicFolder,
                                               final ConcurrentLinkedQueue<Meta> metaList,
                                               final APIClientRequestHandler handler) {
        final String query = baseURL + "getIndexes" + getBaseQuery()
                + "&musicFolderId=" + musicFolderId;
        return getIndexesQuery(query, musicFolder, metaList, handler);
    }

    private CompletableFuture<Void> getIndexesQuery(final String query,
                                                    final String musicFolder,
                                                    final ConcurrentLinkedQueue<Meta> metaList,
                                                    final APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        httpRequestQueue.addToRequestQueue(new StringRequest(
                query,
                response -> {
                    String status = statusOK(response);
                    if (status.isEmpty()) {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONObject jResp = json.getJSONObject(JSON_RESP);
                            JSONObject jIndices = jResp.getJSONObject(JSON_INDICES);
                            List<CompletableFuture<Void>> reqList = new ArrayList<>();
                            if (jIndices.has(JSON_INDEX)) {
                                JSONArray jIndexArray = jIndices.getJSONArray(JSON_INDEX);
                                for (int i = 0; i < jIndexArray.length(); i++) {
                                    JSONObject jIndex = jIndexArray.getJSONObject(i);
                                    JSONArray jArtistArray = jIndex.getJSONArray(JSON_ARTIST);
                                    for (int j = 0; j < jArtistArray.length(); j++) {
                                        JSONObject jArtist = jArtistArray.getJSONObject(j);
                                        String artistId = jArtist.getString(JSON_ID);
                                        final CompletableFuture<Void> req = new CompletableFuture<>();
                                        reqList.add(req);
                                        getMusicDirectory(artistId, musicFolder, metaList, handler)
                                                .thenRun(() -> req.complete(null));
                                    }
                                }
                            }
                            if (jIndices.has(JSON_CHILD)) {
                                JSONArray jChildArray = jIndices.getJSONArray(JSON_CHILD);
                                for (int i = 0; i < jChildArray.length(); i++) {
                                    JSONObject jChild = jChildArray.getJSONObject(i);
                                    // Required attributes
                                    String id = jChild.getString(JSON_ID);
                                    boolean isDir = jChild.getBoolean(JSON_IS_DIR);
                                    if (isDir) {
                                        final CompletableFuture<Void> req = new CompletableFuture<>();
                                        reqList.add(req);
                                        getMusicDirectory(id, musicFolder, metaList, handler)
                                                .thenRun(() -> req.complete(null));
                                    } else {
                                        handleJSONChild(jChild, musicFolder).ifPresent(meta ->
                                                addMeta(meta, metaList, handler)
                                        );
                                    }
                                }
                            }
                            CompletableFuture<Void> allOf = CompletableFuture.allOf(reqList.toArray(new CompletableFuture[0]));
                            allOf.thenRun(() -> ret.complete(null));
                        } catch (JSONException e) {
                            Log.e(LC, "JSON error in getIndexes: " + e.getMessage());
                            ret.complete(null);
                        }
                    } else {
                        Log.e(LC, "getIndexes: " + status);
                        ret.complete(null);
                    }
                },
                error -> {
                    Log.e(LC, "onFailure in getIndexes");
                    onRequestFail(
                            RequestType.GET_INDEXES,
                            ret,
                            query,
                            musicFolder,
                            HTTPRequestQueue.getHTTPErrorMessage(error),
                            metaList,
                            null,
                            null,
                            handler);
                }
        ));
        return ret;
    }

    private void addMeta(Meta meta,
                         ConcurrentLinkedQueue<Meta> metaList,
                         APIClientRequestHandler handler) {
        metaList.add(meta);
        if (metaList.size() % 100 == 0) {
            handler.onProgress("Fetched " + metaList.size() + " entries...");
        }
    }

    private Optional<Meta> handleJSONChild(JSONObject jChild, String musicFolder)
            throws JSONException {
        // Required attributes
        String id = jChild.getString(JSON_ID);
        if (jChild.getBoolean(JSON_IS_DIR)) {
            return Optional.empty();
        }
        String title = jChild.getString(JSON_TITLE);
        EntryID entryID = new EntryID(src, id, Meta.FIELD_SPECIAL_MEDIA_ID);
        Meta meta = new Meta(entryID);
        meta.setTagDelimiter(tagDelimiter);
        meta.addString(Meta.FIELD_MEDIA_ROOT, musicFolder);
        meta.addString(Meta.FIELD_TITLE, title);
        // Optional attributes
        Iterator<String> keys = jChild.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            switch (key) {
                case JSON_ID:
                case JSON_IS_DIR:
                case JSON_TITLE:
                    // Already handled
                    break;
                case JSON_PARENT:
                    meta.addString(Meta.FIELD_PARENT_ID, jChild.getString(JSON_PARENT));
                    break;
                case JSON_ALBUM:
                    meta.addString(Meta.FIELD_ALBUM, jChild.getString(JSON_ALBUM));
                    break;
                case JSON_ARTIST:
                    meta.addString(Meta.FIELD_ARTIST, jChild.getString(JSON_ARTIST));
                    break;
                case JSON_TRACK:
                    meta.addLong(Meta.FIELD_TRACKNUMBER, jChild.getInt(JSON_TRACK));
                    break;
                case JSON_YEAR:
                    meta.addLong(Meta.FIELD_YEAR, jChild.getInt(JSON_YEAR));
                    break;
                case JSON_GENRE:
                    meta.addString(Meta.FIELD_GENRE, jChild.getString(JSON_GENRE));
                    break;
                case JSON_COVER_ART:
                    meta.addString(Meta.FIELD_ALBUM_ART_URI, jChild.getString(JSON_COVER_ART));
                    break;
                case JSON_SIZE:
                    meta.addLong(Meta.FIELD_FILE_SIZE, jChild.getLong(JSON_SIZE));
                    break;
                case JSON_CONTENT_TYPE:
                    meta.addString(Meta.FIELD_CONTENT_TYPE, jChild.getString(JSON_CONTENT_TYPE));
                    break;
                case JSON_SUFFIX:
                    meta.addString(Meta.FIELD_FILE_SUFFIX, jChild.getString(JSON_SUFFIX));
                    break;
                case JSON_TRANSCODED_CONTENT_TYPE:
                    meta.addString(Meta.FIELD_TRANSCODED_TYPE, jChild.getString(JSON_TRANSCODED_CONTENT_TYPE));
                    break;
                case JSON_TRANSCODED_SUFFIX:
                    meta.addString(Meta.FIELD_TRANSCODED_SUFFIX, jChild.getString(JSON_TRANSCODED_SUFFIX));
                    break;
                case JSON_DURATION:
                    meta.addLong(Meta.FIELD_DURATION, jChild.getInt(JSON_DURATION) * 1000L);
                    break;
                case JSON_BITRATE:
                    meta.addLong(Meta.FIELD_BITRATE, jChild.getInt(JSON_BITRATE));
                    break;
                case JSON_PATH:
                    meta.addString(Meta.FIELD_MEDIA_URI, jChild.getString(JSON_PATH));
                    break;
                case JSON_IS_VIDEO:
                    if (jChild.getBoolean(JSON_IS_VIDEO)) {
                        return Optional.empty();
                    }
                    break;
                case JSON_USER_RATING:
                    meta.addLong(Meta.FIELD_USER_RATING, jChild.getInt(JSON_USER_RATING));
                    break;
                case JSON_AVERAGE_RATING:
                    meta.addDouble(Meta.FIELD_AVERAGE_RATING, jChild.getDouble(JSON_AVERAGE_RATING));
                    break;
                case JSON_PLAY_COUNT:
                    // Do not care about subsonic play count
//                  jChild.getLong(JSON_PLAY_COUNT);
                    break;
                case JSON_DISC_NUMBER:
                    meta.addLong(Meta.FIELD_DISCNUMBER, jChild.getInt(JSON_DISC_NUMBER));
                    break;
                case JSON_CREATED:
                    meta.addString(Meta.FIELD_DATE_ADDED, jChild.getString(JSON_CREATED));
                    break;
                case JSON_STARRED:
                    meta.addString(Meta.FIELD_DATE_STARRED, jChild.getString(JSON_STARRED));
                    break;
                case JSON_ALBUM_ID:
                    meta.addString(Meta.FIELD_ALBUM_ID, jChild.getString(JSON_ALBUM_ID));
                    break;
                case JSON_ARTIST_ID:
                    meta.addString(Meta.FIELD_ARTIST_ID, jChild.getString(JSON_ARTIST_ID));
                    break;
                case JSON_TYPE:
                    if (!jChild.getString(JSON_TYPE).equals(JSON_TYPE_MUSIC)) {
                        return Optional.empty();
                    }
                    break;
                case JSON_BOOKMARK_POSITION:
                    meta.addLong(Meta.FIELD_BOOKMARK_POSITION, jChild.getLong(JSON_BOOKMARK_POSITION));
                    break;
                default:
                    Log.w(LC, "Unhandled JSON attribute in child (" + title + "): " + key);
                    break;
            }
        }
        return Optional.of(meta);
    }

    private CompletableFuture<Void>
    getMusicDirectory(final String folderId,
                      final String musicFolder,
                      final ConcurrentLinkedQueue<Meta> metaList,
                      final APIClientRequestHandler handler) {
        String query = baseURL + "getMusicDirectory" + getBaseQuery() + "&id=" + folderId;
        return getMusicDirectoryQuery(query, musicFolder, metaList, handler);
    }

    private CompletableFuture<Void>
    getMusicDirectoryQuery(final String query,
                           final String musicFolder,
                           final ConcurrentLinkedQueue<Meta> metaList,
                           final APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        final List<CompletableFuture<Void>> reqList = new ArrayList<>();
        httpRequestQueue.addToRequestQueue(new StringRequest(
                query,
                response -> {
                    String status = statusOK(response);
                    if (status.isEmpty()) {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONObject jResp = json.getJSONObject(JSON_RESP);
                            JSONObject jDir = jResp.getJSONObject(JSON_DIRECTORY);
                            if (jDir.has(JSON_CHILD)) {
                                JSONArray jChildArray = jDir.getJSONArray(JSON_CHILD);
                                for (int i = 0; i < jChildArray.length(); i++) {
                                    JSONObject jChild = jChildArray.getJSONObject(i);
                                    // Required attributes
                                    String id = jChild.getString(JSON_ID);
                                    boolean isDir = jChild.getBoolean(JSON_IS_DIR);
                                    if (isDir) {
                                        final CompletableFuture<Void> req = new CompletableFuture<>();
                                        reqList.add(req);
                                        getMusicDirectory(id, musicFolder, metaList, handler)
                                                .thenRun(() -> req.complete(null));
                                    } else {
                                        handleJSONChild(jChild, musicFolder).ifPresent(meta ->
                                                addMeta(meta, metaList, handler)
                                        );
                                    }
                                }
                            }
                            CompletableFuture<Void> allOf = CompletableFuture.allOf(reqList.toArray(new CompletableFuture[0]));
                            allOf.thenRun(() -> ret.complete(null));
                        } catch (JSONException e) {
                            Log.e(LC, "JSON error in getMusicDirectory: " + e.getMessage());
                            ret.complete(null);
                        }
                    } else {
                        Log.e(LC, "getMusicDirectory: " + status + "\n" + query);
                        ret.complete(null);
                    }
                },
                error -> {
                    Log.e(LC, "onFailure in getMusicDirectory");
                    onRequestFail(
                            RequestType.GET_MUSIC_DIRECTORY,
                            ret,
                            query,
                            musicFolder,
                            HTTPRequestQueue.getHTTPErrorMessage(error),
                            metaList,
                            null,
                            null,
                            handler);
                }
        ));
        return ret;
    }

    @Override
    public CompletableFuture<Optional<List<Meta>>> getLibrary(APIClientRequestHandler handler) {
        CompletableFuture<Optional<List<Meta>>> getLibraryFuture = new CompletableFuture<>();
        ConcurrentLinkedQueue<Meta> metadataList = new ConcurrentLinkedQueue<>();
        handler.onProgress("Fetching music folders...");
        getMusicFolders(metadataList, handler).thenRun(() ->
                getLibraryFuture.complete(Optional.of(new ArrayList<>(metadataList))));
        return getLibraryFuture;
    }

    @Override
    public boolean hasPlaylists() {
        return true;
    }

    private CompletableFuture<Void> getPlaylists(final ConcurrentLinkedQueue<Playlist> playlists,
                                                    final APIClientRequestHandler handler) {
        String query = baseURL + "getPlaylists" + getBaseQuery();
        return getPlaylistsQuery(query, playlists, handler);
    }

    private CompletableFuture<Void> getPlaylistsQuery(String query,
                                                      ConcurrentLinkedQueue<Playlist> playlists,
                                                      APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        final List<CompletableFuture<Void>> reqList = new ArrayList<>();
        httpRequestQueue.addToRequestQueue(new StringRequest(
                query,
                response -> {
                    String status = statusOK(response);
                    if (status.isEmpty()) {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONObject jResp = json.getJSONObject(JSON_RESP);
                            JSONObject jPlaylists = jResp.getJSONObject(JSON_PLAYLISTS);
                            if (jPlaylists.has(JSON_PLAYLIST)) {
                                JSONArray jPlaylistArray = jPlaylists.getJSONArray(JSON_PLAYLIST);
                                for (int i = 0; i < jPlaylistArray.length(); i++) {
                                    JSONObject jPlaylist = jPlaylistArray.getJSONObject(i);
                                    // Required attributes
                                    String id = jPlaylist.getString(JSON_ID);
                                    String name = jPlaylist.getString(JSON_NAME);
                                    final CompletableFuture<Void> req = new CompletableFuture<>();
                                    reqList.add(req);
                                    ConcurrentLinkedQueue<EntryID> entries = new ConcurrentLinkedQueue<>();
                                    getPlaylist(id, entries, handler)
                                            .thenRun(() -> {
                                                PlaylistID playlistID = new PlaylistID(
                                                        src,
                                                        id,
                                                        PlaylistID.TYPE_STUPID
                                                );
                                                // Subsonic API playlist entries have no ID:s
                                                // Create mock ID:s
                                                List<PlaylistEntry> playlistEntries =
                                                        PlaylistEntry.generatePlaylistEntries(
                                                                playlistID,
                                                                entries.toArray(new EntryID[0])
                                                        );
                                                Playlist playlist = new StupidPlaylist(
                                                        playlistID,
                                                        name,
                                                        playlistEntries
                                                );
                                                playlists.add(playlist);
                                                req.complete(null);
                                            });
                                }
                            }
                            CompletableFuture<Void> allOf = CompletableFuture.allOf(reqList.toArray(new CompletableFuture[0]));
                            allOf.thenRun(() -> ret.complete(null));
                        } catch (JSONException e) {
                            Log.e(LC, "JSON error in getPlaylistsQuery: " + e.getMessage());
                            ret.complete(null);
                        }
                    } else {
                        Log.e(LC, "getPlaylistsQuery: " + status + "\n" + query);
                        ret.complete(null);
                    }
                },
                error -> {
                    Log.e(LC, "onFailure in getPlaylistsQuery");
                    onRequestFail(
                            RequestType.GET_PLAYLISTS,
                            ret,
                            query,
                            null,
                            HTTPRequestQueue.getHTTPErrorMessage(error),
                            null,
                            playlists,
                            null,
                            handler);
                }
        ));
        return ret;
    }

    private CompletableFuture<Void> getPlaylist(String id,
                                                ConcurrentLinkedQueue<EntryID> entries,
                                                APIClientRequestHandler handler) {
        String query = baseURL + "getPlaylist" + getBaseQuery() + "&id=" + id;
        return getPlaylistQuery(query, entries, handler);
    }

    private CompletableFuture<Void> getPlaylistQuery(String query,
                                                     ConcurrentLinkedQueue<EntryID> entries,
                                                     APIClientRequestHandler handler) {
        CompletableFuture<Void> ret = new CompletableFuture<>();
        httpRequestQueue.addToRequestQueue(new StringRequest(
                query,
                response -> {
                    String status = statusOK(response);
                    if (status.isEmpty()) {
                        try {
                            JSONObject json = new JSONObject(response);
                            JSONObject jResp = json.getJSONObject(JSON_RESP);
                            JSONObject jPlaylists = jResp.getJSONObject(JSON_PLAYLIST);
                            if (jPlaylists.has(JSON_ENTRY)) {
                                JSONArray jEntryArray = jPlaylists.getJSONArray(JSON_ENTRY);
                                for (int i = 0; i < jEntryArray.length(); i++) {
                                    JSONObject jPlaylist = jEntryArray.getJSONObject(i);
                                    // Required attributes
                                    String id = jPlaylist.getString(JSON_ID);
                                    entries.add(new EntryID(src, id, Meta.FIELD_SPECIAL_MEDIA_ID));
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(LC, "JSON error in getPlaylistsQuery: " + e.getMessage());
                        }
                    } else {
                        Log.e(LC, "getPlaylistQuery: " + status + "\n" + query);
                    }
                    ret.complete(null);
                },
                error -> {
                    Log.e(LC, "onFailure in getPlaylistQuery");
                    onRequestFail(
                            RequestType.GET_PLAYLIST,
                            ret,
                            query,
                            null,
                            HTTPRequestQueue.getHTTPErrorMessage(error),
                            null,
                            null,
                            entries,
                            handler);
                }
        ));
        return ret;
    }

    @Override
    public CompletableFuture<Optional<List<Playlist>>> getPlaylists(APIClientRequestHandler handler) {
        CompletableFuture<Optional<List<Playlist>>> getPlaylistsFuture = new CompletableFuture<>();
        ConcurrentLinkedQueue<Playlist> playlists = new ConcurrentLinkedQueue<>();
        getPlaylists(playlists, handler).thenRun(() ->
                getPlaylistsFuture.complete(Optional.of(new ArrayList<>(playlists)))
        );
        return getPlaylistsFuture;
    }

    public AudioDataSource getAudioData(EntryID entryID) {
        // TODO(kringlan): Add subsonic setting for max bitrate and FORMAT.
        String query = baseURL + "stream" + getBaseQuery() + "&id=" + entryID.id + "&format=raw";
        return new AudioDataSource(context, query, entryID);
    }

    // TODO: Support configuration with multiple Subsonic backend instances (i.e. use apiInstanceID)
    @Override
    public void loadSettings(Context context, Path workDir, Bundle settings) {
        baseURL = settings.getString(APIClient.SETTINGS_KEY_SUBSONIC_URL) + API_BASE_PATH;
        username = settings.getString(APIClient.SETTINGS_KEY_SUBSONIC_USERNAME);
        password = settings.getString(APIClient.SETTINGS_KEY_SUBSONIC_PASSWORD);
        tagDelimiter = settings.getString(APIClient.SETTINGS_KEY_GENERAL_TAG_DELIM);
    }

    private String statusOK(String resp) {
        String status;
        try {
            JSONObject json = new JSONObject(resp);
            JSONObject jResp = json.getJSONObject(JSON_RESP);
            String respStatus = jResp.getString(JSON_STATUS);
            if (respStatus.equals(STATUS_OK)) {
                String respVersion = jResp.getString(JSON_VERSION);
                status = "Unsupported Subsonic API VERSION: " + respVersion;
                for (String supported_version: SUPPORTED_VERSIONS) {
                    if (respVersion.equals(supported_version)) {
                        status = "";
                        break;
                    }
                }
            } else {
                JSONObject jError = jResp.getJSONObject(JSON_ERROR);
                status = respStatus + ": (" + jError.getString(JSON_CODE) + ")"
                        + jError.getString(JSON_MESSAGE);
            }
        } catch (JSONException e) {
            Log.w(LC, "JSON error: " + e.toString());
            status = "JSON parsing error";
        }
        return status;
    }

    private String getBaseQuery() {
        String salt = Util.getSalt(rand, 32);
        String token = Util.md5(password + salt);
        return String.format(".view?u=%s&t=%s&s=%s&v=%s&c=%s&f=%s", username, token, salt, VERSION,
                CLIENT_ID, FORMAT);
    }
}
