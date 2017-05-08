package se.splushii.dancingbunnies.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;

import cz.msebera.android.httpclient.Header;
import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.events.AlbumRequestFailEvent;
import se.splushii.dancingbunnies.events.ArtistRequestFailEvent;
import se.splushii.dancingbunnies.events.SongRequestFailEvent;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.musiclibrary.Song;
import se.splushii.dancingbunnies.util.Util;

public class SubsonicAPIClient implements APIClient {
    private Context context;

    private static final String API_SRC = "subsonic";
    private static final String API_BASE_URL = "/rest/";

    private static final String LOG_CONTEXT = "SubsonicAPIClient";

    private static final String JSON_RESP = "subsonic-response";
    private static final String JSON_STATUS = "status";
    private static final String JSON_VERSION = "version";
    private static final String JSON_ARTISTS = "artists";
    private static final String JSON_INDEX = "index";
    private static final String JSON_ARTIST = "artist";
    private static final String JSON_ALBUM = "album";
    private static final String JSON_ID = "id";
    private static final String JSON_NAME = "name";
    private static final String JSON_ERROR = "error";
    private static final String JSON_MESSAGE = "message";
    private static final String JSON_ALBUM_COUNT = "albumCount";
    private static final String JSON_SONG_COUNT = "songCount";
    private static final String JSON_SONG = "song";
    private static final String JSON_TITLE = "title";

    private static final String status_ok = "ok";

    private String username = "";
    private String password = "";
    private String baseURL = "";
    private static final String version = "1.14.0";
    private static final String clientId = "dancingbunnies";
    private static final String format = "json";

    private SecureRandom rand;
    private HTTPClient httpClient;

    private HashMap<String, Integer> retries;

    public SubsonicAPIClient(Context context) {
        this.context = context;
        try {
            rand = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            Log.d(LOG_CONTEXT, "SHA1PRNG not supported");
            e.printStackTrace();
            // TODO
        }
        this.httpClient = new HTTPClient();
        httpClient.setRetries(AsyncHttpClient.DEFAULT_MAX_RETRIES * 10,
                AsyncHttpClient.DEFAULT_RETRY_SLEEP_TIME_MILLIS);
        retries = new HashMap<>();
    }

    public void ping(AsyncHttpResponseHandler handler) {
        httpClient.get(baseURL + "ping.view" + getBaseQuery(), null, handler);
    }

