package com.ultraaudio.recorder.visualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 2D/3D Topological map showing sound capture analytics of a microphone array.
 * Displays spatial sound distribution, directionality, and heatmap of audio levels.
 */
public class TopologicalMapView extends View {
    private Paint heatmapPaint;
    private Paint micPaint;
    private Paint textPaint;
    private Paint gridPaint;
    private Paint directionPaint;
    
    private List<MicPosition> micPositions = new ArrayList<>();
    private float[] micLevels;
    private float[] micDirections;
    private boolean is3DMode = false;
    
    // Heatmap data
    private float[][] heatmapData;
    private int heatmapResolution = 64;
    private int[] colorMap;
    
    // Time series data for playback
    private List<float[]> levelHistory = new ArrayList<>();
    private int maxHistory = 600; // 10 minutes at 1fps
    private int playbackIndex = -1;
    
    public static class MicPosition {
        public float x, y, z; // Normalized 0-1
        public String label;
        public float level; // Current dB level
        public float direction; // Estimated direction in degrees
        
        public MicPosition(float x, float y, float z, String label) {
            this.x = x; this.y = y; this.z = z; this.label = label;
        }
    }
    
    public TopologicalMapView(Context context) {
        super(context);
        init();
    }
    
    public TopologicalMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public TopologicalMapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        heatmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        micPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        micPaint.setColor(Color.WHITE);
        micPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#333333"));
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setStyle(Paint.Style.STROKE);
        
        directionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        directionPaint.setColor(Color.parseColor("#FF9800"));
        directionPaint.setStrokeWidth(3f);
        directionPaint.setStyle(Paint.Style.STROKE);
        
        heatmapData = new float[heatmapResolution][heatmapResolution];
        
