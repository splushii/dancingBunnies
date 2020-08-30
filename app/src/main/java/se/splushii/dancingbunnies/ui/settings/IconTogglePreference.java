package se.splushii.dancingbunnies.ui.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.util.Util;

public class IconTogglePreference extends Preference {
    private static final String LC = Util.getLogContext(IconTogglePreference.class);
    private ImageView icon;
    private int onIconResource;
    private int offIconResource;
    private boolean toggled = false;

    public IconTogglePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public IconTogglePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public IconTogglePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IconTogglePreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_widget_dropdown_layout);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        icon = (ImageView) holder.findViewById(R.id.preference_widget_dropdown_icon);
        toggleIcon(toggled);
    }

    public void setToggleIcons(int onIconResource, int offIconResource) {
        this.onIconResource = onIconResource;
        this.offIconResource = offIconResource;
        toggleIcon(toggled);
    }

    private void toggleIcon(boolean toggled) {
        if (icon == null) {
            return;
        }
        icon.setImageResource(toggled ? onIconResource : offIconResource);
    }

    public void toggleIcon() {
        toggled = !toggled;
        toggleIcon(toggled);
    }

    @Override
    protected void onClick() {
        super.onClick();
        toggleIcon();
    }
}
