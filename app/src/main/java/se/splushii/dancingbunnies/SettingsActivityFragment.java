package se.splushii.dancingbunnies;

import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener{

    public SettingsActivityFragment() {
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences settings = getPreferenceManager().getSharedPreferences();
        EditTextPreference SSurl = (EditTextPreference) findPreference(getResources()
                .getString(R.string.pref_key_subsonic_url));
        SSurl.setSummary(settings.getString(getResources()
                .getString(R.string.pref_key_subsonic_url), ""));
        EditTextPreference SSusr = (EditTextPreference) findPreference(getResources()
                .getString(R.string.pref_key_subsonic_usr));
        SSusr.setSummary(settings.getString(getResources()
                .getString(R.string.pref_key_subsonic_usr), ""));
        EditTextPreference SSpwd = (EditTextPreference) findPreference(getResources()
                .getString(R.string.pref_key_subsonic_pwd));
        SSpwd.setSummary(settings.getString(getResources()
                .getString(R.string.pref_key_subsonic_pwd), ""));
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        view.setBackgroundColor(getResources().getColor(android.R.color.white));
        return view;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key.equals(getResources().getString(R.string.pref_key_subsonic_url))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_usr))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_pwd))) {
            EditTextPreference etp = (EditTextPreference) findPreference(key);
            etp.setSummary(sp.getString(key, ""));
            etp.setText(sp.getString(key, ""));
        }
    }
}
