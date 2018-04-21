package se.splushii.dancingbunnies;

import android.app.SearchManager;
import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
   
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.cast.framework.CastButtonFactory;

import se.splushii.dancingbunnies.musiclibrary.MusicLibraryQuery;
import se.splushii.dancingbunnies.ui.LibraryView;
import se.splushii.dancingbunnies.util.Util;

public final class MainActivity extends AppCompatActivity {
    private static final String LC = Util.getLogContext(MainActivity.class);

    private static final int SETTINGS_INTENT_REQUEST = 1;
    private ViewPager mViewPager;
    public static final String PAGER_SELECTION = "dancingbunnies.mainactivity.pagerselection";
    public static final int PAGER_MUSICLIBRARY = 0;
    public static final int PAGER_NOWPLAYING = 1;
    public static final int PAGER_QUEUE = 2;
    private static final int NUM_VIEWS = 3;

    private MusicLibraryFragment musicLibraryFragment;
    private NowPlayingFragment nowPlayingFragment;
    private PlaylistQueueFragment playlistQueueFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        SectionsPagerAdapter mSectionsPagerAdapter =
                new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = findViewById(R.id.main_container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.main_tabs);
        tabLayout.setupWithViewPager(mViewPager);

        musicLibraryFragment = new MusicLibraryFragment();
        nowPlayingFragment = new NowPlayingFragment();
        playlistQueueFragment = new PlaylistQueueFragment();

        MediaRouteButton mediaRouteButton = findViewById(R.id.media_route_button);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), mediaRouteButton);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        int page_id = getIntent().getIntExtra(PAGER_SELECTION, -1);
        if (page_id != -1) {
            mViewPager.setCurrentItem(page_id);
        }
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mViewPager.setCurrentItem(PAGER_MUSICLIBRARY);
            String query = intent.getStringExtra(SearchManager.QUERY);
            musicLibraryFragment.refreshView(
                    new LibraryView(
                            new MusicLibraryQuery(query), 0, 0
                    )
            );
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_activity_menu, menu);
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                onBackPressed();
                return true;
            }
        });
        SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);

        CastButtonFactory.setUpMediaRouteButton(
                getApplicationContext(),
                menu,
                R.id.media_route_menu_item);

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
                Log.d(LC, "Unhandled intent code: " + requestCode);
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
        if (mViewPager.getCurrentItem() != PAGER_MUSICLIBRARY
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
                case PAGER_MUSICLIBRARY:
                    return musicLibraryFragment;
                case PAGER_NOWPLAYING:
                    return nowPlayingFragment;
                case PAGER_QUEUE:
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
                case PAGER_MUSICLIBRARY:
                    return "MusicLibrary";
                case PAGER_NOWPLAYING:
                    return "Now Playing";
                case PAGER_QUEUE:
                    return "Playlist/Queue";
            }
            return null;
        }
    }
}
