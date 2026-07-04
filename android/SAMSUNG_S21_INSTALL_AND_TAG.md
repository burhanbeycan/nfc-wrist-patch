# Install on Samsung Galaxy S21 and Open App from NTAG213

## 1. Build/install the app

Open the `android/` folder in Android Studio, connect the Galaxy S21, and press **Run**.

Or build and install from terminal:

```bash
cd android
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app package is:

```text
com.burhanbeycan.nfcwristpatch
```

## 2. Write the NTAG213 so it opens the app

Open the installed app once and press:

```text
Write NTAG213 launch tag
```

Then hold the NTAG213 label near the Galaxy S21.

The app writes an NDEF message containing:

```text
wristpatch://read?p=fab01&b=72&o=98.0&t=32.6&v=48&q=94&src=ntag213
```

plus an Android Application Record for:

```text
com.burhanbeycan.nfcwristpatch
```

Now, when the Galaxy S21 approaches the label, Android should open the native app screen.

## 3. For true measured heartbeat

A bare NTAG213 cannot read the piezo/silver-flake signal. The real pulse path must be:

```text
piezo/silver-flake electrode
→ charge amplifier or high-impedance instrumentation front end
→ analog filtering and protection
→ ADC
→ MCU signal processing
→ BLE JSON notification or dynamic NFC memory
→ Android app
```

Recommended urgent-demo route:

```text
NTAG213 opens app
BLE MCU sends real BPM/samples
App displays true measured pulse
```

Write this to NTAG213 when using BLE:

```text
wristpatch://read?p=fab01&ble=PiezoPatch
```

The app starts scanning for a BLE device whose name contains `PiezoPatch`.

## 4. BLE data expected by the app

The MCU should notify UTF-8 JSON on BLE service:

```text
6f8a0001-79a6-4a5c-8f53-0c0a7a201001
```

characteristic:

```text
6f8a0002-79a6-4a5c-8f53-0c0a7a201001
```

Recommended JSON summary:

```json
{"patchId":"fab01","bpm":76,"spo2":98,"temp":32.4,"hrv":45,"quality":93,"battery":84,"contact":"good"}
```

Or short sample burst:

```json
{"patchId":"fab01","fs":50,"samples":[0,4,9,20,44,80,42,12,3,0,0,5,12,26,60,95,50,15,4,0]}
```

The app computes BPM from peaks when a sample burst is received.

## 5. NFC troubleshooting

- Enable NFC on the Galaxy S21.
- Unlock the phone for first tests.
- Keep the NTAG213 antenna away from metal snaps, batteries, and wet conductive gel zones.
- Write the label as NDEF, not raw memory.
- If Android opens a browser instead of the app, rewrite the label from inside the app so the Android Application Record is included.
- Do not lock the tag read-only until the demo works reliably.
