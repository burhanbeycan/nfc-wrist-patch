#!/usr/bin/env node
const args = process.argv.slice(2);
const NTAG213_USER_BYTES = 144;
const NDEF_URL_OVERHEAD_ESTIMATE = 8;

if (args.includes("--help") || args.length === 0) {
  console.log(`Usage:
  node tools/build-ntag213-url.mjs --base https://burhanbeycan.github.io/nfc-wrist-patch/ --id fab01 --mode id
  node tools/build-ntag213-url.mjs --base https://burhanbeycan.github.io/nfc-wrist-patch/ --id fab01 --mode compact --bpm 72 --spo2 98 --temp 32.6 --hrv 48 --quality 94

Modes:
  id       Smallest URL: ?id=fab01. Best for NTAG213.
  compact  Stores BPM and small vital snapshot in the URL.
  json     Demonstration only; usually too large for NTAG213.
`);
  process.exit(0);
}

const base = value("--base") || "https://burhanbeycan.github.io/nfc-wrist-patch/";
const id = sanitize(value("--id") || "fab01");
const mode = value("--mode") || "id";
const bpm = numberValue("--bpm", 72);
const spo2 = numberValue("--spo2", 98);
const temp = numberValue("--temp", 32.6);
const hrv = numberValue("--hrv", 48);
const quality = numberValue("--quality", 94);

const url = new URL(base);
url.search = "";
url.hash = "";

if (mode === "id") {
  url.searchParams.set("id", id);
} else if (mode === "compact") {
  url.searchParams.set("p", id);
  url.searchParams.set("b", String(Math.round(bpm)));
  url.searchParams.set("o", String(round(spo2, 1)));
  url.searchParams.set("t", String(round(temp, 1)));
  url.searchParams.set("v", String(Math.round(hrv)));
  url.searchParams.set("q", String(Math.round(quality)));
} else if (mode === "json") {
  const reading = {
    schema: "fabric-ntag213-heart.v1",
    patchId: id,
    patchName: `Fabric Patch ${id.toUpperCase()}`,
    measuredAt: new Date().toISOString(),
    readMode: "encoded-json-url",
    nfc: { chip: "NTAG213", ndef: "https-url", tagMemoryBytes: 144 },
    vitals: { heartRateBpm: bpm, spo2Percent: spo2, skinTempC: temp, hrvRmssdMs: hrv, confidence: quality / 100 },
    signal: { qualityPercent: quality, motionArtifactPercent: 8, contact: "good", batteryPercent: 86, sensorMode: "encoded demo" }
  };
  url.searchParams.set("d", Buffer.from(JSON.stringify(reading), "utf8").toString("base64url"));
} else {
  console.error(`Unknown mode: ${mode}`);
  process.exit(1);
}

const result = url.toString();
const rawBytes = Buffer.byteLength(result, "utf8");
const estimated = rawBytes + NDEF_URL_OVERHEAD_ESTIMATE;
console.log(result);
console.error(`\nURL bytes: ${rawBytes}`);
console.error(`Estimated NDEF bytes: ${estimated}/${NTAG213_USER_BYTES}`);
console.error(estimated <= NTAG213_USER_BYTES
  ? "OK: suitable for NTAG213 in typical URL-record encoding. Test before locking the tag."
  : "Too large for NTAG213. Use --mode id, shorten the domain, or use a larger/dynamic NFC tag.");

function value(flag) {
  const index = args.indexOf(flag);
  return index >= 0 ? args[index + 1] : null;
}

function numberValue(flag, fallback) {
  const raw = value(flag);
  const number = Number(raw);
  return Number.isFinite(number) ? number : fallback;
}

function sanitize(input) {
  return String(input || "fab01").replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 32) || "fab01";
}

function round(number, decimals) {
  const factor = 10 ** decimals;
  return Math.round(number * factor) / factor;
}
