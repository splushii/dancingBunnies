package se.splushii.dancingbunnies.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.backend.APIClient;
import se.splushii.dancingbunnies.jobs.Jobs;
import se.splushii.dancingbunnies.jobs.LibrarySyncWorker;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.export.SchemaValidator;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.PlaylistStorage;
import se.splushii.dancingbunnies.ui.ConfirmationDialogFragment;
import se.splushii.dancingbunnies.ui.TimePreference;
import se.splushii.dancingbunnies.ui.TimePreferenceDialogFragment;
import se.splushii.dancingbunnies.util.Util;

import static se.splushii.dancingbunnies.musiclibrary.MusicLibraryService.API_SRC_ID_REGEX;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        ConfirmationDialogFragment.Handler {
    private static final String LC = Util.getLogContext(SettingsActivityFragment.class);

    public static final long BACKEND_ID_INVALID = -1;
    public static final long BACKEND_ID_ANY = -2;

    private static final String BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_PREFIX = "prefix";
    private static final String BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_BACKEND_ID = "backendID";
    private static final String BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_API = "api";
    private static final String BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX = "suffix";
    private static final String BACKEND_CONFIG_PREF_KEY_DELIM = "/";

//    private String backendConfigPrefKeyPrefix;
//    private String backendConfigPrefKeyRegex;
    private Pattern backendConfigPrefKeyPattern;

    private String backendConfigAPIGeneral;
    private String backendConfigSuffixGeneralDelete;

    private String backendConfigAPISubsonic;
    private String backendConfigSuffixSubsonicUrl;
    private String backendConfigSuffixSubsonicUsr;
    private String backendConfigSuffixSubsonicPwd;

    private Preference subsonicPref;
    private Preference subsonicScheduledRefreshPref;
    private Preference subsonicRefreshPref;
    private CompletableFuture<Optional<String>> currentSubsonicHeartbeat;

    private Preference gitRefreshPref;

    private ListPreference newBackendPref;
    private MultiSelectListPreference libraryClearPref;
    private MultiSelectListPreference libraryClearPlaylistsPref;

    public SettingsActivityFragment() {
    }

    public static Bundle getSettings(Context context, String src) {
        if (!isSourceEnabled(context, src)) {
            return null;
        }
        String api = MusicLibraryService.getAPIFromSource(src);
        String apiInstanceID = MusicLibraryService.getAPIInstanceIDFromSource(src);
        switch (api) {
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
                return getSettingsForSubsonic(context, apiInstanceID);
            case MusicLibraryService.API_SRC_ID_GIT:
                return getSettingsForGit(context, apiInstanceID);
            default:
                return null;
        }
    }

//    // TODO: Remove
//    private static Bundle getSettingsForSubsonicOld(Context context, String apiInstanceID) {
//        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
//        Bundle settings = new Bundle();
//        settings.putString(
//                APIClient.SETTINGS_KEY_SUBSONIC_URL,
//                sharedPrefs.getString(context.getResources()
//                        .getString(R.string.pref_key_subsonic_url), "")
//        );
//        settings.putString(
//                APIClient.SETTINGS_KEY_SUBSONIC_USERNAME,
//                sharedPrefs.getString(context.getResources()
//                        .getString(R.string.pref_key_subsonic_usr), "")
//        );
//        settings.putString(
//                APIClient.SETTINGS_KEY_SUBSONIC_PASSWORD,
//                sharedPrefs.getString(context.getResources()
//                        .getString(R.string.pref_key_subsonic_pwd), "")
//        );
//        settings.putString(
//                APIClient.SETTINGS_KEY_GENERAL_TAG_DELIM,
//                sharedPrefs.getString(context.getResources()
//                        .getString(R.string.pref_key_subsonic_tag_delim), null)
//        );
//        return settings;
//    }

    private static Bundle getSettingsForSubsonic(Context context, String apiInstanceID) {
        long backendID = getBackendConfigIDFromAPIInstanceID(context, apiInstanceID);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Bundle settings = new Bundle();
        Log.e(LC, "bake: " + backendID);
        settings.putString(
                APIClient.SETTINGS_KEY_SUBSONIC_URL,
                sharedPrefs.getString(getBackendConfigPrefKey(
                        context,
                        backendID,
                        Util.getString(context, R.string.pref_backend_config_api_subsonic),
                        R.string.pref_key_backend_config_suffix_subsonic_url
                ), "")
        );
        settings.putString(
                APIClient.SETTINGS_KEY_SUBSONIC_USERNAME,
                sharedPrefs.getString(getBackendConfigPrefKey(
                        context,
                        backendID,
                        Util.getString(context, R.string.pref_backend_config_api_subsonic),
                        R.string.pref_key_backend_config_suffix_subsonic_usr
                ), "")
        );
        settings.putString(
                APIClient.SETTINGS_KEY_SUBSONIC_PASSWORD,
                sharedPrefs.getString(getBackendConfigPrefKey(
                        context,
                        backendID,
                        Util.getString(context, R.string.pref_backend_config_api_subsonic),
                        R.string.pref_key_backend_config_suffix_subsonic_pwd
                ), "")
        );
        settings.putString(
                APIClient.SETTINGS_KEY_GENERAL_TAG_DELIM,
                sharedPrefs.getString(getBackendConfigPrefKey(
                        context,
                        backendID,
                        Util.getString(context, R.string.pref_backend_config_api_general),
                        R.string.pref_key_backend_config_suffix_general_tag_delim
                ), "")
        );
        return settings;
    }

    private static Bundle getSettingsForGit(Context context, String apiInstanceID) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Bundle settings = new Bundle();
        // TODO: CONTINUE
//        settings.putString(
//                APIClient.SETTINGS_KEY_GIT_URL,
//                sharedPrefs.getString(context.getResources()
//                        .getString(R.string.pref_key_git_url), "")
//        );
        return settings;
    }

    @Override
    public void onStart() {
        super.onStart();
        // TODO: heartbeat all backends
//        int preferenceBackendID = 0;
//        heartbeatSubsonicOld(preferenceBackendID);
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
        backendConfigPrefKeyPattern = getBackendConfigPrefKeyPattern(requireContext());
        backendConfigAPIGeneral = getResources().getString(R.string.pref_backend_config_api_general);
        backendConfigSuffixGeneralDelete = getResources().getString(R.string.pref_key_backend_config_suffix_general_delete);
        backendConfigAPISubsonic = getResources().getString(R.string.pref_backend_config_api_subsonic);
        backendConfigSuffixSubsonicUrl = getResources().getString(R.string.pref_key_backend_config_suffix_subsonic_url);
        backendConfigSuffixSubsonicUsr = getResources().getString(R.string.pref_key_backend_config_suffix_subsonic_usr);
        backendConfigSuffixSubsonicPwd = Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_subsonic_pwd);
        onCreateGlobal(savedInstanceState);
