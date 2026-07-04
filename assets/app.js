(() => {
  "use strict";

  const NTAG213_USER_BYTES = 144;
  const NDEF_URL_OVERHEAD_ESTIMATE = 8;
  const DEFAULT_READING_ID = "fab01";
  const $ = (id) => document.getElementById(id);

  const demoReading = {
    schema: "fabric-ntag213-heart.v1",
    patchId: "fab01",
    patchName: "Fabric Patch A",
    subjectAlias: "Demo wearer",
    measuredAt: new Date().toISOString(),
    readMode: "static-json",
    nfc: {
      chip: "NTAG213",
      uid: "04-A2-17-8C-93-21-80",
      ndef: "https-url",
      tagMemoryBytes: 144
    },
    vitals: {
      heartRateBpm: 72,
      spo2Percent: 98,
      skinTempC: 32.6,
      hrvRmssdMs: 48,
      confidence: 0.96
    },
    signal: {
      qualityPercent: 94,
      motionArtifactPercent: 8,
      contact: "good",
      batteryPercent: 86,
      sensorMode: "simulated PPG snapshot"
    },
    hardware: {
      fabricLayer: "polyester/TPU laminate demo",
      biointerface: "optional hydrogel electrode zone",
      sensorStack: "PPG/ECG electronics required for real heartbeat"
    },
    history: [
      { t: -60, bpm: 70 },
      { t: -50, bpm: 71 },
      { t: -40, bpm: 72 },
      { t: -30, bpm: 73 },
      { t: -20, bpm: 72 },
      { t: -10, bpm: 74 },
      { t: 0, bpm: 72 }
    ],
    alerts: [
      { level: "info", title: "Passive NTAG213", detail: "The NFC tag opens the dashboard and carries an ID or stored snapshot. It does not measure heartbeat by itself." },
      { level: "ok", title: "Phone tap flow", detail: "Write this page URL as an NDEF URI record; Android will route the tap to the browser." }
    ]
  };

  let currentReading = null;
  let generatedUrl = "";

  document.addEventListener("DOMContentLoaded", init);
  window.addEventListener("resize", debounce(() => currentReading && drawWaveform(currentReading), 120));

  async function init() {
    registerServiceWorker();
    bindEvents();
    hydrateFormDefaults();
    const loaded = await loadReadingFromUrl();
    if (!loaded) {
      showNotice("No NFC URL parameters were found, so the professional demo heartbeat is shown. Generate an NTAG213 URL below and write it to the fabric tag.");
      renderReading(demoReading, "demo reading");
    }
    generateTagUrl(false);
  }

  function bindEvents() {
    $("loadDemo").addEventListener("click", () => renderReading({ ...demoReading, measuredAt: new Date().toISOString() }, "demo button"));
    $("copyCurrentUrl").addEventListener("click", () => copyText(buildCompactUrl(currentReading || demoReading), "Current NFC URL copied."));
    $("writeCurrentTag").addEventListener("click", () => writeNfcUrl(buildCompactUrl(currentReading || demoReading)));
    $("copyJson").addEventListener("click", () => copyText(JSON.stringify(currentReading || demoReading, null, 2), "Reading JSON copied."));

    $("tagForm").addEventListener("submit", (event) => {
      event.preventDefault();
      generateTagUrl(true);
    });

    ["baseUrl", "urlMode", "formPatchId", "formBpm", "formSpo2", "formTemp", "formHrv", "formQuality"].forEach((id) => {
      $(id).addEventListener("input", () => generateTagUrl(false));
      $(id).addEventListener("change", () => generateTagUrl(false));
    });

    $("copyGenerated").addEventListener("click", () => copyText(generatedUrl, "Generated NTAG213 URL copied."));
    $("writeGenerated").addEventListener("click", () => writeNfcUrl(generatedUrl));
  }

  async function loadReadingFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const hashParams = parseHashParams(window.location.hash);
    for (const [key, value] of hashParams.entries()) {
      if (!params.has(key)) params.set(key, value);
    }

    try {
      if (params.has("d")) {
        const decoded = JSON.parse(base64UrlDecode(params.get("d")));
        renderReading(decoded, "encoded JSON URL");
        return true;
      }

      if (hasCompactVitals(params)) {
        const reading = readingFromCompactParams(params);
        renderReading(reading, "NTAG213 compact URL snapshot");
        return true;
      }

      const id = params.get("id") || params.get("patch") || params.get("p");
      if (id) {
        const safeId = sanitizeId(id);
        const response = await fetch(`data/readings/${safeId}.json`, { cache: "no-store" });
        if (!response.ok) throw new Error(`No reading file found for ${safeId}`);
        const reading = await response.json();
        renderReading(reading, `hosted reading: ${safeId}.json`);
        return true;
      }
    } catch (error) {
      console.error(error);
      showNotice(`Could not load the NFC reading: ${error.message}. Demo data is shown instead.`);
      renderReading(demoReading, "fallback demo");
      return true;
    }

    return false;
  }

  function hasCompactVitals(params) {
    return params.has("b") || params.has("bpm") || params.has("hr");
  }

  function readingFromCompactParams(params) {
    const patchId = sanitizeId(params.get("p") || params.get("id") || DEFAULT_READING_ID);
    const bpm = numberParam(params, ["b", "bpm", "hr"], 72);
    const spo2 = numberParam(params, ["o", "spo2"], 98);
    const temp = numberParam(params, ["t", "temp"], 32.6);
    const hrv = numberParam(params, ["v", "hrv"], 48);
    const quality = numberParam(params, ["q", "quality"], 92);
    const battery = numberParam(params, ["bat", "battery"], 80);
    const confidence = clamp(quality / 100, 0, 1);

    return normalizeReading({
      ...demoReading,
      patchId,
      patchName: `Fabric Patch ${patchId.toUpperCase()}`,
      measuredAt: new Date().toISOString(),
      readMode: "compact-url-snapshot",
      vitals: {
        heartRateBpm: bpm,
        spo2Percent: spo2,
        skinTempC: temp,
        hrvRmssdMs: hrv,
        confidence
      },
      signal: {
        ...demoReading.signal,
        qualityPercent: quality,
        batteryPercent: battery,
        sensorMode: "stored compact URL snapshot"
      },
      history: synthesizeHistory(bpm),
      alerts: [
        { level: "info", title: "Compact NTAG213 snapshot", detail: "This heartbeat value is stored in the NFC URL. It changes only when the URL/tag is rewritten." },
        ...demoReading.alerts
      ]
    });
  }

  function renderReading(input, source) {
    const reading = normalizeReading(input);
    currentReading = reading;

    const vitals = reading.vitals;
    const signal = reading.signal;
    const heart = heartStatus(vitals.heartRateBpm);
    const memoryUrl = buildCompactUrl(reading);
    const memoryBytes = estimateNdefBytes(memoryUrl);

    text("heartRate", round(vitals.heartRateBpm, 0));
    text("miniBpm", round(vitals.heartRateBpm, 0));
    text("miniPatch", reading.patchName || reading.patchId);
    text("spo2", format(vitals.spo2Percent, 1, "%"));
    text("hrv", format(vitals.hrvRmssdMs, 0, " ms"));
    text("skinTemp", format(vitals.skinTempC, 1, " °C"));
    text("measuredAt", formatDate(reading.measuredAt));
    text("readingSource", source);
    text("patchId", reading.patchId);
    text("nfcUid", reading.nfc.uid || "not stored");
    text("readMode", reading.readMode);
    text("battery", format(signal.batteryPercent, 0, "%"));
    text("confidenceValue", format(vitals.confidence * 100, 0, "%"));
    text("motionArtifact", format(signal.motionArtifactPercent, 0, "%"));
    text("contactStatus", signal.contact || "unknown");
    text("sensorMode", signal.sensorMode || "not specified");
    text("payloadMode", reading.readMode.includes("compact") ? "compact URL" : reading.readMode.includes("json") ? "JSON" : "patch ID");
    text("memoryFit", `${memoryBytes}/${NTAG213_USER_BYTES} B`);

    const ring = Math.max(0, Math.min(100, (vitals.heartRateBpm / 180) * 100));
    $("heartRing").style.setProperty("--ring", String(ring.toFixed(0)));
    text("heartInterpretation", heart.message);
    text("trendChip", trendLabel(reading.history));

    setPill("confidencePill", `${Math.round(vitals.confidence * 100)}% conf.`, vitals.confidence * 100, 80, 60);
    setPill("qualityPill", `${Math.round(signal.qualityPercent)}% quality`, signal.qualityPercent, 80, 60);
    setPill("tagPill", reading.nfc.chip || "NTAG213", memoryBytes <= NTAG213_USER_BYTES ? 100 : 20, 80, 60);

    renderAlerts(reading, heart, memoryBytes);
    $("rawJson").textContent = JSON.stringify(reading, null, 2);
    drawWaveform(reading);
    generateTagUrl(false);
  }

  function normalizeReading(input) {
    const reading = input && typeof input === "object" ? input : {};
    const vitals = reading.vitals || {};
    const signal = reading.signal || {};
    const nfc = reading.nfc || {};
    const hardware = reading.hardware || {};
    return {
      schema: String(reading.schema || "fabric-ntag213-heart.v1"),
      patchId: sanitizeId(reading.patchId || DEFAULT_READING_ID),
      patchName: String(reading.patchName || "Fabric Wrist Patch"),
      subjectAlias: String(reading.subjectAlias || "anonymous"),
      measuredAt: reading.measuredAt || new Date().toISOString(),
      readMode: String(reading.readMode || "static-json"),
      nfc: {
        chip: String(nfc.chip || "NTAG213"),
        uid: String(nfc.uid || ""),
        ndef: String(nfc.ndef || "https-url"),
        tagMemoryBytes: clamp(Number(nfc.tagMemoryBytes), 0, 4096, NTAG213_USER_BYTES)
      },
      vitals: {
        heartRateBpm: clamp(Number(vitals.heartRateBpm), 0, 260, 0),
        spo2Percent: clamp(Number(vitals.spo2Percent), 0, 100, 0),
        skinTempC: clamp(Number(vitals.skinTempC), 0, 60, 0),
        hrvRmssdMs: clamp(Number(vitals.hrvRmssdMs), 0, 250, 0),
        confidence: clamp(Number(vitals.confidence), 0, 1, 0)
      },
      signal: {
        qualityPercent: clamp(Number(signal.qualityPercent), 0, 100, 0),
        motionArtifactPercent: clamp(Number(signal.motionArtifactPercent), 0, 100, 0),
        contact: String(signal.contact || "unknown"),
        batteryPercent: clamp(Number(signal.batteryPercent), 0, 100, 0),
        sensorMode: String(signal.sensorMode || "not specified")
      },
      hardware: {
        fabricLayer: String(hardware.fabricLayer || "fabric laminate"),
        biointerface: String(hardware.biointerface || "not specified"),
        sensorStack: String(hardware.sensorStack || "not specified")
      },
      history: normalizeHistory(reading.history, Number(vitals.heartRateBpm) || 72),
      alerts: Array.isArray(reading.alerts) ? reading.alerts : []
    };
  }

  function normalizeHistory(history, bpm) {
    if (Array.isArray(history) && history.length) {
      return history.map((point, index) => ({
        t: Number.isFinite(Number(point.t)) ? Number(point.t) : index,
        bpm: clamp(Number(point.bpm ?? point.heartRateBpm), 0, 260, bpm)
      }));
    }
    return synthesizeHistory(bpm);
  }

  function synthesizeHistory(bpm) {
    const safe = Number.isFinite(Number(bpm)) ? Number(bpm) : 72;
    return [-60, -50, -40, -30, -20, -10, 0].map((t, index) => ({
      t,
      bpm: Math.round(safe + Math.sin(index * 1.2) * 2 + (index % 2 ? 1 : -1))
    }));
  }

  function heartStatus(bpm) {
    if (!bpm) return { level: "warn", message: "No heart-rate value is present in this NFC reading." };
    if (bpm < 45) return { level: "danger", message: "Very low pulse flag. Re-check contact and compare with a validated reference device." };
    if (bpm > 120) return { level: "warn", message: "Elevated pulse flag. Motion, exercise, stress, fever, or sensing error may be involved." };
    return { level: "ok", message: "Heart-rate snapshot is inside the normal prototype display band." };
  }

  function renderAlerts(reading, heart, memoryBytes) {
    const list = $("alerts");
    const template = $("alertTemplate");
    list.replaceChildren();
    const alerts = [
      { level: heart.level, title: "Heart-rate review", detail: heart.message },
      ...reading.alerts
    ];
    if (memoryBytes > NTAG213_USER_BYTES) {
      alerts.unshift({ level: "danger", title: "Too large for NTAG213", detail: "Use ?id=fab01 or compact snapshot mode; full JSON URLs usually exceed NTAG213 memory." });
    }
    if (reading.readMode.includes("static") || reading.readMode.includes("compact")) {
      alerts.push({ level: "info", title: "Stored snapshot", detail: "Passive NTAG213 does not stream live heartbeat. The displayed value is stored or fetched after the phone opens the URL." });
    }
    if (reading.signal.motionArtifactPercent > 25) {
      alerts.push({ level: "warn", title: "Motion artifact", detail: "High motion artifact can make PPG/ECG-derived metrics unreliable." });
    }
    if (reading.signal.qualityPercent < 60) {
      alerts.push({ level: "warn", title: "Low signal quality", detail: "Check fabric tension, sensor placement, and skin contact pressure." });
    }

    for (const alert of alerts.slice(0, 7)) {
      const node = template.content.firstElementChild.cloneNode(true);
      node.classList.add(alert.level || "info");
      node.querySelector("strong").textContent = alert.title || "Notice";
      node.querySelector("p").textContent = alert.detail || "No detail.";
      list.appendChild(node);
    }
  }

  function drawWaveform(reading) {
    const canvas = $("waveCanvas");
    if (!canvas || !canvas.getContext) return;
    const rect = canvas.getBoundingClientRect();
    const dpr = Math.max(1, window.devicePixelRatio || 1);
    const cssWidth = Math.max(320, rect.width || 900);
    const cssHeight = 320;
    canvas.width = Math.round(cssWidth * dpr);
    canvas.height = Math.round(cssHeight * dpr);
    canvas.style.height = `${cssHeight}px`;

    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const width = cssWidth;
    const height = cssHeight;
    const pad = { left: 46, right: 24, top: 26, bottom: 44 };
    const points = reading.history;
    const values = points.map((p) => p.bpm).filter((v) => v > 0);
    const min = Math.max(30, Math.min(...values) - 12);
    const max = Math.min(220, Math.max(...values) + 12);

    ctx.clearRect(0, 0, width, height);
    const bg = ctx.createLinearGradient(0, 0, width, height);
    bg.addColorStop(0, "rgba(95,242,209,.16)");
    bg.addColorStop(1, "rgba(138,180,255,.055)");
    roundRect(ctx, 0, 0, width, height, 18);
    ctx.fillStyle = bg;
    ctx.fill();

    ctx.strokeStyle = "rgba(255,255,255,.085)";
    ctx.lineWidth = 1;
    ctx.font = "12px system-ui";
    ctx.fillStyle = "rgba(238,247,255,.65)";
    for (let i = 0; i <= 4; i += 1) {
      const y = pad.top + ((height - pad.top - pad.bottom) * i) / 4;
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(width - pad.right, y);
      ctx.stroke();
      ctx.fillText(String(Math.round(max - ((max - min) * i) / 4)), 12, y + 4);
    }

    const xFor = (index) => pad.left + ((width - pad.left - pad.right) * index) / Math.max(1, points.length - 1);
    const yFor = (value) => pad.top + (1 - (value - min) / (max - min || 1)) * (height - pad.top - pad.bottom);

    const area = ctx.createLinearGradient(0, pad.top, 0, height - pad.bottom);
    area.addColorStop(0, "rgba(95,242,209,.22)");
    area.addColorStop(1, "rgba(95,242,209,0)");
    ctx.beginPath();
    points.forEach((point, index) => {
      const x = xFor(index);
      const y = yFor(point.bpm);
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.lineTo(xFor(points.length - 1), height - pad.bottom);
    ctx.lineTo(xFor(0), height - pad.bottom);
    ctx.closePath();
    ctx.fillStyle = area;
    ctx.fill();

    const line = ctx.createLinearGradient(pad.left, 0, width - pad.right, 0);
    line.addColorStop(0, "#5ff2d1");
    line.addColorStop(.56, "#8ab4ff");
    line.addColorStop(1, "#f7b2ff");
    ctx.beginPath();
    points.forEach((point, index) => {
      const x = xFor(index);
      const y = yFor(point.bpm);
      if (index === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.lineWidth = 4;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = line;
    ctx.stroke();

    ctx.fillStyle = "rgba(95,242,209,.98)";
    points.forEach((point, index) => {
      ctx.beginPath();
      ctx.arc(xFor(index), yFor(point.bpm), 4.5, 0, Math.PI * 2);
      ctx.fill();
    });

    drawPulseGlyph(ctx, width - 176, 28);
    ctx.fillStyle = "rgba(238,247,255,.58)";
    ctx.fillText("bpm", 14, 20);
    ctx.fillText("last 60 s", width - 84, height - 17);
  }

  function drawPulseGlyph(ctx, x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.strokeStyle = "rgba(95,242,209,.75)";
    ctx.lineWidth = 3;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.beginPath();
    ctx.moveTo(0, 28);
    ctx.lineTo(26, 28);
    ctx.lineTo(36, 18);
    ctx.lineTo(48, 48);
    ctx.lineTo(66, 8);
    ctx.lineTo(82, 28);
    ctx.lineTo(132, 28);
    ctx.stroke();
    ctx.restore();
  }

  function generateTagUrl(showNoticeFlag) {
    const mode = $("urlMode").value;
    const reading = readingFromForm();
    const base = $("baseUrl").value || currentBaseUrl();

    if (mode === "id") generatedUrl = buildIdUrl(reading.patchId, base);
    else if (mode === "json") generatedUrl = buildJsonUrl(reading, base);
    else generatedUrl = buildCompactUrl(reading, base);

    $("generatedUrl").value = generatedUrl;
    const bytes = estimateNdefBytes(generatedUrl);
    const rawBytes = new TextEncoder().encode(generatedUrl).byteLength;
    const box = $("capacityBox");
    box.classList.remove("good", "warn", "bad");
    if (bytes <= NTAG213_USER_BYTES) {
      box.classList.add("good");
      box.textContent = `${rawBytes} URL bytes, about ${bytes} bytes with NDEF overhead. Good fit for NTAG213 (${NTAG213_USER_BYTES} byte user memory).`;
    } else if (rawBytes <= NTAG213_USER_BYTES) {
      box.classList.add("warn");
      box.textContent = `${rawBytes} URL bytes, about ${bytes} bytes with overhead. It may be too close for NTAG213; prefer ?id= mode.`;
    } else {
      box.classList.add("bad");
      box.textContent = `${rawBytes} URL bytes, about ${bytes} bytes with overhead. Too large for NTAG213; use patch ID only or a larger/dynamic NFC tag.`;
    }

    if (showNoticeFlag) showNoticeMessageForMode(mode, bytes);
  }

  function readingFromForm() {
    return normalizeReading({
      ...demoReading,
      patchId: sanitizeId($("formPatchId").value || DEFAULT_READING_ID),
      patchName: `Fabric Patch ${sanitizeId($("formPatchId").value || DEFAULT_READING_ID).toUpperCase()}`,
      measuredAt: new Date().toISOString(),
      readMode: $("urlMode").value === "compact" ? "compact-url-snapshot" : $("urlMode").value === "json" ? "encoded-json-url" : "patch-id-url",
      vitals: {
        heartRateBpm: clamp(Number($("formBpm").value), 0, 260, 72),
        spo2Percent: clamp(Number($("formSpo2").value), 0, 100, 98),
        skinTempC: clamp(Number($("formTemp").value), 0, 60, 32.6),
        hrvRmssdMs: clamp(Number($("formHrv").value), 0, 250, 48),
        confidence: clamp(Number($("formQuality").value) / 100, 0, 1, 0.94)
      },
      signal: {
        ...demoReading.signal,
        qualityPercent: clamp(Number($("formQuality").value), 0, 100, 94)
      },
      history: synthesizeHistory(clamp(Number($("formBpm").value), 0, 260, 72))
    });
  }

  function buildIdUrl(id, base = currentBaseUrl()) {
    const url = new URL(base, window.location.href);
    url.search = "";
    url.hash = "";
    url.searchParams.set("id", sanitizeId(id || DEFAULT_READING_ID));
    return url.toString();
  }

  function buildCompactUrl(reading, base = currentBaseUrl()) {
    const normalized = normalizeReading(reading);
    const url = new URL(base, window.location.href);
    url.search = "";
    url.hash = "";
    url.searchParams.set("p", normalized.patchId);
    url.searchParams.set("b", String(Math.round(normalized.vitals.heartRateBpm)));
    url.searchParams.set("o", String(round(normalized.vitals.spo2Percent, 1)));
    url.searchParams.set("t", String(round(normalized.vitals.skinTempC, 1)));
    url.searchParams.set("v", String(Math.round(normalized.vitals.hrvRmssdMs)));
    url.searchParams.set("q", String(Math.round(normalized.signal.qualityPercent)));
    return url.toString();
  }

  function buildJsonUrl(reading, base = currentBaseUrl()) {
    const url = new URL(base, window.location.href);
    url.search = "";
    url.hash = "";
    url.searchParams.set("d", base64UrlEncode(JSON.stringify(normalizeReading(reading))));
    return url.toString();
  }

  async function writeNfcUrl(url) {
    if (!url) return showNotice("No URL generated yet.");
    if (!("NDEFReader" in window)) {
      await copyText(url, "Web NFC is unavailable in this browser. URL copied; write it using an Android NFC writer app as a URL/URI record.");
      return;
    }
    try {
      showNotice("Hold the writable NTAG213 near the phone. Chrome will ask for NFC permission if needed.");
      const ndef = new NDEFReader();
      await ndef.write({ records: [{ recordType: "url", data: url }] }, { overwrite: true });
      showNotice("NFC tag written successfully. Tap the fabric patch again to test the open-screen flow.");
    } catch (error) {
      console.error(error);
      showNotice(`Web NFC write failed: ${error.message}. The URL is still available to copy manually.`);
    }
  }

  function hydrateFormDefaults() {
    $("baseUrl").value = currentBaseUrl();
  }

  function currentBaseUrl() {
    return `${window.location.origin}${window.location.pathname}`;
  }

  function parseHashParams(hash) {
    const clean = String(hash || "").replace(/^#\/?/, "");
    const index = clean.indexOf("?");
    return new URLSearchParams(index >= 0 ? clean.slice(index + 1) : clean);
  }

  function numberParam(params, keys, fallback) {
    for (const key of keys) {
      if (params.has(key)) return Number(params.get(key));
    }
    return fallback;
  }

  function sanitizeId(value) {
    return String(value || DEFAULT_READING_ID).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 32) || DEFAULT_READING_ID;
  }

  function clamp(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.max(min, Math.min(max, number));
  }

  function round(value, decimals = 0) {
    const number = Number(value);
    if (!Number.isFinite(number)) return 0;
    const factor = 10 ** decimals;
    return Math.round(number * factor) / factor;
  }

  function format(value, decimals, suffix) {
    const number = Number(value);
    if (!Number.isFinite(number) || number === 0) return "—";
    return `${number.toFixed(decimals)}${suffix}`;
  }

  function formatDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value || "—");
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "medium" }).format(date);
  }

  function trendLabel(history) {
    if (!Array.isArray(history) || history.length < 2) return "No trend data";
    const first = history[0].bpm;
    const last = history[history.length - 1].bpm;
    const delta = last - first;
    if (Math.abs(delta) < 2) return "Stable last minute";
    return delta > 0 ? `Up ${Math.abs(delta).toFixed(0)} bpm` : `Down ${Math.abs(delta).toFixed(0)} bpm`;
  }

  function setPill(id, label, value, warnThreshold, dangerThreshold) {
    const node = $(id);
    node.classList.remove("warn", "danger");
    node.textContent = label;
    if (value <= dangerThreshold) node.classList.add("danger");
    else if (value <= warnThreshold) node.classList.add("warn");
  }

  function text(id, value) {
    const node = $(id);
    if (node) node.textContent = value === undefined || value === null || value === "" ? "—" : String(value);
  }

  function estimateNdefBytes(url) {
    return new TextEncoder().encode(String(url || "")).byteLength + NDEF_URL_OVERHEAD_ESTIMATE;
  }

  function showNoticeMessageForMode(mode, bytes) {
    if (mode === "id") showNotice("Patch-ID URL generated. This is the best NTAG213 mode because it is short and robust.");
    else if (mode === "compact") showNotice(bytes <= NTAG213_USER_BYTES ? "Compact snapshot URL generated. It stores BPM and a few vitals directly on the tag." : "Compact URL is still too large. Shorten the base URL or use ?id= mode.");
    else showNotice("Encoded JSON generated. This usually does not fit NTAG213; use NTAG216/dynamic NFC or patch-ID mode.");
  }

  async function copyText(value, message) {
    if (!value) return showNotice("Nothing to copy.");
    try {
      await navigator.clipboard.writeText(String(value));
      showNotice(message || "Copied.");
    } catch (error) {
      console.error(error);
      showNotice("Clipboard permission was blocked. Select the URL field and copy manually.");
    }
  }

  function showNotice(message) {
    const notice = $("systemNotice");
    if (notice) notice.textContent = message;
  }

  function base64UrlEncode(value) {
    const bytes = new TextEncoder().encode(value);
    let binary = "";
    bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  function base64UrlDecode(value) {
    const normalized = String(value || "").replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(String(value || "").length / 4) * 4, "=");
    const binary = atob(normalized);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  }

  function roundRect(ctx, x, y, width, height, radius) {
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + width, y, x + width, y + height, radius);
    ctx.arcTo(x + width, y + height, x, y + height, radius);
    ctx.arcTo(x, y + height, x, y, radius);
    ctx.arcTo(x, y, x + radius, y, radius);
    ctx.closePath();
  }

  function debounce(fn, wait) {
    let timer;
    return (...args) => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => fn(...args), wait);
    };
  }

  function registerServiceWorker() {
    if ("serviceWorker" in navigator && window.location.protocol === "https:") {
      navigator.serviceWorker.register("service-worker.js").catch((error) => console.debug("Service worker skipped", error));
    }
  }
})();
