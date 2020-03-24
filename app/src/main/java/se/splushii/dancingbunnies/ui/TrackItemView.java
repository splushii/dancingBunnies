package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
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
    private TextView discNumView;
    private ImageView sourceView;
    private ImageView preloadedView;
    private TextView cacheStatusView;
    private ImageView cachedView;
    private TextView posView;
    private View dragHandleView;

    private MutableLiveData<EntryID> entryIDLiveData;
    private LiveData<Meta> metaLiveData;

    private String title;
    private String duration;
    private String artist;
    private String trackNum;
    private String album;
    private String discNum;
    private String src;
    private String cacheStatus;
    private long pos;
    private Runnable dragHandleCallback;
    private boolean isCached;
    private boolean isPreloaded;
    private boolean isHighlighted;
    private boolean isPosHighlighted;
    private boolean isStatic;

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
        discNumView = findViewById(R.id.track_item_album_disc);
        sourceView = findViewById(R.id.track_item_source);
        preloadedView = findViewById(R.id.track_item_preload_status);
        cachedView = findViewById(R.id.track_item_cached);
        cacheStatusView = findViewById(R.id.track_item_cache_status);
        posView = findViewById(R.id.track_item_pos);
        dragHandleView = findViewById(R.id.track_item_handle);
        setPos(-1);
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
        isStatic = false;
    }

    private void setFetchState(HashMap<EntryID, AudioStorage.AudioDataFetchState> fetchStateMap,
                              EntryID currentEntryID) {
        if (fetchStateMap != null && fetchStateMap.containsKey(currentEntryID)) {
            AudioStorage.AudioDataFetchState fetchstate = fetchStateMap.get(currentEntryID);
            if (fetchstate == null) {
                return;
            }
            Meta meta = getMeta();
            cacheStatus = fetchstate.getStatusMsg(meta == null ? "?" : meta.getFormattedFileSize());
            setCacheStatus();
        }
    }

    private void setCacheStatus() {
        if (isStatic) {
            return;
        }
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
        if (isStatic) {
            return;
        }
        titleView.setText(title);
    }

    public void setDuration(String duration) {
        this.duration = duration;
        if (isStatic) {
            return;
        }
        durationView.setText(duration);
    }

    public void setArtist(String artist) {
        this.artist = artist;
        if (isStatic) {
            return;
        }
        artistView.setText(artist);
    }

    public void setTrackNum(String trackNum) {
        this.trackNum = trackNum;
        if (isStatic) {
            return;
        }
        trackNumView.setVisibility(trackNum.isEmpty() ? GONE : VISIBLE);
        trackNumView.setText(trackNum);
    }

    public void setAlbum(String album) {
        this.album = album;
        if (isStatic) {
            return;
        }
        albumView.setText(album);
    }

    public void setDiscNum(String discNum) {
        this.discNum = discNum;
        if (isStatic) {
            return;
        }
        if (discNum == null || discNum.isEmpty()) {
            discNumView.setVisibility(GONE);
        } else {
            discNumView.setText(discNum);
            discNumView.setVisibility(VISIBLE);
        }
    }

    public void setSource(String src) {
        this.src = src;
        if (isStatic) {
            return;
        }
        sourceView.setBackgroundResource(MusicLibraryService.getAPIIconResource(src));
        sourceView.setVisibility(VISIBLE);
    }

    public void setPreloaded(boolean preloaded) {
        isPreloaded = preloaded;
        if (isStatic) {
            return;
        }
        preloadedView.setVisibility(preloaded ? VISIBLE : GONE);
    }

    public void useForDrag(String txt) {
        isStatic = true;
        titleView.setText(txt);
        durationView.setText("");
        artistView.setText("");
        trackNumView.setText("");
        albumView.setText("");
        discNumView.setText("");
        posView.setVisibility(GONE);
        sourceView.setVisibility(GONE);
        preloadedView.setVisibility(GONE);
        cachedView.setVisibility(GONE);
        cacheStatusView.setVisibility(GONE);
        setDefaultBackground();
        setDefaultPosBackground();
        setDragHandleListener(null);
    }

    public void resetFromDrag() {
        isStatic = false;
        setTitle(title);
        setDuration(duration);
        setArtist(artist);
        setTrackNum(trackNum);
        setAlbum(album);
        setDiscNum(discNum);
        setSource(src);
        setCacheStatus();
        setPreloaded(isPreloaded);
        setPos(pos);
        setItemHighlight(isHighlighted);
        setPosHighlight(isPosHighlighted);
        setDragHandleListener(dragHandleCallback);
    }

    public void setEntryID(EntryID entryID) {
        entryIDLiveData.setValue(entryID);
        src = entryID.src;
        if (isStatic) {
            return;
        }
        setSource(entryID.src);
    }

    public EntryID getEntryID() {
        return entryIDLiveData.getValue();
    }

    public void setDragHandleListener(Runnable callback) {
        this.dragHandleCallback = callback;
        if (isStatic) {
            return;
        }
        if (callback != null) {
            dragHandleView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    callback.run();
                }
                return false;
            });
            dragHandleView.setVisibility(VISIBLE);
        } else {
            dragHandleView.setVisibility(GONE);
            dragHandleView.setOnClickListener(null);
        }
    }

    public void setPos(long pos) {
        this.pos = pos;
        if (isStatic) {
            return;
        }
        if (pos < 0) {
            posView.setText("");
        } else {
            posView.setText(String.format(Locale.getDefault(), "%01d", pos));
        }
        updatePosViewVisibility();
    }

    public void setItemHighlight(boolean highlighted) {
        this.isHighlighted = highlighted;
        if (isStatic) {
            return;
        }
        if (highlighted) {
            setBackgroundResource(R.color.primary_extra_light_active_accent);
        } else {
            setDefaultBackground();
        }
    }

    private void setDefaultBackground() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.color.transparent, value, true);
        setBackgroundResource(value.resourceId);
    }

    public void setPosHighlight(boolean highlighted) {
        isPosHighlighted = highlighted;
        if (isStatic) {
            return;
        }
        if (highlighted) {
            posView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
            posView.setTextColor(ContextCompat.getColor(getContext(), R.color.white));
        } else {
            setDefaultPosBackground();
        }
        updatePosViewVisibility();
    }

    private void setDefaultPosBackground() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.color.transparent, value, true);
        posView.setBackgroundResource(value.resourceId);
        posView.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.text_secondary_color));
    }

    private void updatePosViewVisibility() {
        if (posView.getText().length() > 0 || isPosHighlighted) {
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
        setDuration("");
        setDiscNum(null);
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
        setFetchState(fetchStateLiveData.getValue(), entryIDLiveData.getValue());
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
                "" : Meta.getDurationString(Long.parseLong(durationString));
        setDuration(duration);
        String artist = meta.getAsString(Meta.FIELD_ARTIST);
        setArtist(artist);
        String trackNum = meta.getAsString(Meta.FIELD_TRACKNUMBER);
        setTrackNum(trackNum.isEmpty() ? "" : "#" + trackNum);
        String album = meta.getAsString(Meta.FIELD_ALBUM);
        setAlbum(album);
        String discNum = meta.getAsString(Meta.FIELD_DISCNUMBER);
        setDiscNum(discNum.isEmpty() ? "" : "(" + discNum + ")");
        String src = meta.entryID.src;
        setSource(src);
    }

    public Meta getMeta() {
        return metaLiveData.getValue();
    }
}