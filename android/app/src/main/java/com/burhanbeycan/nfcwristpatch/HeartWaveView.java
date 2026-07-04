package com.burhanbeycan.nfcwristpatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class HeartWaveView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> samples = new ArrayList<>();
    private final List<Integer> peaks = new ArrayList<>();
    private String label = "Waiting for NFC patch";

    public HeartWaveView(Context context) {
        super(context);
        setMinimumHeight(dp(220));
        text.setColor(Color.argb(180, 238, 247, 255));
        text.setTextSize(dp(12));
    }

    public void setReading(PulseReading reading) {
        samples.clear();
        peaks.clear();
        if (reading == null || reading.samples.isEmpty()) {
            PulseReading demo = PulseReading.demo();
            samples.addAll(demo.samples);
            peaks.addAll(demo.peakIndexes);
            label = "demo piezo waveform";
        } else {
            samples.addAll(reading.samples);
            peaks.addAll(reading.peakIndexes);
            label = reading.mode + " · " + reading.source;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(12, 27, 46));
        canvas.drawRoundRect(0, 0, w, h, dp(22), dp(22), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(32, 255, 255, 255));
        for (int i = 1; i < 5; i++) {
            float y = h * i / 5f;
            canvas.drawLine(dp(16), y, w - dp(16), y, paint);
        }
        if (samples.isEmpty()) return;

        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float v : samples) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (Math.abs(max - min) < 0.001f) max = min + 1f;

        float left = dp(18);
        float right = w - dp(18);
        float top = dp(34);
        float bottom = h - dp(42);
        Path path = new Path();
        for (int i = 0; i < samples.size(); i++) {
            float x = left + (right - left) * i / Math.max(1, samples.size() - 1);
            float y = bottom - ((samples.get(i) - min) / (max - min)) * (bottom - top);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setStrokeWidth(dp(3.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.rgb(95, 242, 209));
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(138, 180, 255));
        for (int peak : peaks) {
            if (peak < 0 || peak >= samples.size()) continue;
            float x = left + (right - left) * peak / Math.max(1, samples.size() - 1);
            float y = bottom - ((samples.get(peak) - min) / (max - min)) * (bottom - top);
            canvas.drawCircle(x, y, dp(4), paint);
        }

        canvas.drawText("piezo pulse waveform", dp(16), dp(22), text);
        String safe = label.length() > 56 ? label.substring(0, 56) + "..." : label;
        canvas.drawText(safe, dp(16), h - dp(16), text);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
