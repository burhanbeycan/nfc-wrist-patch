# NTAG213 Payload Format for the Native App

## Recommended URI

```text
wpatch://read?p=fab01&b=72&o=98&t=326&v=48&q=94
```

This should fit NTAG213 because it is short and stores only the demonstration values needed by the app.

## Parameters

| Key | Example | Meaning |
| --- | --- | --- |
| `p` | `fab01` | patch ID |
| `b` | `72` | heart rate in bpm |
| `o` | `98` | SpO₂ percent |
| `t` | `326` | skin temperature × 10, so 326 = 32.6 °C |
| `v` | `48` | HRV RMSSD in ms |
| `q` | `94` | signal quality percent |
| `bat` | `86` | optional battery percent |
| `m` | `8` | optional motion artifact percent |

## ID-only mode

```text
wpatch://read?id=fab01
```

The app opens and loads the local demo reading for `fab01`.

## HTTPS compatibility mode

The app also accepts:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?p=fab01&b=72&o=98&t=32.6&v=48&q=94
```

However, a custom `wpatch://` URI is better for a sideload demonstration because Android is more likely to route it directly to the installed app.

## Real heartbeat mode

For real live heartbeat, keep NTAG213 as the tap-to-open ID and update data through one of these:

```text
PPG/ECG sensor → MCU → BLE → app
PPG/ECG sensor → MCU → backend → app fetch by tag ID
PPG/ECG sensor → MCU → dynamic NFC tag → phone tap reads latest snapshot
```

NTAG213 alone cannot perform the measurement.