//        onCreateSubsonic(savedInstanceState);
//        onCreateGit(savedInstanceState);
        onCreateAbout(savedInstanceState);
    }

    private void onCreateAbout(Bundle savedInstanceState) {
        String aboutKey = getResources().getString(R.string.pref_key_about);
        findPreference(aboutKey).setOnPreferenceClickListener(preference -> {
            // Add license info to libraries where it's not auto-detected
            // TODO: Add missing license info for JGit, its deps, etc.
            // TODO: Double check all licenses (GPL? No license?)
            // TODO: Maybe group by the license, as with Apache 2.0
            HashMap<String, HashMap<String, String>> libModMap = new HashMap<>();
            String fieldLicName = Libs.LibraryFields.LICENSE_NAME.name();
            String fieldLicWeb = Libs.LibraryFields.LICENSE_WEBSITE.name();
            for (String libID: new String[] {
                    "org_apache_lucene__lucene_core",
                    "org_apache_lucene__lucene_analyzers_common",
                    "org_apache_lucene__lucene_queries",
                    "org_apache_lucene__lucene_queryparser",
                    "org_apache_lucene__lucene_sandbox"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(fieldLicName, "Apache Version 2.0");
                libMods.put(fieldLicWeb, "http://www.apache.org/licenses/LICENSE-2.0");
                libModMap.put(libID, libMods);
            }
            for (String libID: new String[] {
                    "gitlogo"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(fieldLicName, "Attribution 3.0 Unported (CC BY 3.0)");
                libMods.put(fieldLicWeb, "https://creativecommons.org/licenses/by/3.0/");
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
                    .withFields(R.string.class.getFields())
                    .withLibraryModification(libModMap)
                    .start(requireContext());
            return false;
        });
    }

    private void onCreateGlobal(Bundle savedInstanceState) {
        String newBackendKey = getResources().getString(R.string.pref_key_backend_new);
        newBackendPref = findPreference(newBackendKey);
        newBackendPref.setOnPreferenceClickListener(preference -> {
            newBackendPref.setEntries(new String[] {
                    MusicLibraryService.API_SRC_NAME_SUBSONIC,
                    MusicLibraryService.API_SRC_NAME_GIT
            });
            newBackendPref.setEntryValues(new String[]{
                    MusicLibraryService.API_SRC_ID_SUBSONIC,
                    MusicLibraryService.API_SRC_ID_GIT
            });
            newBackendPref.setValue(null);
            return false;
        });

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
    }

//    private void onCreateGit(Bundle savedInstanceState) {
//        String gitRefreshKey = getResources().getString(R.string.pref_key_git_refresh);
//        gitRefreshPref = findPreference(gitRefreshKey);
//        gitRefreshPref.setOnPreferenceClickListener(preference -> {
//            SchemaValidator.validatePlaylist(requireContext(), getResources().openRawResource(R.raw.test_playlist));
//            SchemaValidator.validatePlaylist(requireContext(), getResources().openRawResource(R.raw.test_playlist_smart));
//            long preferenceBackendID = 0;
//            LibrarySyncWorker.runNow(
//                    requireContext(),
//                    MusicLibraryService.API_SRC_ID_GIT,
//                    preferenceBackendID,
//                    R.string.pref_key_git_refresh,
//                    R.string.pref_key_git_refresh_last_sync,
//                    R.string.pref_key_git_refresh_enabled,
//                    R.string.pref_key_git_refresh_time
//            );
//            return false;
//        });
//        setRefreshLastSyncSummary(
//                R.string.pref_key_subsonic_refresh,
//                R.string.pref_key_subsonic_refresh_last_sync
//        );
//    }

//    private void onCreateSubsonic(Bundle savedInstanceState) {
//        String subsonicPrefKey = getResources().getString(R.string.pref_key_subsonic);
//        subsonicPref = findPreference(subsonicPrefKey);
//
//        setupTextPref(R.string.pref_key_subsonic_url);
//        setupTextPref(R.string.pref_key_subsonic_usr);
//        setupTextPref(
//                R.string.pref_key_subsonic_pwd,
//                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
//        );
//        setupTextPref(
//                R.string.pref_key_subsonic_tag_delim,
//                v -> Util.isValidRegex(v) ? v : "(Not a valid regular expression!)"
//        );
//
//        String subsonicScheduledRefreshKey = getResources().getString(R.string.pref_key_subsonic_refresh_enabled);
//        subsonicScheduledRefreshPref = findPreference(subsonicScheduledRefreshKey);
//
//        String subsonicRefreshKey = getResources().getString(R.string.pref_key_subsonic_refresh);
//        subsonicRefreshPref = findPreference(subsonicRefreshKey);
//        subsonicRefreshPref.setOnPreferenceClickListener(preference -> {
//            long preferenceBackendID = 0;
//            LibrarySyncWorker.runNow(
//                    requireContext(),
//                    MusicLibraryService.API_SRC_ID_SUBSONIC,
//                    preferenceBackendID,
//                    R.string.pref_key_subsonic_refresh,
//                    R.string.pref_key_subsonic_refresh_last_sync,
//                    R.string.pref_key_subsonic_refresh_enabled,
//                    R.string.pref_key_subsonic_refresh_time
//            );
//            return false;
//        });
//        setRefreshLastSyncSummary(
//                R.string.pref_key_subsonic_refresh,
//                R.string.pref_key_subsonic_refresh_last_sync
//        );
//    }

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
                    for (WorkInfo workInfo: workInfos) {
                        Data progressData = workInfo.getProgress();
                        String status = progressData.getString(LibrarySyncWorker.DATA_KEY_STATUS);
                        int refreshPrefKey = progressData.getInt(LibrarySyncWorker.DATA_KEY_REFRESH_PREF_KEY, 0);
                        int refreshLastSyncPrefKey = progressData.getInt(LibrarySyncWorker.DATA_KEY_REFRESH_LAST_SYNC_PREF_KEY, 0);
                        if (status == null) {
                            setRefreshLastSyncSummary(refreshPrefKey, refreshLastSyncPrefKey);
                        } else {
                            setRefreshProgress(refreshPrefKey, status);
                        }
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
        renderBackendPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()));
    }

    private void setRefreshProgress(int refreshPrefKey, String status) {
        if (refreshPrefKey <= 0 || status == null) {
            return;
        }
        Preference refreshPref = findPreference(getResources().getString(refreshPrefKey));
        if (refreshPref != null) {
            refreshPref.setSummary(status);
        }
    }

    private void setRefreshLastSyncSummary(int refreshPrefKey, int refreshLastSyncPrefKey) {
        if (refreshPrefKey <= 0 || refreshLastSyncPrefKey <= 0) {
            return;
        }
        Preference refreshPref = findPreference(getResources().getString(refreshPrefKey));
        if (refreshPref == null) {
            return;
        }
        long lastRefresh = LibrarySyncWorker.getLastRefresh(
                requireContext(),
                refreshLastSyncPrefKey
        );
        refreshPref.setSummary(lastRefresh > 0L ? "Last refresh: " + new Date(lastRefresh) : "");
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
        if (key.equals(getResources().getString(R.string.pref_key_backend_new))) {
            String api = newBackendPref.getValue();
            if (api != null) {
                createBackendConfig(sp, api);
            }
        } else if (key.startsWith(getResources().getString(R.string.pref_key_backend_config_prefix))) {
            Log.e(LC, "backend config changed: " + key);
            Matcher matcher = backendConfigPrefKeyPattern.matcher(key);
            if (matcher.matches()) {
                long backendID = getBackendConfigID(matcher);
                String api = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_API);
                String suffix = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX);
                if (backendConfigAPIGeneral.equals(api)) {
                    // API: General
                    if (backendConfigSuffixGeneralDelete.equals(suffix)) {
                        deleteBackendConfig(key, false);
                    } else if (Util.getString(
                            requireContext(),
                            R.string.pref_key_backend_config_suffix_general_refresh_time
                    ).equals(suffix)
                            || Util.getString(
                            requireContext(),
                            R.string.pref_key_backend_config_suffix_general_refresh_enabled
                    ).equals(suffix)) {
                        Log.e(LC, "REFRESH YO");
                        // TODO: Implement
//                        LibrarySyncWorker.requeue(
//                                requireContext(),
//                                MusicLibraryService.API_SRC_ID_SUBSONIC,
//                                backendID,
//                                R.string.pref_key_subsonic_refresh,
//                                R.string.pref_key_subsonic_refresh_last_sync,
//                                R.string.pref_key_subsonic_refresh_enabled,
//                                R.string.pref_key_subsonic_refresh_time
//                        );
                    }
                } else if (backendConfigAPISubsonic.equals(api)) {
                    // API: Subsonic
                    if (backendConfigSuffixSubsonicUrl.equals(suffix)
                            || backendConfigSuffixSubsonicUsr.equals(suffix)
                            || backendConfigSuffixSubsonicPwd.equals(suffix)) {
                        updateTextPref(key, sp);
                        enableSubsonicAuthenticatedPrefs(backendID, false);
                        heartbeatSubsonic(backendID);
                    }
                }
            }
//        } else if (key.equals(getResources().getString(R.string.pref_key_subsonic_url))
//                || key.equals(getResources().getString(R.string.pref_key_subsonic_usr))
//                || key.equals(getResources().getString(R.string.pref_key_subsonic_pwd))
//                || key.equals(getResources().getString(R.string.pref_key_subsonic_tag_delim))) {
//            long preferenceBackendID = 0;
//            updateTextPref(key, sp);
//            enableSubsonicAuthenticatedPrefs(false);
//            heartbeatSubsonicOld(preferenceBackendID);
//        } else if (key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_time))
//                || key.equals(getResources().getString(R.string.pref_key_subsonic_refresh_enabled))) {
//            long preferenceBackendID = 0;
//            LibrarySyncWorker.requeue(
//                    requireContext(),
//                    MusicLibraryService.API_SRC_ID_SUBSONIC,
//                    preferenceBackendID,
//                    R.string.pref_key_subsonic_refresh,
//                    R.string.pref_key_subsonic_refresh_last_sync,
//                    R.string.pref_key_subsonic_refresh_enabled,
//                    R.string.pref_key_subsonic_refresh_time
//            );
        } else if (key.equals(getResources().getString(R.string.pref_key_library_clear))) {
            for (String src: libraryClearPref.getValues()) {
                CompletableFuture.runAsync(() -> MetaStorage.getInstance(requireContext()).clearAll(src));
            }
        } else if (key.equals(getResources().getString(R.string.pref_key_library_clear_playlists))) {
            for (String src: libraryClearPlaylistsPref.getValues()) {
                CompletableFuture.runAsync(() -> PlaylistStorage.getInstance(requireContext()).clearAll(src));
            }
        } else {
            Log.e(LC, "onSharedPreferenceChanged: Unhandled key: " + key);
        }
    }

    private static Pattern getBackendConfigPrefKeyPattern(Context context) {
        String backendConfigPrefKeyPrefix = Util.getString(context, R.string.pref_key_backend_config_prefix);
        String backendConfigPrefKeyRegex = ""
                + "^(?<" + BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_PREFIX + ">" + backendConfigPrefKeyPrefix + ")"
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + "(?<" + BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_BACKEND_ID + ">\\d+)"
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + "(?<" + BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_API + ">" + API_SRC_ID_REGEX + ")"
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + "(?<" + BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX + ">.*)";
        return Pattern.compile(backendConfigPrefKeyRegex);
    }

    private void createBackendConfig(SharedPreferences sp, String api) {
        long preferenceBackendID = getNextBackendConfigID(sp);
        sp.edit()
                .putString(
                        getBackendConfigPrefKey(
                                preferenceBackendID,
                                backendConfigAPIGeneral,
                                R.string.pref_key_backend_config_suffix_general_api
                        ),
                        api
                )
                .apply();
        renderBackendPreferences(sp);
    }

    private void deleteBackendConfig(String prefKey, boolean confirmed) {
        if (!confirmed) {
            ConfirmationDialogFragment.showDialog(
                    this,
                    prefKey,
                    getResources().getString(R.string.pref_backend_config_general_delete_dialog_title),
                    getResources().getString(R.string.pref_backend_config_general_delete_dialog_message)
            );
            return;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = sp.edit();
        long backendID = getBackendConfigID(prefKey);
        sp.getAll()
                .keySet()
                .stream()
                .filter(key -> matchesBackendConfigPrefKey(key, backendID, null, null))
                .forEach(editor::remove);
        editor.apply();
        PreferenceGroup backendsPrefGroup =
                findPreference(getResources().getString(R.string.pref_key_backend));
        backendsPrefGroup.removePreferenceRecursively(getBackendConfigPrefKey(
                backendID,
                backendConfigAPIGeneral,
                R.string.pref_key_backend_config_suffix_general_api
        ));
    }

    private void renderBackendPreferences(SharedPreferences sp) {
        PreferenceGroup backendsPrefGroup =
                findPreference(getResources().getString(R.string.pref_key_backend));
        // Add backend preference to UI
        for (Map.Entry<Long, String> e : getBackendConfigIDs(sp)
                .entrySet()) {
            long preferenceBackendID = e.getKey();
            String api = e.getValue();
            if (backendsPrefGroup.findPreference(getBackendConfigPrefKey(
                    preferenceBackendID,
                    backendConfigAPIGeneral,
                    R.string.pref_key_backend_config_suffix_general_id
            )) != null) {
                Log.e(LC, "api: " + api + " id: " + preferenceBackendID + " already exists");
                continue;
            }
            switch (api) {
                case MusicLibraryService.API_SRC_ID_SUBSONIC:
//        <PreferenceCategory
//                android:title="@string/pref_subsonic"
//                android:summary="subsonic.splushii.se"
//                android:icon="@drawable/api_sub_icon"
//                        >
                    String prefKeyAPI = getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_api
                    );
                    String prefKeyID = getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_id
                    );
                    PreferenceCategory prefCat = new PreferenceCategory(requireContext());
                    backendsPrefGroup.addPreference(prefCat);
                    prefCat.setKey(prefKeyAPI);
                    prefCat.setTitle(R.string.pref_backend_config_subsonic_name);
                    prefCat.setIcon(R.drawable.api_sub_icon);
                    prefCat.setSummary(sp.getString(prefKeyID, ""));
//            <CheckBoxPreference
//                android:key="@string/pref_key_subsonic"
//                android:title="Enabled"
//                android:defaultValue="false" />
                    String prefKeyEnabled = getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_enabled
                    );
                    CheckBoxPreference prefEnabled = new CheckBoxPreference(requireContext());
                    prefCat.addPreference(prefEnabled);
                    prefEnabled.setKey(prefKeyEnabled);
                    prefEnabled.setTitle(R.string.pref_backend_config_general_enabled);
                    prefEnabled.setChecked(sp.getBoolean(prefKeyEnabled, false));
//            <EditTextPreference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_id"
//                android:title="@string/pref_subsonic_id"
//                android:dialogTitle="Customize backend ID"
//                android:dialogMessage="The default backend ID does not need to be changed unless there is a special use case. Leave blank to use the default backend ID.\n\nWhen changing the backend ID you must:\n- Manually clear the local data for the old backend ID.\n- Refresh the backend for changes to take effect."
//                        />
                    EditTextPreference prefID = new EditTextPreference(requireContext());
                    prefCat.addPreference(prefID);
                    prefID.setDependency(prefKeyEnabled);
                    prefID.setKey(prefKeyID);
                    prefID.setTitle(R.string.pref_backend_config_general_id);
                    prefID.setSummary(sp.getString(prefKeyID, ""));
                    prefID.setDialogTitle(R.string.pref_backend_config_general_id_dialog_title);
                    prefID.setDialogMessage(R.string.pref_backend_config_general_id_dialog_message);
//            <EditTextPreference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_url"
//                android:title="@string/pref_subsonic_url" />
                    EditTextPreference prefUrl = new EditTextPreference(requireContext());
                    prefCat.addPreference(prefUrl);
                    prefUrl.setDependency(prefKeyEnabled);
                    prefUrl.setKey(getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPISubsonic,
                            R.string.pref_key_backend_config_suffix_subsonic_url
                    ));
                    prefUrl.setTitle(R.string.pref_backend_config_subsonic_url);
                    prefUrl.setDialogTitle(R.string.pref_backend_config_subsonic_url);
