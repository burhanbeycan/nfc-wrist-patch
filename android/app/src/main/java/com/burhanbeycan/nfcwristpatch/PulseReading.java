package com.burhanbeycan.nfcwristpatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PulseReading {
    public String patchId = "fab01";
    public String source = "demo";
    public String mode = "demo";
    public String tagUid = "";
    public String contact = "good";
    public String sensorMode = "piezo/silver-flake ready";
    public String bleTarget = "";
    public long measuredAtMillis = System.currentTimeMillis();

    public double bpm = 72.0;
    public double spo2 = 98.0;
    public double skinTempC = 32.6;
    public double hrvMs = 48.0;
    public double quality = 94.0;
    public double motionArtifact = 8.0;
    public double battery = 86.0;
    public float sampleRateHz = 50.0f;
    public final List<Float> samples = new ArrayList<>();
    public final List<Integer> peakIndexes = new ArrayList<>();

    public static PulseReading demo() {
        PulseReading reading = new PulseReading();
        reading.patchId = "fab01";
        reading.source = "demo heartbeat";
        reading.mode = "stored NTAG213 demo";
        reading.sensorMode = "simulated piezo pulse waveform";
        reading.samples.clear();
        for (int i = 0; i < 260; i++) {
            double t = i / 50.0;
            double beat = Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * 1.2 * t)), 9.0);
            double dicrotic = 0.26 * Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * 2.4 * t - 0.9)), 6.0);
            double baseline = 0.08 * Math.sin(2.0 * Math.PI * 0.2 * t);
            reading.samples.add((float) (baseline + beat + dicrotic));
        }
        reading.peakIndexes.clear();
        reading.peakIndexes.add(10);
        reading.peakIndexes.add(52);
        reading.peakIndexes.add(94);
        reading.peakIndexes.add(136);
        reading.peakIndexes.add(178);
        reading.peakIndexes.add(220);
        return reading;
    }

    public PulseReading copy() {
        PulseReading copy = new PulseReading();
        copy.patchId = patchId;
        copy.source = source;
        copy.mode = mode;
        copy.tagUid = tagUid;
        copy.contact = contact;
        copy.sensorMode = sensorMode;
        copy.bleTarget = bleTarget;
        copy.measuredAtMillis = measuredAtMillis;
        copy.bpm = bpm;
        copy.spo2 = spo2;
        copy.skinTempC = skinTempC;
        copy.hrvMs = hrvMs;
        copy.quality = quality;
        copy.motionArtifact = motionArtifact;
        copy.battery = battery;
        copy.sampleRateHz = sampleRateHz;
        copy.samples.addAll(samples);
        copy.peakIndexes.addAll(peakIndexes);
        return copy;
    }

    public String measuredAtLabel() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(measuredAtMillis));
    }

    public String summaryLine() {
        return String.format(Locale.US, "%s · %.0f bpm · %.0f%% quality", patchId, bpm, quality);
    }
}
