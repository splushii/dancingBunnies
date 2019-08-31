package se.splushii.dancingbunnies.musiclibrary;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.db.CacheDao;
import se.splushii.dancingbunnies.storage.db.CacheEntry;
import se.splushii.dancingbunnies.storage.db.DB;
import se.splushii.dancingbunnies.storage.db.WaveformEntry;
import se.splushii.dancingbunnies.util.Util;

// TODO: Support reading while buffering.
public class AudioDataSource extends MediaDataSource {
    private static final String LC = Util.getLogContext(AudioDataSource.class);
    private static final long BYTES_BETWEEN_PROGRESS_UPDATE = 100_000L;
    private final String url;
    private final EntryID entryID;
    private final File cacheFile;
    private final File cachePartFile;
    private final AudioStorage audioStorage;
    private final CacheDao cacheModel;
    private Thread fetchThread;
    private final Object fetchLock = new Object();
    private boolean isFetching = false;
    private volatile boolean isDataReady = false;
    private volatile boolean areSamplesReady = false;
    private double[] peakSamples;
    private double[] peakNegativeSamples;
    private double[] rmsSamples;
    private double[] rmsNegativeSamples;

    public AudioDataSource(Context context, String url, EntryID entryID) {
        this.url = url;
        this.entryID = entryID;
        this.cacheModel = DB.getDB(context).cacheModel();
        this.audioStorage = AudioStorage.getInstance(context);
        this.cacheFile = AudioStorage.getCacheFile(context, entryID);
        this.cachePartFile = new File(cacheFile + ".part");
        this.peakSamples = new double[0];
        this.peakNegativeSamples = new double[0];
        this.rmsSamples = new double[0];
        this.rmsNegativeSamples = new double[0];
    }

    public void fetch(final FetchDataHandler handler) {
        synchronized (fetchLock) {
            if (!isFetching) {
                isFetching = true;
                fetchThread = new Thread(() -> {
                    Log.d(LC, "fetching data");
                    if (cacheFile.isFile()) {
                        Log.d(LC, "entryID " + entryID
                                + " in file cache " + cacheFile.getAbsolutePath()
                                + " size: " + cacheFile.length() + " bytes.");
                        isDataReady = true;
                        handler.onDownloadFinished();
                    } else {
                        Log.d(LC, "entryID " + entryID + " downloading");
                        handler.onDownloading();
                        boolean dataSuccess = fetchData(handler);
                        if (!dataSuccess) {
                            isFetching = false;
                            handler.onFailure("Data fetch failed");
                            return;
                        }
                        isDataReady = true;
                    }
                    handler.onSampling();
                    Log.d(LC, "fetching samples");
                    boolean samplesSuccess = fetchSamples(handler);
                    if (!samplesSuccess) {
                        isFetching = false;
                        handler.onFailure("Sample fetch failed");
                        return;
                    }
                    areSamplesReady = true;
                    handler.onSuccess();
                });
                fetchThread.start();
                return;
            }
        }

        // Else wait for fetch thread
        new Thread(() -> {
            Log.d(LC, "entryID " + entryID + " fetched by other thread");
            try {
                fetchThread.join();
            } catch (InterruptedException e) {
                handler.onDownloadFailed("Could not join download thread");
                handler.onSamplingFailed("Could not join download thread");
                handler.onFailure("Could not join download thread");
                return;
            }
            if (isDataReady && areSamplesReady) {
                handler.onDownloadFinished();
                handler.onSamplingFinished(
                        peakSamples,
                        peakNegativeSamples,
                        rmsSamples,
                        rmsNegativeSamples
                );
                handler.onSuccess();
            } else if (isDataReady) {
                handler.onDownloadFinished();
                handler.onSamplingFailed("fetch thread failed");
                handler.onFailure("fetch thread failed");
            } else if (areSamplesReady) {
                handler.onDownloadFailed("fetch thread failed");
                handler.onSamplingFinished(
                        peakSamples,
                        peakNegativeSamples,
                        rmsSamples,
                        rmsNegativeSamples
                );
                handler.onFailure("fetch thread failed");
            } else {
                handler.onDownloadFailed("fetch thread failed");
                handler.onSamplingFailed("fetch thread failed");
                handler.onFailure("fetch thread failed");
            }
        }).start();
    }

