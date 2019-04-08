package se.splushii.dancingbunnies.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Util {
    private static final String LC = getLogContext(Util.class);

    public static String getLogContext(Class c) {
        return c.getSimpleName();
    }

    public static String md5(String in) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e(LC, "MD5 not supported");
            e.printStackTrace();
            return "";
            // TODO
        }
        try {
            digest.update(in.getBytes("UTF-8"), 0, in.length());
        } catch (UnsupportedEncodingException e) {
            Log.e(LC, "UTF-8 not supported");
            e.printStackTrace();
            return "";
            // TODO
        }
        byte[] hash = digest.digest();
        return hex(hash);
    }

    public static String getSalt(SecureRandom rand, int length) {
        byte[] saltBytes = new byte[length / 2]; // Because later hex'd
        rand.nextBytes(saltBytes);
        return hex(saltBytes);
    }

    private static String hex(byte[] in) {
        return String.format("%0" + (in.length * 2) + "x", new BigInteger(1, in));
    }

    public static class FutureException extends Throwable {
        final String msg;
        public FutureException(String msg) {
            super(msg);
            this.msg = msg;
        }

        @Override
        public String toString() {
            return msg;
        }
    }

    public static <T> CompletableFuture<T> futureResult(String error, T value) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (error != null) {
            result.completeExceptionally(new FutureException(error));
            return result;
        }
        result.complete(value);
        return result;
    }

    public static <T> CompletableFuture<T> futureResult(String error) {
        return futureResult(error, null);
    }

    public static String getDurationString(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds %= 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    public static Executor getMainThreadExecutor() {
        return mainThreadExecutor;
    }

    private static Executor mainThreadExecutor = new Executor() {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable r) {
            handler.post(r);
        }
    };
}