//            <EditTextPreference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_usr"
//                android:title="@string/pref_subsonic_usr" />
                    EditTextPreference prefUsr = new EditTextPreference(requireContext());
                    prefCat.addPreference(prefUsr);
                    prefUsr.setDependency(prefKeyEnabled);
                    prefUsr.setKey(getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPISubsonic,
                            R.string.pref_key_backend_config_suffix_subsonic_usr
                    ));
                    prefUsr.setTitle(R.string.pref_backend_config_subsonic_usr);
                    prefUsr.setDialogTitle(R.string.pref_backend_config_subsonic_usr);
//            <EditTextPreference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_pwd"
//                android:title="@string/pref_subsonic_pwd" />
                    EditTextPreference prefPwd = new EditTextPreference(requireContext());
                    prefCat.addPreference(prefPwd);
                    prefPwd.setDependency(prefKeyEnabled);
                    prefPwd.setKey(getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPISubsonic,
                            R.string.pref_key_backend_config_suffix_subsonic_pwd
                    ));
                    prefPwd.setTitle(R.string.pref_backend_config_subsonic_pwd);
                    prefPwd.setDialogTitle(R.string.pref_backend_config_subsonic_pwd);
//            <EditTextPreference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_tag_delim"
//                android:title="@string/pref_subsonic_tag_delim" />
                    EditTextPreference prefTagDelim = new EditTextPreference(requireContext());
                    prefCat.addPreference(prefTagDelim);
                    prefTagDelim.setDependency(prefKeyEnabled);
                    prefTagDelim.setKey(getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_tag_delim
                    ));
                    prefTagDelim.setTitle(R.string.pref_backend_config_general_tag_delim);
                    prefTagDelim.setDialogTitle(R.string.pref_backend_config_general_tag_delim);
