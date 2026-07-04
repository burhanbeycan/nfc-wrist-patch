package com.burhanbeycan.nfcwristpatch;

import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NfcPayloadParser {
    private NfcPayloadParser() {}

    public static PulseReading parse(Intent intent) {
        PulseReading fallback = PulseReading.demo();
        fallback.source = "manual app open";
        fallback.mode = "no NFC payload yet";
        if (intent == null) return fallback;

        String action = intent.getAction();
        Uri uri = intent.getData();
        String uid = tagUid(intent);

        if (uri != null) {
            PulseReading reading = fromUri(uri, uid);
            reading.source = sourceFromAction(action, "URI payload");
            return reading;
        }

        List<NdefMessage> messages = getNdefMessages(intent);
        for (NdefMessage message : messages) {
            for (NdefRecord record : message.getRecords()) {
                Uri recordUri = record.toUri();
                if (recordUri != null) {
                    PulseReading reading = fromUri(recordUri, uid);
                    reading.source = sourceFromAction(action, "NDEF URI record");
                    return reading;
                }
                String text = textFromRecord(record);
                if (!text.isEmpty()) {
                    PulseReading reading = fromText(text, uid);
                    reading.source = sourceFromAction(action, "NDEF text record");
                    return reading;
                }
            }
        }

        if (!uid.isEmpty()) {
            fallback.tagUid = uid;
            fallback.source = sourceFromAction(action, "NTAG213 UID only");
            fallback.mode = "tag opened app, no readable pulse payload";
        }
        return fallback;
    }

    public static PulseReading fromUri(Uri uri, String tagUid) {
        String patchId = param(uri, "p", param(uri, "id", "fab01"));
        String samples = param(uri, "s", param(uri, "samples", ""));
        float fs = (float) number(param(uri, "fs", "50"), 50);
        if (!samples.isEmpty()) {
            PulseReading reading = PiezoPulseAnalyzer.analyze(parseSamples(samples), fs, patchId, "NFC piezo sample burst");
            reading.tagUid = tagUid;
            reading.bleTarget = param(uri, "ble", "");
            return applyCommonParams(reading, uri, tagUid);
        }

        PulseReading reading = PulseReading.demo();
        reading.patchId = cleanId(patchId);
        reading.measuredAtMillis = System.currentTimeMillis();
        reading.tagUid = tagUid;
        reading.mode = "summary from NTAG213 URI";
        reading.source = "NTAG213 compact URI";
        reading.bpm = number(first(uri, "b", "bpm", "hr"), reading.bpm);
        reading.spo2 = number(first(uri, "o", "spo2"), reading.spo2);
        reading.skinTempC = number(first(uri, "t", "temp"), reading.skinTempC);
        reading.hrvMs = number(first(uri, "v", "hrv"), reading.hrvMs);
        reading.quality = number(first(uri, "q", "quality"), reading.quality);
        reading.motionArtifact = number(first(uri, "m", "motion"), reading.motionArtifact);
        reading.battery = number(first(uri, "bat", "battery"), reading.battery);
        reading.contact = param(uri, "contact", reading.quality > 75 ? "good" : "check contact");
        reading.sensorMode = param(uri, "src", "NTAG213 stored summary");
        reading.bleTarget = param(uri, "ble", "");
        return reading;
    }

    public static PulseReading fromText(String text, String tagUid) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("{")) {
            try {
                JSONObject json = new JSONObject(value);
                PulseReading reading = PulseReading.demo();
                reading.patchId = cleanId(json.optString("patchId", json.optString("p", "fab01")));
                reading.bpm = json.optDouble("bpm", json.optDouble("heartRateBpm", reading.bpm));
                reading.spo2 = json.optDouble("spo2", json.optDouble("spo2Percent", reading.spo2));
                reading.skinTempC = json.optDouble("temp", json.optDouble("skinTempC", reading.skinTempC));
                reading.hrvMs = json.optDouble("hrv", json.optDouble("hrvMs", reading.hrvMs));
                reading.quality = json.optDouble("quality", reading.quality);
                reading.battery = json.optDouble("battery", reading.battery);
                reading.contact = json.optString("contact", reading.contact);
                reading.sensorMode = json.optString("src", "JSON NDEF payload");
                reading.mode = "JSON summary from NDEF";
                reading.tagUid = tagUid;
                JSONArray array = json.optJSONArray("samples");
                if (array != null && array.length() > 12) {
                    List<Float> samples = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) samples.add((float) array.optDouble(i));
                    reading = PiezoPulseAnalyzer.analyze(samples, (float) json.optDouble("fs", 50), reading.patchId, "JSON piezo samples");
                    reading.tagUid = tagUid;
                }
                return reading;
            } catch (Exception ignored) {
            }
        }
        if (value.startsWith("wristpatch://") || value.startsWith("https://")) {
            return fromUri(Uri.parse(value), tagUid);
        }
        PulseReading reading = PulseReading.demo();
        reading.tagUid = tagUid;
        reading.mode = "plain text NFC payload";
        reading.source = value;
        return reading;
    }

    private static PulseReading applyCommonParams(PulseReading reading, Uri uri, String tagUid) {
        reading.tagUid = tagUid;
        reading.spo2 = number(first(uri, "o", "spo2"), reading.spo2);
        reading.skinTempC = number(first(uri, "t", "temp"), reading.skinTempC);
        reading.battery = number(first(uri, "bat", "battery"), reading.battery);
        return reading;
    }

    private static List<NdefMessage> getNdefMessages(Intent intent) {
        List<NdefMessage> messages = new ArrayList<>();
        Parcelable[] raw;
        if (Build.VERSION.SDK_INT >= 33) {
            raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Parcelable.class);
        } else {
            raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        }
        if (raw != null) {
            for (Parcelable item : raw) {
                if (item instanceof NdefMessage) messages.add((NdefMessage) item);
            }
        }
        return messages;
    }

    private static String textFromRecord(NdefRecord record) {
        try {
            if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN && matches(record.getType(), NdefRecord.RTD_TEXT)) {
                byte[] payload = record.getPayload();
                if (payload.length == 0) return "";
                int langLength = payload[0] & 0x3F;
                Charset charset = ((payload[0] & 0x80) == 0) ? StandardCharsets.UTF_8 : Charset.forName("UTF-16");
                return new String(payload, 1 + langLength, payload.length - 1 - langLength, charset);
            }
            if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                return new String(record.getPayload(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static boolean matches(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static String tagUid(Intent intent) {
        try {
            Tag tag;
            if (Build.VERSION.SDK_INT >= 33) {
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
            } else {
                tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            }
            if (tag == null || tag.getId() == null) return "";
            StringBuilder sb = new StringBuilder();
            byte[] id = tag.getId();
            for (int i = 0; i < id.length; i++) {
                if (i > 0) sb.append('-');
                sb.append(String.format(Locale.US, "%02X", id[i]));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<Float> parseSamples(String text) {
        List<Float> out = new ArrayList<>();
        String normalized = text.replace(';', ',').replace('|', ',');
        for (String part : normalized.split(",")) {
            try {
                if (!part.trim().isEmpty()) out.add(Float.parseFloat(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static String first(Uri uri, String a, String b) {
        String x = uri.getQueryParameter(a);
        return x != null ? x : uri.getQueryParameter(b);
    }

    private static String first(Uri uri, String a, String b, String c) {
        String x = uri.getQueryParameter(a);
        if (x != null) return x;
        x = uri.getQueryParameter(b);
        return x != null ? x : uri.getQueryParameter(c);
    }

    private static String param(Uri uri, String key, String fallback) {
        String value = uri.getQueryParameter(key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static double number(String raw, double fallback) {
        try {
            if (raw == null || raw.isEmpty()) return fallback;
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String cleanId(String id) {
        if (id == null) return "fab01";
        String cleaned = id.replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.isEmpty() ? "fab01" : cleaned.substring(0, Math.min(32, cleaned.length()));
    }

    private static String sourceFromAction(String action, String fallback) {
        if (action == null) return fallback;
        if (action.contains("NDEF")) return fallback;
        if (action.contains("TECH")) return "NFC technology dispatch";
        if (Intent.ACTION_VIEW.equals(action)) return "Android URI view dispatch";
        return fallback;
    }
}
