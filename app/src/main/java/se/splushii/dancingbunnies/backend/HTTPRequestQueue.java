package se.splushii.dancingbunnies.backend;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
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


    public static String getHTTPErrorMessage(VolleyError error) {
        String errMsg;
        if (error instanceof com.android.volley.NoConnectionError) {
            errMsg = "No connection error";
        } else if (error instanceof com.android.volley.NetworkError) {
            errMsg = "Network error";
        } else if (error instanceof com.android.volley.AuthFailureError) {
            errMsg = "Auth failure error";
        } else if (error instanceof com.android.volley.ClientError) {
            errMsg = "Client error";
        } else if (error instanceof com.android.volley.ParseError) {
            errMsg = "Parse error";
        } else if (error instanceof com.android.volley.ServerError) {
            errMsg = "Server error";
        } else if (error instanceof com.android.volley.TimeoutError) {
            errMsg = "Timeout error";
        } else {
            errMsg = "Unknown error";
        }
        if (error != null) {
            if (error.networkResponse != null) {
                errMsg += " (" + error.networkResponse.statusCode + ")";
            }
            String msg = error.getMessage();
            if (msg != null) {
                errMsg += ": " + msg;
            }
        }
        return errMsg;
    }

    <T> void addToRequestQueue(Request<T> req) {
        requestQueue.add(req);
    }
}