    private boolean fetchData(FetchDataHandler handler) {
        HttpURLConnection conn = null;
        if (url != null) {
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
            } catch (MalformedURLException e) {
                Log.d(LC, "Malformed URL for song with id " + entryID.id + ": " + e.getMessage());
            } catch (IOException e) {
                handler.onDownloadFailed("Exception when opening connection: " + e.getMessage());
                return false;
            }
        }
        if (null == conn) {
            handler.onDownloadFailed("No HttpURLConnection");
            return false;
        }
        try {
            BufferedOutputStream outputStream = new BufferedOutputStream(
                    getCachePartFileOutputStream()
            );
            InputStream in = new BufferedInputStream(conn.getInputStream());
            long contentLength = conn.getContentLengthLong();
            int b = in.read();
            long bytesRead = 0;
            while (b != -1) {
                outputStream.write(b);
                if (bytesRead % BYTES_BETWEEN_PROGRESS_UPDATE == 0) {
                    handler.onDownloadProgress(bytesRead, contentLength);
                }
                bytesRead++;
                b = in.read();
            }
            handler.onDownloadProgress(bytesRead, contentLength);
            in.close();
            conn.disconnect();
            outputStream.flush();
            outputStream.close();
            Files.move(
                    cachePartFile.toPath(),
                    cacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
            cacheModel.insert(CacheEntry.from(entryID));
            Log.d(LC, "entryID " + entryID + " " + cacheFile.length()
                    + " bytes downloaded to " + cacheFile.getAbsolutePath());
            handler.onDownloadFinished();
            return true;
        } catch (InterruptedIOException e) {
            String err = "download interrupted";
            Log.e(LC, err);
            handler.onDownloadFailed(err);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            String err = "Error: " + e.getMessage();
            handler.onDownloadFailed(err);
            return false;
        } catch (CacheFileException e) {
            String err = "Could not get cache file output stream: " + e.getMessage();
            Log.e(LC, err);
            handler.onDownloadFailed(err);
            return false;
        }
    }

