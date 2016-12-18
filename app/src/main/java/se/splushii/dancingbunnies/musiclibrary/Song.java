package se.splushii.dancingbunnies.musiclibrary;

/**
 * Created by splushii on 2016-09-16.
 */
public class Song {
    String id;
    String name;
    public Song(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }
}
