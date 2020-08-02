package se.splushii.dancingbunnies.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TimePicker;

import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceDialogFragmentCompat;

public class TimePreferenceDialogFragment extends PreferenceDialogFragmentCompat {
    private TimePicker timePicker;

    public static DialogFragment newInstance(String key) {
        final TimePreferenceDialogFragment fragment = new TimePreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        timePicker = new TimePicker(context);
        return (timePicker);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        TimePreference pref = (TimePreference) getPreference();
        timePicker.setIs24HourView(true);
        timePicker.setHour(pref.getHour());
        timePicker.setMinute(pref.getMinute());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            TimePreference pref = (TimePreference) getPreference();
            pref.setHour(timePicker.getHour());
            pref.setMinute(timePicker.getMinute());
            String value = TimePreference.timeToString(pref.getHour(), pref.getMinute());
            if (pref.callChangeListener(value)) {
                pref.persistStringValue(value);
            }
        }
    }
}