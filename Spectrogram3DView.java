package com.ultraaudio.recorder.visualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.LinkedList;

/**
 * High-end 3D Spectrogram with touch-controlled rotation, zoom, and perspective.
 * Uses a software-rendered 3D projection for maximum compatibility.
 */
public class Spectrogram3DView extends View {
    private Paint linePaint;
    private Paint fillPaint;
    
    private LinkedList<float[]> history = new LinkedList<>();
    private int maxHistory = 50;
    private int fftSize = 512;
    
    // 3D Projection parameters
    private float rotationX = 45f;
    private float rotationY = -30f;
    private float zoom = 1.0f;
    
    private float lastTouchX, lastTouchY;
    private boolean isMultiTouch = false;

    public Spectrogram3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(2f);
        linePaint.setStyle(Paint.Style.STROKE);
        
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void updateData(float[] magnitudes) {
        float[] copy = new float[magnitudes.length / 2];
        System.arraycopy(magnitudes, 0, copy, 0, copy.length);
        history.addFirst(copy);
        if (history.size() > maxHistory) history.removeLast();
        postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                isMultiTouch = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !isMultiTouch) {
                    rotationY += (x - lastTouchX) * 0.5f;
                    rotationX += (y - lastTouchY) * 0.5f;
                } else if (event.getPointerCount() == 2) {
                    isMultiTouch = true;
                    // Zoom logic (simplified pinch)
                    float dx = event.getX(0) - event.getX(1);
                    float dy = event.getY(0) - event.getY(1);
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (lastTouchX > 0) zoom *= (dist / lastTouchX);
                    lastTouchX = dist;
                }
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (history.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(Color.BLACK);

        float centerX = width / 2f;
        float centerY = height / 2f;
        float scale = Math.min(width, height) * 0.5f * zoom;

        // Draw from back to front for correct depth
        for (int i = history.size() - 1; i >= 0; i--) {
            float[] magnitudes = history.get(i);
            float z = (float) i / maxHistory; // 0 (front) to 1 (back)
            
            Path path = new Path();
            boolean first = true;
            
            // Set color based on depth
            int color = Color.HSVToColor(new float[]{200f + z * 100f, 0.8f, 0.9f - z * 0.5f});
            linePaint.setColor(color);
            
            for (int j = 0; j < magnitudes.length; j += 4) {
                float xNorm = (float) j / magnitudes.length - 0.5f;
                float yNorm = magnitudes[j] * 2.0f; // Height
                float zNorm = z - 0.5f;

                // 3D Rotation
                float[] rotated = rotate(xNorm, -yNorm, zNorm);
                float px = centerX + rotated[0] * scale;
                float py = centerY + rotated[1] * scale;

                if (first) {
                    path.moveTo(px, py);
                    first = false;
                } else {
                    path.lineTo(px, py);
                }
            }
            canvas.drawPath(path, linePaint);
        }
    }

    private float[] rotate(float x, float y, float z) {
        // Rotate around X
        float radX = (float) Math.toRadians(rotationX);
        float cosX = (float) Math.cos(radX);
        float sinX = (float) Math.sin(radX);
        float y1 = y * cosX - z * sinX;
        float z1 = y * sinX + z * cosX;

        // Rotate around Y
        float radY = (float) Math.toRadians(rotationY);
        float cosY = (float) Math.cos(radY);
        float sinY = (float) Math.sin(radY);
        float x2 = x * cosY + z1 * sinY;
        float z2 = -x * sinY + z1 * cosY;

        return new float[]{x2, y1, z2};
    }
}