//            <CheckBoxPreference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_refresh_enabled"
//                android:title="Enable scheduled refresh"
//                android:enabled="false"
//                android:defaultValue="false" />
                    String prefKeyRefreshEnabled = getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_refresh_enabled
                    );
                    CheckBoxPreference prefRefreshEnabled = new CheckBoxPreference(requireContext());
                    prefCat.addPreference(prefRefreshEnabled);
                    prefRefreshEnabled.setDependency(prefKeyEnabled);
                    prefRefreshEnabled.setKey(prefKeyRefreshEnabled);
                    prefRefreshEnabled.setTitle(R.string.pref_backend_config_general_refresh_enabled);
                    prefRefreshEnabled.setChecked(sp.getBoolean(prefKeyRefreshEnabled, false));
//            <se.splushii.dancingbunnies.ui.TimePreference
//                android:dependency="@string/pref_key_subsonic_refresh_enabled"
//                android:key="@string/pref_key_subsonic_refresh_time"
//                android:title="@string/pref_subsonic_refresh_time" />
                    TimePreference prefRefreshTime = new TimePreference(requireContext());
                    prefCat.addPreference(prefRefreshTime);
                    prefRefreshTime.setDependency(prefKeyRefreshEnabled);
                    prefRefreshTime.setKey(getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_refresh_time
                    ));
                    prefRefreshTime.setTitle(R.string.pref_backend_config_general_refresh_time);
                    prefRefreshTime.setDialogTitle(R.string.pref_backend_config_general_refresh_time);
