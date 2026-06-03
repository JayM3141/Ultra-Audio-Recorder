package com.ultraaudio.recorder.visualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Optimized FFT Spectrum View.
 */
public class SpectrumView extends View {
    private Paint paint = new Paint();
    private float[] magnitudes;
    private int width, height;

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFF58A6FF);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w; height = h;
    }

    public void updateData(float[] mags, float[] freqs) {
        if (mags == null) return;
        synchronized (this) {
            if (this.magnitudes == null || this.magnitudes.length != mags.length) {
                this.magnitudes = new float[mags.length];
            }
            System.arraycopy(mags, 0, this.magnitudes, 0, mags.length);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (magnitudes == null || magnitudes.length == 0) return;

        synchronized (this) {
            float barWidth = (float) width / magnitudes.length;
            for (int i = 0; i < magnitudes.length; i++) {
                float val = (float) (20 * Math.log10(magnitudes[i] + 1e-10) + 100) / 100f;
                float barHeight = Math.max(0, val) * height;
                canvas.drawRect(i * barWidth, height - barHeight, (i + 1) * barWidth, height, paint);
            }
        }
    }
}
