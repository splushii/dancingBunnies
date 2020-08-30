package se.splushii.dancingbunnies.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import java.util.Locale;

import androidx.preference.DialogPreference;

public class TimePreference extends DialogPreference {
    public static final String DEFAULT = "00:00";
    private int hour = 0;
    private int minute = 0;

    public TimePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public TimePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimePreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setSummaryProvider(preference -> {
            String value = getPersistedString(timeToString(0, 0));
            return timeToString(parseHour(value), parseMinute(value));
        });
    }

    public static int parseHour(String value) {
        try {
            String[] time = value.split(":");
            return (Integer.parseInt(time[0]));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int parseMinute(String value) {
        try {
            String[] time = value.split(":");
            return (Integer.parseInt(time[1]));
        } catch (Exception e) {
            return 0;
        }
    }

    static String timeToString(int h, int m) {
        return String.format(Locale.getDefault(), "%02d", h)
                + ":" + String.format(Locale.getDefault(), "%02d", m);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String value = defaultValue == null ?
                timeToString(0, 0) : defaultValue.toString();
        hour = parseHour(value);
        minute = parseMinute(value);
    }

    void persistStringValue(String value) {
        persistString(value);
        notifyChanged();
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public int getHour() {
        return parseHour(getPersistedString(timeToString(0, 0)));
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public int getMinute() {
        return parseMinute(getPersistedString(timeToString(0, 0)));
    }

}