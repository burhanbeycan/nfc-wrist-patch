# Real Sensor Next Step

The native app can already open from NTAG213 and show the heartbeat payload. To make the value truly live rather than a stored NFC snapshot, add a real sensor path.

## Minimal real prototype

```text
PPG sensor module
→ low-power MCU
→ BLE GATT characteristic
→ Android app reads latest bpm
→ NTAG213 opens the app and provides patch ID
```

## Alternative backend prototype

```text
PPG/ECG patch electronics
→ phone/gateway/cloud upload
→ backend stores latest reading by random patch/session ID
→ NTAG213 contains wpatch://read?id=<session>
→ app fetches latest reading
```

## Why not NTAG213 only?

NTAG213 is passive memory with small capacity. It has no sensor, no processor, and no power source. It can store the ID/snapshot that opens this app, but the heartbeat value must come from PPG, ECG, a reference device, firmware, BLE, backend, or a dynamic NFC chip.
