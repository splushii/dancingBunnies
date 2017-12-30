package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.util.Pair;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;

import org.greenrobot.eventbus.Subscribe;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import cz.msebera.android.httpclient.Header;
import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.events.SubsonicRequestFailEvent;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.util.Util;

public class SubsonicAPIClient extends APIClient {
    private static String LC = Util.getLogContext(SubsonicAPIClient.class);
    private static final Integer REQ_RETRY_COUNT = 3;
    private HashMap<String, Integer> retries;

    public enum RequestType {
        GET_INDEXES, GET_MUSIC_DIRECTORY, GET_MUSIC_FOLDERS
    }

    private static final String API_BASE_URL = "/rest/";

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

    private static final String status_ok = "ok";

    private String username = "";
    private String password = "";
    private String baseURL = "";
    private static final String version = "1.15.0";
    private static final String clientId = "dancingbunnies";
    private static final String format = "json";

    private SecureRandom rand;
    private HTTPClient httpClient;

    public SubsonicAPIClient() {
        retries = new HashMap<>();
        try {
            rand = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            Log.d(LC, "SHA1PRNG not supported");
            e.printStackTrace();
            // TODO
        }
        this.httpClient = new HTTPClient();
        httpClient.setRetries(AsyncHttpClient.DEFAULT_MAX_RETRIES * 10,
                AsyncHttpClient.DEFAULT_RETRY_SLEEP_TIME_MILLIS);
        EventBus.getDefault().register(this);
    }

    public void destroy() {
        EventBus.getDefault().unregister(this);
        // TODO: some more cleanup?
    }

    @Subscribe
    public void onMessageEvent(final SubsonicRequestFailEvent e) {
        Integer retryCount;
        if ((retryCount = retries.get(e.query)) == null) {
            retryCount = 1;
        }
        final CompletableFuture<Void> pastReq = e.req;
        String status = "SubsonicRequestFailEvent:\ntype: " + e.type + "\nquery: " + e.query;
        if (retryCount > REQ_RETRY_COUNT) {
            Log.d(LC,  status + " No more retries.");
            retries.remove(e.query);
            pastReq.complete(null);
            return;
        }
        retries.put(e.query, retryCount + 1);
        Log.d(LC, status + "\nRetry " + retryCount + "/" + REQ_RETRY_COUNT);
        CompletableFuture<Void> req;
        switch(e.type) {
            case GET_INDEXES:
                req = getIndexesQuery(e.query, e.musicFolder, e.metaList, e.handler);
                break;
            case GET_MUSIC_FOLDERS:
                req = getMusicFoldersQuery(e.query, e.metaList, e.handler);
                break;
            case GET_MUSIC_DIRECTORY:
                req = getMusicDirectoryQuery(e.query, e.musicFolder, e.metaList, e.handler);
                break;
            default:
                req = new CompletableFuture<>();
                req.complete(null);
                break;
        }
        req.thenRun(() -> pastReq.complete(null));
    }

    public void ping(AsyncHttpResponseHandler handler) {
        httpClient.get(baseURL + "ping" + getBaseQuery(), null, handler);
        // TODO: Use this to heartbeat the subsonic server
    }

    @Override
    public boolean hasLibrary() {
        return true;
    }

    private CompletableFuture<Void> getMusicFolders(final ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                                                     final APIClientRequestHandler handler) {
        String query = baseURL + "getMusicFolders" + getBaseQuery();
        return getMusicFoldersQuery(query, metaList, handler);
    }

