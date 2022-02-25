package se.splushii.dancingbunnies.ui.playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.musiclibrary.Meta;
import se.splushii.dancingbunnies.musiclibrary.Query;
import se.splushii.dancingbunnies.musiclibrary.QueryNode;
import se.splushii.dancingbunnies.util.Util;

class PlaylistUserState {
    private static final String LC = Util.getLogContext(PlaylistUserState.class);

    final boolean showPlaylists;
    final boolean showPlaybackEntries;
    final EntryID browsedPlaylistID;
    final int playlistPos;
    final int playlistPad;
    final int playlistEntriesPos;
    final int playlistEntriesPad;
    final int playlistPlaybackEntriesPos;
    final int playlistPlaybackEntriesPad;
    final boolean scrollPlaylistPlaybackToPlaylistPos;
    final int numPlaylistEntries;
    final int numPlaylistPlaybackEntries;
    final List<String> sortByFields;
    final boolean sortOrderAscending;
    private final QueryNode query;

    private PlaylistUserState(boolean showPlaylists,
                              boolean showPlaybackEntries,
                              EntryID browsedPlaylistID,
                              int playlistPos,
                              int playlistPad,
                              int playlistEntriesPos,
                              int playlistEntriesPad,
                              int playlistPlaybackEntriesPos,
                              int playlistPlaybackEntriesPad,
                              boolean scrollPlaylistPlaybackToPlaylistPos,
                              int numPlaylistEntries,
                              int numPlaylistPlaybackEntries,
                              List<String> sortByFields,
                              boolean sortOrderAscending,
                              QueryNode query) {
        this.showPlaylists = showPlaylists;
        this.showPlaybackEntries = showPlaybackEntries;
        this.browsedPlaylistID = browsedPlaylistID;
        this.playlistPos = playlistPos;
        this.playlistPad = playlistPad;
        this.playlistEntriesPos = playlistEntriesPos;
        this.playlistEntriesPad = playlistEntriesPad;
        this.playlistPlaybackEntriesPos = playlistPlaybackEntriesPos;
        this.playlistPlaybackEntriesPad = playlistPlaybackEntriesPad;
        this.scrollPlaylistPlaybackToPlaylistPos = scrollPlaylistPlaybackToPlaylistPos;
        this.numPlaylistEntries = numPlaylistEntries;
        this.numPlaylistPlaybackEntries = numPlaylistPlaybackEntries;
        this.sortByFields = sortByFields == null ?
                new ArrayList<>(Collections.singletonList(Meta.FIELD_TITLE)) : sortByFields;
        this.sortOrderAscending = sortOrderAscending;
        this.query = query;
    }

    boolean isBrowsedCurrent(EntryID currentPlaylistID) {
        if (browsedPlaylistID == null || currentPlaylistID == null) {
            return false;
        }
        return browsedPlaylistID.equals(currentPlaylistID);
    }

    public QueryNode getQuery() {
        return query == null ? null : query.deepCopy();
    }

    static class Builder {
        private boolean showPlaylists = true;
        private boolean showPlaybackEntries = false;
        private EntryID browsedPlaylistID = null;
        private int playlistPos;
        private int playlistPad;
        private int playlistEntriesPos;
        private int playlistEntriesPad;
        private int playlistPlaybackEntriesPos;
        private int playlistPlaybackEntriesPad;
        private boolean scrollPlaylistPlaybackToPlaylistPos;
        private int numPlaylistEntries;
        private int numPlaylistPlaybackEntries;
        private List<String> sortByFields;
        private boolean sortOrderAscending = true;
        private QueryNode query;

        Builder() {}

        Builder fromState(PlaylistUserState state) {
            showPlaylists = state.showPlaylists;
            showPlaybackEntries = state.showPlaybackEntries;
            browsedPlaylistID = state.browsedPlaylistID;
            playlistPos = state.playlistPos;
            playlistPad = state.playlistPad;
            playlistEntriesPos = state.playlistEntriesPos;
            playlistEntriesPad = state.playlistEntriesPad;
            playlistPlaybackEntriesPos = state.playlistPlaybackEntriesPos;
            playlistPlaybackEntriesPad = state.playlistPlaybackEntriesPad;
            scrollPlaylistPlaybackToPlaylistPos = state.scrollPlaylistPlaybackToPlaylistPos;
            numPlaylistEntries = state.numPlaylistEntries;
            numPlaylistPlaybackEntries = state.numPlaylistPlaybackEntries;
            sortByFields = state.sortByFields;
            sortOrderAscending = state.sortOrderAscending;
            return this;
        }

        Builder setShowPlaylists(boolean showPlaylists) {
            this.showPlaylists = showPlaylists;
            return this;
        }

        Builder setBrowsedPlaylist(EntryID playlistID) {
            browsedPlaylistID = playlistID;
            showPlaylists = false;
            return this;
        }

        Builder setShowPlaylistPlaybackEntries(boolean showPlaybackEntries) {
            this.showPlaybackEntries = showPlaybackEntries;
            return this;
        }

        Builder setPlaylistScroll(int pos, int pad) {
            this.playlistPos = pos;
            this.playlistPad = pad;
            return this;
        }

        Builder setPlaylistEntriesScroll(int pos, int pad) {
            this.playlistEntriesPos = pos;
            this.playlistEntriesPad = pad;
            return this;
        }

        Builder setPlaylistPlaybackEntriesScroll(int pos, int pad) {
            this.playlistPlaybackEntriesPos = pos;
            this.playlistPlaybackEntriesPad = pad;
            return this;
        }

        Builder setScrollPlaylistPlaybackToPlaylistPos(boolean scrollPlaylistPlaybackToPlaylistPos) {
            this.scrollPlaylistPlaybackToPlaylistPos = scrollPlaylistPlaybackToPlaylistPos;
            return this;
        }

        Builder setNumPlaylistEntries(int numEntries) {
            this.numPlaylistEntries = numEntries;
            return this;
        }

        Builder setNumPlaylistPlaybackEntries(int numEntries) {
            this.numPlaylistPlaybackEntries = numEntries;
            return this;
        }

        Builder setSortByFields(List<String> sortByFields) {
            this.sortByFields = sortByFields == null ? Query.DEFAULT_SORT_FIELDS : sortByFields;
            return this;
        }

        Builder setSortOrderAscending(boolean sortOrderAscending) {
            this.sortOrderAscending = sortOrderAscending;
            return this;
        }

        Builder setQuery(QueryNode query) {
            this.query = query;
            return this;
        }

        PlaylistUserState build() {
            return new PlaylistUserState(
                    showPlaylists,
                    showPlaybackEntries,
                    browsedPlaylistID,
                    playlistPos,
                    playlistPad,
                    playlistEntriesPos,
                    playlistEntriesPad,
                    playlistPlaybackEntriesPos,
                    playlistPlaybackEntriesPad,
                    scrollPlaylistPlaybackToPlaylistPos,
                    numPlaylistEntries,
                    numPlaylistPlaybackEntries,
                    sortByFields,
                    sortOrderAscending,
                    query
            );
        }
    }
}
