package se.splushii.dancingbunnies.util;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

import static se.splushii.dancingbunnies.util.Util.getLogContext;

public class Diff {
    private static final String LC = getLogContext(Diff.class);

    public final boolean changed;
    public final List<Integer> deleted;
    public final List<Integer> added;
    public final List<Pair<Integer, Integer>> moved;
    public final DiffUtil.DiffResult diffUtilResult;
    private Diff(List<Integer> deleted,
                 List<Integer> added,
                 List<Pair<Integer, Integer>> moved,
                 DiffUtil.DiffResult diffUtilResult) {
        this.deleted = deleted;
        this.added = added;
        this.moved = moved;
        AtomicBoolean diffUtilChanged = new AtomicBoolean();
        diffUtilResult.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                diffUtilChanged.set(true);
            }

            @Override
            public void onRemoved(int position, int count) {
                diffUtilChanged.set(true);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                diffUtilChanged.set(true);
            }

            @Override
            public void onChanged(int position, int count, @Nullable Object payload) {
                diffUtilChanged.set(true);
            }
        });
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

    public static <T> boolean fastDiff(List<T> currentEntries,
                                       List<T> newEntries) {
        return fastDiff(currentEntries, newEntries, Object::equals);
    }

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
            if (!contentComparator.apply(currentEntries.get(i), newEntries.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static <T> Diff diff(List<T> currentEntries, List<T> newEntries) {
        return diff(currentEntries, newEntries, Object::equals);
    }

    public static <T> Diff diff(List<T> currentEntries,
                                List<T> newEntries,
                                BiFunction<T, T, Boolean> contentComparator) {
        return diff(currentEntries, newEntries, contentComparator, false);
    }

    public static <T> Diff diff(List<T> currentEntries,
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
        int numDiffKeptEntries = sameSet.size() + updatedPositions.size() + movedPositions.size();
        int numExpectedKeptEntries = currentEntries.size() - deletedPositions.size();
        if (numDiffKeptEntries != numExpectedKeptEntries) {
            Log.e(LC, "Diff is invalid."
                    + " Number of ops not affecting count (nop/upd/mov) is wrong."
                    + " Got " + numDiffKeptEntries + ", but expected " + numExpectedKeptEntries);
        }

        int numDiffDeltaEntries = addedPositions.size() - deletedPositions.size();
        int numExpectedDeltaEntries = newEntries.size() - currentEntries.size();
        if (numDiffDeltaEntries != numExpectedDeltaEntries) {
            Log.e(LC, "Diff is invalid."
                    + " Number of ops affecting count (add/del) is wrong."
                    + " Got " + numDiffDeltaEntries + ", but expected " + numExpectedDeltaEntries);
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
}