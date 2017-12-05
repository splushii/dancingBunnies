package se.splushii.dancingbunnies;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;

import se.splushii.dancingbunnies.backend.MusicLibraryRequestHandler;
import se.splushii.dancingbunnies.events.LibraryEvent;
import se.splushii.dancingbunnies.events.SettingsChangedEvent;
import se.splushii.dancingbunnies.musiclibrary.MusicLibrary;
import se.splushii.dancingbunnies.services.AudioPlayerService;
import se.splushii.dancingbunnies.util.Util;

/**
 * A placeholder fragment containing a simple view.
 */
public class SettingsActivityFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener{
    private static String LC = Util.getLogContext(SettingsActivityFragment.class);
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
        SSpwd.setSummary(settings.getString(getResources()
                .getString(R.string.pref_key_subsonic_pwd), ""));
        final Preference SSrefresh = findPreference(getResources()
                .getString(R.string.pref_key_subsonic_refresh));
        SSrefresh.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.d(LC, "refresh onClick!");
                EventBus.getDefault().post(new LibraryEvent(MusicLibrary.API_ID_SUBSONIC,
                        LibraryEvent.LibraryAction.FETCH_LIBRARY,
                        new MusicLibraryRequestHandler() {
                            @Override
                            public void onStart() {
                                Log.d(LC, "subsonic refresh onStart");
                                SSrefresh.setSummary("Loading...");
                            }

                            @Override
                            public void onProgress(int i, int max) {
                                SSrefresh.setSummary("Fetched " + i + " of "
                                        + (max == 0 ? "?" : String.valueOf(max)));
                            }

                            @Override
                            public void onProgress(String status) {
                                SSrefresh.setSummary("Status: " + status);
                            }

                            @Override
                            public void onSuccess(String status) {
                                Log.d(LC, "subsonic refresh onSuccess");
                                SSrefresh.setSummary(status);
                            }

                            @Override
                            public void onFailure(String status) {
                                Log.d(LC, "subsonic refresh onFailure: " + status);
                                SSrefresh.setSummary("Failed to fetch library.");
                            }
                        }));
                return false;
            }
        });
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
        } else if (key.equals(getResources().getString(R.string.pref_subsonic_refresh))) {
            Log.d(LC, "derp");
        }
        EventBus.getDefault().post(new SettingsChangedEvent());
    }
}
