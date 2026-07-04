#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const args = process.argv.slice(2);
if (args.length === 0 || args.includes("--help")) {
  console.log(`Usage:
  node tools/build-nfc-url.mjs data/demo-reading.json --base https://burhanbeycan.github.io/nfc-wrist-patch/

Options:
  --base <url>      Public dashboard URL. Defaults to https://example.github.io/nfc-wrist-patch/
  --patch-only      Output a short ?patch=PATCH_ID URL instead of embedding full JSON.
`);
  process.exit(0);
}

const file = args[0];
const base = valueAfter("--base") || "https://example.github.io/nfc-wrist-patch/";
const patchOnly = args.includes("--patch-only");

const text = await readFile(resolve(file), "utf8");
const reading = JSON.parse(text);
const url = new URL(base);
url.search = "";
url.hash = "";

if (patchOnly) {
  url.searchParams.set("patch", reading.patchId || "WP-HYDROGEL-001");
} else {
  const encoded = Buffer.from(JSON.stringify(reading), "utf8").toString("base64url");
  url.searchParams.set("d", encoded);
}

const result = url.toString();
const bytes = Buffer.byteLength(result, "utf8");
console.log(result);
console.error(`\nURL bytes: ${bytes}`);
console.error(bytes > 850
  ? "Warning: large for many low-memory NFC tags. Prefer --patch-only or a dynamic NFC tag."
  : "Size looks suitable for many larger tags. Check your exact NFC tag capacity.");

function valueAfter(flag) {
  const index = args.indexOf(flag);
  return index >= 0 ? args[index + 1] : null;
}
