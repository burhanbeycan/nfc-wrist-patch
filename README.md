# NFC Wrist Patch Dashboard

A GitHub Pages-ready dashboard for an NFC-readable wrist patch. The intended user flow is:

1. The patch stores an NFC Forum NDEF **URL record**.
2. A Samsung Galaxy S21 reads the tag.
3. Android opens the URL in the browser.
4. This static dashboard decodes the reading and displays heart rate, SpO₂, skin temperature, HRV, hydration/contact metrics, signal quality, battery, and alerts.

> Research safety note: this repository is for an engineering prototype. It is not a medical device, not a diagnosis tool, and not a replacement for clinically validated instrumentation.

## Why a URL record?

For the simplest no-install experience, write a URL to the NFC tag:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?d=<base64url-json-reading>
```

Android recognizes NDEF URL records and routes them to a browser. A native Android app is only needed if you want private app-only dispatch, local encrypted storage, Bluetooth streaming, or Health Connect integration.

## Repository structure

```text
nfc-wrist-patch/
├── index.html                         # Dashboard shell
├── assets/
│   ├── app.js                         # NFC URL decode, UI render, Web NFC writer
│   ├── styles.css                     # Responsive expert GUI
│   └── icon.svg                       # PWA icon
├── data/
│   ├── demo-reading.json              # Example reading
│   └── patches/WP-HYDROGEL-001.json   # Short ?patch=WP-HYDROGEL-001 demo route
├── docs/
│   ├── NFC_TAG_SETUP.md               # Galaxy S21 + NFC tag instructions
│   └── PATCH_ARCHITECTURE.md          # Hardware/materials architecture
├── schema/wristpatch-reading.schema.json
├── tools/
│   ├── build-nfc-url.mjs              # CLI URL generator
│   └── write-tag-web-nfc.html         # Standalone Web NFC writer page
├── manifest.webmanifest
└── service-worker.js
```

## Deploy on GitHub Pages

For a project repository, copy this folder to the repo root and enable GitHub Pages for the repository. The app is static HTML/CSS/JS, so it can be served directly. If this folder is inside `burhanbeycan.github.io`, the public URL becomes:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/
```

The included GitHub Actions workflow (`.github/workflows/pages.yml`) is useful when Pages is configured for GitHub Actions deployment.

## Generate an NFC URL

From the repository root:

```bash
node nfc-wrist-patch/tools/build-nfc-url.mjs \
  nfc-wrist-patch/data/demo-reading.json \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/
```

For small-memory tags, use the shorter registry mode:

```bash
node nfc-wrist-patch/tools/build-nfc-url.mjs \
  nfc-wrist-patch/data/demo-reading.json \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/ \
  --patch-only
```

This produces:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?patch=WP-HYDROGEL-001
```

In `?patch=` mode, the dashboard loads `data/patches/<PATCH_ID>.json`. This keeps the NFC tag small, but the data is public because GitHub Pages is public.

## Write the NFC tag

Use one of these methods:

- Open the dashboard on Chrome for Android and use **Write tag with Web NFC**.
- Open `tools/write-tag-web-nfc.html` from the deployed site, paste the generated URL, and tap a writable NFC tag.
- Use an Android NFC writer app and choose **URL / URI record**.

After writing, test by enabling NFC on the Galaxy S21 and tapping the tag. The screen should open the dashboard URL.

## Payload schema

The app accepts JSON shaped like this:

```json
{
  "schema": "wristpatch.health.v1",
  "patchId": "WP-HYDROGEL-001",
  "subjectAlias": "Volunteer A",
  "measuredAt": "2026-07-04T11:42:00+03:00",
  "device": {
    "firmware": "0.3.0-research",
    "sensorStack": ["NFC Type 2 tag", "PPG optical module", "skin impedance pair"],
    "nfcUid": "04-A2-17-8C-93-21-80"
  },
  "vitals": {
    "heartRateBpm": 72,
    "heartRateConfidence": 0.96,
    "spo2Percent": 98,
    "skinTempC": 32.4,
    "hrvRmssdMs": 48,
    "respirationRateBrpm": 15
  },
  "patch": {
    "hydrationIndex": 0.71,
    "electrodeImpedanceKohm": 18.2,
    "adhesionScore": 0.88
  },
  "signal": {
    "quality": 94,
    "motionArtifact": 0.08,
    "batteryPercent": 82
  }
}
```

Full validation schema: `schema/wristpatch-reading.schema.json`.

## Real hardware limitation

A passive NFC sticker cannot measure live heartbeat by itself. It can only expose data already stored in tag memory. For actual heart-rate acquisition, the patch needs active electronics such as PPG/ECG front-end, an MCU, power, and either dynamic NFC memory, BLE, or a backend connection. For a low-power lab prototype, a dynamic NFC tag can hold the latest reading written by the MCU; for a product-like device, use NFC as the tap-to-open identity channel and stream high-frequency data through BLE.

## Security and privacy recommendations

Do not put identifiable personal health information into public URLs. A URL containing `?d=` can be stored in browser history, screenshots, analytics, and server logs. For a real study or product:

- Store only a random patch/session ID on the NFC tag.
- Fetch readings from an authenticated backend.
- Sign each reading with HMAC or an asymmetric signature.
- Encrypt sensitive fields at rest and in transit.
- Implement consent, retention, and deletion workflows.
- Validate the sensor against clinical/reference devices before making health claims.

## Local preview

```bash
cd nfc-wrist-patch
python3 -m http.server 8080
```

Open `http://localhost:8080`.

## License

MIT. See `LICENSE`.