    private CompletableFuture<Void> getMusicFoldersQuery(final String query,
                                                         final ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                                                         final APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        final CompletableFuture<Optional<List<Pair<String, String>>>> req = new CompletableFuture<>();
        httpClient.get(query, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String resp = new String(responseBody);
                String status = statusOK(resp);
                if (status.isEmpty()) {
                    List<Pair<String, String>> folders = new ArrayList<>();
                    try {
                        JSONObject json = new JSONObject(resp);
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
                    req.complete(Optional.<List<Pair<String,String>>>empty());
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                HashMap<String, String> values = new HashMap<>();
                EventBus.getDefault().post(new SubsonicRequestFailEvent(
                        RequestType.GET_MUSIC_FOLDERS, ret, query, "", error.getMessage(),
                        metaList, handler));
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
            }
        });
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
            // (and collected by MusicLibrary or higher)?
            CompletableFuture<Void> allOf = CompletableFuture
                    .allOf(indexReqList.toArray(new CompletableFuture[indexReqList.size()]));
            allOf.thenRun(() -> ret.complete(null));
        });
        return ret;
    }

    private CompletableFuture<Void> getIndexes(final String musicFolderId,
                                               final String musicFolder,
                                               final ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                                               final APIClientRequestHandler handler) {

        final String query = baseURL + "getIndexes" + getBaseQuery()
                + "&musicFolderId=" + musicFolderId;
        return getIndexesQuery(query, musicFolder, metaList, handler);
    }

    private CompletableFuture<Void> getIndexesQuery(final String query,
                                                    final String musicFolder,
                                                    final ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                                                    final APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        httpClient.get(query, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String resp = new String(responseBody);
                String status = statusOK(resp);
                if (status.isEmpty()) {
                    try {
                        JSONObject json = new JSONObject(resp);
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
                                    Optional<MediaMetadataCompat> meta =
                                            handleJSONChild(jChild, musicFolder);
                                    addMeta(meta, metaList, handler);
                                }
                            }
                        }
                        CompletableFuture<Void> allOf = CompletableFuture.allOf(reqList.toArray(new CompletableFuture[reqList.size()]));
                        allOf.thenRun(() -> ret.complete(null));
                    } catch (JSONException e) {
                        Log.e(LC, "JSON error in getIndexes: " + e.getMessage());
                        ret.complete(null);
                    }
                } else {
                    Log.e(LC, "getIndexes: " + status);
                    ret.complete(null);
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody,
                                  Throwable error) {
                Log.e(LC, "onFailure in getIndexes");
                EventBus.getDefault().post(new SubsonicRequestFailEvent(RequestType.GET_INDEXES, ret, query, musicFolder, error.getMessage(), metaList, handler));
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {

            }
        });
        return ret;
    }

    private void addMeta(Optional<MediaMetadataCompat> meta,
                         ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                         APIClientRequestHandler handler) {
        if (meta.isPresent()) {
            metaList.add(meta.get());
            handler.onProgress("Fetched " + metaList.size() + " entries...");
        }
    }

    private Optional<MediaMetadataCompat> handleJSONChild(JSONObject jChild, String musicFolder)
            throws JSONException {
        // Required attributes
        String id = jChild.getString(JSON_ID);
        if (jChild.getBoolean(JSON_IS_DIR)) {
            return Optional.empty();
        }
        String title = jChild.getString(JSON_TITLE);
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder
                .putString(Meta.METADATA_KEY_API, MusicLibrary.API_ID_SUBSONIC)
                .putString(Meta.METADATA_KEY_MEDIA_ROOT, musicFolder)
                .putString(Meta.METADATA_KEY_MEDIA_ID, id)
                .putString(Meta.METADATA_KEY_TITLE, title);
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
                    builder.putString(Meta.METADATA_KEY_PARENT_ID, jChild.getString(JSON_PARENT));
                    break;
                case JSON_ALBUM:
                    builder.putString(Meta.METADATA_KEY_ALBUM, jChild.getString(JSON_ALBUM));
                    break;
                case JSON_ARTIST:
                    builder.putString(Meta.METADATA_KEY_ARTIST, jChild.getString(JSON_ARTIST));
                    break;
                case JSON_TRACK:
                    builder.putLong(Meta.METADATA_KEY_TRACK_NUMBER, jChild.getInt(JSON_TRACK));
                    break;
                case JSON_YEAR:
                    builder.putLong(Meta.METADATA_KEY_YEAR, jChild.getInt(JSON_YEAR));
                    break;
                case JSON_GENRE:
                    builder.putString(Meta.METADATA_KEY_GENRE, jChild.getString(JSON_GENRE));
                    break;
                case JSON_COVER_ART:
                    builder.putString(Meta.METADATA_KEY_ALBUM_ART_URI, jChild.getString(JSON_COVER_ART));
                    break;
                case JSON_SIZE:
                    builder.putLong(Meta.METADATA_KEY_FILE_SIZE, jChild.getLong(JSON_SIZE));
                    break;
                case JSON_CONTENT_TYPE:
                    builder.putString(Meta.METADATA_KEY_CONTENT_TYPE, jChild.getString(JSON_CONTENT_TYPE));
                    break;
                case JSON_SUFFIX:
                    builder.putString(Meta.METADATA_KEY_FILE_SUFFIX, jChild.getString(JSON_SUFFIX));
                    break;
                case JSON_TRANSCODED_CONTENT_TYPE:
                    builder.putString(Meta.METADATA_KEY_TRANSCODED_TYPE, jChild.getString(JSON_TRANSCODED_CONTENT_TYPE));
                    break;
                case JSON_TRANSCODED_SUFFIX:
                    builder.putString(Meta.METADATA_KEY_TRANSCODED_SUFFIX, jChild.getString(JSON_TRANSCODED_SUFFIX));
                    break;
                case JSON_DURATION:
                    builder.putLong(Meta.METADATA_KEY_DURATION, jChild.getInt(JSON_DURATION));
                    break;
                case JSON_BITRATE:
                    builder.putLong(Meta.METADATA_KEY_BITRATE, jChild.getInt(JSON_BITRATE));
                    break;
                case JSON_PATH:
                    builder.putString(Meta.METADATA_KEY_MEDIA_URI, jChild.getString(JSON_PATH));
                    break;
                case JSON_IS_VIDEO:
                    if (jChild.getBoolean(JSON_IS_VIDEO)) {
                        return Optional.empty();
                    }
                    break;
                case JSON_USER_RATING:
                    builder.putRating(Meta.METADATA_KEY_USER_RATING, RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, jChild.getInt(JSON_USER_RATING)));
                    break;
                case JSON_AVERAGE_RATING:
                    builder.putRating(Meta.METADATA_KEY_AVERAGE_RATING, RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, (float) jChild.getDouble(JSON_AVERAGE_RATING)));
                    break;
                case JSON_PLAY_COUNT:
                    // Do not care about subsonic play count
