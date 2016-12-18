package se.splushii.dancingbunnies.backend;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;

import cz.msebera.android.httpclient.Header;
import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.BooleanSupplier;
import java8.util.function.Consumer;
import java8.util.function.Supplier;
import se.splushii.dancingbunnies.musiclibrary.Album;
import se.splushii.dancingbunnies.musiclibrary.Artist;
import se.splushii.dancingbunnies.util.Util;

public class SubsonicAPIClient implements APIClient {
    private static final String API_BASE_URL = "https://subsonic.splushii.se/rest/";

    static final String LOG_CONTEXT = "SubsonicAPIClient";

    static final String JSON_RESP = "subsonic-response";
    static final String JSON_STATUS = "status";
    static final String JSON_VERSION = "version";
    static final String JSON_ARTISTS = "artists";
    static final String JSON_INDEX = "index";
    static final String JSON_ARTIST = "artist";
    static final String JSON_ALBUM = "album";
    static final String JSON_ID = "id";
    static final String JSON_NAME = "name";
    static final String JSON_ERROR = "error";
    static final String JSON_MESSAGE = "message";
    static final String JSON_ALBUM_COUNT = "albumCount";
    static final String JSON_SONG_COUNT = "songCount";

    static final String status_ok = "ok";

    private String username;
    private String password;
    private static final String version = "1.14.0";
    private static final String clientId = "dancingbunnies";
    private static final String format = "json";

    private SecureRandom rand;

    public SubsonicAPIClient() {
        try {
            rand = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            Log.d(LOG_CONTEXT, "SHA1PRNG not supported");
            e.printStackTrace();
            // TODO
        }
    }

    public void setCredentials(String usr, String pwd) {
        username = usr;
        password = pwd;
    }

    public void ping(AsyncHttpResponseHandler handler) {
        HTTPClient.get(API_BASE_URL + "ping.view" + getBaseQuery(), null, handler);
    }


    public void getAlbum(AsyncHttpResponseHandler handler) {
        String query = API_BASE_URL + "getAlbum.view" + getBaseQuery();
        HTTPClient.get(query, null, handler);
    }

    public CompletableFuture<Optional<ArrayList<Album>>> getArtist(String artistId) {
        final String query = API_BASE_URL + "getArtist.view" + getBaseQuery() + "&id=" + artistId;
        final CompletableFuture<Optional<ArrayList<Album>>> req = new CompletableFuture<>();
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {

                    System.out.println("HTTP kvar: " + HTTPClient.lock.availablePermits());

                    HTTPClient.get(query, null, new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                            HTTPClient.lock.release();
                            System.out.println("HTTP kvar: " + HTTPClient.lock.availablePermits());
                            String resp = new String(responseBody);
                            String status = statusOK(resp);
                            if (status.isEmpty()) {
                                req.complete(Optional.of(parseGetArtist(resp)));
                            } else {
                                Toast.make
                                req.complete(Optional.<ArrayList<Album>>empty());
                            }
                        }
                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                            HTTPClient.lock.release();
                            System.out.println("HTTP kvar: " + HTTPClient.lock.availablePermits());
                            req.complete(Optional.<ArrayList<Album>>empty());
                        }

                        @Override
                        public void onProgress(long bytesWritten, long totalSize) {
                        }
                    });

            }
        });

        return req;
    }

    public CompletableFuture<Optional<ArrayList<Artist>>> getArtists(String musicFolderId) {
        String query = API_BASE_URL + "getArtists.view" + getBaseQuery();
        final CompletableFuture<Optional<ArrayList<Artist>>> req = new CompletableFuture<>();
        if (musicFolderId != null && !musicFolderId.isEmpty()) {
            query += "&musicFolderId=" + musicFolderId;
        }
        CompletableFuture lock = CompletableFuture.supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return true;
            }
        });
        final String finalQuery = query;
        lock.thenAccept(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean hasLock) {
                if (hasLock) {
                    HTTPClient.get(finalQuery, null, new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                            String resp = new String(responseBody);
                            String status = statusOK(resp);
                            if (status.isEmpty()) {
                                req.complete(Optional.of(parseGetArtists(resp)));
                            } else {
                                req.complete(Optional.<ArrayList<Artist>>empty());
                            }
                            HTTPClient.lock.release();
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                            req.complete(Optional.<ArrayList<Artist>>empty());
                            HTTPClient.lock.release();
                        }

                        @Override
                        public void onProgress(long bytesWritten, long totalSize) {
                        }
                    });
                } else {
                    req.complete(Optional.<ArrayList<Artist>>empty());
                }
            }
        });
        return req;
    }

    public String statusOK(String resp) {
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

    public String getBaseQuery() {
        String salt = Util.getSalt(rand, 32);
        String token = Util.md5(password + salt);
        return String.format("?u=%s&t=%s&s=%s&v=%s&c=%s&f=%s", username, token, salt, version, clientId, format);
    }

    public ArrayList<Artist> parseGetArtists(String resp) {
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
                    artists.add(new Artist(id, name, albumCount));
                }
            }
        } catch (JSONException e) {
            Log.d(LOG_CONTEXT, "JSON error: " + e.toString());
        }
        return artists;
    }

    public ArrayList<Album> parseGetArtist(String resp) {
        ArrayList<Album> albums = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(resp);
            JSONObject jResp = json.getJSONObject(JSON_RESP);
            JSONObject jArtist = jResp.getJSONObject(JSON_ARTIST);
            JSONArray jAlbums = jArtist.getJSONArray(JSON_ALBUM);
            for (int i = 0; i < jAlbums.length(); i++) {
                JSONObject jAlbum = jAlbums.getJSONObject(i);
                String id = jAlbum.getString(JSON_ID);
                String name = jAlbum.getString(JSON_NAME);
                int songCount = jAlbum.getInt(JSON_SONG_COUNT);
                albums.add(new Album(id, name, songCount));
            }
        } catch (JSONException e) {
            Log.d(LOG_CONTEXT, "JSON error: " + e.toString());
        }
        return albums;
    }
}
