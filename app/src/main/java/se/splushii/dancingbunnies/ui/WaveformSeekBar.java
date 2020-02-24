package se.splushii.dancingbunnies.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.core.content.ContextCompat;
import se.splushii.dancingbunnies.R;
import se.splushii.dancingbunnies.util.Util;

public class WaveformSeekBar extends androidx.appcompat.widget.AppCompatSeekBar {
    private static final String LC = Util.getLogContext(WaveformSeekBar.class);
    private final int FRAME_WIDTH = 1;

    private RectF frame;
    private Paint framePaint;

    private double[] samples;
    private double[] samplesNegative;
    private double[] peakSamples;
    private double[] peakNegativeSamples;

    private int numFrames;
    private float[] frameHeightTop;
    private float[] frameHeightBottom;
    private float[] frameHeightPeakTop;
    private float[] frameHeightPeakBottom;

    public WaveformSeekBar(Context context) {
        super(context);
        init();
    }

    public WaveformSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        resetData();
        frame = new RectF();
        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (samples.length <= 0) {
            super.onDraw(canvas);
            return;
        }
        int lastProgressFrame = (int)((double) numFrames * getProgress() / getMax());
        drawFrames(
                canvas,
                frameHeightPeakTop,
                frameHeightPeakBottom,
                FRAME_WIDTH,
                numFrames,
                lastProgressFrame,
                R.color.waveFormPeakProgress,
                R.color.waveFormPeak
        );
        drawFrames(
                canvas,
                frameHeightTop,
                frameHeightBottom,
                FRAME_WIDTH,
                numFrames,
                lastProgressFrame,
                R.color.waveFormRMSProgress,
                R.color.waveFormRMS
        );
    }

    private void drawFrames(Canvas canvas,
                            float[] framesTop,
                            float[] framesBottom,
                            int frameWidth,
                            int numFrames,
                            int lastProgressFrame,
                            int progressColor,
                            int regularColor) {
        // Draw progress
        framePaint.setColor(ContextCompat.getColor(getContext(), progressColor));
        drawFrames(canvas, framesTop, framesBottom, 0, lastProgressFrame + 1, frameWidth, numFrames);
        // Draw upcoming
        framePaint.setColor(ContextCompat.getColor(getContext(), regularColor));
        drawFrames(canvas, framesTop, framesBottom, lastProgressFrame + 1, numFrames, frameWidth, numFrames);
    }

    private void drawFrames(Canvas canvas,
                            float[] framesTop,
                            float[] framesBottom,
                            int fromFrame,
                            int toFrame,
                            int frameWidth,
                            int numFrames) {
        int currentStart = getPaddingStart() + fromFrame * frameWidth;
        for (int i = fromFrame; i < toFrame && i < numFrames; i++) {
            frame.set(
                    currentStart,
                    framesTop[i],
                    currentStart + frameWidth,
                    framesBottom[i]
            );
            canvas.drawRect(frame, framePaint);
            currentStart = currentStart + frameWidth;
        }
    }

    private void onSetProgress(int progress) {
        invalidate();
    }

    @Override
    public synchronized void setProgress(int progress) {
        onSetProgress(progress);
        super.setProgress(progress);
    }

    @Override
    public void setProgress(int progress, boolean animate) {
        onSetProgress(progress);
        super.setProgress(progress, animate);
    }

    public void resetData() {
        samples = new double[0];
        samplesNegative = new double[0];
        peakSamples = new double[0];
        peakNegativeSamples = new double[0];
    }

    public void setData(double[] samples,
                        double[] samplesNegative,
                        double[] peakSamples,
                        double[] peakNegativeSamples) {
        this.samples = samples;
        this.samplesNegative = samplesNegative;
        this.peakSamples = peakSamples;
        this.peakNegativeSamples = peakNegativeSamples;
        calculateFrames();
        invalidate();
    }

    private void calculateFrames() {
        int usableWidth = getWidth() - getPaddingStart() - getPaddingEnd();
        int usableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float maxWaveHeight = (float) usableHeight / 2;
        numFrames = usableWidth / FRAME_WIDTH;
        frameHeightTop = new float[numFrames];
        frameHeightBottom = new float[numFrames];
        frameHeightPeakTop = new float[numFrames];
        frameHeightPeakBottom = new float[numFrames];

        float rmsPosMax = calculateWeights(samples, frameHeightTop);
        float rmsNegMax = calculateWeights(samplesNegative, frameHeightBottom);
        float peakPosMax = calculateWeights(peakSamples, frameHeightPeakTop);
        float peakNegMax = calculateWeights(peakNegativeSamples, frameHeightPeakBottom);
        float maxWeight = Math.max(Math.max(rmsPosMax, rmsNegMax), Math.max(peakPosMax, peakNegMax));
        calculateFrames(frameHeightTop, maxWeight, maxWaveHeight, true);
        calculateFrames(frameHeightBottom, maxWeight, maxWaveHeight, false);
        calculateFrames(frameHeightPeakTop, maxWeight, maxWaveHeight, true);
        calculateFrames(frameHeightPeakBottom, maxWeight, maxWaveHeight, false);
    }

    private float calculateWeights(double[] samples, float[] frames) {
        int lastFrame = 0;
        int currentFrame = 0;
        float sum = 0;
        int count = 0;
        float weightMax = 0;
        // Downsample samples to frames.length and calculate weights
        for (int i = 0; i < samples.length; i++) {
            currentFrame = frames.length * i / samples.length;
            if (currentFrame > lastFrame) {
                float weight = sum / count;
                if (weight > weightMax) {
                    weightMax = weight;
                }
                frames[lastFrame] = weight;
                sum = 0;
                count = 0;
            }
            sum += samples[i];
            count++;
            lastFrame = currentFrame;
        }
        if (count > 0) {
            float weight = sum / count;
            if (weight > weightMax) {
                weightMax = weight;
            }
            frames[currentFrame] = weight;
        }
        return weightMax;
    }
    private void calculateFrames(float[] weights, float maxWeight, float maxWaveHeight, boolean positive) {
        // Calculate frames and apply weights
        for (int i = 0; i < weights.length; i++) {
            float weight = weights[i];
            float waveHeight = maxWaveHeight * weight / maxWeight;
            weights[i] = getPaddingTop() + maxWaveHeight
                    + (positive ? 1 : -1) * waveHeight;
        }
    }
}
