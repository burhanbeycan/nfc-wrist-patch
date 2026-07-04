package com.burhanbeycan.nfcwristpatch;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BlePulseClient {
    public static final UUID SERVICE_UUID = UUID.fromString("6f8a0001-79a6-4a5c-8f53-0c0a7a201001");
    public static final UUID CHAR_UUID = UUID.fromString("6f8a0002-79a6-4a5c-8f53-0c0a7a201001");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private Listener listener;
    private String targetName = "PiezoPatch";

    public interface Listener {
        void onStatus(String status);
        void onReading(PulseReading reading);
        void onError(String error);
    }

    public void start(Context context, String target, Listener listener) {
        this.listener = listener;
        this.targetName = target == null || target.isEmpty() ? "PiezoPatch" : target;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            emitError("Bluetooth is not enabled.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            emitError("BLE scanner is unavailable.");
            return;
        }
        try {
            emitStatus("Scanning BLE sensor: " + this.targetName);
            scanner.startScan(scanCallback);
        } catch (SecurityException error) {
            emitError("BLE permission missing: " + error.getMessage());
        }
    }

    public void stop() {
        try {
            if (scanner != null) scanner.stopScan(scanCallback);
        } catch (Exception ignored) {}
        try {
            if (gatt != null) gatt.close();
        } catch (Exception ignored) {}
        scanner = null;
        gatt = null;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = safeName(device);
            if (name.toLowerCase(Locale.US).contains(targetName.toLowerCase(Locale.US))) {
                try {
                    if (scanner != null) scanner.stopScan(this);
                    emitStatus("Connecting to " + name);
                    gatt = device.connectGatt(null, false, gattCallback);
                } catch (SecurityException error) {
                    emitError("BLE connect permission missing: " + error.getMessage());
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            emitError("BLE scan failed: " + errorCode);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                emitStatus("BLE connected. Discovering pulse service.");
                try { gatt.discoverServices(); } catch (SecurityException error) { emitError(error.getMessage()); }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                emitStatus("BLE disconnected.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattCharacteristic characteristic = null;
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) characteristic = service.getCharacteristic(CHAR_UUID);
            if (characteristic == null) characteristic = findNotifyCharacteristic(gatt);
            if (characteristic == null) {
                emitError("No notify characteristic found. Firmware should expose JSON pulse notifications.");
                return;
            }
            try {
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor cccd = characteristic.getDescriptor(CCCD_UUID);
                if (cccd != null) {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(cccd);
                }
                emitStatus("Listening for real piezo pulse notifications.");
            } catch (SecurityException error) {
                emitError("BLE notify permission missing: " + error.getMessage());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleBleBytes(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleBleBytes(value);
        }
    };

    private BluetoothGattCharacteristic findNotifyCharacteristic(BluetoothGatt gatt) {
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) return c;
            }
        }
        return null;
    }

    private void handleBleBytes(byte[] value) {
        if (value == null || value.length == 0) return;
        String payload = new String(value, StandardCharsets.UTF_8).trim();
        PulseReading reading = parseBlePayload(payload);
        reading.source = "BLE live piezo sensor";
        reading.sensorMode = "real piezo/silver-flake stream via MCU BLE";
        if (listener != null) listener.onReading(reading);
    }

    private PulseReading parseBlePayload(String payload) {
        try {
            if (payload.startsWith("{")) {
                JSONObject json = new JSONObject(payload);
                String patchId = json.optString("patchId", json.optString("p", "fab01"));
                JSONArray samplesArray = json.optJSONArray("samples");
                if (samplesArray != null && samplesArray.length() > 12) {
                    List<Float> samples = new ArrayList<>();
                    for (int i = 0; i < samplesArray.length(); i++) samples.add((float) samplesArray.optDouble(i));
                    PulseReading reading = PiezoPulseAnalyzer.analyze(samples, (float) json.optDouble("fs", 50), patchId, "BLE sample stream");
                    applyJsonSummary(json, reading);
                    return reading;
                }
                PulseReading reading = PulseReading.demo();
                reading.patchId = patchId;
                applyJsonSummary(json, reading);
                reading.mode = "BLE summary from MCU";
                return reading;
            }
            String normalized = payload.replace(';', '&').replace(',', '&');
            if (normalized.contains("=")) return NfcPayloadParser.fromUri(Uri.parse("wristpatch://read?" + normalized), "");
            List<Float> samples = new ArrayList<>();
            for (String part : payload.split(",")) {
                if (!part.trim().isEmpty()) samples.add(Float.parseFloat(part.trim()));
            }
            return PiezoPulseAnalyzer.analyze(samples, 50, "fab01", "BLE raw samples");
        } catch (Exception error) {
            PulseReading reading = PulseReading.demo();
            reading.mode = "BLE payload parse error";
            reading.source = error.getMessage();
            reading.quality = 30;
            return reading;
        }
    }

    private void applyJsonSummary(JSONObject json, PulseReading reading) {
        reading.bpm = json.optDouble("bpm", json.optDouble("heartRateBpm", reading.bpm));
        reading.spo2 = json.optDouble("spo2", json.optDouble("spo2Percent", reading.spo2));
        reading.skinTempC = json.optDouble("temp", json.optDouble("skinTempC", reading.skinTempC));
        reading.hrvMs = json.optDouble("hrv", json.optDouble("hrvMs", reading.hrvMs));
        reading.quality = json.optDouble("quality", reading.quality);
        reading.battery = json.optDouble("battery", reading.battery);
        reading.contact = json.optString("contact", reading.contact);
        reading.measuredAtMillis = System.currentTimeMillis();
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "" : name;
        } catch (SecurityException ignored) {
            return "";
        }
    }

    private void emitStatus(String status) {
        if (listener != null) listener.onStatus(status);
    }

    private void emitError(String error) {
        if (listener != null) listener.onError(error);
    }
}
