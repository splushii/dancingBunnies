package se.splushii.dancingbunnies.musiclibrary;

import android.os.Parcel;
import android.os.Parcelable;

public class PlaylistItem implements Parcelable {
    public static PlaylistItem defaultPlaylist = new PlaylistItem(
            PlaylistID.defaultPlaylistID,
            "Default"
    );
    public final PlaylistID playlistID;
    public final String name;
    public PlaylistItem(PlaylistID playlistID, String name) {
        this.playlistID = playlistID;
        this.name = name;
    }

    protected PlaylistItem(Parcel in) {
        playlistID = in.readParcelable(PlaylistID.class.getClassLoader());
        name = in.readString();
    }

    public static final Creator<PlaylistItem> CREATOR = new Creator<PlaylistItem>() {
        @Override
        public PlaylistItem createFromParcel(Parcel in) {
            return new PlaylistItem(in);
        }

        @Override
        public PlaylistItem[] newArray(int size) {
            return new PlaylistItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(playlistID, flags);
        dest.writeString(name);
    }
}
