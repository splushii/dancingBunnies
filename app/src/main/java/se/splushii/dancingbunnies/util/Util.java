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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
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

    // TODO: Put diff stuff in its own class
    public static <T> boolean fastDiff(List<T> currentEntries,
                                       List<T> newEntries) {
        return fastDiff(currentEntries, newEntries, Object::equals);
    }

    // TODO: Rename to equals()
    public static <T> boolean fastDiff(List<T> currentEntries,
                                       List<T> newEntries,
                                       BiFunction<T, T, Boolean> contentComparator) {
        if (currentEntries == null && newEntries == null) {
            return false;
        }
        if (currentEntries == null || newEntries == null) {
            return true;
        }
        if (currentEntries.size() != newEntries.size()) {
            return true;
        }
        for (int i = 0; i < currentEntries.size(); i++) {
            if (!currentEntries.get(i).equals(newEntries.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static <T> Diff calculateDiff(List<T> currentEntries, List<T> newEntries) {
        return calculateDiff(currentEntries, newEntries, Object::equals, false);
    }

    public static <T> Diff calculateDiff(List<T> currentEntries,
                                         List<T> newEntries,
                                         BiFunction<T, T, Boolean> contentComparator) {
        return calculateDiff(currentEntries, newEntries, contentComparator, false);
    }

    public static <T> Diff calculateDiff(List<T> currentEntries,
                                         List<T> newEntries,
                                         BiFunction<T, T, Boolean> contentComparator,
                                         boolean debug) {
        if (debug) {
            Log.d(LC, "calculateDiff start "
                    + "(old: " + currentEntries.size() + ", new: " + newEntries.size() + ")");
        }
        long startTime = System.currentTimeMillis();

        // Find deleted/added/moved positions
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
//        List<Integer> samePositions = new ArrayList<>();
        HashSet<Integer> sameSet = new HashSet<>();
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
                        sameSet.add(oldPos);
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


        HashSet<Integer> deletedSet = new HashSet<>(deletedPositions);
        HashMap<Integer, Integer> movedMap = new HashMap<>();
        for (Pair<Integer, Integer> movedPosition: movedPositions) {
            movedMap.put(movedPosition.first, movedPosition.second);
        }
        HashSet<Integer> addedSet = new HashSet<>(addedPositions);

        // Find updated content
        List<Integer> updatedPositions = new ArrayList<>();
        for (int i = 0; i < currentEntries.size(); i++) {
            T oldEntry = currentEntries.get(i);
            if (deletedSet.contains(i)) {
                continue;
            }
            if (movedMap.containsKey(i)) {
                T newEntry = newEntries.get(movedMap.get(i));
                if (!contentComparator.apply(oldEntry, newEntry)) {
                    updatedPositions.add(movedMap.get(i));
                }
                continue;
            }
            // If it's not deleted or moved, it's in the same position
            if (!sameSet.contains(i)) {
                Log.e(LC, "SOMETHING BAD WRONG HERE YO");
            }
            T newEntry = newEntries.get(i);
            if (!contentComparator.apply(oldEntry, newEntry)) {
                updatedPositions.add(i);
                sameSet.remove(i);
            }
        }

        long time = System.currentTimeMillis() - startTime;
        if (debug) {
            Log.d(LC, "calculateDiff finish "
                    + "(old: " + currentEntries.size() + ", new: " + newEntries.size() + ")"
                    + "\ntime: " + time + "ms"
                    + "\nnop: " + sameSet.size()
                    + "\nupd : " + updatedPositions.size()
                    + "\ndel : " + deletedPositions.size()
                    + "\nadd : " + addedPositions.size()
                    + "\nmov : " + movedPositions.size()
            );
        }
        int numKeptEntries = sameSet.size() + updatedPositions.size() + movedPositions.size();
        if (numKeptEntries != currentEntries.size()) {
            Log.e(LC, "Number of ops not affecting count (nop/upd/mov) is invalid."
                    + " Expected " + currentEntries.size() + ", got " + numKeptEntries);
        }
        int numDeltaEntries = addedPositions.size() - deletedPositions.size();
        if (currentEntries.size() + numDeltaEntries != newEntries.size()) {
            Log.e(LC, "Number of ops affecting count (add/del) is invalid."
                    + " Expected " + (newEntries.size() - currentEntries.size()) + ", got " + numDeltaEntries);
        }

        // Calculate DiffUtil diff
        if (debug) {
            Log.d(LC, "calculateDiff DiffUtil start "
                    + "(old: " + currentEntries.size() + ", new: " + newEntries.size() + ")");
        }
        startTime = System.currentTimeMillis();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return currentEntries.size();
            }

            @Override
            public int getNewListSize() {
                return newEntries.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                T oldItem = currentEntries.get(oldItemPosition);
                T newItem = newEntries.get(newItemPosition);
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                T oldItem = currentEntries.get(oldItemPosition);
                T newItem = newEntries.get(newItemPosition);
                return contentComparator.apply(oldItem, newItem);
            }
        }, false);
        time = System.currentTimeMillis() - startTime;
        if (debug) {
            Log.d(LC, "calculateDiff DiffUtil finish "
                    + "(old: " + currentEntries.size() + ", new: " + newEntries.size() + ")"
                    + "\ntime: " + time + "ms");
        }

        return new Diff(deletedPositions, addedPositions, movedPositions, diffResult);
    }

    public static class Diff {
        public final boolean changed;
        public final List<Integer> deleted;
        public final List<Integer> added;
        public final List<Pair<Integer, Integer>> moved;
        public final DiffUtil.DiffResult diffUtilResult;
        Diff(List<Integer> deleted,
             List<Integer> added,
             List<Pair<Integer, Integer>> moved,
             DiffUtil.DiffResult diffUtilResult) {
            this.deleted = deleted;
            this.added = added;
            this.moved = moved;
            AtomicBoolean diffUtilChanged = new AtomicBoolean();
//            diffUtilResult.dispatchUpdatesTo(new ListUpdateCallback() {
//                @Override
//                public void onInserted(int position, int count) {
////                    Log.e(LC, "onInserted(pos: " + position + ", count: " + count + ")");
//                    diffUtilChanged.set(true);
//                }
//
//                @Override
//                public void onRemoved(int position, int count) {
////                    Log.e(LC, "onRemoved(pos: " + position + ", count: " + count + ")");
//                    diffUtilChanged.set(true);
//                }
//
//                @Override
//                public void onMoved(int fromPosition, int toPosition) {
////                    Log.e(LC, "onMoved(from: " + fromPosition + ", to: " + toPosition + ")");
//                    diffUtilChanged.set(true);
//                }
//
//                @Override
//                public void onChanged(int position, int count, @Nullable Object payload) {
////                    Log.e(LC, "onChanged(pos: " + position + ", count: " + count + ")");
//                    diffUtilChanged.set(true);
//                }
//            });
//            Log.e(LC, "diffUtilChanged: " + diffUtilChanged.get());

            // TODO: REMOVE ME
            diffUtilChanged.set(true);


            changed = !(deleted.isEmpty() && added.isEmpty() && moved.isEmpty())
                    || diffUtilChanged.get();
            this.diffUtilResult = diffUtilResult;
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
