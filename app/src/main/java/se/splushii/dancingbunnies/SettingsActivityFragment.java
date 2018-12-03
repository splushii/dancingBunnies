package se.splushii.dancingbunnies;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import se.splushii.dancingbunnies.audioplayer.AudioPlayerService;
import se.splushii.dancingbunnies.jobs.BackendRefreshJob;
import se.splushii.dancingbunnies.jobs.Jobs;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.util.Util;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener{
    private static final String LC = Util.getLogContext(SettingsActivityFragment.class);
    private MediaBrowserCompat mediaBrowser;

    public SettingsActivityFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        mediaBrowser = new MediaBrowserCompat(getActivity(), new ComponentName(getActivity(),
                AudioPlayerService.class), mediaBrowserConnectionCallback, null);
        mediaBrowser.connect();
    }

    @Override
    public void onStop() {
        mediaBrowser.disconnect();
        mediaBrowser = null;
        super.onStop();
    }

    private final MediaBrowserCompat.ConnectionCallback mediaBrowserConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    Log.d(LC, "MediaBrowser connected");
                }

                @Override
                public void onConnectionFailed() {
                    Log.e(LC, "MediaBrowser onConnectFailed");
                }

                @Override
                public void onConnectionSuspended() {
                    Log.w(LC, "MediaBrowser onConnectionSuspended");
                }
            };

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
        SSpwd.setSummary(settings.contains(getResources()
                .getString(R.string.pref_key_subsonic_pwd)) ?
                "********" : ""
        );
        final Preference SSrefresh = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_refresh));
        SSrefresh.setOnPreferenceClickListener(preference -> {
            JobScheduler jobScheduler =
                    (JobScheduler) getContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
            assert jobScheduler != null;
            PersistableBundle backendRefreshBundle = new PersistableBundle();
            backendRefreshBundle.putString(
                    BackendRefreshJob.ACTION,
                    BackendRefreshJob.ACTION_FETCH_LIBRARY
            );
            backendRefreshBundle.putString(
                    BackendRefreshJob.API,
                    MusicLibraryService.API_ID_SUBSONIC
            );
            JobInfo jobInfo = new JobInfo.Builder(
                    Jobs.JOB_BACKEND_REFRESH,
                    new ComponentName(getContext(), BackendRefreshJob.class)
            )
                    .setExtras(backendRefreshBundle)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build();
            Log.d(LC, "refresh onClick!");
            jobScheduler.schedule(jobInfo);
            return false;
        });
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view != null) {
            view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.bluegrey900));
        }
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
        } else if (key.equals(getResources().getString(R.string.pref_subsonic_refresh))) {
            Log.d(LC, "derp");
        }
    }
}
