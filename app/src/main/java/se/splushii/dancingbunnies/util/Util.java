package se.splushii.dancingbunnies.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
        digest.update(in.getBytes(StandardCharsets.UTF_8), 0, in.length());
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

    public static void showSoftInput(Context context, View v) {
        ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void hideSoftInput(Context context, View v) {
        ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static Pair<Integer, Integer> getRecyclerViewPosition(RecyclerView recView) {
        LinearLayoutManager llm = (LinearLayoutManager) recView.getLayoutManager();
        int hPos = llm.findFirstVisibleItemPosition();
        View firstVisibleView = llm.findViewByPosition(hPos);
        int hPad = firstVisibleView == null ? 0 : firstVisibleView.getTop();
        return new Pair<>(hPos, hPad - llm.getPaddingTop());
    }

    public static int dpToPixels(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (density * dp);
    }

    public static boolean isValidRegex(String regex) {
        if (regex == null) {
            return false;
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return false;
        }
        return true;
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
