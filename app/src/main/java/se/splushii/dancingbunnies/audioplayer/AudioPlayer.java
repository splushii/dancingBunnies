package se.splushii.dancingbunnies.audioplayer;

import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import se.splushii.dancingbunnies.backend.AudioDataSource;
import se.splushii.dancingbunnies.musiclibrary.EntryID;
import se.splushii.dancingbunnies.util.Util;

public abstract class AudioPlayer {
    private static final String LC = Util.getLogContext(AudioPlayer.class);

    AudioPlayerCallback audioPlayerCallback;
    private AudioPlayerCallback emptyAudioPlayerCallback = new AudioPlayerCallback() {
        @Override
        public void onReady() {
            Log.w(LC, "onReady");
        }

        @Override
        public void onEnded() {
            Log.w(LC, "onEnded");
        }

        @Override
        public void onStateChanged(int playBackState) {
            Log.w(LC, "onStateChanged");
        }

        @Override
        public void onMetaChanged(EntryID entryID) {
            Log.w(LC, "onMetaChanged");
        }

    };
    AudioPlayer() {
        audioPlayerCallback = emptyAudioPlayerCallback;
    }
    void setListener(AudioPlayerCallback audioPlayerCallback) {
        this.audioPlayerCallback = audioPlayerCallback;
    }
    public void removeListener() {
        audioPlayerCallback = emptyAudioPlayerCallback;
    }
    abstract long getCurrentPosition();
    abstract void play();
    abstract void pause();
    abstract void stop();
    abstract void setSource(AudioDataSource audioDataSource,
                            MediaMetadataCompat meta);

    interface AudioPlayerCallback {
        void onReady();
        void onEnded();
        void onStateChanged(int playBackState);
        void onMetaChanged(EntryID entryID);
    }
}
