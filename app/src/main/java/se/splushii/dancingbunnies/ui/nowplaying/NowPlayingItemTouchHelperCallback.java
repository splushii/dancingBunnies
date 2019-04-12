package se.splushii.dancingbunnies.ui.nowplaying;

import android.view.ActionMode;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.audioplayer.PlaybackEntry;

final class NowPlayingItemTouchHelperCallback extends ItemTouchHelper.Callback {
    private final NowPlayingEntriesAdapter adapter;
    private final NowPlayingFragment fragment;
    private final List<Long> selection;
    private ActionMode actionMode;
    private TreeMap<Integer, PlaybackEntry> selectedPlaybackEntries;
    private int secondarySelectionId;
    private int lastDragPos = -1;
    private boolean abort = false;
    private CharSequence originalActionModeTitle;
    private CharSequence originalHolderTitle;
    private CharSequence originalHolderArtist;
    private CharSequence originalHolderSource;
    private CharSequence originalHolderPreloadStatus;
    private CharSequence originalHolderCacheStatus;
    private NowPlayingEntriesAdapter.SongViewHolder songViewHolder;

    NowPlayingItemTouchHelperCallback(NowPlayingFragment fragment,
                                      NowPlayingEntriesAdapter adapter) {
        this.fragment = fragment;
        this.adapter = adapter;
        this.selection = new LinkedList<>();
    }

    void prepareDrag(ActionMode actionMode,
                     SelectionTracker<Long> selectionTracker,
                     RecyclerView.ViewHolder viewHolder) {
        this.actionMode = actionMode;
        resetActionModeTitle();
        resetViewHolderText();
        MutableSelection<Long> mutableSelection = new MutableSelection<>();
        selectionTracker.copySelection(mutableSelection);
        selection.clear();
        long initialSelectionId = (long) viewHolder.getAdapterPosition();
        List<Long> selectionToRemove = new LinkedList<>();
        mutableSelection.forEach(id -> {
            if (!id.equals(initialSelectionId)) {
                selectionToRemove.add(id);
            }
            selection.add(id);
        });
        selectedPlaybackEntries = adapter.removeItems(selectionToRemove);
        // The id of the draggable item when other items are removed
        lastDragPos = secondarySelectionId = viewHolder.getAdapterPosition();
        songViewHolder = (NowPlayingEntriesAdapter.SongViewHolder) viewHolder;
        if (selection.size() > 1) {
            setViewHolderText(selection.size() + " entries");
        }
        setDropMode();
        toggleActionModeButtons(false);
    }

    private void toggleActionModeButtons(boolean visible) {
        actionMode.getMenu().findItem(R.id.nowplaying_actionmode_action_dequeue).setVisible(visible);
    }

    private void setDropMode() {
        abort = false;
        setActionModeTitle("Drop to move");
    }

    private void setAbortMode() {
        abort = true;
        setActionModeTitle("Drop to abort");
    }

    private void setViewHolderText(String s) {
        if (originalHolderTitle== null) {
            originalHolderTitle = songViewHolder.title.getText();
        }
        if (originalHolderArtist == null) {
            originalHolderArtist = songViewHolder.artist.getText();
        }
        if (originalHolderSource == null) {
            originalHolderSource = songViewHolder.source.getText();
        }
        if (originalHolderPreloadStatus == null) {
            originalHolderPreloadStatus = songViewHolder.preloadStatus.getText();
        }
        if (originalHolderCacheStatus == null) {
            originalHolderCacheStatus = songViewHolder.cacheStatus.getText();
        }
        songViewHolder.title.setText(s);
        songViewHolder.artist.setText("");
        songViewHolder.source.setText("");
        songViewHolder.preloadStatus.setText("");
        songViewHolder.cacheStatus.setText("");
    }

    private void resetViewHolderText() {
        if (originalHolderTitle != null) {
            songViewHolder.title.setText(originalHolderTitle);
            originalHolderTitle = null;
        }
        if (originalHolderArtist != null) {
            songViewHolder.artist.setText(originalHolderArtist);
            originalHolderArtist = null;
        }
        if (originalHolderSource != null) {
            songViewHolder.source.setText(originalHolderSource);
            originalHolderSource = null;
        }
        if (originalHolderPreloadStatus != null) {
            songViewHolder.preloadStatus.setText(originalHolderPreloadStatus);
            originalHolderPreloadStatus = null;
        }
        if (originalHolderCacheStatus != null) {
            songViewHolder.cacheStatus.setText(originalHolderCacheStatus);
            originalHolderCacheStatus = null;
        }
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView,
                                RecyclerView.ViewHolder viewHolder) {
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

    @Override
    public boolean canDropOver(@NonNull RecyclerView recyclerView,
                               @NonNull RecyclerView.ViewHolder current,
                               @NonNull RecyclerView.ViewHolder target) {
        return invalidMove(current, target);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder current,
                          @NonNull RecyclerView.ViewHolder target) {
        int from = current.getAdapterPosition();
        int to = target.getAdapterPosition();
        if (to != lastDragPos) {
            if (invalidMove(current, target)) {
                if (abort) {
                    setDropMode();
                }
                adapter.moveItem(from, to);
                lastDragPos = to;
            }
        }
        return true;
    }

    private boolean invalidMove(RecyclerView.ViewHolder current,
                                RecyclerView.ViewHolder target) {
        return current.getItemViewType() != NowPlayingEntriesAdapter.VIEWTYPE_QUEUE_ITEM
                || target.getItemViewType() != NowPlayingEntriesAdapter.VIEWTYPE_PLAYLIST_NEXT;
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (abort) {
            toggleActionModeButtons(true);
            resetActionModeTitle();
            resetViewHolderText();
            // Reset adapter items
            adapter.moveItem(lastDragPos, secondarySelectionId);
            adapter.insertItems(selectedPlaybackEntries);
        } else {
            fragment.moveQueueItems(selection, lastDragPos).thenAccept(s -> fragment.refreshView());
            actionMode.finish();
        }
    }

    @Override
    public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView,
                                            int viewSize,
                                            int viewSizeOutOfBounds,
                                            int totalSize,
                                            long msSinceStartScroll) {
        if (!abort && Math.abs(viewSizeOutOfBounds) > viewSize) {
            setAbortMode();
        } else if (abort && Math.abs(viewSizeOutOfBounds) < viewSize) {
            setDropMode();
        }
        return super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                viewSizeOutOfBounds, totalSize, msSinceStartScroll);
    }

    private void setActionModeTitle(CharSequence title) {
        if (originalActionModeTitle == null) {
            originalActionModeTitle = actionMode.getTitle();
        }
        actionMode.setTitle(title);
    }

    private void resetActionModeTitle() {
        if (originalActionModeTitle != null) {
            actionMode.setTitle(originalActionModeTitle);
            originalActionModeTitle = null;
        }
    }
}