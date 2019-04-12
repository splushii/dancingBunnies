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
import se.splushii.dancingbunnies.storage.MetaStorage;
import se.splushii.dancingbunnies.ui.ItemActionsView;
import se.splushii.dancingbunnies.ui.MetaDialogFragment;
import se.splushii.dancingbunnies.util.Util;

public class NowPlayingEntriesAdapter
        extends RecyclerView.Adapter<NowPlayingEntriesAdapter.SongViewHolder> {
    private static final String LC = Util.getLogContext(NowPlayingEntriesAdapter.class);
    private final NowPlayingFragment fragment;
    private List<PlaybackEntry> queueData;
    private List<PlaybackEntry> playlistNext;

    private static final int VIEWTYPE_UNKNOWN = -1;
    static final int VIEWTYPE_QUEUE_ITEM = 0;
    static final int VIEWTYPE_PLAYLIST_NEXT = 1;
    private SelectionTracker<Long> selectionTracker;
    private ItemActionsView selectedActionView;
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
            if (cbMap != null) {
                cbMap.remove(pos);
            }
            if (cbMap == null || cbMap.isEmpty()) {
                fetchStatusCallbacks.remove(entryID);
            }
        }
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
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection() && selectedActionView != null) {
                    selectedActionView.animateShow(false);
                    selectedActionView = null;
                }
            }
        });
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
        private final ItemActionsView actionsView;
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

        SongViewHolder(View v) {
            super(v);
            item = v.findViewById(R.id.nowplaying_item);
            title = v.findViewById(R.id.nowplaying_item_title);
            artist = v.findViewById(R.id.nowplaying_item_artist);
            source = v.findViewById(R.id.nowplaying_item_source);
            preloadStatus = v.findViewById(R.id.nowplaying_item_preload_status);
            cacheStatus = v.findViewById(R.id.nowplaying_item_cache_status);
            actionsView = v.findViewById(R.id.nowplaying_item_actions);
            fetchStatusText = "";
            isCached = false;
            item.setOnClickListener(view -> {
                if (selectedActionView != null && selectedActionView != actionsView) {
                    selectedActionView.animateShow(false);
                }
                selectedActionView = actionsView;
                boolean showActionsView = !selectionTracker.hasSelection()
                        && actionsView.getVisibility() != View.VISIBLE;
                actionsView.animateShow(showActionsView);
            });
        }

        ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return itemDetails;
        }

        void setFetchState(AudioStorage.AudioDataFetchState state) {
            fetchStatusText = state.getStatusMsg();
            setCacheStatusText();
        }

        private void setCacheStatusText() {
            cacheStatus.setText(isCached ? "C" : fetchStatusText);
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
        if (position < queueSize) {
            return VIEWTYPE_QUEUE_ITEM;
        }
        int nextSize = playlistNext.size();
        if (position < queueSize + nextSize) {
            return VIEWTYPE_PLAYLIST_NEXT;
        }
        return VIEWTYPE_UNKNOWN;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == VIEWTYPE_QUEUE_ITEM ?
                R.layout.nowplaying_queue_item : R.layout.nowplaying_playlist_item;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new SongViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final SongViewHolder holder, int position) {
        holder.actionsView.initialize();
        PlaybackEntry entry;
        int queueSize = queueData.size();
        switch (getItemViewType(position)) {
            default:
            case VIEWTYPE_QUEUE_ITEM:
                entry = queueData.get(position);
                holder.actionsView.setOnDequeueListener(() ->
                        fragment.dequeue(entry.entryID, position)
                );
                break;
            case VIEWTYPE_PLAYLIST_NEXT:
                int nextPos = position - queueSize;
                entry = playlistNext.get(nextPos);
                holder.actionsView.setOnDequeueListener(null);
                break;
        }
        holder.entryID = entry.entryID;
        holder.position = position;
        boolean selected = selectionTracker != null
                && selectionTracker.isSelected(holder.getItemDetails().getSelectionKey());
        holder.item.setActivated(selected);
        holder.actionsView.setOnPlayListener(() -> {
            fragment.skipItems(position + 1);
            fragment.play();
        });
        MetaStorage.getInstance(fragment.requireContext()).getMeta(entry.entryID)
                .thenAcceptAsync(meta -> {
                    holder.actionsView.setOnInfoListener(() -> {
                        MetaDialogFragment.showMeta(fragment, meta);
                    });
                    String title = meta.getAsString(Meta.FIELD_TITLE);
                    holder.title.setText(title);
                    String artist = meta.getAsString(Meta.FIELD_ARTIST);
                    holder.artist.setText(artist);
                    String src = meta.entryID.src;
                    holder.source.setText(src);
                }, Util.getMainThreadExecutor());
        String preloadStatus = entry.isPreloaded() ? "(preloaded) " : "";
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
