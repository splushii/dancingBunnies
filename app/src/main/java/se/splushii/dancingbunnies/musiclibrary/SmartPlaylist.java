package se.splushii.dancingbunnies.musiclibrary;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import se.splushii.dancingbunnies.util.Util;

public class SmartPlaylist extends Playlist {
    private static final String LC = Util.getLogContext(SmartPlaylist.class);
    private final Bundle query;

    public SmartPlaylist(PlaylistID playlistID, String name, Bundle query) {
        super(playlistID, name);
        this.query = query;
    }

    public static Bundle jsonQueryToBundle(String query) {
        Bundle b = new Bundle();
        try {
            JSONObject json = new JSONObject(query);
            json.keys().forEachRemaining(s -> {
                try {
                    b.putString(s, json.getString(s));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return b;
    }

    public String getJSONQuery() {
        JSONObject jsonRoot = new JSONObject();
        for (String key: query.keySet()) {
            try {
                jsonRoot.put(key, query.getString(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonRoot.toString();
    }
}
