package se.splushii.dancingbunnies.storage.db;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = DB.TABLE_TRACK_ID,
        indices = {
                @Index(value = {
                        DB.COLUMN_SRC,
                        DB.COLUMN_ID
                }, unique = true)
        },
        primaryKeys = {
                DB.COLUMN_SRC,
                DB.COLUMN_ID
        }
)
public class Track extends Entry {
    public static Track from(String src, String id) {
        Track track = new Track();
        track.src = src;
        track.id = id;
        return track;
    }
}