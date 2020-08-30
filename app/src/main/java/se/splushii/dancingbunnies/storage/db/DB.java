package se.splushii.dancingbunnies.storage.db;

import android.content.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import androidx.arch.core.util.Function;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                Entry.class,
                MetaString.class,
                MetaLong.class,
                MetaDouble.class,
                MetaLocalString.class,
                MetaLocalLong.class,
                MetaLocalDouble.class,
                WaveformEntry.class,
                Playlist.class,
                PlaylistEntry.class,
                PlaybackControllerEntry.class
        },
        version = 1
)
public abstract class DB extends RoomDatabase {
    private static final String DB_NAME = "dB";
    static final String TABLE_ENTRY_ID = "entry_id";
    static final String TABLE_META_STRING = "meta_string";
    static final String TABLE_META_LONG = "meta_long";
    static final String TABLE_META_DOUBLE = "meta_double";
    static final String TABLE_META_LOCAL_STRING = "meta_local_string";
    static final String TABLE_META_LOCAL_LONG = "meta_local_long";
    static final String TABLE_META_LOCAL_DOUBLE = "meta_local_double";
    public static final String COLUMN_SRC = "src";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_KEY = "key";
    public static final String COLUMN_VALUE = "value";
    static final String TABLE_WAVEFORM = "waveform";
    static final String TABLE_PLAYLISTS = "playlists";
    static final String TABLE_PLAYLIST_ENTRIES = "playlist_entries";
    static final String TABLE_PLAYBACK_CONTROLLER_ENTRIES = "playback_controller_entries";
    static final String TABLE_LIBRARY_TRANSACTIONS = "library_transactions";
    private static volatile DB instance;

    public static DB getDB(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, DB.class, DB_NAME).build();
        }
        return instance;
    }

    public abstract MetaDao metaModel();
    public abstract WaveformDao waveformModel();
    public abstract PlaylistDao playlistModel();
    public abstract PlaylistEntryDao playlistEntryModel();
    public abstract PlaybackControllerEntryDao playbackControllerEntryModel();

    public static <T> long[] getPositions(List<T> entries, Function<T, Long> positionSupplier) {
        long[] playlistPositions = new long[entries.size()];
        for (int i = 0; i < playlistPositions.length; i++) {
            playlistPositions[i] = positionSupplier.apply(entries.get(i));
        }
        return playlistPositions;
    }

    public static void movePositions(List<Long> sourcePositions,
                                     long targetPos,
                                     BiConsumer<Long, Long> move) {
        // Sort sourcePositions ascending
        Collections.sort(sourcePositions);
        // Calculate number of sourcePositions below targetPos
        long numSourcePosBelowTargetPos = sourcePositions.stream()
                .filter(sourcePosition -> sourcePosition < targetPos)
                .count();
        // Calculate new targetPositions for all sourcePositions
        HashMap<Long, Long> sourceToTargetMap = new HashMap<>();
        for (int i = 0; i < sourcePositions.size(); i ++) {
            long source = sourcePositions.get(i);
            long target = targetPos - numSourcePosBelowTargetPos + i;
            sourceToTargetMap.put(source, target);
        }
        // Divide sourcePositions into "move higher" and "move lower"
        List<Long> sourcePositionsMovingHigher = sourcePositions.stream()
                .filter(sourcePos -> sourcePos < sourceToTargetMap.get(sourcePos))
                .collect(Collectors.toList());
        List<Long> sourcePositionsMovingLower = sourcePositions.stream()
                .filter(sourcePos -> sourcePos > sourceToTargetMap.get(sourcePos))
                .collect(Collectors.toList());
        // Move "move higher" group starting from its highest pos
        // (to avoid moving past another sourcePos)
        Collections.sort(sourcePositionsMovingHigher, Collections.reverseOrder());
        for (long sourcePosition: sourcePositionsMovingHigher) {
            move.accept(sourcePosition, sourceToTargetMap.get(sourcePosition));
        }
        // Move "move lower" group starting from its lowest pos
        // (to avoid moving past another sourcePos)
        for (long sourcePosition: sourcePositionsMovingLower) {
            move.accept(sourcePosition, sourceToTargetMap.get(sourcePosition));
        }
    }
}