    private FileOutputStream getCachePartFileOutputStream() throws CacheFileException {
        File parentDir = cachePartFile.getParentFile();
        if (!parentDir.isDirectory()) {
            if (!parentDir.mkdirs()) {
                throw new CacheFileException("Could not create cache file parent directory: "
                        + parentDir);
            }
        }
        try {
            if (cachePartFile.exists()) {
                if (!cachePartFile.isFile() && !cachePartFile.delete()) {
                    throw new CacheFileException("Cache part file path already exists. Not a file. "
                            + "Can not delete: " + cachePartFile.getAbsolutePath());
                }
            } else {
                if (!cachePartFile.createNewFile()) {
                    throw new CacheFileException("Could not create cache part file: "
                            + cachePartFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new CacheFileException("Could not create cache part file: " + cachePartFile
                    + ": " + e.getMessage());
        }
        try {
            return new FileOutputStream(cachePartFile);
        } catch (FileNotFoundException e) {
            throw new CacheFileException("Could not create fileOutputStream from cache part file: "
                    + e.getMessage());
        }
    }

    @Override
    public int readAt(long position, byte[] bytes, int offset, int size) {
        return readFromCacheFileAt(position, bytes, offset, size);
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
    }

    private long getCacheFileSize() {
        if (cacheFile.isFile()) {
            return cacheFile.length();
        }
        return 0;
    }

    @Override
    public void close() {
        if (isFetching) {
            fetchThread.interrupt();
        }
    }

    String getURL() {
        return url;
    }

    private boolean fetchSamples(FetchDataHandler handler) {
        WaveformEntry waveformEntry = audioStorage.getWaveformSync(entryID);
        if (waveformEntry != null) {
            peakSamples = waveformEntry.getPeakPositive();
            peakNegativeSamples = waveformEntry.getPeakNegative();
            rmsSamples = waveformEntry.getRMSPositive();
            rmsNegativeSamples = waveformEntry.getRMSNegative();
            handler.onSamplingFinished(
                    peakSamples,
                    peakNegativeSamples,
                    rmsSamples,
                    rmsNegativeSamples
            );
            return true;
        }
        if (!isDataReady) {
            handler.onSamplingFailed("getSamples from non-finished source");
            return false;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(this);
        } catch (IOException e) {
            e.printStackTrace();
            handler.onSamplingFailed("getSamples could not set datasource for extractor: " + e.getMessage());
            return false;
        }
        int numTracks = extractor.getTrackCount();
        if (numTracks > 1) {
            handler.onSamplingFailed("getSamples NOT HANDLING MORE THAN ONE TRACK. CONTAINS " + numTracks);
            return false;
        }
        MediaFormat mediaFormat = null;
        String mime = null;
loop:   for (int i = 0; i < numTracks; ++i) {
            mediaFormat = extractor.getTrackFormat(i);
            mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            switch (mime) {
                case MediaFormat.MIMETYPE_AUDIO_RAW:
                case MediaFormat.MIMETYPE_AUDIO_MPEG:
                case MediaFormat.MIMETYPE_AUDIO_FLAC:
                    Log.e(LC, "fetchSamples select track with mime: " + mime);
                    extractor.selectTrack(i);
                    break loop;
                default:
                    handler.onSamplingFailed("getSamples not supported for mime: " + mime);
                    return false;
            }
        }
        if (mime == null) {
            handler.onSamplingFailed("getSamples contains no tracks");
            return false;
        }
        MediaCodec mediaCodec;
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            mediaCodec = MediaCodec.createByCodecName(list.findDecoderForFormat(mediaFormat));
        } catch (IOException e) {
            e.printStackTrace();
            handler.onSamplingFailed("getSamples could not create codec for mime: " + mime);
            return false;
        }
        mediaCodec.configure(mediaFormat, null, null, 0);
        mediaCodec.start();
        final long timeoutUs = -1; // Infinite timeout
        boolean extractorReachedEnd = false;
        boolean outputBufferReachedEnd = false;
        final int downSampledSize = 1024 * 8;
        int downSampleFactor = 1;
        int numSamples = 0;
         double sumSquareSamples = 0;
        double sumSquareSamplesNegative = 0;
        double peak = 0;
        double peakNegative = 0;
        int downSampledPos = 0;
        double[] peakList = new double[downSampledSize];
        double[] peakNegativeList = new double[downSampledSize];
        double[] squaresSumList = new double[downSampledSize];
        double[] squaresSumNegativeList = new double[downSampledSize];
        while (!outputBufferReachedEnd) {
            // Put encoded data into codec's input buffer
            if (!extractorReachedEnd) {
                int inputBufferId = mediaCodec.dequeueInputBuffer(timeoutUs);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                    // Get encoded samples from extractor
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    long presentationTimeUs = extractor.getSampleTime();
                    if (sampleSize < 0) { // No more samples available
                        extractorReachedEnd = true;
                        mediaCodec.queueInputBuffer(
                                inputBufferId,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        );
                    } else {
                        mediaCodec.queueInputBuffer(
                                inputBufferId,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                        );
                        extractor.advance();
                    }
                }
            }
            // Get decoded data from codec's output buffer
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, timeoutUs);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)
                    == MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) {
                handler.onSamplingFailed("getSamples NOT HANDLING PARTIAL FRAMES");
                return false;
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                handler.onSamplingFailed("getSamples NOT HANDLING CODEC CONFIG");
                return false;
            }
            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                MediaFormat outputFormat = mediaCodec.getOutputFormat(outputBufferId);
                int channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
                if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                }
                switch (pcmEncoding) {
                    case AudioFormat.ENCODING_PCM_8BIT:
                        handler.onSamplingFailed("NOT HANDLING PCM 8 BIT");
                        return false;
                    case AudioFormat.ENCODING_PCM_16BIT:
                        ShortBuffer shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();
                        int numFrames = shortBuffer.remaining() / channels;
                        for (int framePos = 0; framePos < numFrames; framePos++) {
                            double gainSumSquare = 0;
                            double gainSumSquareNegative = 0;
                            for (int i = 0; i < channels; i++) {
                                short gain = shortBuffer.get(framePos + i);
                                if (gain > 0) {
                                    double gainPositive = (double) gain / Short.MAX_VALUE;
                                    if (gainPositive > peak) {
                                        peak = gainPositive;
                                    }
                                    gainSumSquare += gainPositive * gainPositive;
                                } else if (gain < 0) {
                                    double gainNegative = (double) gain / Short.MIN_VALUE;
                                    if (gainNegative > peakNegative) {
                                        peakNegative = gainNegative;
                                    }
                                    gainSumSquareNegative += gainNegative * gainNegative;
                                }
                            }
                            if (downSampledPos >= downSampledSize) {
                                // Downsample
                                for (int i = 0; 2 * i + 1 < downSampledSize; i++) {
                                    int firstIndex = 2 * i;
                                    int secondIndex = firstIndex + 1;
                                    peakList[i] = Math.max(
                                            peakList[firstIndex],
                                            peakList[secondIndex]
                                    );
                                    peakNegativeList[i] = Math.max(
                                            peakNegativeList[firstIndex],
                                            peakNegativeList[secondIndex]
                                    );
                                    squaresSumList[i] = squaresSumList[firstIndex]
                                            + squaresSumList[secondIndex];
                                    squaresSumNegativeList[i] = squaresSumNegativeList[firstIndex]
                                            + squaresSumNegativeList[secondIndex];
                                }
                                downSampleFactor *= 2;
                                downSampledPos = downSampledSize / 2;
                            }
                            double averageGainSquare = gainSumSquare / channels;
                            double averageGainSquareNegative = gainSumSquareNegative / channels;
                            if (numSamples < downSampleFactor) {
                                sumSquareSamples += averageGainSquare;
                                sumSquareSamplesNegative += averageGainSquareNegative;
                                numSamples++;
                            } else {
                                peakList[downSampledPos] = peak;
                                peakNegativeList[downSampledPos] = peakNegative;
                                squaresSumList[downSampledPos] = sumSquareSamples;
                                squaresSumNegativeList[downSampledPos] = sumSquareSamplesNegative;
                                downSampledPos++;
                                sumSquareSamples = averageGainSquare;
                                sumSquareSamplesNegative = averageGainSquareNegative;
                                peak = 0;
                                peakNegative = 0;
                                numSamples = 1;
                            }
                        }
                        break;
                    case AudioFormat.ENCODING_PCM_FLOAT:
                        handler.onSamplingFailed("NOT HANDLING PCM FLOAT");
                        return false;
                }
                mediaCodec.releaseOutputBuffer(outputBufferId, false);
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                outputBufferReachedEnd = true;
            }
        }
        mediaCodec.stop();
        mediaCodec.release();
        extractor.release();

        // Calculate RMS
        double[] rmsList = Arrays.copyOf(squaresSumList, downSampledPos);
        double[] rmsNegativeList = Arrays.copyOf(squaresSumNegativeList, downSampledPos);
        for (int i = 0; i < downSampledPos; i++) {
            rmsList[i] = Math.sqrt(rmsList[i] / downSampleFactor);
            rmsNegativeList[i] = Math.sqrt(rmsNegativeList[i] / downSampleFactor);
        }

        peakList = Arrays.copyOf(peakList, downSampledPos);
        peakNegativeList = Arrays.copyOf(peakNegativeList, downSampledPos);

        // Normalize to 0..1
        double maxAmplitude = 0;
        for (int i = 0; i < downSampledPos; i++) {
            if (peakList[i] > maxAmplitude) {
                maxAmplitude = peakList[i];
            }
            if (peakNegativeList[i] > maxAmplitude) {
                maxAmplitude = peakNegativeList[i];
            }
        }
        if (maxAmplitude > 0) {
            for (int i = 0; i < downSampledPos; i++) {
                peakList[i] /= maxAmplitude;
                peakNegativeList[i] /= maxAmplitude;
                rmsList[i] /= maxAmplitude;
                rmsNegativeList[i] /= maxAmplitude;
            }
        }

        this.peakSamples = peakList;
        this.peakNegativeSamples = peakNegativeList;
        this.rmsSamples = rmsList;
        this.rmsNegativeSamples = rmsNegativeList;
        handler.onSamplingFinished(
                peakSamples,
                peakNegativeSamples,
                rmsSamples,
                rmsNegativeSamples
        );
        return true;
    }

    boolean isDataReady() {
        return isDataReady;
    }

    public interface FetchDataHandler {
        void onDownloading();
        void onDownloadProgress(long i, long max);
        void onDownloadFinished();
        void onDownloadFailed(String err);
        void onSampling();
        void onSamplingFinished(double[] peakSamplesPositive,
                                double[] peakSamplesNegative,
                                double[] rmsSamplesPositive,
                                double[] rmsSamplesNegative);
        void onSamplingFailed(String err);
        void onFailure(String message);
        void onSuccess();

    }

    private class CacheFileException extends Exception {
        CacheFileException(String msg) {
            super(msg);
        }
    }
}