//                  jChild.getLong(JSON_PLAY_COUNT);
                    break;
                case JSON_DISC_NUMBER:
                    builder.putLong(Meta.METADATA_KEY_DISC_NUMBER, jChild.getInt(JSON_DISC_NUMBER));
                    break;
                case JSON_CREATED:
                    builder.putString(Meta.METADATA_KEY_DATE_ADDED, jChild.getString(JSON_CREATED));
                    break;
                case JSON_STARRED:
                    builder.putString(Meta.METADATA_KEY_DATE_STARRED, jChild.getString(JSON_STARRED));
                    builder.putRating(Meta.METADATA_KEY_HEART_RATING, RatingCompat.newHeartRating(true));
                    break;
                case JSON_ALBUM_ID:
                    builder.putString(Meta.METADATA_KEY_ALBUM_ID, jChild.getString(JSON_ALBUM_ID));
                    break;
                case JSON_ARTIST_ID:
                    builder.putString(Meta.METADATA_KEY_ARTIST_ID, jChild.getString(JSON_ARTIST_ID));
                    break;
                case JSON_TYPE:
                    if (!jChild.getString(JSON_TYPE).equals(JSON_TYPE_MUSIC)) {
                        return Optional.empty();
                    }
                    break;
                case JSON_BOOKMARK_POSITION:
                    builder.putLong(Meta.METADATA_KEY_BOOKMARK_POSITION, jChild.getLong(JSON_BOOKMARK_POSITION));
                    break;
                default:
                    Log.w(LC, "Unhandled JSON attribute in child (" + title + "): " + key);
                    break;
            }
        }
        return Optional.of(builder.build());
    }

    private CompletableFuture<Void>
    getMusicDirectory(final String folderId,
                      final String musicFolder,
                      final ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                      final APIClientRequestHandler handler) {
        String query = baseURL + "getMusicDirectory" + getBaseQuery() + "&id=" + folderId;
        return getMusicDirectoryQuery(query, musicFolder, metaList, handler);
    }

    private CompletableFuture<Void>
    getMusicDirectoryQuery(final String query,
                           final String musicFolder,
                           final ConcurrentLinkedQueue<MediaMetadataCompat> metaList,
                           final APIClientRequestHandler handler) {
        final CompletableFuture<Void> ret = new CompletableFuture<>();
        final List<CompletableFuture<Void>> reqList = new ArrayList<>();
        httpClient.get(query, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String resp = new String(responseBody);
                String status = statusOK(resp);
                if (status.isEmpty()) {
                    try {
                        JSONObject json = new JSONObject(resp);
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
                                    Optional<MediaMetadataCompat> meta =
                                            handleJSONChild(jChild, musicFolder);
                                    addMeta(meta, metaList, handler);
                                }
                            }
                        }
                        CompletableFuture<Void> allOf = CompletableFuture.allOf(reqList.toArray(new CompletableFuture[reqList.size()]));
                        allOf.thenRun(() -> ret.complete(null));
                    } catch (JSONException e) {
                        Log.e(LC, "JSON error in getMusicDirectory: " + e.getMessage());
                        ret.complete(null);
                    }
                } else {
                    Log.e(LC, "getMusicDirectory: " + status + "\n" + query);
                    ret.complete(null);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                Log.e(LC, "onFailure in getMusicDirectory");
                EventBus.getDefault().post(new SubsonicRequestFailEvent(RequestType.GET_MUSIC_DIRECTORY, ret, query, musicFolder,
                        error.getMessage(), metaList, handler));
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
            }
        });
        return ret;
    }

    @Override
    public CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> getLibrary(final APIClientRequestHandler handler) {
        final CompletableFuture<Optional<ArrayList<MediaMetadataCompat>>> ret = new CompletableFuture<>();
        final ConcurrentLinkedQueue<MediaMetadataCompat> metaList = new ConcurrentLinkedQueue<>();
        handler.onProgress("Fetching music folders...");
        getMusicFolders(metaList, handler).thenRun(() ->
                ret.complete(Optional.of(new ArrayList<>(metaList))));
        return ret;
    }

    @Override
    public boolean hasPlaylists() {
        // TODO: override getPlaylists
        return false;
    }

    public AudioDataSource getAudioData(String id) {
        String query = baseURL + "stream" + getBaseQuery() + "&id=" + id +
                "&estimateContentLength=true";
        URL url;
        try {
            url = new URL(query);
        } catch (MalformedURLException e) {
            Log.d(LC, "Malformed URL for song with id " + id + ": " + e.getMessage());
            return new AudioDataSource(null);
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            return new AudioDataSource(conn);
        } catch (IOException e) {
            e.printStackTrace();
            return new AudioDataSource(null);
        }
        // TODO: add subsonic setting for max bitrate and format
    }

    @Override
    public void loadSettings(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useSubsonic = settings.getBoolean("pref_subsonic", false);
        if (useSubsonic) {
            String url = settings.getString(context.getResources()
                    .getString(R.string.pref_key_subsonic_url), "");
            String usr = settings.getString(context.getResources()
                    .getString(R.string.pref_key_subsonic_usr), "");
            String pwd = settings.getString(context.getResources()
                    .getString(R.string.pref_key_subsonic_pwd), "");
            String pwdPublic = pwd.isEmpty() ? "" : "********";
            Log.d(LC, "Subsonic backend enabled.\nbaseURL: " + url + "\nusr: " + usr
                    + "\npwd: " + pwdPublic + "\n");
            baseURL = url + API_BASE_URL;
            username = usr;
            password = pwd;
        }
    }

    private String statusOK(String resp) {
        String status;
        try {
            JSONObject json = new JSONObject(resp);
            JSONObject jResp = json.getJSONObject(JSON_RESP);
            String respStatus = jResp.getString(JSON_STATUS);
            String respVersion = jResp.getString(JSON_VERSION);
            if (respStatus.equals(status_ok)) {
                if (respVersion.equals(version)) {
                    status = "";
                } else {
                    status = "request/response API version mismatch ("
                            + version + "/" + respVersion + ")";
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
        return String.format(".view?u=%s&t=%s&s=%s&v=%s&c=%s&f=%s", username, token, salt, version,
                clientId, format);
    }
}
