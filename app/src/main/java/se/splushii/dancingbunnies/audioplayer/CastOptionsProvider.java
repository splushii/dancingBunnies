package se.splushii.dancingbunnies.audioplayer;

import android.content.Context;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;

import java.util.List;

public class CastOptionsProvider implements OptionsProvider {
    @Override
    public CastOptions getCastOptions(Context context) {
        CastMediaOptions castMediaOptions = new CastMediaOptions.Builder()
                .setMediaIntentReceiverClassName(CastMediaIntentReceiver.class.getName())
                .setNotificationOptions(null)
                // We manage the MediaSession ourselves
                .setMediaSessionEnabled(false)
                .build();
        return new CastOptions.Builder()
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .setEnableReconnectionService(true)
                .setResumeSavedSession(true)
                .setCastMediaOptions(castMediaOptions)
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
