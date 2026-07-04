# Wrist Patch Hardware and Materials Architecture

This repository provides the phone-side and web-side dashboard. The wrist patch must still provide a valid NFC record and, if heart-rate sensing is required, an active sensor stack.

## Recommended prototype stack

From air side to skin side:

1. **Barrier/encapsulation film** — thin TPU, PU, parylene-C, or medical-grade silicone to protect electronics from sweat and mechanical abrasion.
2. **Flexible interconnect layer** — polyimide, PET, TPU, or elastomeric substrate carrying printed Ag, Cu, carbon, PEDOT:PSS, or liquid-metal traces.
3. **NFC antenna + tag IC** — NFC Forum-compatible Type 2/Type 4 tag. For active systems, choose a dynamic NFC tag with I²C/SPI so an MCU can update NDEF memory.
4. **Sensor island** — PPG optical module, ECG analog front-end, skin temperature IC, IMU, and/or impedance readout IC.
5. **Soft adhesive and strain-relief layer** — silicone adhesive, acrylic medical adhesive, or breathable adhesive mesh.
6. **Biointerface layer** — hydrogel, ionogel, or conductive polymer composite to reduce skin-electrode impedance.
7. **Skin contact region** — designed to maintain conformal contact without occlusion damage or irritation.

## Passive vs active patch

### Passive NFC label

A passive label can hold a URL or small JSON payload. It cannot measure heartbeat by itself. It is useful for:

- Patch identity.
- Lot/batch metadata.
- Last manually written reading.
- Tap-to-open dashboard URL.

### Active sensor patch

Heart rate, HRV, SpO₂, ECG, and motion-compensated signals require power and sensing electronics. A good research prototype uses:

- PPG or ECG sensor front-end.
- Low-power MCU.
- Thin-film battery, coin cell, supercapacitor, or energy-harvesting module.
- Dynamic NFC tag for tap-to-read latest snapshot.
- Optional BLE for continuous waveform or trend streaming.

## Data update patterns

| Pattern | NFC content | Best use | Limitation |
| --- | --- | --- | --- |
| Static URL | Dashboard URL only | Open UI from patch | No measurement data |
| Embedded JSON URL | Dashboard URL + `?d=` payload | Demo and lab snapshots | Tag memory and privacy limits |
| Patch ID URL | Dashboard URL + `?patch=` or `?session=` | Small tag payload | Requires hosted data source |
| Dynamic NFC memory | MCU writes latest NDEF | Battery-powered patch | More complex electronics |
| NFC identity + BLE | NFC opens app/web, BLE streams data | Continuous data | Requires app/browser BLE support and power |

## Polymer and biointerface considerations

- Hydrogels can reduce electrode impedance but may dry during long wear; add humectants or encapsulation strategy if compatible with skin safety.
- PEDOT:PSS, carbon, Ag/AgCl, and conductive hydrogel composites can be useful for electrode interfaces; validate cytotoxicity, adhesion, sweat stability, and drift.
- Stretchable traces need strain isolation around the NFC antenna and sensor island; avoid antenna detuning from bending, metal, and high-water-content gels.
- Skin-safe adhesives should be tested for wear time, peel force, moisture vapor transmission, residue, and sensitization.
- Encapsulation must balance water ingress protection, breathability, modulus matching, and NFC antenna performance.

## Validation plan

1. Bench validate NFC readability at multiple antenna orientations and bend radii.
2. Compare heart-rate readings with a reference pulse oximeter/ECG under rest and motion.
3. Measure electrode impedance over wear time, sweat exposure, and humidity.
4. Record skin temperature drift against a calibrated surface probe.
5. Evaluate adhesive performance after sweat, shower simulation, and repeated wrist flexion.
6. Verify that the URL opens reliably on the target phone, including the Samsung Galaxy S21.
7. Run privacy review before storing any identifiable health information.

## Safety disclaimer

The dashboard and schema are intended for research and engineering demonstration. Any clinical, diagnostic, or therapeutic use requires regulatory strategy, validated hardware, verified firmware, risk management, usability engineering, cybersecurity controls, and clinical validation.
