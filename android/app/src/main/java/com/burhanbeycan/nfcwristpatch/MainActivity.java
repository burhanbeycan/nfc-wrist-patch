package com.burhanbeycan.nfcwristpatch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BLE_PERMISSION_REQUEST = 501;

    private NfcAdapter nfcAdapter;
    private final BlePulseClient bleClient = new BlePulseClient();
    private PulseReading current = PulseReading.demo();
    private String pendingBleTarget = "";

    private TextView bpmView;
    private TextView patchView;
    private TextView sourceView;
    private TextView statusView;
    private TextView noticeView;
    private TextView qualityView;
    private TextView spo2View;
    private TextView tempView;
    private TextView hrvView;
    private TextView batteryView;
    private TextView contactView;
    private TextView nfcView;
    private TextView memoryView;
    private HeartWaveView waveView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(8, 17, 31));
        window.setNavigationBarColor(Color.rgb(8, 17, 31));
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        buildUi();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableNfcWriterMode();
    }

    @Override
    protected void onDestroy() {
        bleClient.stop();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(Color.rgb(8, 17, 31));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = card();
        hero.setPadding(dp(22), dp(24), dp(22), dp(24));
        TextView eyebrow = text("NTAG213 · PIEZO · SILVER FLAKE", 12, Color.rgb(95, 242, 209), Typeface.BOLD);
        TextView title = text("Fabric Pulse Patch", 34, Color.rgb(238, 247, 255), Typeface.BOLD);
        title.setLetterSpacing(-0.04f);
        TextView lead = text("Tap the Samsung Galaxy S21 to the NTAG213 label. The label opens this app; real pulse values must come from piezo/silver-flake electronics through BLE, dynamic NFC, or a written summary.", 15, Color.rgb(190, 210, 232), Typeface.NORMAL);
        lead.setLineSpacing(0, 1.18f);
        hero.addView(eyebrow);
        hero.addView(title);
        hero.addView(lead);
        root.addView(hero);

        LinearLayout heartCard = card();
        heartCard.setGravity(Gravity.CENTER_HORIZONTAL);
        bpmView = text("--", 76, Color.rgb(238, 247, 255), Typeface.BOLD);
        bpmView.setGravity(Gravity.CENTER);
        TextView bpmLabel = text("beats per minute", 14, Color.rgb(157, 180, 207), Typeface.BOLD);
        bpmLabel.setGravity(Gravity.CENTER);
        patchView = pill("waiting for tag");
        statusView = text("Approach the NTAG213 label to open or update the app.", 15, Color.rgb(204, 224, 246), Typeface.NORMAL);
        statusView.setGravity(Gravity.CENTER);
        heartCard.addView(patchView);
        heartCard.addView(bpmView);
        heartCard.addView(bpmLabel);
        heartCard.addView(statusView);
        root.addView(heartCard);

        waveView = new HeartWaveView(this);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        waveParams.setMargins(0, 0, 0, dp(14));
        root.addView(waveView, waveParams);

        LinearLayout grid1 = new LinearLayout(this);
        grid1.setOrientation(LinearLayout.HORIZONTAL);
        grid1.setWeightSum(3);
        spo2View = metric(grid1, "SpO₂", "--", "oxygen");
        hrvView = metric(grid1, "HRV", "--", "RMSSD");
        tempView = metric(grid1, "Temp", "--", "skin °C");
        root.addView(grid1);

        LinearLayout grid2 = new LinearLayout(this);
        grid2.setOrientation(LinearLayout.HORIZONTAL);
        grid2.setWeightSum(3);
        qualityView = metric(grid2, "Quality", "--", "signal");
        batteryView = metric(grid2, "Battery", "--", "sensor");
        contactView = metric(grid2, "Contact", "--", "patch");
        root.addView(grid2);

        LinearLayout detail = card();
        detail.addView(sectionTitle("NFC and measurement source"));
        sourceView = row(detail, "Source", "--");
        nfcView = row(detail, "NFC UID", "--");
        memoryView = row(detail, "NTAG213 write size", "--");
        root.addView(detail);

        LinearLayout actions = card();
        actions.addView(sectionTitle("Demonstration controls"));
        Button write = button("Write NTAG213 launch tag");
        write.setOnClickListener(v -> armNtag213Writer());
        Button ble = button("Start real BLE piezo sensor scan");
        ble.setOnClickListener(v -> startBleSensor(current.bleTarget == null || current.bleTarget.isEmpty() ? "PiezoPatch" : current.bleTarget));
        Button demo = button("Load demo heartbeat");
        demo.setOnClickListener(v -> showReading(PulseReading.demo(), "Demo loaded. For physical truth, connect the piezo/silver-flake sensor to MCU/ADC and send data to the app."));
        actions.addView(write);
        actions.addView(ble);
        actions.addView(demo);
        noticeView = text("", 13, Color.rgb(157, 180, 207), Typeface.NORMAL);
        noticeView.setLineSpacing(0, 1.15f);
        actions.addView(noticeView);
        root.addView(actions);

        setContentView(scroll);
    }

    private void handleIncomingIntent(Intent intent) {
        PulseReading reading = NfcPayloadParser.parse(intent);
        showReading(reading, "Incoming NFC/app intent processed.");
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (reading.bleTarget != null && !reading.bleTarget.isEmpty()) {
            startBleSensor(reading.bleTarget);
        } else if (action.contains("NFC") && reading.mode.contains("no readable")) {
            setNotice("NTAG213 opened the app, but no pulse payload was found. Press Write NTAG213 launch tag to write a compact app-launch record.");
        }
    }

    private void showReading(PulseReading reading, String notice) {
        current = reading == null ? PulseReading.demo() : reading;
        bpmView.setText(String.format(Locale.US, "%.0f", current.bpm));
        patchView.setText(current.patchId + " · " + current.mode);
        statusView.setText(statusFor(current));
        sourceView.setText(current.source + " · " + current.sensorMode);
        nfcView.setText(current.tagUid == null || current.tagUid.isEmpty() ? "not scanned" : current.tagUid);
        spo2View.setText(String.format(Locale.US, "%.0f%%", current.spo2));
        hrvView.setText(String.format(Locale.US, "%.0f ms", current.hrvMs));
        tempView.setText(String.format(Locale.US, "%.1f °C", current.skinTempC));
        qualityView.setText(String.format(Locale.US, "%.0f%%", current.quality));
        batteryView.setText(String.format(Locale.US, "%.0f%%", current.battery));
        contactView.setText(current.contact);
        Uri uri = Ntag213Writer.launchUri(current);
        int bytes = Ntag213Writer.estimateBytes(Ntag213Writer.buildMessage(uri, getPackageName()));
        memoryView.setText(bytes + " / " + Ntag213Writer.NTAG213_USER_BYTES + " bytes");
        waveView.setReading(current);
        setNotice(notice);
    }

    private String statusFor(PulseReading reading) {
        if (reading.bpm < 45) return "Very low pulse flag. Re-check sensor contact and reference measurement.";
        if (reading.bpm > 120) return "Elevated pulse flag. Confirm with reference ECG/pulse sensor and check motion.";
        if (reading.quality < 60) return "Pulse displayed with low quality. Improve fabric pressure, shielding, and ADC filtering.";
        return "Heartbeat result displayed. For true measurement, source must be MCU/BLE/dynamic-NFC data, not bare NTAG213 memory.";
    }

    private void armNtag213Writer() {
        if (nfcAdapter == null) {
            setNotice("This phone does not expose NFC to the app.");
            return;
        }
        Uri uri = Ntag213Writer.launchUri(current);
        NdefMessage message = Ntag213Writer.buildMessage(uri, getPackageName());
        int bytes = Ntag213Writer.estimateBytes(message);
        setNotice("Ready to write: " + uri + "\nHold the NTAG213 label near the Galaxy S21. Estimated NDEF size: " + bytes + " bytes.");
        try {
            nfcAdapter.enableReaderMode(this, tag -> {
                Ntag213Writer.WriteResult result = Ntag213Writer.write(tag, message);
                runOnUiThread(() -> {
                    setNotice(result.message + (result.success ? " Tap the label again to open the app." : ""));
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                    disableNfcWriterMode();
                });
            }, NfcAdapter.FLAG_READER_NFC_A, null);
        } catch (Exception error) {
            setNotice("NFC writer mode failed: " + error.getMessage());
        }
    }

    private void disableNfcWriterMode() {
        try {
            if (nfcAdapter != null) nfcAdapter.disableReaderMode(this);
        } catch (Exception ignored) {}
    }

    private void startBleSensor(String target) {
        pendingBleTarget = target == null || target.isEmpty() ? "PiezoPatch" : target;
        if (!ensureBlePermissions()) return;
        bleClient.stop();
        bleClient.start(this, pendingBleTarget, new BlePulseClient.Listener() {
            @Override public void onStatus(String status) { runOnUiThread(() -> setNotice(status)); }
            @Override public void onReading(PulseReading reading) { runOnUiThread(() -> showReading(reading, "Live BLE piezo/silver-flake reading received.")); }
            @Override public void onError(String error) { runOnUiThread(() -> setNotice(error)); }
        });
    }

    private boolean ensureBlePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), BLE_PERMISSION_REQUEST);
            setNotice("Bluetooth permission requested for the real piezo sensor bridge.");
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLE_PERMISSION_REQUEST) startBleSensor(pendingBleTarget);
    }

    private void setNotice(String message) {
        if (noticeView != null) noticeView.setText(message == null ? "" : message);
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(18, 39, 68), Color.rgb(7, 16, 28)});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), Color.argb(46, 159, 205, 255));
        view.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        view.setLayoutParams(lp);
        return view;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setPadding(0, dp(3), 0, dp(3));
        return v;
    }

    private TextView pill(String value) {
        TextView v = text(value, 12, Color.rgb(95, 242, 209), Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(30, 95, 242, 209));
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), Color.argb(90, 95, 242, 209));
        v.setBackground(bg);
        v.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 0, 0, dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView sectionTitle(String label) {
        TextView v = text(label, 16, Color.rgb(238, 247, 255), Typeface.BOLD);
        v.setPadding(0, 0, 0, dp(10));
        return v;
    }

    private TextView row(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView left = text(label, 13, Color.rgb(157, 180, 207), Typeface.NORMAL);
        TextView right = text(value, 13, Color.rgb(238, 247, 255), Typeface.BOLD);
        right.setGravity(Gravity.END);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        parent.addView(row);
        return right;
    }

    private TextView metric(LinearLayout parent, String label, String value, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(13), dp(12), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(42, 255, 255, 255));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.argb(26, 255, 255, 255));
        box.setBackground(bg);
        TextView labelView = text(label, 12, Color.rgb(157, 180, 207), Typeface.BOLD);
        TextView valueView = text(value, 22, Color.rgb(238, 247, 255), Typeface.BOLD);
        TextView hintView = text(hint, 10, Color.rgb(157, 180, 207), Typeface.NORMAL);
        box.addView(labelView);
        box.addView(valueView);
        box.addView(hintView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), dp(14));
        parent.addView(box, lp);
        return valueView;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(4, 17, 26));
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(95, 242, 209), Color.rgb(138, 180, 255)});
        bg.setCornerRadius(dp(999));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, 0, 0, dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