//            <Preference
//                android:dependency="@string/pref_key_subsonic"
//                android:key="@string/pref_key_subsonic_refresh"
//                android:enabled="false"
//                android:title="@string/pref_subsonic_refresh"
//                        />
                    String prefKeyRefresh = getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_refresh
                    );
                    Preference prefRefresh = new Preference(requireContext());
                    prefCat.addPreference(prefRefresh);
                    prefRefresh.setDependency(prefKeyEnabled);
                    prefRefresh.setKey(prefKeyRefresh);
                    prefRefresh.setTitle(R.string.pref_backend_config_general_refresh);
                    prefRefresh.setOnPreferenceClickListener(preference -> {
                        onSharedPreferenceChanged(sp, prefKeyRefresh);
                        return true;
                    });
//            <Preference
//                android:key="@string/pref_key_DELETE"
//                android:enabled="false"
//                android:title="@string/pref_subsonic_refresh"
//                        />
                    String prefKeyDelete = getBackendConfigPrefKey(
                            preferenceBackendID,
                            backendConfigAPIGeneral,
                            R.string.pref_key_backend_config_suffix_general_delete
                    );
                    Preference prefDelete = new Preference(requireContext());
                    prefCat.addPreference(prefDelete);
                    prefDelete.setKey(prefKeyDelete);
                    prefDelete.setTitle(R.string.pref_backend_config_general_delete);
                    prefDelete.setOnPreferenceClickListener(preference -> {
                        onSharedPreferenceChanged(sp, prefKeyDelete);
                        return true;
                    });
