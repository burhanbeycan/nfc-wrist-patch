# Real Heartbeat Architecture

The dashboard is ready for a professional phone screen, but the physical patch needs active sensing hardware to measure live heartbeat.

## A. Passive NTAG213 demo

```text
NTAG213 fabric tag → phone tap → dashboard URL → displayed stored snapshot
```

Use this for exhibitions, mockups, teaching, and first UI validation.

Pros:

- Very cheap.
- No battery.
- Opens directly on Samsung Galaxy S21.
- Good for patch identity and last written demo values.

Limitations:

- No real sensing.
- No continuous update.
- Small memory.
- Public URL data can leak if personal data is embedded.

## B. Real sensor patch with backend

```text
PPG/ECG sensor → MCU → BLE or gateway → backend → phone tap reads NTAG213 ID → dashboard fetches latest data
```

Use NTAG213 only for:

```text
?id=<random-session-id>
```

The dashboard fetches current values from a secure API. This is the cleanest architecture for a real study.

Required hardware:

- PPG optical sensor or ECG analog front-end.
- Low-power MCU.
- Power source: thin-film battery, coin cell, LiPo, supercapacitor, or energy harvesting plus storage.
- BLE or other data uplink.
- Skin-safe encapsulation and adhesive/biointerface.

## C. Real sensor patch with dynamic NFC

```text
PPG/ECG sensor → MCU → dynamic NFC memory → phone tap → dashboard reads/open URL or app reads memory
```

Use a dynamic NFC tag with an MCU interface if you need the phone to read the most recent snapshot from NFC memory. NTAG213 is not this kind of chip.

## D. Data model expected by the dashboard

The dashboard accepts this data shape:

```json
{
  "schema": "fabric-ntag213-heart.v1",
  "patchId": "fab01",
  "measuredAt": "2026-07-04T14:40:00+03:00",
  "readMode": "static-json",
  "vitals": {
    "heartRateBpm": 72,
    "spo2Percent": 98,
    "skinTempC": 32.6,
    "hrvRmssdMs": 48,
    "confidence": 0.96
  },
  "signal": {
    "qualityPercent": 94,
    "motionArtifactPercent": 8,
    "contact": "good",
    "batteryPercent": 86,
    "sensorMode": "simulated PPG snapshot"
  }
}
```

## E. Validation plan for a real prototype

1. Validate NFC read distance on bare inlay.
2. Validate NFC read distance after fabric lamination.
3. Validate antenna response during wrist bending.
4. Compare heart-rate output against reference ECG or validated pulse oximeter.
5. Test motion artifact during walking, wrist rotation, and hand movement.
6. Track skin-contact quality over wear time and sweat exposure.
7. Run privacy/security review before using human subject data.

## F. Medical caution

Do not present the output as diagnostic without verified hardware, validated algorithms, usability engineering, cybersecurity controls, and applicable regulatory work.
