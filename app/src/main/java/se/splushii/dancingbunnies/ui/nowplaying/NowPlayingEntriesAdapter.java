package se.splushii.dancingbunnies.ui.nowplaying;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.PlaylistItem;
import se.splushii.dancingbunnies.storage.AudioStorage;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingEntriesAdapter
        extends RecyclerView.Adapter<NowPlayingEntriesAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);
    private final NowPlayingFragment fragment;
    private List<PlaybackEntry> queueData;
    private List<PlaybackEntry> playlistNext;
    private RecyclerView.ViewHolder contextMenuHolder;

    private static final int VIEWTYPE_UNKNOWN = -1;
    static final int VIEWTYPE_QUEUE_ITEM = 0;
    static final int VIEWTYPE_PLAYLIST_NEXT = 1;
    private SelectionTracker<Long> selectionTracker;
    private View selectedItemView;
    private final HashMap<EntryID, HashMap<Integer, Consumer<AudioStorage.AudioDataFetchState>>> fetchStatusCallbacks;
    private final HashMap<EntryID, HashMap<Integer, Consumer<Boolean>>> isCachedCallbacks;
    private HashSet<EntryID> cachedEntries;

    NowPlayingEntriesAdapter(NowPlayingFragment fragment) {
        this.fragment = fragment;
        queueData = new ArrayList<>();
        playlistNext = new ArrayList<>();
        fetchStatusCallbacks = new HashMap<>();
        isCachedCallbacks = new HashMap<>();
        cachedEntries = new HashSet<>();
    }

    void setModel(NowPlayingFragmentModel model) {
        synchronized (fetchStatusCallbacks) {
            fetchStatusCallbacks.clear();

        }
        model.getFetchState(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), audioDataFetchStates -> {
                    for (AudioStorage.AudioDataFetchState state: audioDataFetchStates) {
                        synchronized (fetchStatusCallbacks) {
                            if (fetchStatusCallbacks.containsKey(state.entryID)) {
                                fetchStatusCallbacks.get(state.entryID)
                                        .forEach((key, value) -> value.accept(state));
                            }
                        }
                    }
                });
        synchronized (isCachedCallbacks) {
            isCachedCallbacks.clear();
        }
        model.getCachedEntries(fragment.getContext())
                .observe(fragment.getViewLifecycleOwner(), cachedEntries -> {
                    synchronized (isCachedCallbacks) {
                        this.cachedEntries = cachedEntries;
                        for (EntryID entryID : isCachedCallbacks.keySet()) {
                            boolean isCached = cachedEntries.contains(entryID);
                            isCachedCallbacks.get(entryID)
                                    .forEach((key, value) -> value.accept(isCached));
                        }
                    }
                });

    }

    private void addIsCachedCallback(EntryID entryID,
                                     int pos,
                                     Consumer<Boolean> cb) {
        synchronized (isCachedCallbacks) {
            HashMap<Integer, Consumer<Boolean>> cbMap =
                    isCachedCallbacks.getOrDefault(entryID, new HashMap<>());
            cbMap.put(pos, cb);
            isCachedCallbacks.put(entryID, cbMap);
        }
        cb.accept(cachedEntries.contains(entryID));
    }

    private void removeIsCachedCallback(EntryID entryID, int pos) {
        synchronized (isCachedCallbacks) {
            HashMap<Integer, Consumer<Boolean>> cbMap =
                    isCachedCallbacks.get(entryID);
            cbMap.remove(pos);
            if (cbMap.isEmpty()) {
                isCachedCallbacks.remove(entryID);
            }
        }
    }

    private void addFetchStatusCallback(EntryID entryID,
                                        int pos,
                                        Consumer<AudioStorage.AudioDataFetchState> cb) {
        synchronized (fetchStatusCallbacks) {
            HashMap<Integer, Consumer<AudioStorage.AudioDataFetchState>> cbMap =
                    fetchStatusCallbacks.getOrDefault(entryID, new HashMap<>());
            cbMap.put(pos, cb);
            fetchStatusCallbacks.put(entryID, cbMap);
        }
    }

    private void removeFetchStatusCallback(EntryID entryID, int pos) {
        synchronized (fetchStatusCallbacks) {
            HashMap<Integer, Consumer<AudioStorage.AudioDataFetchState>> cbMap =
                    fetchStatusCallbacks.get(entryID);
            cbMap.remove(pos);
            if (cbMap.isEmpty()) {
                fetchStatusCallbacks.remove(entryID);
            }
        }
    }

    PlaybackEntry getItemData(int childPosition) {
        return queueData.get(childPosition);
    }

    public void setQueue(List<PlaybackEntry> queue) {
        queueData = queue;
        notifyDataSetChanged();
    }

    void setCurrentPlaylistItem(PlaylistItem playlistItem) {
        notifyDataSetChanged();
    }

    void setPlaylistNext(List<PlaybackEntry> playbackEntries) {
        playlistNext = playbackEntries;
        notifyDataSetChanged();
    }

    void setSelectionTracker(SelectionTracker<Long> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    PlaybackEntry getPlaybackEntry(Long key) {
        int index = key.intValue();
        if (index < queueData.size()) {
            return queueData.get(index);
        }
        index -= queueData.size();
        return index < playlistNext.size() ? playlistNext.get(index) : null;
    }

    private PlaybackEntry removePlaybackEntry(Long key) {
        if (key < queueData.size()) {
            return queueData.remove(key.intValue());
        }
        return playlistNext.remove(key.intValue() - queueData.size());
    }

    private void insertPlaybackEntry(PlaybackEntry entry, int position) {
        if (position <= queueData.size()) {
            queueData.add(position, entry);
        } else {
            playlistNext.add(position - queueData.size(), entry);
        }
    }

    TreeMap<Integer, PlaybackEntry> removeItems(List<Long> positions) {
        positions.sort(Comparator.reverseOrder());
        TreeMap<Integer, PlaybackEntry> entries = new TreeMap<>();
        for (Long pos: positions) {
            entries.put(pos.intValue(), getPlaybackEntry(pos));
            if (pos < queueData.size()) {
                entries.put(
                        pos.intValue(),
                        queueData.remove(pos.intValue())
                );
            } else {
                entries.put(
                        pos.intValue(),
                        playlistNext.remove(pos.intValue() - queueData.size())
                );
            }
            notifyItemRemoved(pos.intValue());
        }
        return entries;
    }

    void insertItems(TreeMap<Integer, PlaybackEntry> selectedPlaybackEntries) {
        selectedPlaybackEntries.forEach((k, v) -> {
            if (v.playbackType.equals(PlaybackEntry.USER_TYPE_QUEUE)) {
                queueData.add(k, v);
            } else {
                playlistNext.add(k, v);
            }
            notifyItemInserted(k);
        });
    }

    void moveItem(int from, int to) {
        PlaybackEntry entry = removePlaybackEntry((long) from);
        insertPlaybackEntry(entry, to);
        notifyItemMoved(from, to);
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final View item;
        final TextView title;
        final TextView artist;
        final TextView source;
        final TextView preloadStatus;
        final TextView cacheStatus;
        private final View moreActions;
        private final View actionPlay;
        private final View actionDequeue;
        private final View overflowMenu;
        private EntryID entryID;
        private int position;
        private String fetchStatusText;
        private boolean isCached;

        private final ItemDetailsLookup.ItemDetails<Long> itemDetails = new ItemDetailsLookup.ItemDetails<Long>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Nullable
            @Override
            public Long getSelectionKey() {
                return (long) getAdapterPosition();
            }
        };

        SongViewHolder(View v, boolean isQueueItem) {
            super(v);
            item = v.findViewById(R.id.nowplaying_item);
            title = v.findViewById(R.id.nowplaying_item_title);
            artist = v.findViewById(R.id.nowplaying_item_artist);
            source = v.findViewById(R.id.nowplaying_item_source);
            preloadStatus = v.findViewById(R.id.nowplaying_item_preload_status);
            cacheStatus = v.findViewById(R.id.nowplaying_item_cache_status);
            moreActions = v.findViewById(R.id.nowplaying_item_more_actions);
            actionPlay = v.findViewById(R.id.nowplaying_item_action_play);
            overflowMenu = v.findViewById(R.id.nowplaying_item_overflow_menu);
            actionDequeue = isQueueItem ?
                    v.findViewById(R.id.nowplaying_item_action_dequeue) : null;
            fetchStatusText = "";
            isCached = false;
            item.setOnClickListener(view -> {
                if (selectedItemView != null && selectedItemView != moreActions) {
                    selectedItemView.setVisibility(View.GONE);
                }
                moreActions.setVisibility(selectionTracker.hasSelection() ? View.GONE :
                        moreActions.getVisibility() == View.VISIBLE ?
                                View.GONE : View.VISIBLE
                );
                selectedItemView = moreActions;
            });
            overflowMenu.setOnClickListener(view -> {
                contextMenuHolder = this;
                view.showContextMenu();
            });
        }

        ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return itemDetails;
        }

        void setFetchState(AudioStorage.AudioDataFetchState state) {
            String txt;
            switch (state.getState()) {
                default:
                case AudioStorage.AudioDataFetchState.IDLE:
                case AudioStorage.AudioDataFetchState.SUCCESS:
                    txt = "";
                    break;
                case AudioStorage.AudioDataFetchState.DOWNLOADING:
                    txt = state.getProgress();
                    break;
                case AudioStorage.AudioDataFetchState.FAILURE:
                    txt = "dl failed";
                    break;
            }
            fetchStatusText = txt;
            setCacheStatusText();
        }

        private void setCacheStatusText() {
            cacheStatus.setText(fetchStatusText + (isCached ? " C" : ""));
        }

        void setIsCached(Boolean isCached) {
            this.isCached = isCached;
            setCacheStatusText();
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        int queueSize = queueData.size();
        int nextSize = playlistNext.size();
        if (position < queueSize) {
            return VIEWTYPE_QUEUE_ITEM;
        }
        if (position < queueSize + nextSize) {
            return VIEWTYPE_PLAYLIST_NEXT;
        }
        return VIEWTYPE_UNKNOWN;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean isQueueType = viewType == VIEWTYPE_QUEUE_ITEM;
        int layout = isQueueType ?
                R.layout.nowplaying_queue_item : R.layout.nowplaying_playlist_item;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new SongViewHolder(v, isQueueType);
    }

    RecyclerView.ViewHolder getContextMenuHolder() {
        return contextMenuHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        PlaybackEntry entry;
        int queueSize = queueData.size();
        switch (getItemViewType(position)) {
            default:
            case VIEWTYPE_QUEUE_ITEM:
                entry = queueData.get(position);
                holder.actionDequeue.setOnClickListener(view -> {
                    fragment.dequeue(EntryID.from(entry.meta), position);
                    holder.moreActions.setVisibility(View.GONE);
                });
                break;
            case VIEWTYPE_PLAYLIST_NEXT:
                int nextPos = position - queueSize;
                entry = playlistNext.get(nextPos);
                break;
        }
        holder.entryID = entry.entryID;
        holder.position = position;
        boolean selected = selectionTracker != null
                && selectionTracker.isSelected(holder.getItemDetails().getSelectionKey());
        holder.item.setActivated(selected);
        String title = entry.meta.getString(Meta.METADATA_KEY_TITLE);
        holder.actionPlay.setOnClickListener(view -> {
            fragment.skipItems(position + 1);
            fragment.play();
            holder.moreActions.setVisibility(View.GONE);
        });
        holder.title.setText(title);
        String artist = entry.meta.getString(Meta.METADATA_KEY_ARTIST);
        holder.artist.setText(artist);
        String src = entry.meta.getString(Meta.METADATA_KEY_API);
        holder.source.setText(src);
        String preloadStatusValue = entry.meta.getString(Meta.METADATA_KEY_PLAYBACK_PRELOADSTATUS);
        String preloadStatus = preloadStatusValue == null ? "" : "(" + preloadStatusValue + ") ";
        holder.preloadStatus.setText(preloadStatus);
        holder.fetchStatusText = "";
        holder.isCached = false;
        holder.cacheStatus.setText("");
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull SongViewHolder holder) {
        removeFetchStatusCallback(holder.entryID, holder.position);
        removeIsCachedCallback(holder.entryID, holder.position);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull SongViewHolder holder) {
        addFetchStatusCallback(holder.entryID, holder.position, holder::setFetchState);
        addIsCachedCallback(holder.entryID, holder.position, holder::setIsCached);
        super.onViewAttachedToWindow(holder);
    }

    @Override
    public int getItemCount() {
        return playlistNext.size() + queueData.size();
    }
}
