package se.splushii.dancingbunnies.backend;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class HTTPRequestQueue {
    private static HTTPRequestQueue instance;
    private RequestQueue requestQueue;

    private HTTPRequestQueue(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public static synchronized HTTPRequestQueue getInstance(Context context) {
        if (instance == null) {
            instance = new HTTPRequestQueue(context);
        }
        return instance;
    }

    <T> void addToRequestQueue(Request<T> req) {
        requestQueue.add(req);
    }
}
