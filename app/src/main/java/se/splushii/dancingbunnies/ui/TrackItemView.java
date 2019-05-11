package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.util.Util;

public class TrackItemView extends LinearLayout {
    private static final String LC = Util.getLogContext(TrackItemView.class);
    private TextView titleView;
    private TextView artistView;
    private ImageView sourceView;
    private ImageView preloadedView;
    private TextView cacheStatusView;
    private ImageView cachedView;

    private String title;
    private String artist;
    private String src;
    private String cacheStatus;
    private boolean isCached;
    private boolean isPreloaded;

    public TrackItemView(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public TrackItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public TrackItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    public TrackItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        inflate(getContext(), R.layout.track_item_content, this);
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.TrackItemView, defStyleAttr, defStyleRes);
        a.recycle();
        titleView = findViewById(R.id.track_item_title);
        artistView = findViewById(R.id.track_item_artist);
        sourceView = findViewById(R.id.track_item_source);
        preloadedView = findViewById(R.id.track_item_preload_status);
        cachedView = findViewById(R.id.track_item_cached);
        cacheStatusView = findViewById(R.id.track_item_cache_status);
        title = "";
        artist = "";
        cacheStatus = "";
        src = "";
        isCached = false;
        isPreloaded = false;
    }

    public void initialize() {
        cacheStatus = "";
        setIsCached(false);
    }

    public void setFetchState(AudioStorage.AudioDataFetchState state) {
        cacheStatus = state.getStatusMsg();
        setCacheStatus();
    }

    private void setCacheStatus() {
        if (isCached) {
            cacheStatusView.setVisibility(View.GONE);
            cacheStatusView.setText("");
            cachedView.setVisibility(View.VISIBLE);
        } else {
            cachedView.setVisibility(View.GONE);
            cacheStatusView.setText(cacheStatus);
            cacheStatusView.setVisibility(View.VISIBLE);
        }
    }

    public void setIsCached(Boolean isCached) {
        this.isCached = isCached;
        setCacheStatus();
    }

    public void setTitle(String title) {
        this.title = title;
        titleView.setText(title);
    }

    public void setArtist(String artist) {
        this.artist = artist;
        artistView.setText(artist);
    }

    public void setSource(String src) {
        this.src = src;
        sourceView.setBackgroundResource(MusicLibraryService.getAPIIconResource(src));
        sourceView.setVisibility(VISIBLE);
    }

    public void setPreloaded(boolean preloaded) {
        isPreloaded = preloaded;
        preloadedView.setVisibility(preloaded ? View.VISIBLE : View.GONE);
    }

    public void setDragTitle(String txt) {
        titleView.setText(txt);
        artistView.setText("");
        sourceView.setVisibility(GONE);
        preloadedView.setVisibility(GONE);
        cacheStatusView.setVisibility(GONE);
    }

    public void reset() {
        setTitle(title);
        setArtist(artist);
        setSource(src);
        setCacheStatus();
        setPreloaded(isPreloaded);
    }
}