package se.splushii.dancingbunnies.ui.settings;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.arch.core.util.Function;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
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
import se.splushii.dancingbunnies.backend.DummyAPIClient;
import se.splushii.dancingbunnies.jobs.Jobs;
import se.splushii.dancingbunnies.jobs.LibrarySyncWorker;
import se.splushii.dancingbunnies.jobs.TransactionsWorker;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.musiclibrary.QueryLeaf;
import se.splushii.dancingbunnies.search.Searcher;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.storage.TransactionStorage;
import se.splushii.dancingbunnies.ui.ConfirmationDialogFragment;
import se.splushii.dancingbunnies.ui.transactions.TransactionsDialogFragment;
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
    private static final String BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_CONFIG_GROUP = "confgroup";
    private static final String BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX = "suffix";
    private static final String BACKEND_CONFIG_PREF_KEY_DELIM = "/";

    // If changed, update backend_sync_values_array in arrays.xml
    private static final String BACKEND_SYNC_LIBRARY = "sync_library";
    private static final String BACKEND_REINDEX_LIBRARY = "reindex_library";
    private static final String BACKEND_SYNC_PLAYLISTS = "sync_playlists";

    private Pattern backendConfigPrefKeyPattern;

    private final HashMap<Long, CompletableFuture<Void>> currentHeartbeatMap = new HashMap<>();
    private final HashMap<Long, Boolean> backendSyncWorkingMap = new HashMap<>();

    private final HashMap<Long, LiveData<Integer>> backendIDToNumEntriesMap = new HashMap<>();
    private final Set<String> backendIDToNumIndexedSubIDs = new HashSet<>();
    private final HashMap<Long, LiveData<Integer>> backendIDToNumIndexedMap = new HashMap<>();
    private final HashMap<Long, LiveData<Integer>> backendIDToNumPlaylistsMap = new HashMap<>();

    private boolean resetSyncValues = false;

    private int pendingTransactions = 0;
    private String transactionsWorkMsg;

    private ListPreference newBackendPref;
    private MultiSelectListPreference libraryClearPref;
    private MultiSelectListPreference libraryClearPlaylistsPref;
    private MultiSelectListPreference libraryClearSearchIndexPref;
    private Preference showTransactionsPref;

    public SettingsActivityFragment() {}

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

    private static Bundle getSettingsForSubsonic(Context context, String apiInstanceID) {
        long backendID = getBackendConfigIDFromAPIInstanceID(context, apiInstanceID);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Bundle settings = new Bundle();
        settings.putString(
                APIClient.SETTINGS_KEY_SUBSONIC_URL,
                sharedPrefs.getString(getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_subsonic_url), "")
        );
        settings.putString(
                APIClient.SETTINGS_KEY_SUBSONIC_USERNAME,
                sharedPrefs.getString(getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_subsonic_usr), "")
        );
        settings.putString(
                APIClient.SETTINGS_KEY_SUBSONIC_PASSWORD,
                sharedPrefs.getString(getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_subsonic_pwd), "")
        );
        settings.putString(
                APIClient.SETTINGS_KEY_DB_TAG_DELIM,
                sharedPrefs.getString(getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_db_tag_delim), "")
        );
        return settings;
    }

    private static Bundle getSettingsForGit(Context context, String apiInstanceID) {
        long backendID = getBackendConfigIDFromAPIInstanceID(context, apiInstanceID);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Bundle settings = GitRepoPreference.getSettingsBundle(
                sharedPrefs,
                getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_git_repo)
        );
        settings.putString(
                APIClient.SETTINGS_KEY_GIT_BRANCH,
                sharedPrefs.getString(getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_git_branch), "")
        );
        return settings;
    }

    public static boolean getScheduledSyncEnabled(Context context, long preferenceBackendID) {
        String scheduledSyncEnabledPrefKey = SettingsActivityFragment.getBackendConfigPrefKey(
                context,
                preferenceBackendID,
                R.string.pref_key_backend_config_suffix_db_sync_scheduled_enabled
        );
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(scheduledSyncEnabledPrefKey, false);
    }

    public static String getScheduledSyncTime(Context context, long preferenceBackendID) {
        String scheduledSyncTimePrefKey = SettingsActivityFragment.getBackendConfigPrefKey(
                context,
                preferenceBackendID,
                R.string.pref_key_backend_config_suffix_db_sync_scheduled_time
        );
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(scheduledSyncTimePrefKey, TimePreference.DEFAULT);
    }

    public static void setSyncCompleteLastRun(Context context, long backendID) {
        setSyncCompleteLastRun(context, backendID, R.string.pref_key_backend_config_suffix_db_sync_complete_last_run);
    }

    private static void setSyncCompleteLastRun(Context context, long backendID, int prefKeyResource) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext());
        String prefKey = SettingsActivityFragment.getBackendConfigPrefKey(
                context,
                backendID,
                prefKeyResource
        );
        sharedPreferences.edit()
                .putLong(prefKey, System.currentTimeMillis())
                .apply();
    }

    private long getSyncCompleteLastRun(long backendID) {
        String prefKey = getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_sync_complete_last_run);
        return PreferenceManager.getDefaultSharedPreferences(requireContext()).getLong(prefKey, -1);
    }

    @Override
    public void onStart() {
        super.onStart();
        getBackendConfigIDs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
                .forEach(this::heartbeatAPI);
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
        } else if (preference instanceof GitRepoPreference) {
            dialogFragment = GitRepoPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof MultiSelectListPreference) {
            if (matchesBackendConfigPrefKey(
                    preference.getKey(),
                    BACKEND_ID_ANY,
                    null,
                    Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_sync))
            ) {
                long backendID = getBackendConfigID(preference.getKey());
                if (isSyncWorking(backendID)) {
                    // Do not display dialog if sync is already running
                    // The sync will instead be cancelled
                    return;
                }
            }
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
        onCreateGlobal(savedInstanceState);
        onCreateAbout(savedInstanceState);
    }

    private void onCreateAbout(Bundle savedInstanceState) {
        String aboutKey = getResources().getString(R.string.pref_key_about);
        findPreference(aboutKey).setOnPreferenceClickListener(preference -> {
            // Add license info to libraries where it's not auto-detected
            HashMap<String, HashMap<String, String>> libModMap = new HashMap<>();
            String fieldLicName = Libs.LibraryFields.LICENSE_NAME.name();
            String fieldLicWeb = Libs.LibraryFields.LICENSE_WEBSITE.name();
            // Apache 2.0
            for (String libID: new String[] {
                    "org_apache_lucene__lucene_core",
                    "org_apache_lucene__lucene_analyzers_common",
                    "org_apache_lucene__lucene_queries",
                    "org_apache_lucene__lucene_queryparser",
                    "org_apache_lucene__lucene_sandbox",
                    "commons_io__commons_io",
                    "org_apache_commons__commons_lang3",
                    "com_fasterxml_jackson_dataformat__jackson_dataformat_yaml"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(fieldLicName, "Apache Version 2.0");
                libMods.put(fieldLicWeb, "http://www.apache.org/licenses/LICENSE-2.0");
                libModMap.put(libID, libMods);
            }
            // MIT
            for (String libID: new String[] {
                    "org_bouncycastle__bcpkix_jdk15on",
                    "org_bouncycastle__bcprov_jdk15on"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(fieldLicName, "MIT");
                libMods.put(fieldLicWeb, "https://opensource.org/licenses/MIT");
                libModMap.put(libID, libMods);
            }
            // BSD
            for (String libID: new String[] {
                    "com_jcraft__jsch",
                    "com_jcraft__jzlib"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(fieldLicName, "BSD 3-Clause");
                libMods.put(fieldLicWeb, "https://opensource.org/licenses/BSD-3-Clause");
                libModMap.put(libID, libMods);
            }
            // EDL
            for (String libID: new String[] {
                    "org_eclipse_jgit__org_eclipse_jgit"
            }) {
                HashMap<String, String> libMods = new HashMap<>();
                libMods.put(fieldLicName, "EDL");
                libMods.put(fieldLicWeb, "https://www.eclipse.org/org/documents/edl-v10.html");
                libModMap.put(libID, libMods);
            }
            // CC BY 3.0
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

        String libraryClearSearchIndexKey = getResources().getString(R.string.pref_key_library_clear_search_index);
        libraryClearSearchIndexPref = findPreference(libraryClearSearchIndexKey);
        libraryClearSearchIndexPref.setOnPreferenceClickListener(preference -> {
            libraryClearSearchIndexPref.setValues(Collections.emptySet());
            return false;
        });

        String showTransactionsKey = getResources().getString(R.string.pref_key_library_show_transactions);
        showTransactionsPref = findPreference(showTransactionsKey);
        showTransactionsPref.setOnPreferenceClickListener(preference -> {
            onSharedPreferenceChanged(
                    getPreferenceManager().getSharedPreferences(),
                    showTransactionsKey
            );
            return false;
        });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        WorkManager.getInstance(requireContext())
                .getWorkInfosByTagLiveData(Jobs.WORK_NAME_LIBRARY_SYNC_TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    for (WorkInfo workInfo: workInfos) {
                        long backendID = LibrarySyncWorker.getBackendIDFromTags(workInfo.getTags());
                        if (backendID == BACKEND_ID_INVALID) {
                            continue;
                        }
                        Data data;
                        String status;
                        switch (workInfo.getState()) {
                            case ENQUEUED:
                                status = "Sync enqueued.";
                                setSyncWorking(backendID, true);
                                break;
                            case RUNNING:
                                data = workInfo.getProgress();
                                status = data.getString(LibrarySyncWorker.DATA_KEY_STATUS);
                                setSyncWorking(backendID, true);
                                break;
                            case SUCCEEDED:
                                setSyncCompleteLastRunSummary(backendID);
                                setSyncWorking(backendID, false);
                                continue;
                            case FAILED:
                                data = workInfo.getOutputData();
                                status = "Sync failed (" + workInfo.getRunAttemptCount() + " attempts): "
                                        + data.getString(LibrarySyncWorker.DATA_KEY_STATUS);
                                setSyncWorking(backendID, false);
                                break;
                            case BLOCKED:
                                status = "Sync blocked.";
                                setSyncWorking(backendID, true);
                                break;
                            case CANCELLED:
                                status = "Sync cancelled.";
                                setSyncWorking(backendID, false);
                                break;
                            default:
                                continue;
                        }
                        setSyncCompleteStatus(backendID, status);
                    }
                });
        WorkManager.getInstance(requireContext())
                .getWorkInfosByTagLiveData(Jobs.WORK_NAME_TRANSACTIONS_TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    StringBuilder msg = new StringBuilder();
                    for (WorkInfo workInfo: workInfos) {
                        if (workInfo != null &&
                                (workInfo.getState() == WorkInfo.State.ENQUEUED ||
                                        workInfo.getState() == WorkInfo.State.FAILED)) {
                            String workMsg = workInfo.getOutputData()
                                    .getString(TransactionsWorker.DATA_KEY_STATUS);
                            if (workMsg != null) {
                                if (msg.length() > 0) {
                                    msg.append(", ");
                                }
                                msg.append(workMsg);
                            }
                        }
                    }
                    transactionsWorkMsg = msg.toString();
                    setShowTransactionsSummary(pendingTransactions, transactionsWorkMsg);
                });
        MetaStorage.getInstance(requireContext())
                .getTrackSources()
                .observe(getViewLifecycleOwner(), sources -> {
                    String[] valuesArray = sources.toArray(new String[0]);
                    libraryClearPref.setEntries(valuesArray);
                    libraryClearPref.setEntryValues(valuesArray);
                    backendIDToNumEntriesMap.clear();
                    getBackendConfigIDs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
                            .forEach(backendID -> {
                                String src = getSourceFromConfig(requireContext(), backendID);
                                LiveData<Integer> liveData = sources.contains(src) ?
                                        MetaStorage.getInstance(requireContext()).getNumTracks(
                                                null,
                                                new QueryLeaf(
                                                        Meta.FIELD_SPECIAL_ENTRY_SRC,
                                                        QueryLeaf.Op.EQUALS,
                                                        src,
                                                        false
                                                ),
                                                false
                                        )
                                        :
                                        new MutableLiveData<>(0);
                                backendIDToNumEntriesMap.put(backendID, liveData);
                            });
                    observeBackendIDToNumEntriesMap();
                });
        if (!Searcher.getInstance().initialize(requireContext())) {
            Log.e(LC, "Could not initialize searcher");
        }
        Searcher.getInstance()
                .searchFieldValuesSubscribe(
                        Searcher.SUB_ID_SEARCH_FIELD_VALUES_SETTINGS_FRAGMENT,
                        Meta.FIELD_SPECIAL_ENTRY_SRC + ":*",
                        Meta.FIELD_SPECIAL_ENTRY_SRC
                )
                .observe(getViewLifecycleOwner(), sources -> {
                    String[] valuesArray = sources.toArray(new String[0]);
                    libraryClearSearchIndexPref.setEntries(valuesArray);
                    libraryClearSearchIndexPref.setEntryValues(valuesArray);
                    clearBackendIDToNumIndexedMap();
                    getBackendConfigIDs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
                            .forEach(backendID -> {
                                String src = getSourceFromConfig(requireContext(), backendID);
                                String subID = Long.toString(backendID);
                                backendIDToNumIndexedSubIDs.add(subID);
                                if (sources.contains(src)) {
                                    backendIDToNumIndexedMap.put(
                                            backendID,
                                            Searcher.getInstance().searchHitsSubscribe(
                                                    subID,
                                                    Meta.FIELD_SPECIAL_ENTRY_SRC + ":\"" + src + "\""
                                            )
                                    );
                                } else {
                                    backendIDToNumIndexedMap.put(
                                            backendID,
                                            new MutableLiveData<>(0)
                                    );
                                }
                            });
                    observeBackendIDToNumIndexedMap();
                });
        MetaStorage.getInstance(requireContext())
                .getPlaylistSources()
                .observe(getViewLifecycleOwner(), sources -> {
                    String[] valuesArray = sources.toArray(new String[0]);
                    libraryClearPlaylistsPref.setEntries(valuesArray);
                    libraryClearPlaylistsPref.setEntryValues(valuesArray);
                    backendIDToNumPlaylistsMap.clear();
                    getBackendConfigIDs(PreferenceManager.getDefaultSharedPreferences(requireContext()))
                            .forEach(backendID -> {
                                String src = getSourceFromConfig(requireContext(), backendID);
                                LiveData<Integer> liveData = sources.contains(src) ?
                                        MetaStorage.getInstance(requireContext())
                                                .getNumPlaylists(src)
                                        :
                                        new MutableLiveData<>(0);
                                backendIDToNumPlaylistsMap.put(backendID, liveData);
                            });
                    observeBackendIDToNumPlaylistsMap();
                });
        TransactionStorage.getInstance(requireContext())
                .getTransactions()
                .observe(getViewLifecycleOwner(), transactions -> {
                    pendingTransactions = transactions.size();
                    setShowTransactionsSummary(pendingTransactions, transactionsWorkMsg);
                });
        renderBackendPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()));
        setDivider(null);
    }

    private void setShowTransactionsSummary(int pendingTransactions, String transactionsWorkMsg) {
        String summary = "Pending transactions: " + pendingTransactions;
        if (transactionsWorkMsg != null && !transactionsWorkMsg.isEmpty()) {
            summary += "\nStatus:" + transactionsWorkMsg;
        }
        showTransactionsPref.setSummary(summary);
    }

    private void clearBackendIDToNumIndexedMap() {
        backendIDToNumIndexedSubIDs.forEach(subID ->
                Searcher.getInstance().searchHitsUnsubscribe(subID)
        );
        backendIDToNumIndexedMap.clear();
        backendIDToNumIndexedSubIDs.clear();
    }

    @Override
    public void onDestroy() {
        Searcher.getInstance().searchFieldValuesUnsubscribe(
                Searcher.SUB_ID_SEARCH_FIELD_VALUES_SETTINGS_FRAGMENT
        );
        clearBackendIDToNumIndexedMap();
        super.onDestroy();
    }

    private void observeBackendIDToNumEntriesMap() {
        for (long backendID: backendIDToNumEntriesMap.keySet()) {
            backendIDToNumEntriesMap.get(backendID).observe(
                    getViewLifecycleOwner(),
                    numEntries -> updateBackendAPIPreference(backendID)
            );
        }
    }

    private void observeBackendIDToNumPlaylistsMap() {
        for (long backendID: backendIDToNumPlaylistsMap.keySet()) {
            backendIDToNumPlaylistsMap.get(backendID).observe(
                    getViewLifecycleOwner(),
                    numEntries -> updateBackendAPIPreference(backendID)
            );
        }
    }

    private void observeBackendIDToNumIndexedMap() {
        for (long backendID: backendIDToNumIndexedMap.keySet()) {
            backendIDToNumIndexedMap.get(backendID).observe(
                    getViewLifecycleOwner(),
                    numEntries -> updateBackendAPIPreference(backendID)
            );
        }
    }

    private boolean isSyncWorking(long backendID) {
        Boolean working = backendSyncWorkingMap.getOrDefault(backendID, false);
        return working != null && working;
    }

    private void setSyncWorking(long backendID, boolean working) {
        String prefKey = getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_sync);
        Preference pref = findPreference(prefKey);
        if (!working) {
            backendSyncWorkingMap.remove(backendID);
        } else {
            backendSyncWorkingMap.put(backendID, true);
        }
        if (pref != null) {
            pref.setTitle(
                    working ? R.string.pref_backend_config_db_click_to_abort
                            : R.string.pref_backend_config_db_sync
            );
        }
    }

    private void setSyncCompleteStatus(long backendID, String status) {
        String prefKey = getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_sync);
        if (status == null) {
            return;
        }
        Preference pref = findPreference(prefKey);
        if (pref != null) {
            pref.setSummary(status);
        }
    }

    private void setSyncCompleteLastRunSummary(long backendID) {
        String prefKey = getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_sync);
        Preference pref = findPreference(prefKey);
        if (pref == null) {
            return;
        }
        long lastRun = getSyncCompleteLastRun(backendID);
        pref.setSummary(lastRun > 0L ? "Last run: " + new Date(lastRun) : "");
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
            Log.d(LC, "backend config changed: " + key);
            Matcher matcher = backendConfigPrefKeyPattern.matcher(key);
            if (matcher.matches()) {
                long backendID = getBackendConfigID(matcher);
                updateBackendAPIPreference(backendID);
                String configGroup = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_CONFIG_GROUP);
                String suffix = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX);
                if (suffix == null) {
                    return;
                }
                if (MusicLibraryService.API_SRC_ID_DANCINGBUNNIES.equals(configGroup)) {
                    // API: General dancingBunnies
                    if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_id))
                            || suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_api))
                            || suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_backend))
                            || suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_sync_complete_last_run))) {
                        // No-op
                    } else if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_enabled))) {
                        heartbeatAPI(backendID);
                    } else if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_delete))) {
                        deleteBackendConfig(key, false);
                    } else if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_sync_scheduled_time))
                            || suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_sync_scheduled_enabled))) {
                        LibrarySyncWorker.requeue(
                                requireContext(),
                                backendID,
                                true,
                                true,
                                true
                        );
                    } else if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_sync))) {
                        if (resetSyncValues) {
                            resetSyncValues = false;
                        } else {
                            MultiSelectListPreference syncPref = findPreference(key);
                            if (syncPref != null) {
                                Set<String> selected = syncPref.getValues();
                                boolean fetchLibrary = selected.contains(BACKEND_SYNC_LIBRARY);
                                boolean indexLibrary = selected.contains(BACKEND_REINDEX_LIBRARY);
                                boolean fetchPlaylists = selected.contains(BACKEND_SYNC_PLAYLISTS);
                                if (isSyncWorking(backendID)) {
                                    LibrarySyncWorker.cancel(requireContext(), backendID);
                                } else {
                                    LibrarySyncWorker.runNow(
                                            requireContext(),
                                            backendID,
                                            fetchLibrary,
                                            indexLibrary,
                                            fetchPlaylists
                                    );
                                }
                            }
                        }
                    } else if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_tag_delim))) {
                        updateTextPref(key, sp);
                    } else {
                        Log.e(LC, "onSharedPreferenceChanged: Unhandled key(" + configGroup + "): " + key);
                    }
                } else if (MusicLibraryService.API_SRC_ID_SUBSONIC.equals(configGroup)) {
                    // API: Subsonic
                    if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_subsonic_url))
                            || suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_subsonic_usr))
                            || suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_subsonic_pwd))) {
                        updateTextPref(key, sp);
                        enableAuthenticatedPrefs(backendID, false);
                        heartbeatAPI(backendID);
                    } else {
                        Log.e(LC, "onSharedPreferenceChanged: Unhandled key(" + configGroup + "): " + key);
                    }
                } else if (MusicLibraryService.API_SRC_ID_GIT.equals(configGroup)) {
                    // API: Git
                    if (suffix.equals(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_git_branch))) {
                        updateTextPref(key, sp);
                        enableAuthenticatedPrefs(backendID, false);
                        heartbeatAPI(backendID);
                    } else if (suffix.startsWith(Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_git_repo))) {
                        enableAuthenticatedPrefs(backendID, false);
                        heartbeatAPI(backendID);
                    } else {
                        Log.e(LC, "onSharedPreferenceChanged: Unhandled key(" + configGroup + "): " + key);
                    }
                }
            }
        } else if (key.equals(Util.getString(requireContext(), R.string.pref_key_library_clear))) {
            for (String src: libraryClearPref.getValues()) {
                CompletableFuture.runAsync(() -> MetaStorage.getInstance(requireContext())
                        .clearAllTracks(src))
                        .handle(Util::printFutureError);
            }
        } else if (key.equals(Util.getString(requireContext(), R.string.pref_key_library_clear_playlists))) {
            for (String src: libraryClearPlaylistsPref.getValues()) {
                CompletableFuture.runAsync(() -> MetaStorage.getInstance(requireContext())
                        .clearAllPlaylists(src))
                        .handle(Util::printFutureError);
            }
        } else if (key.equals(Util.getString(requireContext(), R.string.pref_key_library_clear_search_index))) {
            for (String src : libraryClearSearchIndexPref.getValues()) {
                CompletableFuture.runAsync(() -> MusicLibraryService.clearIndex(requireContext(), src, 10000))
                        .handle(Util::printFutureError);
            }
        } else if (key.equals(Util.getString(requireContext(), R.string.pref_key_library_show_transactions))) {
            TransactionsDialogFragment.showDialog(this);
        } else if (key.equals(Util.getString(requireContext(), R.string.pref_key_backend_id_counter))) {
            // No-op
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
                + "(?<" + BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_CONFIG_GROUP + ">" + API_SRC_ID_REGEX + ")"
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + "(?<" + BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX + ">.*)";
        return Pattern.compile(backendConfigPrefKeyRegex);
    }

    private void createBackendConfig(SharedPreferences sp, String api) {
        long preferenceBackendID = getNextBackendConfigID(sp);
        sp.edit()
                .putLong(
                        getBackendConfigPrefKey(preferenceBackendID, R.string.pref_key_backend_config_suffix_db_backend),
                        preferenceBackendID
                )
                .putString(
                        getBackendConfigPrefKey(preferenceBackendID, R.string.pref_key_backend_config_suffix_db_api),
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
                    getResources().getString(R.string.pref_backend_config_db_delete_dialog_title),
                    getResources().getString(R.string.pref_backend_config_db_delete_dialog_message)
            );
            return;
        }
        long backendID = getBackendConfigID(prefKey);
        PreferenceGroup backendsPrefGroup =
                findPreference(getResources().getString(R.string.pref_key_backend));
        if (backendsPrefGroup != null) {
            backendsPrefGroup.removePreferenceRecursively(getBackendConfigPrefKey(
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_backend
            ));
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = sp.edit();
        sp.getAll()
                .keySet()
                .stream()
                .filter(key -> matchesBackendConfigPrefKey(key, backendID, null, null))
                .forEach(editor::remove);
        editor.apply();

        LibrarySyncWorker.cancel(requireContext(), backendID);
        backendSyncWorkingMap.remove(backendID);

        cancelHeartbeatAPI(backendID);
    }

    private void renderBackendPreferences(SharedPreferences sp) {
        PreferenceGroup backendsPrefGroup = findPreference(getResources().getString(R.string.pref_key_backend));
        // Add backend preference to UI
        for (long backendID: getBackendConfigIDs(sp)) {
            String api = getBackendAPI(requireContext(), backendID);
            if (backendsPrefGroup.findPreference(getBackendConfigPrefKey(
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_id
            )) != null) {
                Log.d(LC, "Preference for api: " + api + " id: " + backendID + " already rendered. Skipping.");
                continue;
            }
            String prefKeyBackend = getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_backend);

            PreferenceCategory prefRootCat = new PreferenceCategory(requireContext());
            prefRootCat.setKey(prefKeyBackend);
            backendsPrefGroup.addPreference(prefRootCat);

            String prefKeyAPI = getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_api);
            IconTogglePreference prefAPI = new IconTogglePreference(requireContext());
            prefAPI.setToggleIcons(
                    R.drawable.ic_keyboard_arrow_down_black_24dp,
                    R.drawable.ic_keyboard_arrow_up_black_24dp
            );

            prefAPI.setKey(prefKeyAPI);
            prefRootCat.addPreference(prefAPI);
            prefAPI.setIcon(MusicLibraryService.getAPIIconResourceFromAPI(api));
            prefAPI.setWidgetLayoutResource(R.layout.preference_widget_dropdown_layout);

            PreferenceCategory prefSettingsCat = new PreferenceCategory(requireContext());
            prefRootCat.addPreference(prefSettingsCat);
            prefSettingsCat.setVisible(false);

            prefAPI.setOnPreferenceClickListener(preference -> {
                prefSettingsCat.setVisible(!prefSettingsCat.isVisible());
                return true;
            });

            Preference prefEnabled = addCheckPref(
                    sp,
                    prefSettingsCat,
                    null,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_enabled,
                    R.string.pref_backend_config_db_enabled,
                    true,
                    true
            );
            String prefKeyEnabled = prefEnabled.getKey();

            updateBackendAPIPreference(backendID);

            PreferenceCategory prefSettingsSpecificCat = new PreferenceCategory(requireContext());
            prefSettingsCat.addPreference(prefSettingsSpecificCat);
            String backendName = Util.getString(requireContext(), getBackendAPINameResource(api));
            prefSettingsSpecificCat.setTitle(backendName + " specific settings");

            switch (api) {
                case MusicLibraryService.API_SRC_ID_SUBSONIC:
                    renderSubsonicBackendPreferences(backendID, prefSettingsSpecificCat, prefKeyEnabled, sp);
                    break;
                case MusicLibraryService.API_SRC_ID_GIT:
                    renderGitBackendPreferences(backendID, prefSettingsSpecificCat, prefKeyEnabled, sp);
                    break;
            }

            PreferenceCategory prefSettingsGeneralCat = new PreferenceCategory(requireContext());
            prefSettingsCat.addPreference(prefSettingsGeneralCat);
            prefSettingsGeneralCat.setTitle("General backend settings");
            addTextPref(
                    sp,
                    prefSettingsGeneralCat,
                    prefKeyEnabled,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_id,
                    R.string.pref_backend_config_db_id,
                    true,
                    false,
                    R.string.pref_backend_config_db_id_dialog_title,
                    R.string.pref_backend_config_db_id_dialog_message,
                    true,
                    InputType.TYPE_CLASS_TEXT,
                    null,
                    null
            );

            addTextPref(
                    sp,
                    prefSettingsGeneralCat,
                    prefKeyEnabled,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_tag_delim,
                    R.string.pref_backend_config_db_tag_delim,
                    true,
                    true,
                    R.string.pref_backend_config_db_tag_delim,
                    0,
                    true,
                    InputType.TYPE_CLASS_TEXT,
                    ";\\s+",
                    value -> Util.isValidRegex(value) ? value : "(Not a valid regular expression!)"
            );

            Preference prefScheduledSyncEnabled = addCheckPref(
                    sp,
                    prefSettingsGeneralCat,
                    prefKeyEnabled,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_sync_scheduled_enabled,
                    R.string.pref_backend_config_db_sync_scheduled_enabled,
                    true,
                    true
            );
            String prefKeyScheduledSyncEnabled = prefScheduledSyncEnabled.getKey();

            TimePreference prefScheduledSyncTime = new TimePreference(requireContext());
            setupPrefBase(
                    prefScheduledSyncTime,
                    sp,
                    prefSettingsGeneralCat,
                    prefKeyScheduledSyncEnabled,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_sync_scheduled_time,
                    R.string.pref_backend_config_db_sync_scheduled_time,
                    true,
                    true
            );
            prefScheduledSyncTime.setDialogTitle(R.string.pref_backend_config_db_sync_scheduled_time);

            PreferenceCategory prefSettingsActionsCat = new PreferenceCategory(requireContext());
            prefSettingsCat.addPreference(prefSettingsActionsCat);
            prefSettingsActionsCat.setTitle("Actions");

            MultiSelectListPreference syncPref = new MultiSelectListPreference(requireContext());
            setupPrefBase(
                    syncPref,
                    sp,
                    prefSettingsActionsCat,
                    prefKeyEnabled,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_sync,
                    R.string.pref_backend_config_db_sync,
                    false,
                    true
            );
            APIClient apiClient = APIClient.getAPIClient(
                    requireContext(),
                    getSourceFromConfig(requireContext(), backendID)
            );
            List<CharSequence> syncPrefEntries = new ArrayList<>();
            List<CharSequence> syncPrefEntryValues = new ArrayList<>();
            if (apiClient != null) {
                if (apiClient.hasLibrary()) {
                    syncPrefEntries.add("Sync library");
                    syncPrefEntryValues.add(BACKEND_SYNC_LIBRARY);
                    syncPrefEntries.add("Reindex library");
                    syncPrefEntryValues.add(BACKEND_REINDEX_LIBRARY);
                }
                if (apiClient.hasPlaylists()) {
                    syncPrefEntries.add("Sync playlists");
                    syncPrefEntryValues.add(BACKEND_SYNC_PLAYLISTS);
                }
            }
            syncPref.setDialogTitle(R.string.pref_backend_config_db_sync);
            syncPref.setEntries(syncPrefEntries.toArray(new CharSequence[0]));
            syncPref.setEntryValues(syncPrefEntryValues.toArray(new CharSequence[0]));
            syncPref.setOnPreferenceClickListener(preference -> {
                if (isSyncWorking(backendID)) {
                    // Manually trigger onSharedPreferenceChanged for pref onClick sync cancellation
                    onSharedPreferenceChanged(sp, syncPref.getKey());
                    // Dialog suppressed in onDisplayPreferenceDialog
                } else {
                    resetSyncValues = true;
                    syncPref.setValues(Collections.emptySet());
                    // Dialog shown in onDisplayPreferenceDialog
                    // Dialog OK will trigger onSharedPreferenceChanged
                }
                return true;
            });

            addPref(sp,
                    prefSettingsActionsCat,
                    prefKeyEnabled,
                    backendID,
                    R.string.pref_key_backend_config_suffix_db_delete,
                    R.string.pref_backend_config_db_delete,
                    true,
                    true
            );
        }
    }

    private void updateBackendAPIPreference(long backendID) {
        String src = getSourceFromConfig(requireContext(), backendID);
        String api = MusicLibraryService.getAPIFromSource(src);
        int apiNameResource = getBackendAPINameResource(api);
        Preference prefAPI = findPreference(getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_api));
        if (prefAPI != null && apiNameResource > 0) {
            prefAPI.setTitle(src != null ? src : Util.getString(requireContext(), apiNameResource));
            APIClient apiClient = APIClient.getAPIClient(requireContext(), src);
            int numLibrary = 0;
            int numIndexed = 0;
            int numPlaylists = 0;
            LiveData<Integer> numLibraryLiveData = backendIDToNumEntriesMap.get(backendID);
            if (numLibraryLiveData != null) {
                Integer numLibraryValue = numLibraryLiveData.getValue();
                if (numLibraryValue != null) {
                    numLibrary = numLibraryValue;
                }
            }
            LiveData<Integer> numIndexedLiveData = backendIDToNumIndexedMap.get(backendID);
            if (numIndexedLiveData != null) {
                Integer numIndexedValue = numIndexedLiveData.getValue();
                if (numIndexedValue != null) {
                    numIndexed = numIndexedValue;
                }
            }
            LiveData<Integer> numPlaylistsLiveData = backendIDToNumPlaylistsMap.get(backendID);
            if (numPlaylistsLiveData != null) {
                Integer numPlaylistsValue = numPlaylistsLiveData.getValue();
                if (numPlaylistsValue != null) {
                    numPlaylists = numPlaylistsValue;
                }
            }
            String prefAPISummary = "";
            if (apiClient instanceof DummyAPIClient) {
                prefAPISummary += "? entries"
                        + "\n? playlists";
            } else {
                if (apiClient.hasLibrary()) {
                    prefAPISummary += Math.max(numLibrary, 0) + " "
                            + (numLibrary == 1 ? "entry" : "entries")
                            + " (" + (Math.max(numIndexed, 0)) + " indexed)";
                }
                if (apiClient.hasPlaylists()) {
                    if (!prefAPISummary.isEmpty()) {
                        prefAPISummary += "\n";
                    }
                    prefAPISummary += Math.max(numPlaylists, 0) + " "
                            + (numPlaylists == 1 ? "playlist" : "playlists");
                }
            }
            prefAPI.setSummary(prefAPISummary);
        }
    }

    private int getBackendAPINameResource(String api) {
        if (api == null) {
            return 0;
        }
        switch (api) {
            case MusicLibraryService.API_SRC_ID_SUBSONIC:
                return R.string.pref_backend_config_subsonic_name;
            case MusicLibraryService.API_SRC_ID_GIT:
                return R.string.pref_backend_config_git_name;
        }
        return 0;
    }

    private void renderSubsonicBackendPreferences(long backendID,
                                                  PreferenceCategory parentPref,
                                                  String dependencyPrefKey,
                                                  SharedPreferences sp) {
        addTextPref(
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                R.string.pref_key_backend_config_suffix_subsonic_url,
                R.string.pref_backend_config_subsonic_url,
                true,
                true,
                R.string.pref_backend_config_subsonic_url,
                0,
                true,
                InputType.TYPE_CLASS_TEXT,
                "http://demo.subsonic.org",
                null
        );
        addTextPref(
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                R.string.pref_key_backend_config_suffix_subsonic_usr,
                R.string.pref_backend_config_subsonic_usr,
                true,
                true,
                R.string.pref_backend_config_subsonic_usr,
                0,
                true,
                InputType.TYPE_CLASS_TEXT,
                "guest4",
                null
        );
        addTextPref(
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                R.string.pref_key_backend_config_suffix_subsonic_pwd,
                R.string.pref_backend_config_subsonic_pwd,
                true,
                true,
                R.string.pref_backend_config_subsonic_pwd,
                0,
                true,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                "guest",
                null
        );
    }

    private void renderGitBackendPreferences(long backendID,
                                             PreferenceCategory parentPref,
                                             String dependencyPrefKey,
                                             SharedPreferences sp) {
        GitRepoPreference prefRepo = new GitRepoPreference(requireContext());
        prefRepo.setKey(getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_git_repo));
        parentPref.addPreference(prefRepo);
        prefRepo.setDependency(dependencyPrefKey);
        prefRepo.setTitle(R.string.pref_backend_config_git_repo);
        prefRepo.setDialogTitle(R.string.pref_backend_config_git_repo);
        addTextPref(
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                R.string.pref_key_backend_config_suffix_git_branch,
                R.string.pref_backend_config_git_branch,
                true,
                true,
                R.string.pref_backend_config_git_branch,
                0,
                true,
                InputType.TYPE_CLASS_TEXT,
                "master",
                null
        );
    }

    private void setupPrefBase(Preference pref,
                               SharedPreferences sp,
                               PreferenceCategory parentPref,
                               String dependencyPrefKey,
                               long backendID,
                               int suffixResource,
                               int titleResource,
                               boolean enabled,
                               boolean visible) {
        parentPref.addPreference(pref);
        String prefKey = getBackendConfigPrefKey(backendID, suffixResource);
        pref.setKey(prefKey);
        pref.setEnabled(enabled);
        pref.setVisible(visible);
        if (dependencyPrefKey != null) {
            pref.setDependency(dependencyPrefKey);
        }
        pref.setTitle(titleResource > 0 ? titleResource : R.string.empty);
    }

    private void addPref(SharedPreferences sp,
                         PreferenceCategory parentPref,
                         String dependencyPrefKey,
                         long backendID,
                         int suffixResource,
                         int titleResource,
                         boolean enabled,
                         boolean visible) {
        Preference newPref = new Preference(requireContext());
        setupPrefBase(
                newPref,
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                suffixResource,
                titleResource,
                enabled,
                visible
        );
        newPref.setOnPreferenceClickListener(preference -> {
            onSharedPreferenceChanged(sp, newPref.getKey());
            return true;
        });
    }

    private void addTextPref(SharedPreferences sp,
                             PreferenceCategory parentPref,
                             String dependencyPrefKey,
                             long backendID,
                             int suffixResource,
                             int titleResource,
                             boolean enabled,
                             boolean visible,
                             int dialogTitleResource,
                             int dialogMessageResource,
                             boolean singleLine,
                             int inputType,
                             String defaultValue,
                             Function<String, String> summarySupplier
    ) {
        EditTextPreference newPref = new EditTextPreference(requireContext());
        setupPrefBase(
                newPref,
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                suffixResource,
                titleResource,
                enabled,
                visible
        );
        if (defaultValue != null && sp.getString(newPref.getKey(), null) == null) {
            newPref.setDefaultValue(defaultValue);
            sp.edit()
                    .putString(newPref.getKey(), defaultValue)
                    .apply();
        }
        if (summarySupplier != null) {
            newPref.setSummaryProvider(preference -> summarySupplier.apply(
                    preference.getSharedPreferences().getString(preference.getKey(), "")
            ));
        } else {
            newPref.setSummaryProvider(preference -> {
                String value = preference.getSharedPreferences().getString(preference.getKey(), "");
                if (!value.isEmpty()) {
                    value = maskIfSensitive(preference.getKey(), value);
                }
                return value;
            });
        }
        updateTextPref(newPref.getKey(), sp);
        newPref.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(singleLine);
            if (!singleLine) {
                editText.setHeight(400);
            }
            editText.setInputType(inputType);
        });
        newPref.setDialogTitle(dialogTitleResource > 0 ? dialogTitleResource : R.string.empty);
        newPref.setDialogMessage(dialogMessageResource > 0 ? dialogMessageResource : R.string.empty);
    }

    private Preference addCheckPref(SharedPreferences sp,
                                    PreferenceCategory parentPref,
                                    String dependencyPrefKey,
                                    long backendID,
                                    int suffixResource,
                                    int titleResource,
                                    boolean enabled,
                                    boolean visible
    ) {
        CheckBoxPreference newPref = new CheckBoxPreference(requireContext());
        setupPrefBase(
                newPref,
                sp,
                parentPref,
                dependencyPrefKey,
                backendID,
                suffixResource,
                titleResource,
                enabled,
                visible
        );
        newPref.setChecked(sp.getBoolean(newPref.getKey(), false));
        return newPref;
    }

    public static String getBackendConfigPrefKey(Context context,
                                                 long backendID,
                                                 int prefKeySuffixID) {
        String prefKeyPrefix = Util.getString(context, R.string.pref_key_backend_config_prefix);
        String prefKeyConfigGroup = getBackendConfigPrefKeyConfigGroup(prefKeySuffixID);
        String prefKeySuffix = Util.getString(context, prefKeySuffixID);
        return prefKeyPrefix
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + backendID
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + prefKeyConfigGroup
                + BACKEND_CONFIG_PREF_KEY_DELIM
                + prefKeySuffix;
    }

    private String getBackendConfigPrefKey(long backendID, int prefKeySuffixID) {
        return getBackendConfigPrefKey(requireContext(), backendID, prefKeySuffixID);
    }

    private static String getBackendConfigPrefKeyConfigGroup(int prefKeySuffixID) {
        String prefKeyConfigGroup;
        switch (prefKeySuffixID) {
            case R.string.pref_key_backend_config_suffix_subsonic_url:
            case R.string.pref_key_backend_config_suffix_subsonic_usr:
            case R.string.pref_key_backend_config_suffix_subsonic_pwd:
                prefKeyConfigGroup = MusicLibraryService.API_SRC_ID_SUBSONIC;
                break;
            case R.string.pref_key_backend_config_suffix_git_repo:
            case R.string.pref_key_backend_config_suffix_git_branch:
                prefKeyConfigGroup = MusicLibraryService.API_SRC_ID_GIT;
                break;
            case R.string.pref_key_backend_config_suffix_db_api:
            case R.string.pref_key_backend_config_suffix_db_id:
            case R.string.pref_key_backend_config_suffix_db_enabled:
            case R.string.pref_key_backend_config_suffix_db_tag_delim:
            case R.string.pref_key_backend_config_suffix_db_sync:
            case R.string.pref_key_backend_config_suffix_db_sync_scheduled_enabled:
            case R.string.pref_key_backend_config_suffix_db_sync_scheduled_time:
            case R.string.pref_key_backend_config_suffix_db_sync_complete_last_run:
            case R.string.pref_key_backend_config_suffix_db_delete:
            default:
                prefKeyConfigGroup = MusicLibraryService.API_SRC_ID_DANCINGBUNNIES;
                break;
        }
        return prefKeyConfigGroup;
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

    private List<Long> getBackendConfigIDs(SharedPreferences sp) {
        return getBackendConfigIDs(requireContext(), sp);
    }

    private static List<Long> getBackendConfigIDs(Context context, SharedPreferences sp) {
        return sp.getAll()
                .keySet()
                .stream()
                .filter(key -> matchesBackendConfigPrefKey(
                        context,
                        key,
                        BACKEND_ID_ANY,
                        MusicLibraryService.API_SRC_ID_DANCINGBUNNIES,
                        Util.getString(context, R.string.pref_key_backend_config_suffix_db_api)
                ))
                .map(key -> getBackendConfigID(context, key)
                )
                .collect(Collectors.toList());
    }

    public static long getBackendConfigIDFromSource(Context context, String src) {
        return getBackendConfigIDFromAPIInstanceID(
                context,
                MusicLibraryService.getAPIInstanceIDFromSource(src)
        );
    }

    private static long getBackendConfigIDFromAPIInstanceID(Context context, String apiInstanceID) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getAll()
                .keySet()
                .stream()
                .filter(key -> matchesBackendConfigPrefKey(
                        context,
                        key,
                        BACKEND_ID_ANY,
                        MusicLibraryService.API_SRC_ID_DANCINGBUNNIES,
                        Util.getString(context, R.string.pref_key_backend_config_suffix_db_api)
                ))
                .filter(key -> {
                    long backendID = getBackendConfigID(context, key);
                    String currentSrc = getSourceFromConfig(context, backendID);
                    String currentAPIInstanceID = MusicLibraryService.getAPIInstanceIDFromSource(currentSrc);
                    return apiInstanceID.equals(currentAPIInstanceID);
                })
                .map(o -> getBackendConfigID(context, o))
                .findFirst()
                .orElse(BACKEND_ID_INVALID);
    }

    private static String getBackendAPI(Context context, long backendID) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getString(
                        getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_db_api),
                        null
                );
    }

    // TODO: Remove configGroup argument. It's not used. Use suffix int id instead, then configGroup
    //  can be gotten in the same way as in getBackendConfigPrefKey
    private static boolean matchesBackendConfigPrefKey(Pattern backendConfigPrefKeyPattern,
                                                       String prefKey,
                                                       long backendID,
                                                       String configGroup,
                                                       String suffix) {
        Matcher matcher = backendConfigPrefKeyPattern.matcher(prefKey);
        if (!matcher.matches()) {
            return false;
        }
        long prefKeyID = getBackendConfigID(matcher);
        String prefKeyConfigGroup = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_CONFIG_GROUP);
        String prefKeySuffix = matcher.group(BACKEND_CONFIG_PREF_KEY_PATTERN_GROUP_SUFFIX);
        return (backendID == BACKEND_ID_ANY || backendID == prefKeyID)
                && (configGroup == null || configGroup.equals(prefKeyConfigGroup))
                && (suffix == null || suffix.equals(prefKeySuffix));
    }

    private static boolean matchesBackendConfigPrefKey(Context context,
                                                       String prefKey,
                                                       long backendID,
                                                       String configGroup,
                                                       String suffix) {
        return matchesBackendConfigPrefKey(
                getBackendConfigPrefKeyPattern(context),
                prefKey,
                backendID,
                configGroup,
                suffix
        );
    }

    private boolean matchesBackendConfigPrefKey(String prefKey,
                                                long backendID,
                                                String configGroup,
                                                String suffix) {
        return matchesBackendConfigPrefKey(
                backendConfigPrefKeyPattern,
                prefKey,
                backendID,
                configGroup,
                suffix
        );
    }

        private synchronized long getNextBackendConfigID(SharedPreferences sp) {
        return Util.getNextIDs(sp, getResources().getString(R.string.pref_key_backend_id_counter), 1);
    }

    private void updateTextPref(String prefKey, SharedPreferences sp) {
        EditTextPreference etp = findPreference(prefKey);
        if (etp != null) {
            etp.setText(sp.getString(prefKey, ""));
        }
    }

    private String maskIfSensitive(String key, String value) {
        if (value != null && !value.isEmpty()
                &&
                matchesBackendConfigPrefKey(
                        key,
                        BACKEND_ID_ANY,
                        MusicLibraryService.API_SRC_ID_SUBSONIC,
                        Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_subsonic_pwd)
                )
        ) {
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
        if (MusicLibraryService.API_SRC_ID_DANCINGBUNNIES.equals(api)) {
            return true;
        }
        long backendID = getBackendConfigIDFromAPIInstanceID(context, apiInstanceID);
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_db_enabled),
                false
        );
    }

    public static String getSourceFromConfig(Context context, long backendID) {
        String api = getBackendAPI(context, backendID);
        if (api == null) {
            return null;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String customID = sp.getString(
                getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_db_id),
                null
        );
        String apiInstanceID;
        if (customID != null && !customID.isEmpty()) {
            apiInstanceID = customID;
        } else {
            switch (api) {
                case MusicLibraryService.API_SRC_ID_SUBSONIC:
                    apiInstanceID = getSubsonicAPIInstanceID(context, backendID);
                    break;
                case MusicLibraryService.API_SRC_ID_GIT:
                    apiInstanceID = getGitAPIInstanceID(context, backendID);
                    break;
                case MusicLibraryService.API_SRC_ID_DANCINGBUNNIES:
                default:
                    apiInstanceID = null;
            }
        }
        if (apiInstanceID == null) {
            return null;
        }
        return MusicLibraryService.getAPISource(
                api,
                apiInstanceID
        );
    }

    public static List<String> getSources(Context context) {
        List<String> sources = new ArrayList<>(Collections.singletonList(
                MusicLibraryService.API_SRC_DANCINGBUNNIES_LOCAL
        ));
        sources.addAll(
                getBackendConfigIDs(context, PreferenceManager.getDefaultSharedPreferences(context))
                        .stream()
                        .map(backendID -> getSourceFromConfig(context, backendID))
                        .collect(Collectors.toList())
        );
        return sources;
    }

    private static String getGitAPIInstanceID(Context context, long backendID) {
        return GitRepoPreference.getInstanceID(
                PreferenceManager.getDefaultSharedPreferences(context),
                getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_git_repo)
        );
    }

    private static String getSubsonicAPIInstanceID(Context context, long backendID) {
        String url = PreferenceManager.getDefaultSharedPreferences(context).getString(
                getBackendConfigPrefKey(context, backendID, R.string.pref_key_backend_config_suffix_subsonic_url),
                null
        );
        if (url == null) {
            Log.e(LC, "Source URL is null for api \"" + MusicLibraryService.API_SRC_ID_SUBSONIC
                    + "\" with backendID \"" + backendID + "\"");
            return null;
        }
        return Util.getHostFromUrl(url);
    }

    private void heartbeatAPI(long backendID) {
        CompletableFuture<Void> currentHeartbeat = currentHeartbeatMap.get(backendID);
        if (currentHeartbeat == null) {
            currentHeartbeat = CompletableFuture.completedFuture(null);
        } else {
            currentHeartbeat.completeExceptionally(new Util.FutureException("Interrupted"));
            currentHeartbeatMap.put(backendID, currentHeartbeat);
        }
        CheckBoxPreference enabledPref = findPreference(getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_enabled));
        if (enabledPref != null) {
            enabledPref.setSummary("Testing connection...");
        }
        String src = getSourceFromConfig(requireContext(), backendID);
        APIClient apiClient = APIClient.getAPIClient(requireContext(), src);
        if (apiClient instanceof DummyAPIClient) {
            enableAuthenticatedPrefs(backendID, false);
            if (enabledPref != null) {
                enabledPref.setSummary("");
            }
            return;
        }
        currentHeartbeat
                .thenCompose(error -> apiClient.heartbeat())
                .handleAsync((aVoid, e) -> {
                    if (e != null) {
                        if (enabledPref != null) {
                            enabledPref.setSummary(e.getMessage());
                        }
                        enableAuthenticatedPrefs(backendID, false);
                    } else if (enabledPref != null && enabledPref.isChecked()) {
                        enabledPref.setSummary("Successfully authenticated");
                        enableAuthenticatedPrefs(backendID, true);
                    }
                    return Util.printFutureError(aVoid, e);
                }, Util.getMainThreadExecutor());
    }

    private void cancelHeartbeatAPI(long backendID) {
        CompletableFuture<Void> currentHeartbeat = currentHeartbeatMap.get(backendID);
        if (currentHeartbeat != null) {
            currentHeartbeat.completeExceptionally(new Util.FutureException("Interrupted"));
            currentHeartbeatMap.remove(backendID);
        }
        enableAuthenticatedPrefs(backendID, false);
    }

    private void enableAuthenticatedPrefs(long backendID, boolean enabled) {
        Preference syncCompletePref = findPreference(getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_sync));
        if (syncCompletePref != null) {
            syncCompletePref.setEnabled(enabled);
        }
        Preference scheduledSyncPref = findPreference(getBackendConfigPrefKey(backendID, R.string.pref_key_backend_config_suffix_db_sync_scheduled_time));
        if (scheduledSyncPref != null) {
            scheduledSyncPref.setEnabled(enabled);
        }
        String api = getBackendAPI(requireContext(), backendID);
        if (api != null) {
            switch (api) {
                case MusicLibraryService.API_SRC_ID_SUBSONIC:
                    enableSubsonicAuthenticatedPrefs(backendID, enabled);
                    break;
                case MusicLibraryService.API_SRC_ID_GIT:
                    enableGitAuthenticatedPrefs(backendID, enabled);
                    break;
            }
        }
    }

    private void enableSubsonicAuthenticatedPrefs(long backendID, boolean enabled) {
    }

    private void enableGitAuthenticatedPrefs(long backendID, boolean enabled) {
    }

    @Override
    public void onConfirmationDialogClicked(String id, boolean confirmed) {
        if (confirmed && matchesBackendConfigPrefKey(
                id,
                BACKEND_ID_ANY,
                MusicLibraryService.API_SRC_ID_DANCINGBUNNIES,
                Util.getString(requireContext(), R.string.pref_key_backend_config_suffix_db_delete)
        )) {
            deleteBackendConfig(id, true);
        }
    }
}
