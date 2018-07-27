package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.media.MediaIntentReceiver;

import se.splushii.dancingbunnies.util.Util;

public class CastMediaIntentReceiver extends MediaIntentReceiver {
    private static final String LC = Util.getLogContext(CastMediaIntentReceiver.class);
    private Context context;

    private void sendAudioPlayerCommand(String castCommand) {
        Intent intent = new Intent(context, AudioPlayerService.class);
        intent.putExtra(AudioPlayerService.STARTCMD_INTENT_CAST_ACTION, castCommand);
        context.startService(intent);
    }

    @Override
    protected void onReceiveActionTogglePlayback(Session session) {
        Log.d(LC, "onReceiveActionTogglePlayback");
        sendAudioPlayerCommand(AudioPlayerService.CAST_ACTION_TOGGLE_PLAYBACK);
    }

    @Override
    protected void onReceiveActionSkipPrev(Session session) {
        Log.d(LC, "onReceiveActionSkipPrev");
        sendAudioPlayerCommand(AudioPlayerService.CAST_ACTION_PREVIOUS);
    }

    @Override
    protected void onReceiveActionSkipNext(Session session) {
        Log.d(LC, "onReceiveActionSkipNext");
        sendAudioPlayerCommand(AudioPlayerService.CAST_ACTION_NEXT);
    }

    @Override
    protected void onReceiveOtherAction(Context context, String action, Intent intent) {
        Log.d(LC, "onReceiveOtherAction: " + action);
        super.onReceiveOtherAction(context, action, intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LC, "onReceive");
        this.context = context;
        super.onReceive(context, intent);
    }
}
