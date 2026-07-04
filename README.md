# NTAG213 Fabric Wrist Patch Heartbeat Dashboard + Native Android App

Professional tap-to-open system for a fabric-integrated NFC wrist patch.

The repository now contains two demonstration layers:

1. **Web dashboard**: NTAG213 opens a GitHub Pages heartbeat screen.
2. **Native Android app**: Samsung Galaxy S21 opens a sideloaded app directly from the NTAG213 label and shows the heartbeat GUI.

## Target workflow

1. Integrate an **NTAG213 NFC inlay** on the top layer of a fabric wrist patch.
2. Install the native Android APK on the Samsung Galaxy S21.
3. Write the NTAG213 with a short NDEF URI plus Android Application Record.
4. Bring the phone close to the NTAG213 label.
5. Android opens the app and displays heart rate, SpO₂, HRV, skin temperature, signal quality, battery, patch identity, alerts, and waveform.

> Important: the piezoelectric material and silver-flake electrode can be the pulse-sensing layer, but **NTAG213 cannot digitize the piezo signal by itself**. True heartbeat needs a front-end amplifier, ADC, MCU, and BLE or dynamic-NFC bridge. NTAG213 is the tap-to-open trigger and small ID/summary memory.

## Native Android app

Open:

```text
android/
```

Package name:

```text
com.burhanbeycan.nfcwristpatch
```

Main app launch tag URI:

```text
wristpatch://read?p=fab01&b=72&o=98.0&t=32.6&v=48&q=94&src=ntag213
```

BLE live-sensor launch URI:

```text
wristpatch://read?p=fab01&ble=PiezoPatch
```

After installing the app, open it manually once and press:

```text
Write NTAG213 launch tag
```

The app writes the NTAG213 with a compact URI and an Android Application Record so tapping the label opens the native app.

Detailed setup:

```text
android/SAMSUNG_S21_INSTALL_AND_TAG.md
```

Firmware/BLE payload contract:

```text
firmware/PULSE_SENSOR_PROTOCOL.md
```

## True heartbeat architecture

For physical pulse measurement from piezo material + silver-flake electrode:

```text
piezo/silver-flake material
→ charge amplifier or high-impedance instrumentation front end
→ band-pass filter and ADC
→ MCU peak detection or sample streaming
→ BLE JSON notification or dynamic NFC memory
→ Android app heartbeat GUI
```

Recommended urgent demo architecture:

```text
NTAG213 opens app
BLE MCU named PiezoPatch sends real BPM/samples
Android app displays true measured pulse
```

The Android app listens for BLE service:

```text
6f8a0001-79a6-4a5c-8f53-0c0a7a201001
```

notify characteristic:

```text
6f8a0002-79a6-4a5c-8f53-0c0a7a201001
```

Example JSON notification:

```json
{"patchId":"fab01","bpm":76,"quality":93,"battery":84,"contact":"good"}
```

Or sample burst:

```json
{"patchId":"fab01","fs":50,"samples":[0,4,9,20,44,80,42,12,3,0,0,5,12,26,60,95,50,15,4,0]}
```

## Web dashboard URL

Use this when you want browser-only demonstration:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/
```

Smallest web NTAG213 URL:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?id=fab01
```

Compact web snapshot URL:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?p=fab01&b=72&o=98&t=32.6&v=48&q=94
```

## Project files

```text
.
├── android/                            # Native Samsung Galaxy S21 app
│   ├── README.md
│   ├── SAMSUNG_S21_INSTALL_AND_TAG.md
│   ├── settings.gradle
│   ├── build.gradle
│   └── app/
├── firmware/PULSE_SENSOR_PROTOCOL.md   # BLE/dynamic-NFC protocol for real piezo pulse
├── index.html                          # Web dashboard
├── assets/app.js
├── assets/styles.css
├── data/readings/fab01.json
├── data/readings/fab02.json
├── docs/NTAG213_FABRIC_PATCH.md
├── docs/REAL_HEARTBEAT_ARCHITECTURE.md
├── schema/fabric-reading.schema.json
└── tools/build-ntag213-url.mjs
```

## NTAG213 web URL generation

```bash
node tools/build-ntag213-url.mjs \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/ \
  --id fab01 \
  --mode id
```

Compact web snapshot:

```bash
node tools/build-ntag213-url.mjs \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/ \
  --id fab01 \
  --mode compact \
  --bpm 72 \
  --spo2 98 \
  --temp 32.6 \
  --hrv 48 \
  --quality 94
```

## Fabric integration summary

- Place the NTAG213 antenna inlay on the top/outside textile layer.
- Keep the NFC antenna away from metal snaps, batteries, conductive gels, and very wet hydrogel zones.
- Add TPU/silicone/PU encapsulation and strain relief so wrist bending does not crack the inlay.
- Do not sew through the antenna trace.
- Test read distance after lamination, sweat exposure, washing simulation, and wrist flexion.
- For true pulse, connect the piezo/silver-flake layer to active electronics and feed the app through BLE/dynamic NFC.

## Safety and privacy

This is a research/engineering prototype, not a medical device. Do not put identifiable medical data in public URLs, public JSON files, or unencrypted BLE packets. Validate pulse values against ECG or a known reference device before presenting accuracy claims.

## License

MIT. See `LICENSE`.
