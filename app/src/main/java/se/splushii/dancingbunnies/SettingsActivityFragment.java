package se.splushii.dancingbunnies;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;

import java.util.Collections;
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
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.jobs.Jobs;
import se.splushii.dancingbunnies.jobs.LibrarySyncWorker;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.ui.TimePreference;
import se.splushii.dancingbunnies.ui.TimePreferenceDialogFragment;
import se.splushii.dancingbunnies.util.Util;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LC = Util.getLogContext(SettingsActivityFragment.class);

    public static final int BACKEND_ID_INVALID = -1;

    private Preference subsonicPref;
    private Preference subsonicScheduledRefreshPref;
    private Preference subsonicRefreshPref;
    private CompletableFuture<Optional<String>> currentSubsonicHeartbeat;
    private MultiSelectListPreference libraryClearPref;
    private MultiSelectListPreference libraryClearPlaylistsPref;

    public SettingsActivityFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        int preferenceBackendID = 0;
        heartbeatSubsonic(preferenceBackendID);
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
            int preferenceBackendID = 0;
            LibrarySyncWorker.runNow(
                    requireContext(),
                    MusicLibraryService.API_ID_SUBSONIC,
                    preferenceBackendID,
                    R.string.pref_key_subsonic_refresh_last_sync,
                    R.string.pref_key_subsonic_refresh_enabled,
                    R.string.pref_key_subsonic_refresh_time
            );
            return false;
        });
        setLastRefreshSummary();

        String libraryClearKey = getResources().getString(R.string.pref_key_library_clear);
        libraryClearPref = findPreference(libraryClearKey);
        libraryClearPref.setOnPreferenceClickListener(preference -> {
            libraryClearPref.setValues(Collections.emptySet());
            return false;
        });

        String libraryClearPlaylistsKey = getResources().getString(R.string.pref_key_library_clear_playlists);
        libraryClearPlaylistsPref = findPreference(libraryClearPlaylistsKey);
        libraryClearPlaylistsPref.setOnPreferenceClickListener(preference -> {
            libraryClearPlaylistsPref.setValues(Collections.emptySet());
            return false;
        });

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
                .getWorkInfosByTagLiveData(Jobs.WORK_NAME_LIBRARY_SYNC_TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    List<String> statuses = workInfos.stream()
                            .filter(workInfo -> !workInfo.getState().isFinished())
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
        MetaStorage.getInstance(requireContext()).getSources()
                .observe(getViewLifecycleOwner(), values -> {
                    String[] valuesArray = values.toArray(new String[0]);
                    libraryClearPref.setEntries(valuesArray);
                    libraryClearPref.setEntryValues(valuesArray);
                });
        PlaylistStorage.getInstance(requireContext()).getSources()
                .observe(getViewLifecycleOwner(), values -> {
                    String[] valuesArray = values.toArray(new String[0]);
                    libraryClearPlaylistsPref.setEntries(valuesArray);
                    libraryClearPlaylistsPref.setEntryValues(valuesArray);
                });
    }

    private void setLastRefreshSummary() {
        long lastRefresh = LibrarySyncWorker.getLastRefresh(
                requireContext(),
                R.string.pref_key_subsonic_refresh_last_sync
        );
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
            int preferenceBackendID = 0;
            updateTextPref(key, sp);
            enableSubsonicAuthenticatedPrefs(false);
            heartbeatSubsonic(preferenceBackendID);
        } else if (key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_time))
                || key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_enabled))) {
            int preferenceBackendID = 0;
            LibrarySyncWorker.requeue(
                    requireContext(),
                    MusicLibraryService.API_ID_SUBSONIC,
                    preferenceBackendID,
                    R.string.pref_key_subsonic_refresh_last_sync,
                    R.string.pref_key_subsonic_refresh_enabled,
                    R.string.pref_key_subsonic_refresh_time
            );
        } else if (key.equals(getResources().getString(R.string.pref_key_library_clear))) {
            for (String src: libraryClearPref.getValues()) {
                CompletableFuture.runAsync(() -> MetaStorage.getInstance(requireContext()).clearAll(src));
            }
        } else if (key.equals(getResources().getString(R.string.pref_key_library_clear_playlists))) {
            for (String src: libraryClearPlaylistsPref.getValues()) {
                CompletableFuture.runAsync(() -> PlaylistStorage.getInstance(requireContext()).clearAll(src));
            }
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

    public static String getSourceFromConfig(Context context, String api, int preferenceBackendID) {
        if (api == null) {
            return null;
        }
        switch (api) {
            case MusicLibraryService.API_ID_SUBSONIC:
                return getSubsonicSource(context, preferenceBackendID);
            case MusicLibraryService.API_ID_DANCINGBUNNIES:
            default:
                return null;
        }
    }

    // TODO: Support multiple configurations using the same api. Use preferenceBackendID.
    private static String getSubsonicSource(Context context, int preferenceBackendID) {
        String api = MusicLibraryService.API_ID_SUBSONIC;
        String url = PreferenceManager.getDefaultSharedPreferences(context).getString(
                context.getResources().getString(R.string.pref_key_subsonic_url),
                null
        );
        if (url == null) {
            Log.e(LC, "requeue: Source URL is null for api \"" + api + "\"");
            return null;
        }
        return MusicLibraryService.getAPISource(
                api,
                Util.getHostFromUrl(url)
        );
    }

    private void heartbeatSubsonic(int preferenceBackendID) {
        if (currentSubsonicHeartbeat != null) {
            currentSubsonicHeartbeat.complete(Optional.of("interrupted"));
        }
        subsonicPref.setSummary("Testing connection...");
        String src = getSubsonicSource(requireContext(), preferenceBackendID);
        APIClient apiClient = APIClient.getAPIClient(requireContext(), src);
        Log.e(LC, "apiClient: " + apiClient);
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