    public CompletableFuture<Optional<ArrayList<Song>>> getSongs(Album album) {
        return getAlbum(album);
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
            System.out.println("Subsonic backend enabled.\nbaseURL: " + url + "\nusr: " + usr
                    + "\npwd: " + pwd + "\n");
            baseURL = url + API_BASE_URL;
            username = usr;
            password = pwd;
        }
    }

    private CompletableFuture<Optional<ArrayList<Song>>> getAlbum(final Album album) {
        final String query = baseURL + "getAlbum.view" + getBaseQuery() + "&id=" + album.id();
        final CompletableFuture<Optional<ArrayList<Song>>> req = new CompletableFuture<>();
        httpClient.get(query, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String resp = new String(responseBody);
                String status = statusOK(resp);
                if (status.isEmpty()) {
                    req.complete(Optional.of(parseGetAlbum(resp, album)));
                } else {
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show();
                    req.complete(Optional.<ArrayList<Song>>empty());
                }
            }
            @Override
            public void onFailure(int statusCode,
                                  Header[] headers,
                                  byte[] responseBody,
                                  Throwable error) {
                String status = error.getMessage();
                EventBus.getDefault().post(new SongRequestFailEvent(MusicLibrary.API_ID_SUBSONIC,
                        query, album, req, status));
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
            }
        });
        return req;
    }

    public CompletableFuture<Optional<ArrayList<Album>>> getAlbums(Artist artist) {
        return getArtist(artist);
    }

    private CompletableFuture<Optional<ArrayList<Album>>> getArtist(final Artist artist) {
        final String query = baseURL + "getArtist.view" + getBaseQuery() + "&id=" + artist.id();
        final CompletableFuture<Optional<ArrayList<Album>>> req = new CompletableFuture<>();
        httpClient.get(query, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String resp = new String(responseBody);
                String status = statusOK(resp);
                if (status.isEmpty()) {
                    req.complete(Optional.of(parseGetArtist(resp, artist)));
                } else {
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show();
                    req.complete(Optional.<ArrayList<Album>>empty());
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody,
                                  Throwable error) {
                String status = error.getMessage();
                EventBus.getDefault().post(new AlbumRequestFailEvent(MusicLibrary.API_ID_SUBSONIC,
                        query, artist, req, status));
            }
            @Override
            public void onProgress(long bytesWritten, long totalSize) {
            }
        });
        return req;
    }

    public CompletableFuture<Optional<ArrayList<Artist>>> getArtists() {
        final String query = baseURL + "getArtists.view" + getBaseQuery();
        final CompletableFuture<Optional<ArrayList<Artist>>> req = new CompletableFuture<>();
        httpClient.get(query, null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                String resp = new String(responseBody);
                String status = statusOK(resp);
                if (status.isEmpty()) {
                    req.complete(Optional.of(parseGetArtists(resp)));
                } else {
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show();
                    req.complete(Optional.<ArrayList<Artist>>empty());
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody,
                                  Throwable error) {
                String status = error.getMessage();
                EventBus.getDefault().post(new ArtistRequestFailEvent(MusicLibrary.API_ID_SUBSONIC,
                        query, req, status));
            }
            @Override
            public void onProgress(long bytesWritten, long totalSize) {
            }
        });
        return req;
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
                    status = "request/response API version mismatch";
                }
            } else {
                JSONObject jError = jResp.getJSONObject(JSON_ERROR);
                status = respStatus + ": " + jError.getString(JSON_MESSAGE);
            }
        } catch (JSONException e) {
            Log.d(LOG_CONTEXT, "JSON error: " + e.toString());
            status = "JSON parsing error";
        }
        return status;
    }

    private String getBaseQuery() {
        String salt = Util.getSalt(rand, 32);
        String token = Util.md5(password + salt);
        return String.format("?u=%s&t=%s&s=%s&v=%s&c=%s&f=%s", username, token, salt, version,
                clientId, format);
    }

    private ArrayList<Artist> parseGetArtists(String resp) {
        ArrayList<Artist> artists = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(resp);
            JSONObject jResp = json.getJSONObject(JSON_RESP);
            JSONObject jArtists = jResp.getJSONObject(JSON_ARTISTS);
            JSONArray jIndices = jArtists.getJSONArray(JSON_INDEX);
            for (int i = 0; i < jIndices.length(); i++) {
                JSONObject jIndex = jIndices.getJSONObject(i);
                JSONArray jArtistList = jIndex.getJSONArray(JSON_ARTIST);
                for (int j = 0; j < jArtistList.length(); j++) {
                    JSONObject jArtist = jArtistList.getJSONObject(j);
                    String id = jArtist.getString(JSON_ID);
                    String name = jArtist.getString(JSON_NAME);
                    int albumCount = jArtist.getInt(JSON_ALBUM_COUNT);
                    artists.add(new Artist(API_SRC, id, name));
                }
            }
        } catch (JSONException e) {
            Log.d(LOG_CONTEXT, "JSON error: " + e.toString());
        }
        return artists;
    }

    private ArrayList<Album> parseGetArtist(String resp, Artist artist) {
        ArrayList<Album> albums = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(resp);
            JSONObject jResp = json.getJSONObject(JSON_RESP);
            JSONObject jArtist = jResp.getJSONObject(JSON_ARTIST);
            int jAlbumCount = jArtist.getInt(JSON_ALBUM_COUNT);
            if (jAlbumCount == 0) {
                return albums;
            }
            JSONArray jAlbums = jArtist.getJSONArray(JSON_ALBUM);
            for (int i = 0; i < jAlbums.length(); i++) {
                JSONObject jAlbum = jAlbums.getJSONObject(i);
                String id = jAlbum.getString(JSON_ID);
                String name = jAlbum.getString(JSON_NAME);
                int songCount = jAlbum.getInt(JSON_SONG_COUNT);
                albums.add(new Album(API_SRC, id, name, artist));
            }
        } catch (JSONException e) {
            Log.d(LOG_CONTEXT, "JSON error: " + e.toString());
            System.out.println(resp);
        }
        return albums;
    }

    private ArrayList<Song> parseGetAlbum(String resp, Album album) {
        ArrayList<Song> songs = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(resp);
            JSONObject jResp = json.getJSONObject(JSON_RESP);
            JSONObject jAlbum = jResp.getJSONObject(JSON_ALBUM);
            int songCount = jAlbum.getInt(JSON_SONG_COUNT);
            if (songCount == 0) {
                return songs;
            }
            JSONArray jSongs = jAlbum.getJSONArray(JSON_SONG);
            for (int i = 0; i < jSongs.length(); i++) {
                JSONObject jSong = jSongs.getJSONObject(i);
                String id = jSong.getString(JSON_ID);
                String name = jSong.getString(JSON_TITLE);
                songs.add(new Song(API_SRC, id, name, album));
            }
        } catch (JSONException e) {
            Log.d(LOG_CONTEXT, "JSON error: " + e.toString());
            System.out.println(resp);
        }
        return songs;
    }
}
