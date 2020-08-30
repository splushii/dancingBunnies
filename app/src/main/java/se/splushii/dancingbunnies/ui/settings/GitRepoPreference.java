package se.splushii.dancingbunnies.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

public class GitRepoPreference extends DialogPreference {
    private String repo;

    public GitRepoPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public GitRepoPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public GitRepoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GitRepoPreference(Context context) {
        super(context);
        init();
    }

    public static Bundle getSettingsBundle(SharedPreferences sp, String prefix) {
        return GitRepoPreferenceDialogFragment.getSettingsBundle(sp, prefix);
    }

    public static String getInstanceID(SharedPreferences sp, String prefix) {
        return GitRepoPreferenceDialogFragment.getInstanceID(sp, prefix);
    }

    private void init() {
        setSummaryProvider(preference -> getPersistedString(
                GitRepoPreferenceDialogFragment.getURI(getSharedPreferences(), getKey())
        ));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        repo = defaultValue == null ? "" : defaultValue.toString();
    }

    void persistStringValue(String value) {
        persistString(value);
        notifyChanged();
    }
}
