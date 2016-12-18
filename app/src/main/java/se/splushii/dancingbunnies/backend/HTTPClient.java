package se.splushii.dancingbunnies.backend;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import java8.util.Optional;
import java8.util.concurrent.CompletableFuture;

public class HTTPClient {

    private static AsyncHttpClient client = new AsyncHttpClient();
    public static Semaphore lock = new Semaphore(10);

    public static RequestHandle get(
            String url,
            RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        return client.get(url, params, responseHandler);
    }

    public static void post(
            String url,
            RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        client.post(url, params, responseHandler);
    }

    public static void cancelAllRequests(boolean mayInterruptIfRunning) {
        client.cancelAllRequests(mayInterruptIfRunning);
    }
}
