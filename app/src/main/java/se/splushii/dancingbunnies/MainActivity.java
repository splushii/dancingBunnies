package se.splushii.dancingbunnies;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.material.tabs.TabLayout;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProviders;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.viewpager.widget.ViewPager;
import se.splushii.dancingbunnies.ui.musiclibrary.MusicLibraryFragment;
import se.splushii.dancingbunnies.ui.musiclibrary.MusicLibraryFragmentModel;
import se.splushii.dancingbunnies.ui.nowplaying.NowPlayingFragment;
import se.splushii.dancingbunnies.ui.playlist.PlaylistFragment;
import se.splushii.dancingbunnies.util.Util;

public final class MainActivity extends AppCompatActivity {
    private static final String LC = Util.getLogContext(MainActivity.class);
    private static final int SETTINGS_INTENT_REQUEST = 1;
    private static final int NUM_VIEWS = 3;
    public static final int PAGER_MUSICLIBRARY = 0;
    public static final int PAGER_NOWPLAYING = 1;
    public static final int PAGER_PLAYLIST = 2;
    public static final String PAGER_SELECTION = "dancingbunnies.mainactivity.pagerselection";
    public static final String SELECTION_ID_NOWPLAYING = "nowplaying_selection_id";
    public static final String SELECTION_ID_MUSICLIBRARY = "musiclibrary_selection_id";

    private SearchView searchView;
    private ViewPager mViewPager;
    private SectionsPagerAdapter mSectionsPagerAdapter;

    private MusicLibraryFragmentModel musicLibraryModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);
        musicLibraryModel = ViewModelProviders.of(this).get(MusicLibraryFragmentModel.class);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mViewPager = findViewById(R.id.main_container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        setSupportActionBar(findViewById(R.id.toolbar));

        TabLayout tabLayout = findViewById(R.id.main_tabs);
        tabLayout.setupWithViewPager(mViewPager);

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
            musicLibraryModel.search(query);
            intent.setAction(Intent.ACTION_MAIN);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d(LC, "onStop");
        super.onStop();
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
        searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setSearchableInfo(Objects.requireNonNull(searchManager).getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

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
        if (!mSectionsPagerAdapter.onBackPressed()) {
            Log.w(LC, "Backpress ignored.");
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        private MusicLibraryFragment musicLibraryFragment;
        private NowPlayingFragment nowPlayingFragment;
        private PlaylistFragment playlistFragment;
        private int lastPosition = -1;

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case PAGER_MUSICLIBRARY:
                    return new MusicLibraryFragment();
                case PAGER_NOWPLAYING:
                    return new NowPlayingFragment();
                case PAGER_PLAYLIST:
                    return new PlaylistFragment();
            }
            return null;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Object fragment = super.instantiateItem(container, position);
            switch (position) {
                case PAGER_MUSICLIBRARY:
                    musicLibraryFragment = (MusicLibraryFragment) fragment;
                    break;
                case PAGER_NOWPLAYING:
                    nowPlayingFragment = (NowPlayingFragment) fragment;
                    break;
                case PAGER_PLAYLIST:
                    playlistFragment = (PlaylistFragment) fragment;
                    break;
            }
            return fragment;
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            checkPositionChanged(position);
        }

        private void checkPositionChanged(int position) {
            if (position != lastPosition) {
                lastPosition = position;
                musicLibraryFragment.clearSelection();
                nowPlayingFragment.clearSelection();
            }
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
                case PAGER_PLAYLIST:
                    return "Playlists";
            }
            return null;
        }

        boolean onBackPressed() {
            switch (mViewPager.getCurrentItem()) {
                case PAGER_MUSICLIBRARY:
                    return musicLibraryFragment.onBackPressed();
                case PAGER_PLAYLIST:
                    return playlistFragment.onBackPressed();
                default:
                    return false;
            }
        }
    }
}
