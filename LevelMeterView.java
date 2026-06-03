package com.ultraaudio.recorder.visualization;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Audio level meter showing dBA, dB SPL, Sones, and peak levels.
 */
public class LevelMeterView extends View {
    private Paint meterPaint;
    private Paint backgroundPaint;
    private Paint textPaint;
    private Paint peakPaint;
    
    private float currentLevel = -60f; // dB
    private float peakLevel = -60f;
    private float rmsLevel = 0f;
    private float dbSPL = 0f;
    private float dBA = 0f;
    private float sones = 0f;
    
    private float minDB = -60f;
    private float maxDB = 6f;
    
    private long lastPeakTime = 0;
    private static final long PEAK_HOLD_MS = 2000;
    
    public LevelMeterView(Context context) {
        super(context);
        init();
    }
    
    public LevelMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public LevelMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        meterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.parseColor("#1A1A1A"));
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        
        peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        peakPaint.setColor(Color.RED);
        peakPaint.setStrokeWidth(3f);
    }
    
    public void updateLevel(float rms, float peak, float dbSPL) {
        this.rmsLevel = rms;
        this.currentLevel = 20.0f * (float) Math.log10(rms + 1e-10f);
        this.dbSPL = dbSPL;
        
        float peakDB = 20.0f * (float) Math.log10(peak + 1e-10f);
        if (peakDB > peakLevel || System.currentTimeMillis() - lastPeakTime > PEAK_HOLD_MS) {
            peakLevel = peakDB;
            lastPeakTime = System.currentTimeMillis();
        }
        
        postInvalidate();
    }
    
    public void setDBA(float dba) { this.dBA = dba; }
    public void setSones(float sones) { this.sones = sones; }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;
        
        canvas.drawColor(Color.parseColor("#121212"));
        
        int meterHeight = 30;
        int meterY = 20;
        int textY = meterY + meterHeight + 30;
        
        // Draw meter background
        RectF meterRect = new RectF(60, meterY, width - 20, meterY + meterHeight);
        canvas.drawRoundRect(meterRect, 4, 4, backgroundPaint);
        
        // Draw meter level
        float normalized = (currentLevel - minDB) / (maxDB - minDB);
        normalized = Math.max(0, Math.min(1, normalized));
        
        RectF levelRect = new RectF(60, meterY, 60 + normalized * (width - 80), meterY + meterHeight);
        meterPaint.setShader(new LinearGradient(60, 0, width - 20, 0,
            new int[]{Color.parseColor("#00E676"), Color.parseColor("#FFEB3B"), Color.parseColor("#FF1744")},
            new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(levelRect, 4, 4, meterPaint);
        
        // Draw peak indicator
        float peakNorm = (peakLevel - minDB) / (maxDB - minDB);
        peakNorm = Math.max(0, Math.min(1, peakNorm));
        float peakX = 60 + peakNorm * (width - 80);
        canvas.drawLine(peakX, meterY, peakX, meterY + meterHeight, peakPaint);
        
        // Draw dB markers
        Paint markerPaint = new Paint(textPaint);
        markerPaint.setTextSize(18f);
        markerPaint.setColor(Color.parseColor("#888888"));
        for (float db = minDB; db <= maxDB; db += 10) {
            float x = 60 + ((db - minDB) / (maxDB - minDB)) * (width - 80);
            canvas.drawText(String.format("%.0f", db), x - 10, meterY + meterHeight + 18, markerPaint);
        }
        
        // Draw level readings
        int col1 = 20;
        int col2 = width / 3;
        int col3 = width * 2 / 3;
        
        textPaint.setTextSize(22f);
        textPaint.setColor(Color.parseColor("#00E676"));
        canvas.drawText(String.format("%.1f dB", currentLevel), col1, textY + 30, textPaint);
        
        textPaint.setColor(Color.parseColor("#2979FF"));
        canvas.drawText(String.format("%.1f dB SPL", dbSPL), col2, textY + 30, textPaint);
        
        textPaint.setColor(Color.parseColor("#FF9800"));
        canvas.drawText(String.format("%.1f dBA", dBA), col1, textY + 60, textPaint);
        
        textPaint.setColor(Color.parseColor("#E040FB"));
        canvas.drawText(String.format("%.2f Sones", sones), col2, textY + 60, textPaint);
        
        textPaint.setColor(Color.RED);
        canvas.drawText(String.format("Peak: %.1f dB", peakLevel), col3, textY + 30, textPaint);
    }
}
