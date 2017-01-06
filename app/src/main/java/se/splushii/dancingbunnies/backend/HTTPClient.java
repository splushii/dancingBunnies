package se.splushii.dancingbunnies.backend;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;

import java.util.concurrent.Executors;

public class HTTPClient {

    private AsyncHttpClient client;

    public HTTPClient() {
        client = new AsyncHttpClient();
        client.setThreadPool(Executors.newFixedThreadPool(3));
    }

    public RequestHandle get(
            String url,
            RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        return client.get(url, params, responseHandler);
    }

    public void setRetries(int retries, int timeout) {
        client.setMaxRetriesAndTimeout(retries, timeout);
    }

    public void post(
            String url,
            RequestParams params,
            AsyncHttpResponseHandler responseHandler) {
        client.post(url, params, responseHandler);
    }

    public void cancelAllRequests(boolean mayInterruptIfRunning) {
        client.cancelAllRequests(mayInterruptIfRunning);
    }
}
