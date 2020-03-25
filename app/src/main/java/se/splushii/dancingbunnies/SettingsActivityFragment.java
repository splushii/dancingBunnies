package se.splushii.dancingbunnies;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.work.WorkManager;
import se.splushii.dancingbunnies.jobs.Jobs;
import se.splushii.dancingbunnies.jobs.LibrarySyncWorker;
import se.splushii.dancingbunnies.ui.TimePreference;
import se.splushii.dancingbunnies.ui.TimePreferenceDialogFragment;
import se.splushii.dancingbunnies.util.Util;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LC = Util.getLogContext(SettingsActivityFragment.class);
    private Preference SSrefresh;

    public SettingsActivityFragment() {}

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
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
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dialogFragment = null;
        if (preference instanceof TimePreference) {
            dialogFragment = TimePreferenceDialogFragment.newInstance(preference.getKey());
        }
        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(requireFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences settings = getPreferenceManager().getSharedPreferences();
        EditTextPreference SSurl = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_url));
        SSurl.setSummary(settings.getString(getResources()
                .getString(R.string.pref_key_subsonic_url), ""));
        EditTextPreference SSusr = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_usr));
        SSusr.setSummary(settings.getString(getResources()
                .getString(R.string.pref_key_subsonic_usr), ""));
        EditTextPreference SSpwd = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_pwd));
        SSpwd.setSummary(settings.contains(getResources()
                .getString(R.string.pref_key_subsonic_pwd)) ?
                "********" : ""
        );
        EditTextPreference SSTagDelim = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_tag_delim));
        String SSTagDelimValue = settings.getString(getResources().getString(R.string.pref_key_subsonic_tag_delim), "");
        SSTagDelim.setSummary(Util.isValidRegex(SSTagDelimValue) ? SSTagDelimValue : "(Not a valid regular expression!)");
        SSrefresh = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_refresh));
        SSrefresh.setOnPreferenceClickListener(preference -> {
            Log.d(LC, "refresh onClick!");
            LibrarySyncWorker.runNow(requireContext());
            return false;
        });
        setLastRefreshSummary();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(Jobs.WORK_NAME_LIBRARY_SYNC)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    List<String> statuses = workInfos.stream()
                            .map(workInfo -> workInfo.getProgress().getString("status"))
                            .collect(Collectors.toList());
                    if (statuses.size() == 1) {
                        String status = statuses.get(0);
                        if (status == null) {
                            setLastRefreshSummary();
                        } else {
                            SSrefresh.setSummary(status);
                        }
                    } else if (statuses.size() > 1) {
                        SSrefresh.setSummary("Multiple refresh jobs running...");
                    } else {
                        setLastRefreshSummary();
                    }
                });
    }

    private void setLastRefreshSummary() {
        long lastRefresh = LibrarySyncWorker.getLastRefresh(requireContext());
        SSrefresh.setSummary(lastRefresh > 0L ?
                "Last refresh: " + new Date(lastRefresh) : ""
        );
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key.equals(getResources().getString(R.string.pref_key_subsonic_url))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_usr))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_pwd))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_tag_delim))) {
            EditTextPreference etp = findPreference(key);
            etp.setSummary(sp.getString(key, ""));
            etp.setText(sp.getString(key, ""));
        } else if (key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_time))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_enabled))) {
            LibrarySyncWorker.requeue(requireContext());
        }
    }
}
