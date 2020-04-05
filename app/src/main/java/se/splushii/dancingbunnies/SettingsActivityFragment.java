package se.splushii.dancingbunnies;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.work.WorkManager;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.jobs.Jobs;
import se.splushii.dancingbunnies.jobs.LibrarySyncWorker;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.ui.TimePreference;
import se.splushii.dancingbunnies.ui.TimePreferenceDialogFragment;
import se.splushii.dancingbunnies.util.Util;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LC = Util.getLogContext(SettingsActivityFragment.class);
    private Preference subsonicPref;
    private Preference subsonicScheduledRefreshPref;
    private Preference subsonicRefreshPref;
    private CompletableFuture<Optional<String>> currentSubsonicHeartbeat;

    public SettingsActivityFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        heartbeatSubsonic();
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
    public void onStop() {
        super.onStop();
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

        String subsonicPrefKey = getResources().getString(R.string.pref_key_subsonic);
        subsonicPref = findPreference(subsonicPrefKey);

        setupTextPref(R.string.pref_key_subsonic_url);
        setupTextPref(R.string.pref_key_subsonic_usr);
        setupTextPref(
                R.string.pref_key_subsonic_pwd,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        setupTextPref(
                R.string.pref_key_subsonic_tag_delim,
                v -> Util.isValidRegex(v) ? v : "(Not a valid regular expression!)"
        );

        String subsonicScheduledRefreshKey = getResources().getString(R.string.pref_key_subsonic_refresh_enabled);
        subsonicScheduledRefreshPref = findPreference(subsonicScheduledRefreshKey);

        String subsonicRefreshKey = getResources().getString(R.string.pref_key_subsonic_refresh);
        subsonicRefreshPref = findPreference(subsonicRefreshKey);
        subsonicRefreshPref.setOnPreferenceClickListener(preference -> {
            LibrarySyncWorker.runNow(requireContext());
            return false;
        });
        setLastRefreshSummary();

        String aboutKey = getResources().getString(R.string.pref_key_about);
        findPreference(aboutKey).setOnPreferenceClickListener(preference -> {
            // Add license info to libraries where it's not auto-detected
            HashMap<String, HashMap<String, String>> libModMap = new HashMap<>();
            for (String libID: new String[] {
                    "org_apache_lucene__lucene_core",
                    "org_apache_lucene__lucene_analyzers_common",
                    "org_apache_lucene__lucene_queries",
                    "org_apache_lucene__lucene_queryparser",
                    "org_apache_lucene__lucene_sandbox"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(
                        Libs.LibraryFields.LICENSE_NAME.name(),
                        "Apache Version 2.0"
                );
                libMods.put(
                        Libs.LibraryFields.LICENSE_WEBSITE.name(),
                        "http://www.apache.org/licenses/LICENSE-2.0"
                );
                libModMap.put(libID, libMods);
            }
            new LibsBuilder()
                    .withAboutIconShown(true)
                    .withAboutVersionShown(true)
                    .withAboutVersionShownCode(true)
                    .withAboutVersionShownName(true)
                    .withAboutAppName(getResources().getString(R.string.app_name))
                    .withLicenseShown(true)
                    .withLicenseDialog(true)
                    .withLibraryModification(libModMap)
                    .start(requireContext());
            return false;
        });
    }

    private void setupTextPref(int prefKeyResource) {
        setupTextPref(prefKeyResource, null);
    }

    private void setupTextPref(int prefKeyResource, int inputType) {
        EditTextPreference textPreference = setupTextPref(prefKeyResource, null);
        textPreference.setOnBindEditTextListener(editText -> editText.setInputType(inputType));
    }

    private EditTextPreference setupTextPref(int prefKeyResource, Function<String, String> valueMapper) {
        String prefKey = getResources().getString(prefKeyResource);
        EditTextPreference textPreference = findPreference(prefKey);
        SharedPreferences settings = getPreferenceManager().getSharedPreferences();
        String value = settings.getString(prefKey, "");
        if (valueMapper != null) {
            value = valueMapper.apply(value);
        }
        textPreference.setSummary(maskIfSensitive(prefKey, value));
        return textPreference;
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
                            subsonicRefreshPref.setSummary(status);
                        }
                    } else if (statuses.size() > 1) {
                        subsonicRefreshPref.setSummary("Multiple refresh jobs running...");
                    } else {
                        setLastRefreshSummary();
                    }
                });
    }

    private void setLastRefreshSummary() {
        long lastRefresh = LibrarySyncWorker.getLastRefresh(requireContext());
        subsonicRefreshPref.setSummary(lastRefresh > 0L ?
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
            updateTextPref(key, sp);
            enableSubsonicAuthenticatedPrefs(false);
            heartbeatSubsonic();
        } else if (key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_time))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_enabled))) {
            LibrarySyncWorker.requeue(requireContext());
        }
    }

    private void updateTextPref(String key, SharedPreferences sp) {
        EditTextPreference etp = findPreference(key);
        etp.setSummary(maskIfSensitive(key, sp.getString(key, "")));
        etp.setText(sp.getString(key, ""));
    }

    private String maskIfSensitive(String key, String value) {
        if (key.equals(getResources().getString(R.string.pref_key_subsonic_pwd))) {
            return "********";
        }
        return value;
    }

    private void heartbeatSubsonic() {
        if (currentSubsonicHeartbeat != null) {
            currentSubsonicHeartbeat.complete(Optional.of("interrupted"));
        }
        subsonicPref.setSummary("Testing connection...");
        APIClient apiClient = APIClient.getAPIClient(
                requireContext(),
                MusicLibraryService.API_ID_SUBSONIC
        );
        if (apiClient == null) {
            enableSubsonicAuthenticatedPrefs(false);
            subsonicPref.setSummary("");
            return;
        }
        currentSubsonicHeartbeat = apiClient.heartbeat();
        currentSubsonicHeartbeat.thenAccept(error -> {
                    if (error.isPresent()) {
                        String errorMsg = error.get();
                        Log.e(LC, "error: " + errorMsg);
                        subsonicPref.setSummary(errorMsg);
                        enableSubsonicAuthenticatedPrefs(false);
                    } else {
                        subsonicPref.setSummary("Successfully authenticated");
                        enableSubsonicAuthenticatedPrefs(true);
                    }
                });
    }

    private void enableSubsonicAuthenticatedPrefs(boolean enabled) {
        subsonicRefreshPref.setEnabled(enabled);
        subsonicScheduledRefreshPref.setEnabled(enabled);
    }
}
