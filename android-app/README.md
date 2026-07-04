# Native Android App — Samsung Galaxy S21 + NTAG213 Wrist Patch

This folder contains a native Android sideload demo app for the fabric NTAG213 wrist-patch project.

It is designed for urgent demonstration without Google Play Store approval:

1. Build a debug APK in Android Studio.
2. Install the APK directly on the Samsung Galaxy S21.
3. Open the app once.
4. Press **Arm write** / **Write NTAG213** and touch a blank NTAG213 label.
5. Move the phone away.
6. Touch the NTAG213 label again.
7. Android opens the app and the pro heartbeat screen displays the stored reading.

## Recommended NTAG213 payload

Use this custom URI as an NDEF URI record:

```text
wpatch://read?p=fab01&b=72&o=98&t=326&v=48&q=94
```

Meaning:

| Parameter | Meaning |
| --- | --- |
| `p` | patch ID |
| `b` | heart rate in bpm |
| `o` | SpO₂ percent |
| `t` | skin temperature multiplied by 10, so `326` means `32.6 °C` |
| `v` | HRV RMSSD in ms |
| `q` | signal quality percent |

The custom `wpatch://` scheme is intentionally short. It is more suitable for NTAG213 than a full HTTPS URL and it can route directly to the installed app.

## Build the APK

Open this `android-app/` folder in Android Studio, then select:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

The debug APK will be generated under:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

Install it with Android Studio, or with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Why this app opens from the NFC tag

The app manifest declares NFC and URI intent filters for:

```text
android.nfc.action.NDEF_DISCOVERED
android.intent.action.VIEW
wpatch://...
https://burhanbeycan.github.io/nfc-wrist-patch/...
```

For the most reliable sideload demo, write the `wpatch://` URI to the NTAG213.

## Truthful heartbeat behavior

NTAG213 is passive memory. It cannot measure heartbeat by itself. This Android app displays the true numeric payload stored on the tag, or a local reading selected by tag ID. For actual live heartbeat, connect real PPG/ECG electronics, an MCU, power, and a backend/BLE/dynamic-NFC update path.

## Demo values

Default written URI:

```text
wpatch://read?p=fab01&b=72&o=98&t=326&v=48&q=94
```

The app will show:

```text
Heart rate: 72 bpm
SpO₂: 98%
Skin temperature: 32.6 °C
HRV: 48 ms
Signal quality: 94%
```
