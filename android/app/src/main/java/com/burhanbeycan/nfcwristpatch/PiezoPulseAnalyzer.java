package com.burhanbeycan.nfcwristpatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PiezoPulseAnalyzer {
    private PiezoPulseAnalyzer() {}

    public static PulseReading analyze(List<Float> rawSamples, float sampleRateHz, String patchId, String source) {
        PulseReading reading = PulseReading.demo();
        reading.patchId = cleanId(patchId);
        reading.source = source == null || source.isEmpty() ? "piezo sample burst" : source;
        reading.mode = "computed from piezo/silver-flake samples";
        reading.sensorMode = "piezoelectric film + silver-flake electrode, ADC samples";
        reading.sampleRateHz = sampleRateHz > 1f ? sampleRateHz : 50f;
        reading.samples.clear();
        reading.peakIndexes.clear();
        reading.measuredAtMillis = System.currentTimeMillis();

        if (rawSamples == null || rawSamples.size() < 12) {
            reading.quality = 25;
            reading.contact = "insufficient samples";
            return reading;
        }

        List<Float> detrended = detrend(rawSamples);
        List<Float> smooth = smooth(detrended, 5);
        reading.samples.addAll(smooth);

        Stats stats = stats(smooth);
        double range = stats.max - stats.min;
        double threshold = stats.mean + Math.max(0.18 * range, 0.65 * stats.std);
        int refractory = Math.max(8, Math.round(reading.sampleRateHz * 0.34f));
        List<Integer> peaks = new ArrayList<>();
        int lastPeak = -refractory;

        for (int i = 2; i < smooth.size() - 2; i++) {
            float v = smooth.get(i);
            boolean localMax = v >= smooth.get(i - 1) && v > smooth.get(i - 2) && v >= smooth.get(i + 1) && v > smooth.get(i + 2);
            if (localMax && v > threshold && i - lastPeak >= refractory) {
                peaks.add(i);
                lastPeak = i;
            }
        }

        reading.peakIndexes.addAll(peaks);
        if (peaks.size() >= 2) {
            double intervalSum = 0.0;
            double intervalSq = 0.0;
            int count = 0;
            for (int i = 1; i < peaks.size(); i++) {
                double interval = peaks.get(i) - peaks.get(i - 1);
                intervalSum += interval;
                intervalSq += interval * interval;
                count++;
            }
            double meanInterval = intervalSum / Math.max(1, count);
            double bpm = 60.0 * reading.sampleRateHz / meanInterval;
            double variance = Math.max(0.0, intervalSq / Math.max(1, count) - meanInterval * meanInterval);
            double cv = Math.sqrt(variance) / Math.max(1.0, meanInterval);

            reading.bpm = clamp(bpm, 30, 220);
            reading.hrvMs = clamp((Math.sqrt(variance) / reading.sampleRateHz) * 1000.0, 5, 180);
            reading.quality = clamp(96.0 - cv * 220.0 + Math.min(10.0, range * 8.0), 20, 99);
            reading.contact = reading.quality > 75 ? "good" : reading.quality > 55 ? "acceptable" : "weak contact";
            reading.motionArtifact = clamp(cv * 120.0, 0, 55);
        } else {
            reading.quality = 35;
            reading.contact = "no stable peaks";
            reading.mode = "sample burst present, pulse not resolved";
        }
        return reading;
    }

    private static List<Float> detrend(List<Float> input) {
        List<Float> out = new ArrayList<>(input.size());
        int window = Math.max(9, Math.min(31, input.size() / 6));
        for (int i = 0; i < input.size(); i++) {
            int start = Math.max(0, i - window);
            int end = Math.min(input.size() - 1, i + window);
            double sum = 0.0;
            int count = 0;
            for (int j = start; j <= end; j++) {
                sum += input.get(j);
                count++;
            }
            out.add((float) (input.get(i) - sum / Math.max(1, count)));
        }
        return out;
    }

    private static List<Float> smooth(List<Float> input, int window) {
        List<Float> out = new ArrayList<>(input.size());
        int half = Math.max(1, window / 2);
        for (int i = 0; i < input.size(); i++) {
            double sum = 0.0;
            int count = 0;
            for (int j = Math.max(0, i - half); j <= Math.min(input.size() - 1, i + half); j++) {
                sum += input.get(j);
                count++;
            }
            out.add((float) (sum / Math.max(1, count)));
        }
        return out;
    }

    private static Stats stats(List<Float> values) {
        Stats stats = new Stats();
        stats.min = Double.POSITIVE_INFINITY;
        stats.max = Double.NEGATIVE_INFINITY;
        for (float v : values) {
            stats.mean += v;
            stats.min = Math.min(stats.min, v);
            stats.max = Math.max(stats.max, v);
        }
        stats.mean /= Math.max(1, values.size());
        for (float v : values) {
            double d = v - stats.mean;
            stats.std += d * d;
        }
        stats.std = Math.sqrt(stats.std / Math.max(1, values.size()));
        return stats;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String cleanId(String id) {
        if (id == null) return "fab01";
        String cleaned = id.replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.isEmpty() ? "fab01" : cleaned.substring(0, Math.min(32, cleaned.length()));
    }

    private static final class Stats {
        double mean;
        double std;
        double min;
        double max;
    }

    public static String shortReport(PulseReading reading) {
        return String.format(Locale.US, "%.0f bpm · %.0f%% quality · %s", reading.bpm, reading.quality, reading.contact);
    }
}
