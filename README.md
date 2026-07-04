# NTAG213 Fabric Wrist Patch Heartbeat Dashboard + Native Android App

Professional tap-to-open system for a fabric-integrated NFC wrist patch.

The repository contains two demonstration layers:

1. **Native Android app** for Samsung Galaxy S21 sideload demo.
2. **Web dashboard** for browser-only GitHub Pages demo.

## Target phone-app workflow

1. Integrate an **NTAG213 NFC inlay** on the top/outside layer of the fabric wrist patch.
2. Build and install the native Android APK from `android-app/` on the Samsung Galaxy S21.
3. Open the app once.
4. In the app, press **Write NTAG213** and touch the NTAG213 label.
5. Move the phone away.
6. Touch the label again.
7. Android opens the app and the pro GUI displays heart rate, SpO₂, HRV, skin temperature, signal quality, battery, patch identity, alerts, and pulse trend.

> Important: NTAG213 is passive NFC memory. It can open the app and store a small ID/snapshot, but it cannot measure heartbeat by itself. True live heartbeat needs PPG/ECG or piezo sensor electronics, analog front end, MCU, power, and BLE/backend/dynamic-NFC update path.

## Native Android app

Open this folder in Android Studio:

```text
android-app/
```

Package name:

```text
com.burhanbeycan.nfcpatch
```

Recommended NTAG213 app-launch URI:

```text
wpatch://read?p=fab01&b=72&o=98&t=326&v=48&q=94
```

This compact payload means:

```text
p=fab01    patch ID
b=72       heart rate, bpm
o=98       SpO₂, percent
t=326      skin temperature ×10, so 326 = 32.6 °C
v=48       HRV RMSSD, ms
q=94       signal quality, percent
```

The custom `wpatch://` scheme is better than a full HTTPS URL for urgent sideload demonstration because it is short and routes to the installed app.

Android app files:

```text
android-app/
├── README.md
├── settings.gradle
├── build.gradle
├── gradle.properties
├── app/build.gradle
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/com/burhanbeycan/nfcpatch/MainActivity.java
├── app/src/main/res/values/*.xml
├── app/src/main/res/xml/*.xml
├── app/src/main/res/drawable/*.xml
└── docs/
    ├── GALAXY_S21_INSTALL.md
    ├── NTAG213_PAYLOAD_FORMAT.md
    └── REAL_SENSOR_NEXT_STEP.md
```

## Build and install APK

In Android Studio:

```text
File > Open > android-app/
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

Debug APK output:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

ADB install:

```bash
adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
```

Phone settings:

```text
Settings > Connections > NFC and contactless payments > On
```

Detailed install guide:

```text
android-app/docs/GALAXY_S21_INSTALL.md
```

## True heartbeat architecture

For physical pulse measurement from a fabric sensor layer:

```text
PPG / ECG / piezo sensor interface
→ analog front end, filter, ADC
→ low-power MCU
→ BLE, backend, or dynamic NFC memory
→ Android app heartbeat GUI
```

Recommended urgent real-sensor path:

```text
NTAG213 opens app by patch ID
sensor electronics measure pulse
MCU sends latest BPM through BLE or backend
Android app displays true measured value
```

The included Android app already handles the **tap-to-open app** and **stored snapshot** demonstration. The next engineering step is to connect a real sensor data source to replace the stored snapshot values.

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
- For true pulse, connect the sensor layer to active electronics and feed the app through BLE, backend, or dynamic NFC.

## Safety and privacy

This is a research/engineering prototype, not a medical device. Do not put identifiable medical data in public URLs, public JSON files, or unencrypted BLE packets. Validate pulse values against ECG or a known reference device before presenting accuracy claims.

## License

MIT. See `LICENSE`.
