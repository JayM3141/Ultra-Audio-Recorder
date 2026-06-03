package com.ultraaudio.recorder.visualization;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Optimized Spectrogram with real-time 3D projection and heatmap.
 */
public class SpectrogramView extends View {
    private Paint paint = new Paint();
    private Bitmap spectrogramBitmap;
    private Canvas bitmapCanvas;
    private int[] colors;
    private int width, height;
    private int currentColumn = 0;
    private boolean is3DMode = false;
    private float rotationX = 45f;
    private float rotationZ = 45f;
    
    // History for 3D projection
    private float[][] history;
    private int historySize = 100;
    private int historyIdx = 0;

    public SpectrogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        colors = new int[256];
        for (int i = 0; i < 256; i++) {
            float t = i / 255f;
            colors[i] = Color.HSVToColor(new float[]{(1f - t) * 240f, 1f, t > 0.1f ? 1f : t * 10f});
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w; height = h;
        if (w > 0 && h > 0) {
            spectrogramBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmapCanvas = new Canvas(spectrogramBitmap);
            history = new float[historySize][h];
        }
    }

    public void updateColumn(float[] magnitudes) {
        if (bitmapCanvas == null) return;
        
        int numBins = Math.min(magnitudes.length, height);
        
        // Update history for 3D
        float[] current = new float[height];
        for (int i = 0; i < numBins; i++) {
            float val = (float) (20 * Math.log10(magnitudes[i] + 1e-10) + 100) / 100f;
            current[height - 1 - i] = Math.max(0, Math.min(1, val));
        }
        history[historyIdx] = current;
        historyIdx = (historyIdx + 1) % historySize;

        // Update 2D bitmap (scrolling)
        Paint colPaint = new Paint();
        for (int i = 0; i < height; i++) {
            int colorIdx = (int) (current[i] * 255);
            colPaint.setColor(colors[colorIdx]);
            bitmapCanvas.drawPoint(currentColumn, i, colPaint);
        }
        
        currentColumn = (currentColumn + 1) % width;
        postInvalidate();
    }

    public void set3DMode(boolean mode) {
        this.is3DMode = mode;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (spectrogramBitmap == null) return;

        if (!is3DMode) {
            // Draw 2D with wrap-around
            Matrix matrix = new Matrix();
            canvas.drawBitmap(spectrogramBitmap, matrix, paint);
            // Draw a line for current position
            paint.setColor(Color.WHITE);
            canvas.drawLine(currentColumn, 0, currentColumn, height, paint);
        } else {
            // Draw 3D Projection
            draw3D(canvas);
        }
    }

    private void draw3D(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        paint.setStrokeWidth(2f);
        
        float stepX = (float) width / historySize;
        float scaleY = height * 0.5f;
        
        for (int i = 0; i < historySize; i++) {
            int idx = (historyIdx + i) % historySize;
            float[] data = history[idx];
            if (data == null) continue;
            
            float xBase = i * stepX;
            float zOffset = (float) i / historySize * 100f;
            
            for (int j = 0; j < height; j += 4) {
                float val = data[j];
                if (val < 0.05f) continue;
                
                int colorIdx = (int) (val * 255);
                paint.setColor(colors[colorIdx]);
                
                // Simple isometric projection
                float px = xBase + j * 0.5f - zOffset;
                float py = height - (val * scaleY) - (j * 0.2f) - (i * 2f);
                
                canvas.drawPoint(px, py, paint);
            }
        }
    }
}
