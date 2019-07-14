package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import androidx.annotation.Nullable;
import se.splushii.dancingbunnies.R;

public class FastScrollerBubble extends LinearLayout {
    private TextView text;
    private Consumer<Integer> callback;

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

    public void setUpdateCallback(Consumer<Integer> callback) {
        this.callback = callback;
    }

    public void update(int pos) {
        callback.accept(pos);
    }
}
