(() => {
  "use strict";

  const MIME_TYPE = "application/vnd.polymerpatch.health+json";
  const DEFAULT_PATCH_ID = "WP-HYDROGEL-001";
  const params = new URLSearchParams(window.location.search || "");
  const hashParams = parseHashParams(window.location.hash);
  const $ = (id) => document.getElementById(id);

  let currentReading = null;
  let generatedUrl = "";

  const demoReading = {
    schema: "wristpatch.health.v1",
    patchId: DEFAULT_PATCH_ID,
    subjectAlias: "Volunteer A",
    measuredAt: new Date().toISOString(),
    device: {
      firmware: "0.3.0-research",
      sensorStack: ["NFC Type 2 tag", "PPG optical module", "skin impedance pair", "hydrogel electrode interface"],
      nfcUid: "04-A2-17-8C-93-21-80"
    },
    vitals: {
      heartRateBpm: 72,
      heartRateConfidence: 0.96,
      spo2Percent: 98,
      skinTempC: 32.4,
      hrvRmssdMs: 48,
      respirationRateBrpm: 15
    },
    patch: {
      hydrationIndex: 0.71,
      electrodeImpedanceKohm: 18.2,
      adhesionScore: 0.88,
      interfaceMaterial: "conductive hydrogel / Ag-AgCl reference island"
    },
    signal: {
      quality: 94,
      motionArtifact: 0.08,
      batteryPercent: 82
    },
    history: [
      { t: -60, heartRateBpm: 70 }, { t: -50, heartRateBpm: 71 }, { t: -40, heartRateBpm: 72 },
      { t: -30, heartRateBpm: 73 }, { t: -20, heartRateBpm: 72 }, { t: -10, heartRateBpm: 74 },
      { t: 0, heartRateBpm: 72 }
    ],
    alerts: [
      { level: "ok", title: "Signal quality acceptable", detail: "PPG confidence and contact impedance are within the configured prototype range." },
      { level: "info", title: "Research mode", detail: "Values are displayed as a research snapshot and must not be used for diagnosis." }
    ],
    signature: null
  };

  document.addEventListener("DOMContentLoaded", init);
  window.addEventListener("resize", () => currentReading && drawChart(currentReading));

  async function init() {
    registerServiceWorker();
    hydrateBaseUrl();
    bindEvents();
    const loaded = await loadFromUrl();
    if (!loaded) {
      setNotice("No NFC payload found in the URL. Demo data is loaded so the interface can be tested before writing a real tag.");
      updateDashboard(demoReading, "demo");
    }
    buildGeneratedUrlFromForm();
  }

  function bindEvents() {
    $("demoButton").addEventListener("click", () => updateDashboard({ ...demoReading, measuredAt: new Date().toISOString() }, "demo button"));
    $("copyUrlButton").addEventListener("click", () => copyText(buildNfcUrl(currentReading || demoReading), "NFC URL copied."));
    $("copyJsonButton").addEventListener("click", () => copyText(JSON.stringify(currentReading || demoReading, null, 2), "JSON payload copied."));
    $("writeTagButton").addEventListener("click", () => writeUrlWithWebNfc(buildNfcUrl(currentReading || demoReading)));
    $("builderForm").addEventListener("submit", (event) => {
      event.preventDefault();
      buildGeneratedUrlFromForm(true);
    });
    ["baseUrl", "builderPatchId", "builderHeartRate", "builderSpo2", "builderSkinTemp", "builderHydration"].forEach((id) => {
      $(id).addEventListener("input", () => buildGeneratedUrlFromForm(false));
    });
    $("copyGeneratedButton").addEventListener("click", () => copyText(generatedUrl, "Generated NFC URL copied."));
    $("writeGeneratedButton").addEventListener("click", () => writeUrlWithWebNfc(generatedUrl));
  }

  async function loadFromUrl() {
    try {
      const merged = new URLSearchParams(hashParams.toString());
      for (const [key, value] of params.entries()) merged.set(key, value);

      if (merged.has("d")) {
        const reading = parseEncodedPayload(merged.get("d"));
        updateDashboard(reading, "NFC URL payload");
        return true;
      }

      if (merged.has("patch")) {
        const patchId = sanitizePatchId(merged.get("patch"));
        const response = await fetch(`data/patches/${patchId}.json`, { cache: "no-store" });
        if (!response.ok) throw new Error(`Patch file not found: ${patchId}`);
        const reading = await response.json();
        updateDashboard(reading, `patch registry: ${patchId}`);
        return true;
      }

      if (merged.has("url")) {
        const url = new URL(merged.get("url"), window.location.href);
        if (url.origin !== window.location.origin) throw new Error("External payload URLs are blocked by default.");
        const response = await fetch(url.href, { cache: "no-store" });
        if (!response.ok) throw new Error(`Payload URL failed: ${response.status}`);
        const reading = await response.json();
        updateDashboard(reading, `payload URL: ${url.pathname}`);
        return true;
      }
    } catch (error) {
      console.error(error);
      setNotice(`Payload could not be decoded: ${error.message}. Demo data is shown instead.`);
      updateDashboard(demoReading, "decode fallback");
      return true;
    }
    return false;
  }

  function updateDashboard(rawReading, sourceLabel) {
    const reading = normalizeReading(rawReading);
    currentReading = reading;

    const vitals = reading.vitals;
    const patch = reading.patch;
    const signal = reading.signal;

    $("recordStatus").textContent = "Loaded";
    $("recordSource").textContent = sourceLabel;
    $("integrityStatus").textContent = reading.signature ? "Signature present" : "Unsigned prototype";
    $("measuredAt").textContent = formatDate(reading.measuredAt);
    $("patchId").textContent = reading.patchId;
    $("subjectAlias").textContent = reading.subjectAlias || "—";
    $("firmware").textContent = reading.device.firmware || "—";
    $("nfcUid").textContent = reading.device.nfcUid || "—";
    $("sensorStack").textContent = Array.isArray(reading.device.sensorStack) ? reading.device.sensorStack.join(", ") : "—";

    setText("heartRate", vitals.heartRateBpm, "—");
    setText("spo2", formatNumber(vitals.spo2Percent, 1, "%"));
    setText("skinTemp", formatNumber(vitals.skinTempC, 1, " °C"));
    setText("hrv", formatNumber(vitals.hrvRmssdMs, 0, " ms"));
    setText("hydration", formatNumber(patch.hydrationIndex * 100, 0, "%"));
    setText("impedance", formatNumber(patch.electrodeImpedanceKohm, 1, " kΩ"));
    setText("adhesion", formatNumber(patch.adhesionScore * 100, 0, "%"));
    setText("motionArtifact", formatNumber(signal.motionArtifact * 100, 0, "%"));
    setText("battery", formatNumber(signal.batteryPercent, 0, "% battery"));
    setText("signalQuality", formatNumber(signal.quality, 0, "% quality"));
    setText("heartConfidence", formatNumber(vitals.heartRateConfidence * 100, 0, "% conf."));

    const heartLevel = heartRateLevel(vitals.heartRateBpm);
    $("heartRing").style.setProperty("--value", Math.max(0, Math.min(100, (vitals.heartRateBpm / 180) * 100)).toFixed(0));
    $("heartStatus").textContent = heartLevel.message;
    $("heartTrend").textContent = trendLabel(reading.history);
    updatePillClass($("heartConfidence"), vitals.heartRateConfidence * 100, 80, 60);
    updatePillClass($("signalQuality"), signal.quality, 80, 60);
    updatePillClass($("battery"), signal.batteryPercent, 35, 15);

    renderAlerts(reading, heartLevel);
    $("rawPayload").textContent = JSON.stringify(reading, null, 2);
    drawChart(reading);
    buildGeneratedUrlFromForm(false);
  }

  function normalizeReading(raw) {
    const reading = typeof raw === "object" && raw !== null ? raw : {};
    const vitals = reading.vitals || {};
    const patch = reading.patch || {};
    const signal = reading.signal || {};
    const device = reading.device || {};
    return {
      schema: String(reading.schema || "wristpatch.health.v1"),
      patchId: String(reading.patchId || DEFAULT_PATCH_ID),
      subjectAlias: String(reading.subjectAlias || "Anonymous"),
      measuredAt: reading.measuredAt || new Date().toISOString(),
      device: {
        firmware: String(device.firmware || "unknown"),
        sensorStack: Array.isArray(device.sensorStack) ? device.sensorStack.map(String) : [],
        nfcUid: String(device.nfcUid || "not provided")
      },
      vitals: {
        heartRateBpm: clampNumber(vitals.heartRateBpm, 0, 260, 0),
        heartRateConfidence: clampNumber(vitals.heartRateConfidence, 0, 1, 0),
        spo2Percent: clampNumber(vitals.spo2Percent, 0, 100, 0),
        skinTempC: clampNumber(vitals.skinTempC, 0, 60, 0),
        hrvRmssdMs: clampNumber(vitals.hrvRmssdMs, 0, 250, 0),
        respirationRateBrpm: clampNumber(vitals.respirationRateBrpm, 0, 80, 0)
      },
      patch: {
        hydrationIndex: clampNumber(patch.hydrationIndex, 0, 1, 0),
        electrodeImpedanceKohm: clampNumber(patch.electrodeImpedanceKohm, 0, 500, 0),
        adhesionScore: clampNumber(patch.adhesionScore, 0, 1, 0),
        interfaceMaterial: String(patch.interfaceMaterial || "not specified")
      },
      signal: {
        quality: clampNumber(signal.quality, 0, 100, 0),
        motionArtifact: clampNumber(signal.motionArtifact, 0, 1, 0),
        batteryPercent: clampNumber(signal.batteryPercent, 0, 100, 0)
      },
      history: Array.isArray(reading.history) ? reading.history.map((point, index) => ({
        t: Number.isFinite(Number(point.t)) ? Number(point.t) : index,
        heartRateBpm: clampNumber(point.heartRateBpm, 0, 260, 0)
      })) : [],
      alerts: Array.isArray(reading.alerts) ? reading.alerts : [],
      signature: reading.signature || null
    };
  }

  function heartRateLevel(bpm) {
    if (!bpm) return { level: "warn", message: "No heart-rate value in this NFC payload." };
    if (bpm < 45) return { level: "danger", message: "Very low pulse flag for research review. Confirm sensor contact and subject state." };
    if (bpm > 120) return { level: "warn", message: "Elevated pulse flag. Motion, stress, fever, or exercise may be contributing." };
    return { level: "ok", message: "Heart-rate snapshot is within the configured prototype display band." };
  }

  function renderAlerts(reading, heartLevel) {
    const list = $("alerts");
    list.replaceChildren();
    const alerts = [
      { level: heartLevel.level, title: "Heart-rate display band", detail: heartLevel.message },
      ...reading.alerts
    ];
    if (reading.signal.motionArtifact > 0.25) alerts.push({ level: "warn", title: "Motion artifact", detail: "Motion artifact is high; PPG-derived metrics may be unreliable." });
    if (reading.patch.electrodeImpedanceKohm > 80) alerts.push({ level: "warn", title: "Contact impedance", detail: "High impedance can indicate drying hydrogel or poor skin contact." });
    if (!reading.signature) alerts.push({ level: "info", title: "Unsigned NFC payload", detail: "For production, add a backend-signed reading or HMAC to reduce spoofing risk." });

    const template = $("alertTemplate");
    for (const alert of alerts) {
      const node = template.content.firstElementChild.cloneNode(true);
      node.classList.add(alert.level === "danger" ? "danger" : alert.level === "warn" ? "warn" : "ok");
      node.querySelector("strong").textContent = alert.title || "Notice";
      node.querySelector("p").textContent = alert.detail || "No detail provided.";
      list.appendChild(node);
    }
  }

  function drawChart(reading) {
    const canvas = $("heartChart");
    if (!canvas || !canvas.getContext) return;
    const rect = canvas.getBoundingClientRect();
    const dpr = Math.max(1, window.devicePixelRatio || 1);
    canvas.width = Math.max(640, Math.floor(rect.width * dpr));
    canvas.height = Math.floor(320 * dpr);
    const ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
    const width = canvas.width / dpr;
    const height = canvas.height / dpr;
    const points = reading.history.length ? reading.history : [{ t: 0, heartRateBpm: reading.vitals.heartRateBpm }];
    const values = points.map((point) => point.heartRateBpm).filter((value) => value > 0);
    const min = Math.max(30, Math.min(...values, 60) - 12);
    const max = Math.min(220, Math.max(...values, 100) + 12);
    const pad = { left: 44, right: 22, top: 28, bottom: 44 };

    ctx.clearRect(0, 0, width, height);
    const gradient = ctx.createLinearGradient(0, 0, width, height);
    gradient.addColorStop(0, "rgba(95, 242, 209, 0.16)");
    gradient.addColorStop(1, "rgba(138, 180, 255, 0.06)");
    ctx.fillStyle = gradient;
    roundRect(ctx, 0, 0, width, height, 18);
    ctx.fill();

    ctx.strokeStyle = "rgba(255,255,255,.08)";
    ctx.lineWidth = 1;
    ctx.font = "12px system-ui";
    ctx.fillStyle = "rgba(233,243,255,.64)";
    for (let i = 0; i <= 4; i += 1) {
      const y = pad.top + ((height - pad.top - pad.bottom) * i) / 4;
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(width - pad.right, y);
      ctx.stroke();
      const label = Math.round(max - ((max - min) * i) / 4);
      ctx.fillText(`${label}`, 12, y + 4);
    }

    const xFor = (index) => pad.left + ((width - pad.left - pad.right) * index) / Math.max(1, points.length - 1);
    const yFor = (value) => pad.top + (1 - (value - min) / (max - min || 1)) * (height - pad.top - pad.bottom);

    ctx.lineWidth = 4;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    const lineGradient = ctx.createLinearGradient(pad.left, 0, width - pad.right, 0);
    lineGradient.addColorStop(0, "#5ff2d1");
    lineGradient.addColorStop(1, "#8ab4ff");
    ctx.strokeStyle = lineGradient;
    ctx.beginPath();
    points.forEach((point, index) => {
      const x = xFor(index);
      const y = yFor(point.heartRateBpm);
      index === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    });
    ctx.stroke();

    ctx.fillStyle = "rgba(95,242,209,.95)";
    points.forEach((point, index) => {
      ctx.beginPath();
      ctx.arc(xFor(index), yFor(point.heartRateBpm), 4.5, 0, Math.PI * 2);
      ctx.fill();
    });

    ctx.fillStyle = "rgba(233,243,255,.58)";
    ctx.fillText("bpm", 12, 20);
    ctx.fillText("time", width - 52, height - 16);
  }

  function buildGeneratedUrlFromForm(showNotice = false) {
    const reading = normalizeReading({
      ...demoReading,
      patchId: $("builderPatchId")?.value || DEFAULT_PATCH_ID,
      measuredAt: new Date().toISOString(),
      vitals: {
        ...demoReading.vitals,
        heartRateBpm: Number($("builderHeartRate")?.value || 72),
        spo2Percent: Number($("builderSpo2")?.value || 98),
        skinTempC: Number($("builderSkinTemp")?.value || 32.4)
      },
      patch: {
        ...demoReading.patch,
        hydrationIndex: Number($("builderHydration")?.value || 0.71)
      }
    });
    const baseUrl = $("baseUrl")?.value || currentBaseUrl();
    generatedUrl = buildNfcUrl(reading, baseUrl);
    if ($("generatedUrl")) $("generatedUrl").value = generatedUrl;
    const bytes = new TextEncoder().encode(generatedUrl).byteLength;
    const recommendation = bytes > 850 ? "Use a dynamic NFC tag or store only ?patch=ID; this URL is large for common NTAG213/215 tags." : "Fits many larger NTAG/NFC Forum Type 2 tags; verify exact tag capacity before locking it.";
    if ($("urlSize")) $("urlSize").textContent = `${bytes} bytes. ${recommendation}`;
    if (showNotice) setNotice("Generated NFC URL is ready. Copy it or write it to an NFC tag as a URL record.");
  }

  function buildNfcUrl(reading, baseUrl = currentBaseUrl()) {
    const url = new URL(baseUrl, window.location.href);
    url.search = "";
    url.hash = "";
    url.searchParams.set("d", base64UrlEncode(JSON.stringify(normalizeReading(reading))));
    return url.toString();
  }

  async function writeUrlWithWebNfc(url) {
    if (!url) return setNotice("No URL generated yet.");
    if (!("NDEFReader" in window)) {
      await copyText(url, "Web NFC is not available in this browser; NFC URL copied. Use NFC Tools or Samsung-compatible tag writer app.");
      return;
    }
    try {
      setNotice("Hold the writable NFC tag near the phone. Chrome will ask for NFC permission if needed.");
      const ndef = new NDEFReader();
      await ndef.write({ records: [{ recordType: "url", data: url }] }, { overwrite: true });
      setNotice("NFC URL record written successfully. Test by locking the phone, enabling NFC, and tapping the tag.");
    } catch (error) {
      console.error(error);
      setNotice(`Web NFC write failed: ${error.message}. The URL remains available to copy manually.`);
    }
  }

  function parseEncodedPayload(encoded) {
    const json = base64UrlDecode(encoded || "");
    return JSON.parse(json);
  }

  function parseHashParams(hash) {
    const clean = (hash || "").replace(/^#\/?/, "");
    const queryIndex = clean.indexOf("?");
    return new URLSearchParams(queryIndex >= 0 ? clean.slice(queryIndex + 1) : "");
  }

  function hydrateBaseUrl() {
    const input = $("baseUrl");
    if (input) input.value = currentBaseUrl();
  }

  function currentBaseUrl() {
    return `${window.location.origin}${window.location.pathname}`;
  }

  function base64UrlEncode(value) {
    const bytes = new TextEncoder().encode(value);
    let binary = "";
    bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function base64UrlDecode(value) {
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
    const binary = atob(base64);
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  }

  async function copyText(text, message) {
    if (!text) return setNotice("Nothing to copy yet.");
    try {
      await navigator.clipboard.writeText(text);
      setNotice(message || "Copied.");
    } catch {
      setNotice("Clipboard permission was blocked. Select and copy manually from the generated field.");
    }
  }

  function setNotice(message) {
    const notice = $("runtimeNotice");
    if (notice) notice.textContent = message;
  }

  function setText(id, value, fallback = "—") {
    const node = $(id);
    if (node) node.textContent = value || fallback;
  }

  function formatNumber(value, decimals = 0, suffix = "") {
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
    const first = history[0].heartRateBpm;
    const last = history[history.length - 1].heartRateBpm;
    const delta = last - first;
    if (Math.abs(delta) < 2) return "Stable over snapshot";
    return delta > 0 ? `Up ${delta.toFixed(0)} bpm` : `Down ${Math.abs(delta).toFixed(0)} bpm`;
  }

  function clampNumber(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.max(min, Math.min(max, number));
  }

  function sanitizePatchId(value) {
    return String(value || DEFAULT_PATCH_ID).replace(/[^a-zA-Z0-9._-]/g, "").slice(0, 80) || DEFAULT_PATCH_ID;
  }

  function updatePillClass(node, value, warnThreshold, dangerThreshold) {
    if (!node) return;
    node.classList.remove("warn", "danger");
    if (value <= dangerThreshold) node.classList.add("danger");
    else if (value <= warnThreshold) node.classList.add("warn");
  }

  function roundRect(ctx, x, y, width, height, radius) {
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + width, y, x + width, y + height, radius);
    ctx.arcTo(x + width, y + height, x, y + height, radius);
    ctx.arcTo(x, y + height, x, y, radius);
    ctx.arcTo(x, y, x + width, y, radius);
    ctx.closePath();
  }

  function registerServiceWorker() {
    if ("serviceWorker" in navigator && window.location.protocol === "https:") {
      navigator.serviceWorker.register("service-worker.js").catch((error) => console.debug("Service worker skipped", error));
    }
  }
})();
