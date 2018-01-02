package se.splushii.dancingbunnies;

import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
   
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import se.splushii.dancingbunnies.util.Util;

public final class MainActivity extends AppCompatActivity {
    private static final String LC = Util.getLogContext(MainActivity.class);

    private static final int SETTINGS_INTENT_REQUEST = 1;
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private static final int MUSICLIBRARY = 0;
    private static final int NOWPLAYING = 1;
    private static final int QUEUE = 2;
    private static final int NUM_VIEWS = 3;

    private MusicLibraryFragment musicLibraryFragment;
    private NowPlayingFragment nowPlayingFragment;
    private PlaylistQueueFragment playlistQueueFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        musicLibraryFragment = new MusicLibraryFragment();
        nowPlayingFragment = new NowPlayingFragment();
        playlistQueueFragment = new PlaylistQueueFragment();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    private void showSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, SETTINGS_INTENT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SETTINGS_INTENT_REQUEST:
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                showSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (mViewPager.getCurrentItem() != MUSICLIBRARY
                || !musicLibraryFragment.onBackPressed()) {
            Log.w(LC, "Backpress ignored.");
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case MUSICLIBRARY:
                    return musicLibraryFragment;
                case NOWPLAYING:
                    return nowPlayingFragment;
                case QUEUE:
                    return playlistQueueFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            return NUM_VIEWS;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case MUSICLIBRARY:
                    return "MusicLibrary";
                case NOWPLAYING:
                    return "Now Playing";
                case QUEUE:
                    return "Playlist/Queue";
            }
            return null;
        }
    }
}
