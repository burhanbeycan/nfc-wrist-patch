# NTAG213 on Fabric Wrist Patch — Practical Setup

This document describes the exact build logic for a fabric NFC wrist patch that opens the heartbeat dashboard on a Samsung Galaxy S21.

## 1. What NTAG213 can do

NTAG213 is a passive NFC Forum Type 2 tag. In this project, it should be used as the **tap-to-open identity and snapshot carrier**.

Good uses:

- Open a web dashboard on the phone.
- Store a patch ID such as `?id=fab01`.
- Store a small static reading such as `?b=72`.
- Identify the fabric patch, batch, or test session.

Not possible with NTAG213 alone:

- Measuring heartbeat.
- Measuring SpO₂.
- Updating pulse values continuously.
- Streaming waveform data.

A passive tag has no optical/ECG sensor, no processor, and no power source. It can only return stored memory when energized by the phone.

## 2. Recommended NFC URL

Use patch-ID mode for reliability:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?id=fab01
```

The dashboard then loads:

```text
data/readings/fab01.json
```

For a pure stored snapshot, use compact mode:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?p=fab01&b=72&o=98&t=32.6&v=48&q=94
```

The compact URL stores heart rate and a few vital values directly in the NFC URL. It must be rewritten when the values change.

## 3. Writing the tag

### Chrome Android Web NFC

1. Open the deployed dashboard in Chrome on Android.
2. Generate a URL in the **NTAG213 writer** panel.
3. Press **Write with Web NFC**.
4. Hold the phone near the NTAG213 inlay.
5. Test the tag by tapping again.

### NFC writer app

Any NFC writing app can be used:

1. Choose **URL / URI** record.
2. Paste the generated URL.
3. Write to NTAG213.
4. Test on Galaxy S21 before locking.

## 4. Fabric placement

Recommended layer stack, top to skin:

```text
outer textile / TPU film
NTAG213 antenna inlay
strain relief / adhesive carrier
sensor island or optical window if active electronics are used
soft adhesive / hydrogel contact region
skin
```

Practical notes:

- Put the NTAG213 inlay on the outside/top fabric layer to maximize coupling with the phone antenna.
- Avoid metal snaps, metal-coated yarns, batteries, and conductive hydrogel directly over the antenna.
- Leave a flex-neutral zone around the inlay; do not sew through the antenna trace.
- Add TPU, PU, silicone, or compatible textile adhesive as encapsulation.
- Test bending around wrist radius, sweat exposure, repeated tap cycles, and wash-like humidity before use.

## 5. Validation checklist

- NFC opens the dashboard URL on Galaxy S21.
- URL byte count is below NTAG213 capacity.
- Tag still reads after lamination.
- Tag still reads while worn on wrist.
- Tag still reads after bending/flex cycles.
- Dashboard shows correct `patchId` and reading source.
- If compact mode is used, BPM changes only after rewriting the NFC tag.

## 6. Production recommendation

For real human data, store only a random session ID on NTAG213:

```text
https://your-domain.example/r?id=8k3x9d
```

Then fetch signed readings from an authenticated backend. Do not store identifiable health data inside public URLs.
