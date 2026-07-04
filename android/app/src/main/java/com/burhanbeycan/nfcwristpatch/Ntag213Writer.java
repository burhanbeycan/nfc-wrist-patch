package com.burhanbeycan.nfcwristpatch;

import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Ntag213Writer {
    public static final int NTAG213_USER_BYTES = 144;

    private Ntag213Writer() {}

    public static Uri launchUri(PulseReading reading) {
        PulseReading r = reading == null ? PulseReading.demo() : reading;
        return Uri.parse("wristpatch://read")
                .buildUpon()
                .appendQueryParameter("p", clean(r.patchId))
                .appendQueryParameter("b", String.valueOf(Math.round(r.bpm)))
                .appendQueryParameter("o", oneDecimal(r.spo2))
                .appendQueryParameter("t", oneDecimal(r.skinTempC))
                .appendQueryParameter("v", String.valueOf(Math.round(r.hrvMs)))
                .appendQueryParameter("q", String.valueOf(Math.round(r.quality)))
                .appendQueryParameter("src", "ntag213")
                .build();
    }

    public static NdefMessage buildMessage(Uri uri, String packageName) {
        NdefRecord uriRecord = NdefRecord.createUri(uri);
        NdefRecord appRecord = NdefRecord.createApplicationRecord(packageName);
        return new NdefMessage(new NdefRecord[]{uriRecord, appRecord});
    }

    public static int estimateBytes(NdefMessage message) {
        return message == null ? 0 : message.toByteArray().length;
    }

    public static WriteResult write(Tag tag, NdefMessage message) {
        if (tag == null) return WriteResult.fail("No NFC tag detected.");
        int bytes = estimateBytes(message);
        if (bytes > NTAG213_USER_BYTES) {
            return WriteResult.fail("NDEF message is " + bytes + " bytes; NTAG213 practical user memory is about " + NTAG213_USER_BYTES + " bytes.");
        }

        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                if (!ndef.isWritable()) return WriteResult.fail("Tag is not writable.");
                if (ndef.getMaxSize() > 0 && bytes > ndef.getMaxSize()) {
                    return WriteResult.fail("Message is larger than tag max NDEF size: " + ndef.getMaxSize() + " bytes.");
                }
                ndef.writeNdefMessage(message);
                return WriteResult.ok("NTAG213 written: " + bytes + " bytes.");
            } catch (IOException | FormatException | SecurityException error) {
                return WriteResult.fail("NDEF write failed: " + error.getMessage());
            } finally {
                try { ndef.close(); } catch (Exception ignored) {}
            }
        }

        NdefFormatable formatable = NdefFormatable.get(tag);
        if (formatable != null) {
            try {
                formatable.connect();
                formatable.format(message);
                return WriteResult.ok("Tag formatted and written: " + bytes + " bytes.");
            } catch (IOException | FormatException | SecurityException error) {
                return WriteResult.fail("Format/write failed: " + error.getMessage());
            } finally {
                try { formatable.close(); } catch (Exception ignored) {}
            }
        }
        return WriteResult.fail("This tag is not NDEF writable.");
    }

    public static String textRecord(String text) {
        String safe = text == null ? "" : text;
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String clean(String id) {
        if (id == null) return "fab01";
        String cleaned = id.replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.isEmpty() ? "fab01" : cleaned.substring(0, Math.min(32, cleaned.length()));
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    public static final class WriteResult {
        public final boolean success;
        public final String message;

        private WriteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static WriteResult ok(String message) {
            return new WriteResult(true, message);
        }

        public static WriteResult fail(String message) {
            return new WriteResult(false, message);
        }
    }
}
