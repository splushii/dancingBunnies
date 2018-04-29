package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import se.splushii.dancingbunnies.R;

public class FastScrollerBubble extends LinearLayout {
    private TextView text;

    public FastScrollerBubble(Context context) {
        super(context);
        init(context);
    }

    public FastScrollerBubble(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setClipChildren(false);
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.fastscroller_bubble, this);

        text = findViewById(R.id.fastscroller_bubble_text);
        setVisibility(INVISIBLE);
    }

    public void setText(String s) {
        text.setText(s);
    }
}
