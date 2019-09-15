package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.MusicLibraryService;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.util.Util;

public class TrackItemView extends LinearLayout {
    private static final String LC = Util.getLogContext(TrackItemView.class);
    private TextView titleView;
    private TextView durationView;
    private TextView artistView;
    private TextView trackNumView;
    private TextView albumView;
    private ImageView sourceView;
    private ImageView preloadedView;
    private TextView cacheStatusView;
    private ImageView cachedView;
    private TextView posView;

    private MutableLiveData<EntryID> entryIDLiveData;
    private LiveData<Meta> metaLiveData;

    private String title;
    private String duration;
    private String artist;
    private String trackNum;
    private String album;
    private String src;
    private String cacheStatus;
    private boolean isCached;
    private boolean isPreloaded;
    private boolean posViewHighlighted = false;

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
        durationView = findViewById(R.id.track_item_duration);
        artistView = findViewById(R.id.track_item_artist);
        trackNumView = findViewById(R.id.track_item_track_num);
        albumView = findViewById(R.id.track_item_album);
        sourceView = findViewById(R.id.track_item_source);
        preloadedView = findViewById(R.id.track_item_preload_status);
        cachedView = findViewById(R.id.track_item_cached);
        cacheStatusView = findViewById(R.id.track_item_cache_status);
        posView = findViewById(R.id.track_item_pos);
        entryIDLiveData = new MutableLiveData<>();
        title = "";
        duration = "";
        artist = "";
        trackNum = "";
        album = "";
        cacheStatus = "";
        src = "";
        isCached = false;
        isPreloaded = false;
    }

    private void setFetchState(HashMap<EntryID, AudioStorage.AudioDataFetchState> fetchStateMap,
                              EntryID currentEntryID) {
        if (fetchStateMap != null && fetchStateMap.containsKey(currentEntryID)) {
            AudioStorage.AudioDataFetchState fetchstate = fetchStateMap.get(currentEntryID);
            if (fetchstate == null) {
                return;
            }
            cacheStatus = fetchstate.getStatusMsg();
            setCacheStatus();
        }
    }

    private void setCacheStatus() {
        if (isCached) {
            cacheStatusView.setVisibility(GONE);
            cacheStatusView.setText("");
            cachedView.setVisibility(VISIBLE);
        } else {
            cachedView.setVisibility(GONE);
            cacheStatusView.setText(cacheStatus);
            cacheStatusView.setVisibility(cacheStatus.isEmpty() ? GONE : VISIBLE);
        }
    }

    private void setCached(HashSet<EntryID> cachedEntries, EntryID currentEntryID) {
        if (cachedEntries == null) {
            return;
        }
        setIsCached(cachedEntries.contains(currentEntryID));
    }

    private void setIsCached(Boolean isCached) {
        this.isCached = isCached;
        setCacheStatus();
    }

    public void setTitle(String title) {
        this.title = title;
        titleView.setText(title);
    }

    public void setDuration(String duration) {
        this.duration = duration;
        durationView.setText(duration);
    }

    public void setArtist(String artist) {
        this.artist = artist;
        artistView.setText(artist);
    }

    public void setTrackNum(String trackNum) {
        this.trackNum = trackNum;
        trackNumView.setVisibility(trackNum.isEmpty() ? GONE : VISIBLE);
        trackNumView.setText(trackNum);
    }

    public void setAlbum(String album) {
        this.album = album;
        albumView.setText(album);
    }

    public void setSource(String src) {
        this.src = src;
        sourceView.setBackgroundResource(MusicLibraryService.getAPIIconResource(src));
        sourceView.setVisibility(VISIBLE);
    }

    public void setPreloaded(boolean preloaded) {
        isPreloaded = preloaded;
        preloadedView.setVisibility(preloaded ? VISIBLE : GONE);
    }

    public void setDragTitle(String txt) {
        titleView.setText(txt);
        durationView.setText("");
        artistView.setText("");
        trackNumView.setText("");
        albumView.setText("");
        sourceView.setVisibility(GONE);
        preloadedView.setVisibility(GONE);
        cacheStatusView.setVisibility(GONE);
    }

    public void reset() {
        setTitle(title);
        setDuration(duration);
        setArtist(artist);
        setTrackNum(trackNum);
        setAlbum(album);
        setSource(src);
        setCacheStatus();
        setPreloaded(isPreloaded);
    }

    public void setEntryID(EntryID entryID) {
        entryIDLiveData.setValue(entryID);
        setSource(entryID.src);
    }

    public void setPos(long pos) {
        posView.setText(String.format(Locale.getDefault(), "%01d", pos));
        updatePosViewVisibility();
    }

    public void resetPos() {
        posView.setText("");
        updatePosViewVisibility();
    }

    public void setItemHighlight(boolean highlighted) {
        if (highlighted) {
            setBackgroundResource(R.color.primary_extra_light_active_accent);
        } else {
            TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.color.transparent, value, true);
            setBackgroundResource(value.resourceId);
        }
    }

    public void setPosHighlight(boolean highlighted) {
        posViewHighlighted = highlighted;
        if (highlighted) {
            posView.setBackgroundColor(ContextCompat.getColor(
                    getContext(),
                    R.color.colorAccentLight
            ));
            posView.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
        } else {
            TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.color.transparent, value, true);
            posView.setBackgroundResource(value.resourceId);
            posView.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.secondary_text_color));
        }
        updatePosViewVisibility();
    }

    private void updatePosViewVisibility() {
        if (posView.getText().length() > 0 || posViewHighlighted) {
            posView.setVisibility(VISIBLE);
        } else {
            posView.setVisibility(GONE);
        }
    }

    private void clearInfo() {
        setTitle("");
        setArtist("");
        setTrackNum("");
        setAlbum("");
    }

    public void initMetaObserver(Context context) {
        metaLiveData = Transformations.switchMap(entryIDLiveData, entryID -> {
            if (!Meta.FIELD_SPECIAL_MEDIA_ID.equals(entryID.type)) {
                MutableLiveData<Meta> nullMeta = new MutableLiveData<>();
                nullMeta.setValue(null);
                return nullMeta;
            }
            return MetaStorage.getInstance(context).getMeta(entryID);
        });
    }

    public void observeMeta(LifecycleOwner lifecycleOwner) {
        metaLiveData.observe(lifecycleOwner, this::setMeta);
    }

    public void observeCachedLiveData(LiveData<HashSet<EntryID>> cachedEntriesLiveData,
                                      LifecycleOwner lifecycleOwner) {
        cachedEntriesLiveData.observe(
                lifecycleOwner,
                cachedEntries -> setCached(cachedEntries, entryIDLiveData.getValue())
        );
        entryIDLiveData.observe(
                lifecycleOwner,
                e -> setCached(cachedEntriesLiveData.getValue(), e)
        );
    }

    public void observeFetchStateLiveData(
            LiveData<HashMap<EntryID, AudioStorage.AudioDataFetchState>> fetchStateLiveData,
            LifecycleOwner lifecycleOwner
    ) {
        fetchStateLiveData.observe(
                lifecycleOwner,
                fetchState -> setFetchState(fetchState, entryIDLiveData.getValue())
        );
        entryIDLiveData.observe(
                lifecycleOwner,
                e -> setFetchState(fetchStateLiveData.getValue(), e)
        );
    }

    private void setMeta(Meta meta) {
        if (meta == null) {
            clearInfo();
            return;
        }
        String title = meta.getAsString(Meta.FIELD_TITLE);
        setTitle(title);
        String durationString = meta.getAsString(Meta.FIELD_DURATION);
        String duration = durationString.isEmpty() ?
                "" : Util.getDurationString(Long.parseLong(durationString));
        setDuration(duration);
        String artist = meta.getAsString(Meta.FIELD_ARTIST);
        setArtist(artist);
        String trackNum = meta.getAsString(Meta.FIELD_TRACKNUMBER);
        setTrackNum(trackNum.isEmpty() ? "" : "#" + trackNum);
        String album = meta.getAsString(Meta.FIELD_ALBUM);
        setAlbum(album);
        String src = meta.entryID.src;
        setSource(src);
    }

    public Meta getMeta() {
        return metaLiveData.getValue();
    }
}