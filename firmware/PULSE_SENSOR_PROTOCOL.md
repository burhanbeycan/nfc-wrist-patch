# Piezo/Silver-Flake Pulse Sensor Protocol

This protocol tells the sensor electronics what to send to the Android app.

## Why this is needed

The piezoelectric layer and silver-flake electrode can produce a pulse-related analog signal, but NTAG213 cannot digitize that signal. Add an analog front end and MCU:

```text
piezo/silver-flake material
→ high-input-impedance charge/voltage amplifier
→ band-pass filter around pulse band
→ ADC sampling
→ MCU peak detection or sample packet
→ BLE notification or dynamic NFC update
```

## BLE GATT profile used by the Android app

Service UUID:

```text
6f8a0001-79a6-4a5c-8f53-0c0a7a201001
```

Notify characteristic UUID:

```text
6f8a0002-79a6-4a5c-8f53-0c0a7a201001
```

Device name should contain:

```text
PiezoPatch
```

The NTAG213 can contain:

```text
wristpatch://read?p=fab01&ble=PiezoPatch
```

When the phone taps the NTAG213, the app opens and scans for the BLE sensor.

## JSON summary notification

Send this when the MCU computes BPM:

```json
{"patchId":"fab01","bpm":76,"quality":93,"battery":84,"contact":"good"}
```

Optional fields:

```json
{
  "spo2": 98,
  "temp": 32.4,
  "hrv": 45,
  "motion": 8
}
```

## JSON sample notification

Send this when the phone app should compute BPM:

```json
{"patchId":"fab01","fs":50,"samples":[0,4,9,20,44,80,42,12,3,0,0,5,12,26,60,95,50,15,4,0]}
```

For real analysis, send at least 5 to 10 seconds of samples when possible. A very short NTAG213 payload cannot hold enough waveform data for reliable pulse; BLE is preferred.

## Analog front-end hints

- Use a high-input-impedance charge amplifier or voltage buffer so the piezo film is not loaded.
- Add input protection because piezo materials can generate high transient voltages under impact.
- Use a band-pass filter suitable for pulse mechanical vibration, then tune with real data.
- Sample at 50 to 200 Hz for a clean pulse envelope.
- Send signal quality and contact state, not only BPM.
- Validate against ECG or a reference pulse sensor before claiming accuracy.

## Data truth rule

The app displays true heartbeat only if the value came from the active sensor electronics. If the value is stored only in NTAG213, it is a stored snapshot, not a new measurement.
