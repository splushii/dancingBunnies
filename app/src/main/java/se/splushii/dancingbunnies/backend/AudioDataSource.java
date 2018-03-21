package se.splushii.dancingbunnies.backend;

import android.media.MediaDataSource;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;

import se.splushii.dancingbunnies.util.Util;

public class AudioDataSource extends MediaDataSource {
    private static final String LC = Util.getLogContext(AudioDataSource.class);
    private HttpURLConnection conn;
    private volatile byte[] buffer;
    private volatile boolean isDownloading = false;
    private volatile boolean isFinished = false;
    private Thread downloadThread;

    public AudioDataSource(HttpURLConnection conn) {
        if (conn == null) {
            buffer = new byte[0];
        } else {
            this.conn = conn;
        }
    }

    public void download(final AudioDataDownloadHandler handler) {
        if (conn == null) {
            handler.onFailure("No HttpURLConnection");
            return;
        } else if (isDownloading) {
            handler.onFailure("Already downloading");
            return;
        } else if (isFinished) {
            Log.d(LC, "download() when already finished.");
            return;
        }
        isDownloading = true;
        downloadThread = new Thread(() -> {
            handler.onStart();
            try {
                ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                InputStream in = new BufferedInputStream(conn.getInputStream());
                long contentLength = conn.getContentLengthLong();
                int b = in.read();
                long bytesRead = 0;
                while (b != -1) {
                    arrayOutputStream.write(b);
                    if (bytesRead % 1000 == 0) {
                        handler.onProgress(bytesRead, contentLength);
                    }
                    bytesRead++;
                    b = in.read();
                }
                in.close();
                conn.disconnect();
                arrayOutputStream.flush();
                buffer = arrayOutputStream.toByteArray();
                arrayOutputStream.close();
                isFinished = true;
                handler.onSuccess();
            } catch (InterruptedIOException e) {
                Log.d(LC, "download interrupted");
                handler.onFailure("interrupted");
            } catch (IOException e) {
                e.printStackTrace();
                handler.onFailure("Error: " + e.getMessage());
            } finally {
                isDownloading = false;
            }
        });
        downloadThread.start();
    }

    @Override
    public int readAt(long position, byte[] bytes, int offset, int size) throws IOException {
        long len = buffer.length;
        if (position > len) {
            return -1;
        }
        if (position + size > len) {
            size = (int) (len - position);
        }
        System.arraycopy(buffer, (int) position, bytes, offset, size);
        return size;
    }

    @Override
    public long getSize() {
        if (buffer != null) {
            return buffer.length;
        }
        return 0;
    }

    @Override
    public void close() {
        if (isDownloading) {
            downloadThread.interrupt();
        }
    }
}