        // Generate thermal color map
        colorMap = new int[256];
        for (int i = 0; i < 256; i++) {
            float t = i / 255f;
            int r, g, b, a;
            if (t < 0.25f) {
                float s = t / 0.25f;
                r = 0; g = 0; b = (int)(s * 200); a = (int)(s * 150);
            } else if (t < 0.5f) {
                float s = (t - 0.25f) / 0.25f;
                r = 0; g = (int)(s * 200); b = 200; a = 150 + (int)(s * 50);
            } else if (t < 0.75f) {
                float s = (t - 0.5f) / 0.25f;
                r = (int)(s * 255); g = 200 + (int)(s * 55); b = 200 - (int)(s * 200); a = 200;
            } else {
                float s = (t - 0.75f) / 0.25f;
                r = 255; g = 255 - (int)(s * 200); b = 0; a = 200 + (int)(s * 55);
            }
            colorMap[i] = Color.argb(a, Math.min(255, r), Math.min(255, g), Math.min(255, b));
        }
    }
    
    public void setMicPositions(List<MicPosition> positions) {
        this.micPositions = positions;
        micLevels = new float[positions.size()];
        micDirections = new float[positions.size()];
    }
    
    public void updateLevels(float[] levels, float[] directions) {
        if (levels != null) {
            this.micLevels = levels;
            // Store in history
            float[] copy = new float[levels.length];
            System.arraycopy(levels, 0, copy, 0, levels.length);
            levelHistory.add(copy);
            if (levelHistory.size() > maxHistory) {
                levelHistory.remove(0);
            }
        }
        if (directions != null) {
            this.micDirections = directions;
        }
        
        // Recompute heatmap
        computeHeatmap();
        postInvalidate();
    }
    
    private void computeHeatmap() {
        if (micPositions.isEmpty() || micLevels == null) return;
        
        // Inverse distance weighted interpolation
        for (int gy = 0; gy < heatmapResolution; gy++) {
            for (int gx = 0; gx < heatmapResolution; gx++) {
                float px = (float) gx / heatmapResolution;
                float py = (float) gy / heatmapResolution;
                
                float weightedSum = 0;
                float weightTotal = 0;
                
                for (int m = 0; m < micPositions.size(); m++) {
                    MicPosition mic = micPositions.get(m);
                    float dx = px - mic.x;
                    float dy = py - mic.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    
                    if (dist < 0.001f) {
                        heatmapData[gy][gx] = micLevels[m];
                        weightTotal = 1;
                        weightedSum = micLevels[m];
                        break;
                    }
                    
                    float weight = 1.0f / (dist * dist);
                    weightedSum += weight * micLevels[m];
                    weightTotal += weight;
                }
                
                if (weightTotal > 0) {
                    heatmapData[gy][gx] = weightedSum / weightTotal;
                }
            }
        }
    }
    
    public void set3DMode(boolean mode) {
        this.is3DMode = mode;
        postInvalidate();
    }
    
    public void setPlaybackIndex(int index) {
        this.playbackIndex = index;
        if (index >= 0 && index < levelHistory.size()) {
            micLevels = levelHistory.get(index);
            computeHeatmap();
            postInvalidate();
        }
    }
    
    public int getHistoryLength() {
        return levelHistory.size();
    }
    
    private float rotationX = 45f;
    private float rotationY = -30f;
    private float zoom = 1.0f;
    private float lastTouchX, lastTouchY;

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (!is3DMode) return super.onTouchEvent(event);
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                rotationY += (x - lastTouchX) * 0.5f;
                rotationX += (y - lastTouchY) * 0.5f;
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
        
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;
        
        canvas.drawColor(Color.parseColor("#0D1117"));
        
        if (is3DMode) {
            draw3D(canvas, width, height);
        } else {
            int mapSize = Math.min(width, height) - 60;
            int offsetX = (width - mapSize) / 2;
            int offsetY = (height - mapSize) / 2;
            drawHeatmap(canvas, offsetX, offsetY, mapSize);
            drawGrid(canvas, offsetX, offsetY, mapSize);
            drawMicrophones(canvas, offsetX, offsetY, mapSize);
            drawDirections(canvas, offsetX, offsetY, mapSize);
        }
        drawLegend(canvas, width, height);
    }

    private void draw3D(Canvas canvas, int width, int height) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        float scale = Math.min(width, height) * 0.4f * zoom;

        // Draw 3D Grid
        for (int i = 0; i <= 10; i++) {
            float t = i / 10f - 0.5f;
            draw3DLine(canvas, centerX, centerY, scale, t, 0, -0.5f, t, 0, 0.5f);
            draw3DLine(canvas, centerX, centerY, scale, -0.5f, 0, t, 0.5f, 0, t);
        }

        // Draw 3D Mic Positions and Levels
        for (int i = 0; i < micPositions.size(); i++) {
            MicPosition mic = micPositions.get(i);
            float level = (micLevels != null && i < micLevels.length) ? micLevels[i] : 0;
            float normalizedLevel = Math.max(0, Math.min(1, (level + 60) / 60f));
            
            float[] p = project(mic.x - 0.5f, -normalizedLevel * 0.5f, mic.y - 0.5f);
            float px = centerX + p[0] * scale;
            float py = centerY + p[1] * scale;
            
            micPaint.setColor(Color.WHITE);
            canvas.drawCircle(px, py, 10, micPaint);
            
            // Draw 3D Pillar for level
            float[] base = project(mic.x - 0.5f, 0, mic.y - 0.5f);
            float bx = centerX + base[0] * scale;
            float by = centerY + base[1] * scale;
            
            directionPaint.setColor(colorMap[(int)(normalizedLevel * 255)]);
            canvas.drawLine(bx, by, px, py, directionPaint);
        }
    }

    private void draw3DLine(Canvas canvas, float cx, float cy, float scale, float x1, float y1, float z1, float x2, float y2, float z2) {
        float[] p1 = project(x1, y1, z1);
        float[] p2 = project(x2, y2, z2);
        canvas.drawLine(cx + p1[0] * scale, cy + p1[1] * scale, cx + p2[0] * scale, cy + p2[1] * scale, gridPaint);
    }

    private float[] project(float x, float y, float z) {
        float radX = (float) Math.toRadians(rotationX);
        float cosX = (float) Math.cos(radX);
        float sinX = (float) Math.sin(radX);
        float y1 = y * cosX - z * sinX;
        float z1 = y * sinX + z * cosX;

        float radY = (float) Math.toRadians(rotationY);
        float cosY = (float) Math.cos(radY);
        float sinY = (float) Math.sin(radY);
        float x2 = x * cosY + z1 * sinY;
        
        return new float[]{x2, y1};
    }
    
    private void drawHeatmap(Canvas canvas, int offsetX, int offsetY, int mapSize) {
        float cellWidth = (float) mapSize / heatmapResolution;
        float cellHeight = (float) mapSize / heatmapResolution;
        
        // Find min/max for normalization
        float minLevel = Float.MAX_VALUE, maxLevel = Float.MIN_VALUE;
        for (int y = 0; y < heatmapResolution; y++) {
            for (int x = 0; x < heatmapResolution; x++) {
                if (heatmapData[y][x] < minLevel) minLevel = heatmapData[y][x];
                if (heatmapData[y][x] > maxLevel) maxLevel = heatmapData[y][x];
            }
        }
        
        float range = maxLevel - minLevel;
        if (range < 1) range = 1;
        
        for (int y = 0; y < heatmapResolution; y++) {
            for (int x = 0; x < heatmapResolution; x++) {
                float normalized = (heatmapData[y][x] - minLevel) / range;
                int colorIdx = (int) (normalized * 255);
                colorIdx = Math.max(0, Math.min(255, colorIdx));
                
                heatmapPaint.setColor(colorMap[colorIdx]);
                canvas.drawRect(
                    offsetX + x * cellWidth,
                    offsetY + y * cellHeight,
                    offsetX + (x + 1) * cellWidth,
                    offsetY + (y + 1) * cellHeight,
                    heatmapPaint
                );
            }
        }
    }
    
    private void drawGrid(Canvas canvas, int offsetX, int offsetY, int mapSize) {
        for (int i = 0; i <= 4; i++) {
            float pos = mapSize * i / 4f;
            canvas.drawLine(offsetX + pos, offsetY, offsetX + pos, offsetY + mapSize, gridPaint);
            canvas.drawLine(offsetX, offsetY + pos, offsetX + mapSize, offsetY + pos, gridPaint);
        }
        
        // Border
        canvas.drawRect(offsetX, offsetY, offsetX + mapSize, offsetY + mapSize, gridPaint);
    }
    
    private void drawMicrophones(Canvas canvas, int offsetX, int offsetY, int mapSize) {
        for (int i = 0; i < micPositions.size(); i++) {
            MicPosition mic = micPositions.get(i);
            float cx = offsetX + mic.x * mapSize;
            float cy = offsetY + mic.y * mapSize;
            
            // Draw glow based on level
            float level = (micLevels != null && i < micLevels.length) ? micLevels[i] : 0;
            float normalizedLevel = Math.max(0, Math.min(1, (level + 60) / 60f));
            
            Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            int glowColor = colorMap[(int)(normalizedLevel * 255)];
            glowPaint.setShader(new RadialGradient(cx, cy, 30,
                glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, 30, glowPaint);
            
            // Draw mic dot
            micPaint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, 8, micPaint);
            
            // Draw label
            canvas.drawText(mic.label, cx, cy - 15, textPaint);
            
            // Draw level text
            Paint levelTextPaint = new Paint(textPaint);
            levelTextPaint.setTextSize(18f);
            levelTextPaint.setColor(Color.parseColor("#CCCCCC"));
            canvas.drawText(String.format("%.1f dB", level), cx, cy + 25, levelTextPaint);
        }
    }
    
    private void drawDirections(Canvas canvas, int offsetX, int offsetY, int mapSize) {
        if (micDirections == null) return;
        
        for (int i = 0; i < micPositions.size() && i < micDirections.length; i++) {
            MicPosition mic = micPositions.get(i);
            float cx = offsetX + mic.x * mapSize;
            float cy = offsetY + mic.y * mapSize;
            
            float angle = (float) Math.toRadians(micDirections[i]);
            float arrowLength = 40;
            float endX = cx + (float) Math.cos(angle) * arrowLength;
            float endY = cy + (float) Math.sin(angle) * arrowLength;
            
            canvas.drawLine(cx, cy, endX, endY, directionPaint);
            
            // Arrowhead
            float headAngle1 = angle + (float) Math.PI * 0.8f;
            float headAngle2 = angle - (float) Math.PI * 0.8f;
            canvas.drawLine(endX, endY, 
                endX + (float) Math.cos(headAngle1) * 10,
                endY + (float) Math.sin(headAngle1) * 10, directionPaint);
            canvas.drawLine(endX, endY,
                endX + (float) Math.cos(headAngle2) * 10,
                endY + (float) Math.sin(headAngle2) * 10, directionPaint);
        }
    }
    
    private void drawLegend(Canvas canvas, int width, int height) {
        // Color scale legend
        int legendX = width - 40;
        int legendY = 30;
        int legendHeight = height - 60;
        int legendWidth = 20;
        
        for (int i = 0; i < legendHeight; i++) {
            float t = 1.0f - (float) i / legendHeight;
            int colorIdx = (int) (t * 255);
            heatmapPaint.setColor(colorMap[colorIdx]);
            canvas.drawLine(legendX, legendY + i, legendX + legendWidth, legendY + i, heatmapPaint);
        }
        
        Paint legendText = new Paint(textPaint);
        legendText.setTextSize(16f);
        legendText.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("High", legendX - 5, legendY + 10, legendText);
        canvas.drawText("Low", legendX - 5, legendY + legendHeight, legendText);
    }
}
