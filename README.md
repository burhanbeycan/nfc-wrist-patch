# NTAG213 Fabric Wrist Patch Heartbeat Dashboard

Professional tap-to-open dashboard for a fabric-integrated NFC wrist patch.

The target workflow is:

1. Integrate an **NTAG213 NFC inlay** on the top layer of a fabric wrist patch.
2. Write a very short **NDEF HTTPS/URI record** to the tag.
3. Bring a Samsung Galaxy S21 close to the NTAG213.
4. Android opens the URL in the browser.
5. The dashboard displays heart rate, SpO₂, HRV, skin temperature, signal quality, battery, patch identity, alerts, and a polished heartbeat GUI.

> Important: NTAG213 is a passive NFC tag. It can open a URL and store a small static payload, but it cannot measure heartbeat by itself. Real heartbeat requires PPG/ECG sensing electronics, power, and firmware/backend/BLE/dynamic-NFC support. This repository gives the phone screen and NFC-open workflow.

## Live URL after GitHub Pages deployment

Use this as the NFC URL base:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/
```

Smallest recommended NTAG213 URL:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?id=fab01
```

Compact snapshot URL that stores BPM directly on the tag:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?p=fab01&b=72&o=98&t=32.6&v=48&q=94
```

## URL modes

### 1. Patch ID mode — best for NTAG213

```text
?id=fab01
```

The tag stays tiny. The dashboard fetches:

```text
data/readings/fab01.json
```

Use this when you want NTAG213 reliability and a short URL.

### 2. Compact snapshot mode — stores numbers in the tag

```text
?p=fab01&b=72&o=98&t=32.6&v=48&q=94
```

Parameter meaning:

| Parameter | Meaning |
| --- | --- |
| `p` | patch ID |
| `b` | heart rate in bpm |
| `o` | SpO₂ percent |
| `t` | skin temperature in °C |
| `v` | HRV RMSSD in ms |
| `q` | signal quality percent |

This can fit NTAG213 if your base URL is short. The displayed value changes only when the tag URL is rewritten.

### 3. Encoded JSON mode — not recommended for NTAG213

```text
?d=<base64url-json>
```

This is useful for NTAG216/dynamic NFC demos but usually too large for NTAG213.

## Generate an NTAG213-safe URL

```bash
node tools/build-ntag213-url.mjs \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/ \
  --id fab01 \
  --mode id
```

Compact snapshot:

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

## Write the NTAG213

Use one of these:

1. Open the dashboard on Chrome for Android and press **Write current tag** or **Write with Web NFC**.
2. Open `tools/write-web-nfc.html` from the deployed site.
3. Use any Android NFC writer app and select **URL / URI record**.

Do not lock the tag read-only until the Samsung Galaxy S21 opens the dashboard correctly.

## Fabric integration summary

- Place the NTAG213 antenna inlay on the top/outside textile layer.
- Keep the NFC antenna away from metal snaps, batteries, conductive gels, and very wet hydrogel zones.
- Add TPU/silicone/PU encapsulation and strain relief so wrist bending does not crack the inlay.
- Test read distance after lamination, sweat exposure, washing simulation, and wrist flexion.
- For real pulse measurements, add PPG/ECG electronics and use NFC only as the tap-to-open identity layer.

## Project files

```text
.
├── index.html
├── assets/app.js
├── assets/styles.css
├── assets/icon.svg
├── data/readings/fab01.json
├── data/readings/fab02.json
├── docs/NTAG213_FABRIC_PATCH.md
├── docs/REAL_HEARTBEAT_ARCHITECTURE.md
├── schema/fabric-reading.schema.json
├── tools/build-ntag213-url.mjs
└── tools/write-web-nfc.html
```

## Local preview

```bash
python3 -m http.server 8080
```

Open:

```text
http://localhost:8080/?id=fab01
```

## GitHub Pages

This repository includes `.github/workflows/pages.yml`. In GitHub repository settings, set Pages source to **GitHub Actions**. The workflow publishes the static site on pushes to `main`.

## Safety and privacy

This is a research/engineering prototype, not a medical device. Do not put identifiable medical data in public URLs or public JSON files. For real human data, keep only a random session ID on the tag and fetch signed/encrypted readings from an authenticated backend.

## License

MIT. See `LICENSE`.