//        </PreferenceCategory>

                    break;
                case MusicLibraryService.API_SRC_ID_GIT:
                    // TODO: Implement
                default:
                    Log.e(LC, "createNewBackendPreferences: API not implemented: " + api);
            }
        }
    }

    static private String getBackendConfigPrefKey(Context context,
                                                  long backendID,
                                                  String api,
                                                  int prefKeySuffixID) {
        Resources r = context.getResources();
        String prefKeyPrefix = r.getString(R.string.pref_key_backend_config_prefix);
        String prefKeyAPIPart;
        switch (api) {
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
                prefKeyAPIPart = r.getString(R.string.pref_backend_config_api_subsonic);
                break;
            case MusicLibraryService.API_SRC_ID_GIT:
                prefKeyAPIPart = r.getString(R.string.pref_backend_config_api_git);
                break;
            default:
                prefKeyAPIPart = r.getString(R.string.pref_backend_config_api_general);
                break;
        }
        String prefKeySuffix = r.getString(prefKeySuffixID);
        return prefKeyPrefix
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + backendID
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + prefKeyAPIPart
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + prefKeySuffix;
    }

    private String getBackendConfigPrefKey(long backendID, String api, int prefKeySuffixID) {
        return getBackendConfigPrefKey(requireContext(), backendID, api, prefKeySuffixID);
    }

    private static long getBackendConfigID(Context context, String prefKey) {
        return getBackendConfigID(getBackendConfigPrefKeyPattern(context), prefKey);
    }

    private static long getBackendConfigID(Pattern backendConfigPrefKeyPattern, String prefKey) {
        Matcher backendConfigPrefKeyMatcher = backendConfigPrefKeyPattern.matcher(prefKey);
        if (!backendConfigPrefKeyMatcher.matches()) {
            return BACKEND_ID_INVALID;
        }
        return getBackendConfigID(backendConfigPrefKeyMatcher);
    }

    private long getBackendConfigID(String prefKey) {
        return getBackendConfigID(backendConfigPrefKeyPattern, prefKey);
    }

    private static long getBackendConfigID(Matcher matcher) {
        String backendID = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_BACKEND_ID);
        if (backendID == null) {
            return BACKEND_ID_INVALID;
        }
        try {
            return Long.parseLong(backendID);
        } catch (NumberFormatException e) {
            return BACKEND_ID_INVALID;
        }
    }

    private Map<Long, String> getBackendConfigIDs(SharedPreferences sp) {
        return sp.getAll()
                .entrySet()
                .stream()
                .filter(e -> {
                    String generalAPISuffix = getResources().getString(R.string.pref_key_backend_config_suffix_general_api);
                    return matchesBackendConfigPrefKey(
                            e.getKey(),
                            BACKEND_ID_ANY,
                            backendConfigAPIGeneral,
                            generalAPISuffix
                    );
                })
                .collect(Collectors.toMap(
                        e -> getBackendConfigID(e.getKey()),
                        e -> (String) e.getValue()
                ));
    }

    private static long getBackendConfigIDFromAPIInstanceID(Context context, String apiInstanceID) {
        Log.e(LC, "get backendID for: " + apiInstanceID);
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getAll()
                .entrySet()
                .stream()
                .filter(e -> {
                    boolean matches = matchesBackendConfigPrefKey(
                            context,
                            e.getKey(),
                            BACKEND_ID_ANY,
                            Util.getString(context, R.string.pref_backend_config_api_general),
                            Util.getString(context, R.string.pref_key_backend_config_suffix_general_api)
                    );
                    if (matches) {
                        Log.e(LC, "first match key: " + e.getKey());
                    }
                    return matches;
                })
                .filter(e -> {
                    long backendID = getBackendConfigID(context, e.getKey());
                    String api = (String) e.getValue();
                    String currentSrc = getSourceFromConfig(context, api, backendID);
                    String currentAPIInstanceID = MusicLibraryService.getAPIInstanceIDFromSource(currentSrc);
                    boolean matches = apiInstanceID.equals(currentAPIInstanceID);
                    Log.e(LC, "second match: " + matches + " key: " + e.getKey() + " apiID: " + currentAPIInstanceID);
                    return matches;
                })
                .map(e -> getBackendConfigID(context, e.getKey()))
                .findFirst()
                .orElse(BACKEND_ID_INVALID);
    }

    private static boolean matchesBackendConfigPrefKey(Context context,
                                                       String prefKey,
                                                       long backendID,
                                                       String api,
                                                       String suffix) {
        return matchesBackendConfigPrefKey(
                getBackendConfigPrefKeyPattern(context),
                prefKey,
                backendID,
                api,
                suffix
        );
    }

    private static boolean matchesBackendConfigPrefKey(Pattern backendConfigPrefKeyPattern,
                                                       String prefKey,
                                                       long backendID,
                                                       String api,
                                                       String suffix) {
        Matcher matcher = backendConfigPrefKeyPattern.matcher(prefKey);
        if (!matcher.matches()) {
            return false;
        }
        long prefKeyID = getBackendConfigID(matcher);
        String prefKeyAPI = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_API);
        String prefKeySuffix = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX);
        return (backendID == BACKEND_ID_ANY || backendID == prefKeyID)
                && (api == null || api.equals(prefKeyAPI))
                && (suffix == null || suffix.equals(prefKeySuffix));
    }

    private boolean matchesBackendConfigPrefKey(String prefKey,
                                                long backendID,
                                                String api,
                                                String suffix) {
        return matchesBackendConfigPrefKey(
                backendConfigPrefKeyPattern,
                prefKey,
                backendID,
                api,
                suffix
        );
    }

        private synchronized long getNextBackendConfigID(SharedPreferences sp) {
        return Util.getNextIDs(sp, getResources().getString(R.string.pref_key_backend_id_counter), 1);
    }

    private void updateTextPref(String key, SharedPreferences sp) {
        EditTextPreference etp = findPreference(key);
        etp.setSummary(maskIfSensitive(key, sp.getString(key, "")));
        etp.setText(sp.getString(key, ""));
    }

    private String maskIfSensitive(String key, String value) {
        if (key.endsWith(getResources().getString(R.string.pref_key_backend_config_suffix_subsonic_pwd))) {
            return "********";
        }
        return value;
    }

    private static boolean isSourceEnabled(Context context, String src) {
        return isSourceEnabled(
                context,
                MusicLibraryService.getAPIFromSource(src),
                MusicLibraryService.getAPIInstanceIDFromSource(src)
        );
    }

    public static boolean isSourceEnabled(Context context, String api, String apiInstanceID) {
        long backendID = getBackendConfigIDFromAPIInstanceID(context, apiInstanceID);
//        int apiPrefKey;
//        switch (api) {
//            case MusicLibraryService.API_SRC_ID_SUBSONIC:
//                apiPrefKey = R.string.pref_key_subsonic;
//                break;
//            case MusicLibraryService.API_SRC_ID_GIT:
//                apiPrefKey = R.string.pref_key_git;
//                break;
//            default:
//                return false;
//        }
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                getBackendConfigPrefKey(
                        context,
                        backendID,
                        Util.getString(context, R.string.pref_backend_config_api_general),
                        R.string.pref_key_backend_config_suffix_general_enabled
                ),
                false
        );
    }

    public static String getSourceFromConfig(Context context, String api, long backendID) {
        if (api == null) {
            return null;
        }
        switch (api) {
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
                return getSubsonicSource(context, backendID);
            case MusicLibraryService.API_SRC_ID_GIT:
                return getGitSource(context, backendID);
            case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
            default:
                return null;
        }
    }

    private static String getGitSource(Context context, long preferenceBackendID) {
        // TODO: Implement
        return MusicLibraryService.getAPISource(MusicLibraryService.API_SRC_ID_GIT, "test");
    }

