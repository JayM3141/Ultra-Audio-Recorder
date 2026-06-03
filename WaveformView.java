package com.ultraaudio.recorder.visualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Optimized Oscilloscope/Waveform View.
 */
public class WaveformView extends View {
    private Paint paint = new Paint();
    private Path path = new Path();
    private float[] data;
    private int width, height;

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFF3FB950);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w; height = h;
    }

    public void updateData(float[] newData) {
        if (newData == null || newData.length == 0) return;
        synchronized (this) {
            if (this.data == null || this.data.length != newData.length) {
                this.data = new float[newData.length];
            }
            System.arraycopy(newData, 0, this.data, 0, newData.length);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.length == 0) return;

        synchronized (this) {
            path.reset();
            float centerY = height / 2f;
            float stepX = (float) width / data.length;

            path.moveTo(0, centerY);
            for (int i = 0; i < data.length; i++) {
                float x = i * stepX;
                float y = centerY - (data[i] * centerY);
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, paint);
    }
}
