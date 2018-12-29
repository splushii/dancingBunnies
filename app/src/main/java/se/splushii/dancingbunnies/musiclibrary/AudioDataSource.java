package se.splushii.dancingbunnies.musiclibrary;

import android.content.Context;
import android.media.MediaDataSource;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.util.Util;

// TODO: Support reading while buffering.
public class AudioDataSource extends MediaDataSource {
    private static final String LC = Util.getLogContext(AudioDataSource.class);
    private final Object lock = new Object();
    private final String url;
    private final EntryID entryID;
    private final File cacheFile;
//    private volatile byte[] buffer;
    private volatile boolean isDownloading = false;
    private volatile boolean isFinished = false;
    private Thread downloadThread;

    public AudioDataSource(Context context, String url, EntryID entryID) {
        this.url = url;
        this.entryID = entryID;
        this.cacheFile = AudioStorage.getCacheFile(context, entryID);
//        buffer = new byte[0];
    }

    public void download(final Handler handler) {
        synchronized (lock) {
            if (isFinished) {
                Log.d(LC, "download() when already finished.");
                handler.onSuccess();
                return;
            }
            if (!isDownloading) {
                if (cacheFile.isFile()) {
                    Log.d(LC, "entryID " + entryID + " in file cache " + cacheFile.getAbsolutePath()
                            + " size: " + cacheFile.length() + " bytes.");
                    handler.onSuccess();
                    isFinished = true;
                    return;
                }

                HttpURLConnection conn = null;
                if (url != null) {
                    try {
                        conn = (HttpURLConnection) new URL(url).openConnection();
                    } catch (MalformedURLException e) {
                        Log.d(LC, "Malformed URL for song with id " + entryID.id + ": " + e.getMessage());
                    } catch (IOException e) {
                        handler.onFailure("Exception when opening connection: " + e.getMessage());
                        return;
                    }
                }
                final HttpURLConnection connection = conn;
                if (null == conn) {
                    handler.onFailure("No HttpURLConnection");
                    return;
                }
                isDownloading = true;
                downloadThread = new Thread(() -> {
                    handler.onStart();
                    try {
                        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                        InputStream in = new BufferedInputStream(connection.getInputStream());
                        long contentLength = connection.getContentLengthLong();
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
                        connection.disconnect();
                        arrayOutputStream.flush();
                        FileOutputStream fileOutputStream = getCacheFileOutputStream();

                        arrayOutputStream.writeTo(fileOutputStream);
//                        buffer = arrayOutputStream.toByteArray();
                        fileOutputStream.flush();
                        fileOutputStream.close();
                        arrayOutputStream.close();
                        isFinished = true;
                        Log.d(LC, "entryID " + entryID + " " + cacheFile.length()
                                + " bytes downloaded to " + cacheFile.getAbsolutePath());
                        handler.onSuccess();
                    } catch (InterruptedIOException e) {
                        Log.d(LC, "download interrupted");
                        handler.onFailure("interrupted");
                    } catch (IOException e) {
                        e.printStackTrace();
                        handler.onFailure("Error: " + e.getMessage());
                    } catch (CacheFileException e) {
                        Log.e(LC, e.getMessage());
                        handler.onFailure("Could not get cache file output stream: " + e.getMessage());
                    } finally {
                        isDownloading = false;
                    }
                });
                downloadThread.start();
                return;
            }
        }

        // Else wait for download thread
        new Thread(() -> {
            try {
                downloadThread.join();
            } catch (InterruptedException e) {
                handler.onFailure("Could not join download thread");
                return;
            }
            if (isFinished) {
                handler.onSuccess();
                return;
            }
            handler.onFailure("Download thread failed");
        }).start();
    }

    private FileOutputStream getCacheFileOutputStream() throws CacheFileException {
        File parentDir = cacheFile.getParentFile();
        if (!parentDir.isDirectory()) {
            if (!parentDir.mkdirs()) {
                throw new CacheFileException("Could not create cache file parent directory: "
                        + parentDir);
            }
        }
        try {
            if (cacheFile.exists()) {
                if (!cacheFile.isFile() && !cacheFile.delete()) {
                    throw new CacheFileException("Cache file path already exists. Not a file. "
                            + "Can not delete: " + cacheFile.getAbsolutePath());
                }
            } else {
                if (!cacheFile.createNewFile()) {
                    throw new CacheFileException("Could not create cache file: "
                            + cacheFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new CacheFileException("Could not create cache file: " + cacheFile
                    + ": " + e.getMessage());
        }
        try {
            return new FileOutputStream(cacheFile);
        } catch (FileNotFoundException e) {
            throw new CacheFileException("Could not create fileOutputStream: " + e.getMessage());
        }
    }

    @Override
    public int readAt(long position, byte[] bytes, int offset, int size) {
        return readFromCacheFileAt(position, bytes, offset, size);

        // TODO: Read from buffer if still buffering
//        long len = buffer.length;
//        if (position > len) {
//            return -1;
//        }
//        if (position + size > len) {
//            size = (int) (len - position);
//        }
//        System.arraycopy(buffer, (int) position, bytes, offset, size);
//        return size;
    }

    private int readFromCacheFileAt(long position, byte[] bytes, int offset, int size) {
        long len = cacheFile.length();
        if (position > len) {
            return -1;
        }
        if (position + size > len) {
            size = (int) (len - position);
        }
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(cacheFile, "r");
            randomAccessFile.seek(position);
            int readBytes = randomAccessFile.read(bytes, offset, size);
            randomAccessFile.close();
            return readBytes;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public long getSize() {
        return getCacheFileSize();
        // TODO: Get from buffer if still buffering
//        if (buffer != null) {
//            return buffer.length;
//        }
//        return 0;
    }

    private long getCacheFileSize() {
        if (cacheFile.isFile()) {
            return cacheFile.length();
        }
        return 0;
    }

    @Override
    public void close() {
        if (isDownloading) {
            downloadThread.interrupt();
        }
    }

    public boolean isFinished() {
        return isFinished;
    }

    String getURL() {
        return url;
    }

    public interface Handler {
        void onStart();
        void onFailure(String message);
        void onSuccess();
        void onProgress(long i, long max);
    }

    private class CacheFileException extends Exception {
        CacheFileException(String msg) {
            super(msg);
        }
    }
}
