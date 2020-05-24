package se.splushii.dancingbunnies.storage.db;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

@Entity(tableName = DB.TABLE_WAVEFORM,
        indices = {
                @Index(value = {DB.COLUMN_SRC, DB.COLUMN_ID}, unique = true)
        },
        primaryKeys = {DB.COLUMN_SRC, DB.COLUMN_ID}
)
public class WaveformEntry {
    private static final String LC = Util.getLogContext(WaveformEntry.class);

    private static final String COLUMN_PEAK_POSITIVE = "peak_positive";
    private static final String COLUMN_PEAK_NEGATIVE = "peak_negative";
    private static final String COLUMN_RMS_POSITIVE = "rms_positive";
    private static final String COLUMN_RMS_NEGATIVE = "rms_negative";

    @NonNull
    @ColumnInfo(name = DB.COLUMN_SRC)
    public String src;
    @NonNull
    @ColumnInfo(name = DB.COLUMN_ID)
    public String id;
    @NonNull
    @ColumnInfo(name = COLUMN_PEAK_POSITIVE, typeAffinity = ColumnInfo.BLOB)
    byte[] peakPositive;
    @NonNull
    @ColumnInfo(name = COLUMN_PEAK_NEGATIVE, typeAffinity = ColumnInfo.BLOB)
    byte[] peakNegative;
    @NonNull
    @ColumnInfo(name = COLUMN_RMS_POSITIVE, typeAffinity = ColumnInfo.BLOB)
    byte[] rmsPositive;
    @NonNull
    @ColumnInfo(name = COLUMN_RMS_NEGATIVE, typeAffinity = ColumnInfo.BLOB)
    byte[] rmsNegative;

    public static WaveformEntry from(EntryID entryID,
                                     byte[] peakPositive,
                                     byte[] peakNegative,
                                     byte[] rmsPositive,
                                     byte[] rmsNegative) {
        WaveformEntry waveform = new WaveformEntry();
        waveform.src = entryID.src;
        waveform.id = entryID.id;
        waveform.peakPositive = peakPositive;
        waveform.peakNegative = peakNegative;
        waveform.rmsPositive = rmsPositive;
        waveform.rmsNegative = rmsNegative;
        return waveform;
    }

    public double[] getRMSPositive() {
        return dataToSamples(rmsPositive);
    }

    public double[] getRMSNegative() {
        return dataToSamples(rmsNegative);
    }

    public double[] getPeakPositive() {
        return dataToSamples(peakPositive);
    }

    public double[] getPeakNegative() {
        return dataToSamples(peakNegative);
    }

    private double[] dataToSamples(byte[] data) {
        try {
            JSONArray jsonArray = new JSONArray(new String(data));
            double[] peakPositiveList = new double[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                peakPositiveList[i] = jsonArray.getDouble(i);
            }
            return peakPositiveList;
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(LC, "Could not extract json array from byte array");
            return new double[0];
        }
    }
}