//    // TODO: Support multiple configurations using the same api. Use preferenceBackendID.
//    // TODO: Remove
//    private static String getSubsonicSourceOld(Context context, long preferenceBackendID) {
//        String api = MusicLibraryService.API_SRC_ID_SUBSONIC;
//        String url = PreferenceManager.getDefaultSharedPreferences(context).getString(
//                context.getResources().getString(R.string.pref_key_subsonic_url),
//                null
//        );
//        if (url == null) {
//            Log.e(LC, "requeue: Source URL is null for api \"" + api + "\"");
//            return null;
//        }
//        return MusicLibraryService.getAPISource(
//                api,
//                Util.getHostFromUrl(url)
//        );
//    }

    private static String getSubsonicSource(Context context, long backendID) {
        String api = MusicLibraryService.API_SRC_ID_SUBSONIC;
        // TODO: Get Source from "Backend ID" Pref first, fallback to using URL
        String url = PreferenceManager.getDefaultSharedPreferences(context).getString(
                getBackendConfigPrefKey(
                        context,
                        backendID,
                        Util.getString(context, R.string.pref_backend_config_api_subsonic),
                        R.string.pref_key_backend_config_suffix_subsonic_url
                ),
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

//    // TODO: Remove
//    private void heartbeatSubsonicOld(long preferenceBackendID) {
//        if (currentSubsonicHeartbeat != null) {
//            currentSubsonicHeartbeat.complete(Optional.of("interrupted"));
//        }
//        subsonicPref.setSummary("Testing connection...");
//        String src = getSubsonicSourceOld(requireContext(), preferenceBackendID);
//        APIClient apiClient = APIClient.getAPIClient(requireContext(), src);
//        Log.e(LC, "apiClient: " + apiClient);
//        if (apiClient == null) {
//            enableSubsonicAuthenticatedPrefs(false);
//            subsonicPref.setSummary("");
//            return;
//        }
//        currentSubsonicHeartbeat = apiClient.heartbeat();
//        currentSubsonicHeartbeat.thenAccept(error -> {
//                    if (error.isPresent()) {
//                        String errorMsg = error.get();
//                        Log.e(LC, "error: " + errorMsg);
//                        subsonicPref.setSummary(errorMsg);
//                        enableSubsonicAuthenticatedPrefs(false);
//                    } else {
//                        subsonicPref.setSummary("Successfully authenticated");
//                        enableSubsonicAuthenticatedPrefs(true);
//                    }
//                });
//    }

    // TODO: Also heartbeat when "enabled" toggle is changed. Clear on "off". Healthcheck on "on".
    private void heartbeatSubsonic(long backendID) {
        // TODO: Use one future per backend
        if (currentSubsonicHeartbeat != null) {
            currentSubsonicHeartbeat.complete(Optional.of("interrupted"));
        }
        Preference enabledPref = findPreference(getBackendConfigPrefKey(
                backendID,
                backendConfigAPIGeneral,
                R.string.pref_key_backend_config_suffix_general_enabled
        ));
        if (enabledPref != null) {
            enabledPref.setSummary("Testing connection...");
        }
        String src = getSubsonicSource(requireContext(), backendID);
        APIClient apiClient = APIClient.getAPIClient(requireContext(), src);
        Log.e(LC, "apiClient: " + apiClient);
        if (apiClient == null) {
            enableSubsonicAuthenticatedPrefs(backendID, false);
            if (enabledPref != null) {
                enabledPref.setSummary("");
            }
            return;
        }
        currentSubsonicHeartbeat = apiClient.heartbeat();
        currentSubsonicHeartbeat.thenAccept(error -> {
            if (error.isPresent()) {
                String errorMsg = error.get();
                Log.e(LC, "error: " + errorMsg);
                if (enabledPref != null) {
                    enabledPref.setSummary(errorMsg);
                }
                enableSubsonicAuthenticatedPrefs(backendID, false);
            } else {
                if (enabledPref != null) {
                    enabledPref.setSummary("Successfully authenticated");
                }
                enableSubsonicAuthenticatedPrefs(backendID, true);
            }
        });
    }

//    // TODO: Remove
//    private void enableSubsonicAuthenticatedPrefs(boolean enabled) {
//        subsonicRefreshPref.setEnabled(enabled);
//        subsonicScheduledRefreshPref.setEnabled(enabled);
//    }

    private void enableSubsonicAuthenticatedPrefs(long backendID, boolean enabled) {
        Preference refreshPref = findPreference(getBackendConfigPrefKey(
                backendID,
                backendConfigAPIGeneral,
                R.string.pref_key_backend_config_suffix_general_refresh
        ));
        if (refreshPref != null) {
            refreshPref.setEnabled(enabled);
        }
        Preference scheduledRefreshPref = findPreference(getBackendConfigPrefKey(
                backendID,
                backendConfigAPIGeneral,
                R.string.pref_key_backend_config_suffix_general_refresh_time
        ));
        if (scheduledRefreshPref != null) {
            scheduledRefreshPref.setEnabled(enabled);
        }
    }

    @Override
    public void onConfirmationDialogClicked(String id, boolean confirmed) {
        if (confirmed && matchesBackendConfigPrefKey(
                id,
                BACKEND_ID_ANY,
                backendConfigAPIGeneral,
                backendConfigSuffixGeneralDelete
        )) {
            deleteBackendConfig(id, true);
        }
    }
}
