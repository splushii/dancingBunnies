package se.splushii.dancingbunnies.musiclibrary;

import java.util.ArrayList;

/**
 * Created by splushii on 2016-09-16.
 */
public class Album {
    String id;
    String name;
    int songCount;
    ArrayList<Song> songs;
    public Album(String id, String name, int songCount) {
        this.id = id;
        this.name = name;
        this.songCount = songCount;
        songs = new ArrayList<>(songCount);
    }

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }

    public ArrayList<Song> getSongs() {
        return songs;
    }
}
