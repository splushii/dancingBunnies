package se.splushii.dancingbunnies.musiclibrary;

import se.splushii.dancingbunnies.util.Util;

public class SmartPlaylist extends Playlist {
    private static final String LC = Util.getLogContext(SmartPlaylist.class);
    private final QueryNode queryNode;

    public SmartPlaylist(Meta meta, QueryNode queryNode) {
        super(meta);
        meta.addString(Meta.FIELD_QUERY, getJSONQueryString());
        this.queryNode = queryNode;
    }

    public String getJSONQueryString() {
        return queryNode.toJSON().toString();
    }
}
