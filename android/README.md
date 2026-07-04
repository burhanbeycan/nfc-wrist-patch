# Native Android App — Samsung Galaxy S21 NFC Pulse Patch

This folder contains a sideloadable native Android application for the NTAG213 fabric wrist patch demonstration.

The app is designed for this urgent demo flow:

1. Install the APK directly on the Samsung Galaxy S21.
2. Write the NTAG213 with a `wristpatch://read?...` NDEF URI plus an Android Application Record.
3. Bring the phone close to the NTAG213 label on the fabric patch.
4. Android opens this app automatically.
5. The screen shows heartbeat results in a professional interface.

## Important physics note

Your piezoelectric material and silver-flake electrode layer can be the pulse sensor interface, but an NTAG213 label cannot sample that piezo signal by itself. NTAG213 is a passive tag: it stores bytes and sends them when energized by the phone.

For true measured pulse, the fabric patch must include one of these bridges:

- **Piezo/silver-flake sensor → charge amplifier/filter → ADC → MCU → BLE → Android app**.
- **Piezo/silver-flake sensor → charge amplifier/filter → ADC → MCU → dynamic NFC tag**.
- **Piezo/silver-flake sensor → external acquisition board → app/backend → NFC tag opens the patient/session screen**.

This Android app is prepared for all three demo styles:

- It opens from NTAG213 NDEF URI/AAR.
- It can display a real BPM summary written into the tag URL.
- It includes a piezo-sample analyzer for short sample bursts.
- It includes a BLE client scaffold for real-time values from a custom MCU.

## Build in Android Studio

1. Open the `android/` folder in Android Studio.
2. Let Gradle sync.
3. Connect the Samsung Galaxy S21 with USB debugging enabled.
4. Press **Run** to install the debug APK.

The package name is:

```text
com.burhanbeycan.nfcwristpatch
```

## Build APK from terminal

With Android Studio/Android SDK installed:

```bash
cd android
./gradlew assembleDebug
```

If the Gradle wrapper is not generated on your machine, open the folder once in Android Studio or run with a locally installed Gradle:

```bash
gradle assembleDebug
```

The APK will be generated under:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install using ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Write the NTAG213 label

Open the app manually once, then press **Write NTAG213 launch tag**. Hold the NTAG213 label near the phone. The app writes:

```text
wristpatch://read?p=fab01&b=72&o=98&t=32.6&v=48&q=94&src=ntag213
```

plus an Android Application Record for `com.burhanbeycan.nfcwristpatch`.

After that, tapping the fabric label opens the app directly.

## Real pulse payload formats

### 1. BPM summary from your electronics

```text
wristpatch://read?p=fab01&b=76&q=91&src=piezo-mcu
```

### 2. Short piezo sample burst

```text
wristpatch://read?p=fab01&fs=50&s=0,4,9,20,44,80,42,12,3,0,0,5,12,26,60,95,50,15,4,0
```

The app computes BPM from peaks when `s=` and `fs=` are present.

### 3. BLE live sensor

```text
wristpatch://read?p=fab01&ble=PiezoPatch
```

The app scans for a BLE device name containing `PiezoPatch` and listens for JSON notifications such as:

```json
{"bpm":76,"spo2":98,"temp":32.4,"hrv":45,"quality":93,"samples":[0,4,9,20,44,80,42]}
```

## Files

```text
android/
├── settings.gradle
├── build.gradle
├── app/build.gradle
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/com/burhanbeycan/nfcwristpatch/
│   ├── MainActivity.java
│   ├── BlePulseClient.java
│   ├── HeartWaveView.java
│   ├── NfcPayloadParser.java
│   ├── Ntag213Writer.java
│   ├── PiezoPulseAnalyzer.java
│   └── PulseReading.java
└── app/src/main/res/
    ├── values/colors.xml
    ├── values/strings.xml
    ├── values/styles.xml
    ├── drawable/app_icon.xml
    └── xml/nfc_tech_filter.xml
```

## Demo truthfulness

If only a bare NTAG213 label is attached to the fabric, the app can show stored or hosted values, not newly measured pulse. If you want the displayed BPM to be physically true, connect the piezo/silver-flake sensor to an active measurement chain and send BPM/samples to the app through BLE or a dynamic NFC memory device.
