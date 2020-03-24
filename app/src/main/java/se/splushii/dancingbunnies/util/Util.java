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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import androidx.annotation.NonNull;
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

    public static <T> Diff calculateDiff(List<T> currentEntries, List<T> newEntries) {
        HashMap<T, List<Integer>> oldMap = new HashMap<>();
        for (int i = 0; i < currentEntries.size(); i++) {
            T entry = currentEntries.get(i);
            List<Integer> indices = oldMap.getOrDefault(entry, new ArrayList<>());
            indices.add(i);
            oldMap.put(entry, indices);
        }
        HashMap<T, List<Integer>> newMap = new HashMap<>();
        for (int i = 0; i < newEntries.size(); i++) {
            T entry = newEntries.get(i);
            List<Integer> indices = newMap.getOrDefault(entry, new ArrayList<>());
            indices.add(i);
            newMap.put(entry, indices);
        }
        // Find unchanged, deleted and moved
        List<Integer> deletedPositions = new ArrayList<>();
        List<Integer> addedPositions = new ArrayList<>();
        List<Pair<Integer, Integer>> movedPositions = new ArrayList<>();
        for (T entry: oldMap.keySet()) {
            List<Integer> oldPositions = oldMap.get(entry);
            List<Integer> newPositions = newMap.getOrDefault(entry, Collections.emptyList());
            // Find unchanged
            nextOldPos:
            for (int oldPosIndex = 0; oldPosIndex < oldPositions.size(); oldPosIndex++) {
                int oldPos = oldPositions.get(oldPosIndex);
                for (int newPosIndex = 0; newPosIndex < newPositions.size(); newPosIndex++) {
                    int newPosition = newPositions.get(newPosIndex);
                    if (oldPos == newPosition) {
                        oldPositions.remove(oldPosIndex);
                        oldPosIndex--;
                        newPositions.remove(newPosIndex);
                        continue nextOldPos;
                    }
                }
            }
            // Find deleted
            while (oldPositions.size() > newPositions.size()) {
                int oldPos = oldPositions.remove(oldPositions.size() - 1);
                deletedPositions.add(oldPos);
            }
            // Find moved
            while (!oldPositions.isEmpty()) {
                int oldPos = oldPositions.remove(oldPositions.size() - 1);
                int newPos = newPositions.remove(newPositions.size() - 1);
                movedPositions.add(new Pair<>(oldPos, newPos));
            }
        }
        // Find added
        for (T entry: newMap.keySet()) {
            List<Integer> newPositions = newMap.getOrDefault(entry, Collections.emptyList());
            addedPositions.addAll(newPositions);
        }
        Collections.sort(deletedPositions);
        Collections.sort(addedPositions);
        return new Diff(deletedPositions, addedPositions, movedPositions);
    }

    public static class Diff {
        public final boolean changed;
        public final List<Integer> deleted;
        public final List<Integer> added;
        public final List<Pair<Integer, Integer>> moved;
        Diff(List<Integer> deleted,
             List<Integer> added,
             List<Pair<Integer, Integer>> moved) {
            this.deleted = deleted;
            this.added = added;
            this.moved = moved;
            changed = !(deleted.isEmpty() && added.isEmpty() && moved.isEmpty());
        }

        @NonNull
        @Override
        public String toString() {
            return "changed: " + changed
                    + " deleted: " + deleted.size()
                    + " added: " + added.size()
                    + " moved: " + moved.size();
        }
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

    public static <T> T printFutureError(T result, Throwable t) {
        if (t != null) {
            Log.e(LC, Log.getStackTraceString(t));
        }
        return result;
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
