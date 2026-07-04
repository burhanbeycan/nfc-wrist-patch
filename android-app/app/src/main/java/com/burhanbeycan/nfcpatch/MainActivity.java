package com.burhanbeycan.nfcpatch;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int NTAG213_BYTES = 144;
    private static final int NDEF_OVERHEAD = 8;
    private static final String DEFAULT_URI = "wpatch://read?p=fab01&b=72&o=98&t=326&v=48&q=94";

    private final int bg = Color.rgb(8, 17, 31);
    private final int surface = Color.argb(226, 18, 38, 66);
    private final int border = Color.argb(58, 159, 205, 255);
    private final int text = Color.rgb(238, 247, 255);
    private final int muted = Color.rgb(157, 180, 207);
    private final int accent = Color.rgb(95, 242, 209);
    private final int accent2 = Color.rgb(138, 180, 255);
    private final int danger = Color.rgb(255, 101, 122);

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private boolean writeArmed;
    private Reading reading = Reading.demo();

    private TextView status;
    private TextView bpm;
    private TextView patch;
    private TextView source;
    private TextView measured;
    private TextView spo2;
    private TextView hrv;
    private TextView temp;
    private TextView quality;
    private TextView battery;
    private TextView mode;
    private TextView tag;
    private TextView memory;
    private TextView alerts;
    private TextView raw;
    private EditText uriBox;
    private Gauge gauge;
    private Trend trend;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags);
        setContentView(buildUi());
        handle(getIntent(), "app start");
    }

    @Override protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            String[][] tech = new String[][]{
                    new String[]{android.nfc.tech.Ndef.class.getName()},
                    new String[]{android.nfc.tech.NfcA.class.getName()},
                    new String[]{android.nfc.tech.NdefFormatable.class.getName()}
            };
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, tech);
        }
    }

    @Override protected void onPause() {
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (writeArmed) {
            Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (t != null) {
                writeArmed = false;
                writeTag(t, uriBox.getText().toString().trim());
                return;
            }
        }
        handle(intent, "NFC tap");
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(false);
        scroll.setPadding(dp(12), dp(18), dp(12), dp(26));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = card();
        hero.addView(eyebrow("NTAG213 · Galaxy S21 · native app"));
        hero.addView(title("Tap label.\nOpen heartbeat."));
        hero.addView(body("Write a compact wpatch:// NDEF URI to the NTAG213. When the phone approaches the fabric label, Android opens this installed app and displays the heartbeat payload."));
        status = body("Ready. Install app, enable NFC, write tag, tap label.");
        hero.addView(status);
        LinearLayout hButtons = row();
        hButtons.addView(button("Demo", v -> render(Reading.demo(), "manual demo")), weight());
        hButtons.addView(button("Write", v -> armWrite()), weight());
        hButtons.addView(button("Copy URI", v -> copy(uriBox.getText().toString())), weight());
        hero.addView(hButtons);
        root.addView(hero, margin());

        LinearLayout heart = card();
        heart.addView(eyebrow("Primary vital"));
        heart.addView(section("Heart rate"));
        LinearLayout h = row();
        gauge = new Gauge(this);
        h.addView(gauge, new LinearLayout.LayoutParams(dp(160), dp(160)));
        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.VERTICAL);
        numbers.setPadding(dp(16), 0, 0, 0);
        bpm = huge("--");
        numbers.addView(bpm);
        numbers.addView(body("bpm"));
        patch = value("—");
        source = body("No NFC yet");
        measured = body("—");
        numbers.addView(patch);
        numbers.addView(source);
        numbers.addView(measured);
        h.addView(numbers, weight());
        heart.addView(h);
        root.addView(heart, margin());

        LinearLayout metrics = row();
        spo2 = value("—"); hrv = value("—"); temp = value("—");
        metrics.addView(metric("SpO₂", spo2, "oxygen estimate"), weight());
        metrics.addView(metric("HRV", hrv, "RMSSD"), weight());
        metrics.addView(metric("Temp", temp, "skin interface"), weight());
        root.addView(metrics, margin());

        LinearLayout chart = card();
        chart.addView(eyebrow("Pulse trend"));
        chart.addView(section("Last minute"));
        trend = new Trend(this);
        chart.addView(trend, new LinearLayout.LayoutParams(-1, dp(220)));
        root.addView(chart, margin());

        LinearLayout details = card();
        details.addView(eyebrow("Signal and NFC"));
        details.addView(section("Patch state"));
        quality = value("—"); battery = value("—"); mode = value("—"); tag = value("—"); memory = value("—"); alerts = body("—");
        details.addView(line("Quality", quality));
        details.addView(line("Battery", battery));
        details.addView(line("Read mode", mode));
        details.addView(line("NFC UID", tag));
        details.addView(line("NDEF size", memory));
        details.addView(alerts);
        root.addView(details, margin());

        LinearLayout writer = card();
        writer.addView(eyebrow("NTAG213 writer"));
        writer.addView(section("Write URI that opens this app"));
        writer.addView(body("Recommended payload: compact custom URI. It is shorter and more reliable for a sideloaded demo than a normal browser URL."));
        uriBox = new EditText(this);
        uriBox.setText(DEFAULT_URI);
        uriBox.setTextColor(text);
        uriBox.setHintTextColor(muted);
        uriBox.setMinLines(2);
        uriBox.setSingleLine(false);
        uriBox.setBackground(round(Color.argb(55,255,255,255), dp(14), Color.argb(45,255,255,255)));
        uriBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        writer.addView(uriBox, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout wButtons = row();
        wButtons.addView(button("Preview", v -> render(readUri(Uri.parse(uriBox.getText().toString()), "preview"), "preview")), weight());
        wButtons.addView(button("Write NTAG213", v -> armWrite()), weight());
        writer.addView(wButtons);
        root.addView(writer, margin());

        LinearLayout rawCard = card();
        rawCard.addView(eyebrow("Raw payload"));
        raw = body("No payload yet.");
        raw.setTypeface(Typeface.MONOSPACE);
        raw.setTextSize(12);
        rawCard.addView(raw);
        root.addView(rawCard, margin());

        LinearLayout truth = card();
        truth.addView(eyebrow("Truthful architecture"));
        truth.addView(section("NTAG213 opens the app; sensors create real heartbeat."));
        truth.addView(body("NTAG213 is passive memory. It cannot measure pulse alone. This app displays the true values stored in the tag URI or selected by tag ID. Real live heartbeat needs PPG/ECG electronics, MCU, power, and BLE/backend/dynamic NFC update path."));
        root.addView(truth, margin());
        return scroll;
    }

    private void handle(Intent intent, String fallback) {
        if (intent == null) { render(Reading.demo(), fallback); return; }
        Uri data = intent.getData();
        if (data != null) { render(readUri(data, label(intent.getAction())), label(intent.getAction())); return; }
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMsgs != null) {
            for (Parcelable p : rawMsgs) if (p instanceof NdefMessage) {
                for (NdefRecord r : ((NdefMessage)p).getRecords()) {
                    String s = recordToString(r);
                    if (s != null && (s.startsWith("wpatch://") || s.startsWith("https://"))) {
                        render(readUri(Uri.parse(s), "NDEF URI"), "NDEF URI"); return;
                    }
                    if (s != null && (s.contains("b=") || s.contains("bpm="))) {
                        render(readQuery(s, "NDEF text"), "NDEF text"); return;
                    }
                }
            }
        }
        Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (t != null) {
            Reading r = Reading.demo();
            r.src = "tag detected without wpatch payload";
            r.uid = hex(t.getId());
            r.alert = "Tag detected, but no app payload. Press Write NTAG213 to format it.";
            render(r, r.src); return;
        }
        render(Reading.demo(), fallback);
    }

    private Reading readUri(Uri u, String src) {
        String id = q(u, "id", "patch");
        boolean hasVitals = q(u, "b", "bpm", "hr") != null;
        if (!hasVitals && id != null) return local(id, src + " id=" + id);
        return readQuery(u.getEncodedQuery() == null ? "" : u.getEncodedQuery(), src);
    }

    private Reading readQuery(String q, String src) {
        Uri u = Uri.parse("wpatch://read?" + q.replace(';', '&'));
        Reading r = new Reading();
        r.id = clean(or(q(u,"p","id","patch"), "fab01"));
        r.name = "Fabric Patch " + r.id.toUpperCase(Locale.US);
        r.bpm = num(u,72,"b","bpm","hr");
        r.spo2 = num(u,98,"o","spo2");
        double tv = num(u,326,"t","temp");
        r.temp = tv > 80 ? tv / 10.0 : tv;
        r.hrv = num(u,48,"v","hrv");
        r.quality = num(u,94,"q","quality");
        r.conf = Math.max(0, Math.min(1, r.quality/100.0));
        r.battery = num(u,86,"bat","battery");
        r.motion = num(u,8,"m","motion");
        r.src = src;
        r.mode = "NTAG213 compact URI snapshot";
        r.uid = "from URI payload";
        r.uri = "wpatch://read?" + q;
        r.history = hist(r.bpm);
        r.alert = "Displayed values are the true numeric payload read from the NTAG213 URI. Update/overwrite the tag when the value changes.";
        return r;
    }

    private Reading local(String id, String src) {
        Reading r = "fab02".equalsIgnoreCase(id) ? Reading.motion() : Reading.demo();
        r.src = src; r.mode = "local demo by tag ID"; r.alert = "Tag carried only an ID; app loaded local demo reading.";
        return r;
    }

    private void render(Reading r, String src) {
        reading = r; r.src = src;
        bpm.setText(String.valueOf(Math.round(r.bpm)));
        patch.setText(r.name + " · " + r.id);
        source.setText(src);
        measured.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(r.time)));
        spo2.setText(fmt(r.spo2,1) + "%");
        hrv.setText(fmt(r.hrv,0) + " ms");
        temp.setText(fmt(r.temp,1) + " °C");
        quality.setText(fmt(r.quality,0) + "% · conf " + fmt(r.conf*100,0) + "%");
        battery.setText(fmt(r.battery,0) + "%");
        mode.setText(r.mode);
        tag.setText("NTAG213 · " + r.uid);
        int bytes = estimate(r.uri == null ? DEFAULT_URI : r.uri);
        memory.setText(bytes + "/" + NTAG213_BYTES + " B estimated");
        String flag = r.bpm < 45 ? "Very low pulse flag." : r.bpm > 120 ? "Elevated pulse flag." : "Heartbeat snapshot is inside demo display band.";
        alerts.setText("• " + flag + "\n• " + r.alert + "\n• NTAG213 is passive; real live values need sensor electronics.");
        raw.setText(r.json());
        status.setText("Loaded: " + src);
        gauge.setBpm((float)r.bpm);
        trend.setData(r.history);
    }

    private void armWrite() {
        if (nfcAdapter == null) { toast("No NFC adapter detected."); return; }
        if (!nfcAdapter.isEnabled()) { toast("Enable NFC in Android settings first."); return; }
        String u = uriBox.getText().toString().trim();
        if (estimate(u) > NTAG213_BYTES) { toast("URI too large for NTAG213. Use shorter wpatch URI."); return; }
        writeArmed = true;
        status.setText("Writer armed. Touch the NTAG213 label now.");
        toast("Touch NTAG213 to phone now.");
    }

    private void writeTag(Tag tagObj, String uri) {
        if (uri == null || uri.length() == 0) uri = DEFAULT_URI;
        try {
            NdefMessage msg = new NdefMessage(new NdefRecord[]{NdefRecord.createUri(Uri.parse(uri))});
            int size = msg.toByteArray().length;
            if (size > NTAG213_BYTES) throw new IOException("NDEF " + size + " B > NTAG213 target " + NTAG213_BYTES + " B");
            Ndef ndef = Ndef.get(tagObj);
            if (ndef != null) {
                ndef.connect();
                try { if (!ndef.isWritable()) throw new IOException("Tag read-only"); if (ndef.getMaxSize() < size) throw new IOException("Tag too small"); ndef.writeNdefMessage(msg); }
                finally { ndef.close(); }
            } else {
                NdefFormatable f = NdefFormatable.get(tagObj);
                if (f == null) throw new IOException("Not NDEF formatable");
                f.connect(); try { f.format(msg); } finally { f.close(); }
            }
            status.setText("Success. Move phone away, then tap label again to open app.");
            toast("NTAG213 written successfully.");
        } catch (IOException | FormatException | RuntimeException e) {
            status.setText("Write failed: " + e.getMessage()); toast("Write failed: " + e.getMessage());
        }
    }

    private String recordToString(NdefRecord r) {
        try {
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(r.getType(), NdefRecord.RTD_URI)) return decodeUri(r.getPayload());
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(r.getType(), NdefRecord.RTD_TEXT)) {
                byte[] p = r.getPayload(); if (p.length == 0) return ""; int lang = p[0] & 0x3F; return new String(p, 1 + lang, p.length - 1 - lang, StandardCharsets.UTF_8);
            }
            if (r.getTnf() == NdefRecord.TNF_MIME_MEDIA) return new String(r.getPayload(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
        return null;
    }

    private String decodeUri(byte[] p) {
        String[] prefixes = {"","http://www.","https://www.","http://","https://","tel:","mailto:"};
        if (p == null || p.length == 0) return "";
        int code = p[0] & 255; String pre = code < prefixes.length ? prefixes[code] : "";
        return pre + new String(p, 1, p.length - 1, StandardCharsets.UTF_8);
    }

    private LinearLayout card(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),dp(18),dp(18),dp(18)); l.setBackground(round(surface,dp(24),border)); return l; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private View metric(String name, TextView v, String sub){ LinearLayout c=card(); c.setPadding(dp(12),dp(12),dp(12),dp(12)); c.addView(eyebrow(name)); c.addView(v); c.addView(body(sub)); return c; }
    private View line(String k, TextView v){ LinearLayout r=row(); r.setPadding(0,dp(8),0,dp(8)); r.addView(body(k), weight()); v.setGravity(Gravity.END); r.addView(v, weight()); return r; }
    private TextView title(String s){ TextView v=t(s,42,text,true); v.setIncludeFontPadding(false); return v; }
    private TextView section(String s){ return t(s,22,text,true); }
    private TextView value(String s){ return t(s,20,text,true); }
    private TextView huge(String s){ TextView v=t(s,54,text,true); v.setIncludeFontPadding(false); return v; }
    private TextView body(String s){ TextView v=t(s,14,muted,false); v.setLineSpacing(dp(2),1); return v; }
    private TextView eyebrow(String s){ TextView v=t(s.toUpperCase(Locale.US),11,accent,true); v.setLetterSpacing(.12f); return v; }
    private TextView t(String s,int sp,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextColor(color); v.setTextSize(sp); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private Button button(String s, View.OnClickListener l){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.rgb(4,17,26)); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setBackground(round(accent,dp(999),Color.TRANSPARENT)); b.setOnClickListener(l); return b; }
    private GradientDrawable round(int fill,int rad,int stroke){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(rad); if(stroke!=Color.TRANSPARENT)g.setStroke(dp(1),stroke); return g; }
    private LinearLayout.LayoutParams margin(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(14)); return p; }
    private LinearLayout.LayoutParams weight(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(4),dp(4),dp(4),dp(4)); return p; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void copy(String s){ ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); if(c!=null)c.setPrimaryClip(ClipData.newPlainText("wpatch URI",s)); toast("Copied."); }
    private int estimate(String s){ return s.getBytes(StandardCharsets.UTF_8).length + NDEF_OVERHEAD; }
    private String q(Uri u,String... keys){ for(String k:keys){String v=u.getQueryParameter(k); if(v!=null&&v.length()>0)return v;} return null; }
    private String or(String a,String b){ return a==null?b:a; }
    private double num(Uri u,double f,String... keys){ try{String v=q(u,keys); return v==null?f:Double.parseDouble(v);}catch(Exception e){return f;} }
    private String clean(String s){ String out=s.replaceAll("[^A-Za-z0-9._-]",""); return out.length()==0?"fab01":(out.length()>32?out.substring(0,32):out); }
    private String fmt(double n,int d){ return String.format(Locale.US,"%."+d+"f",n); }
    private String label(String a){ if(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(a))return "NDEF_DISCOVERED"; if(Intent.ACTION_VIEW.equals(a))return "ACTION_VIEW"; if(NfcAdapter.ACTION_TECH_DISCOVERED.equals(a))return "TECH_DISCOVERED"; return a==null?"unknown":a; }
    private String hex(byte[] b){ if(b==null)return "unknown"; StringBuilder s=new StringBuilder(); for(int i=0;i<b.length;i++){ if(i>0)s.append('-'); s.append(String.format(Locale.US,"%02X",b[i])); } return s.toString(); }
    private List<Float> hist(double bpm){ List<Float> h=new ArrayList<>(); for(int i=0;i<8;i++)h.add((float)(bpm+Math.sin(i*1.1)*2+(i%2==0?-1:1))); return h; }

    public final class Gauge extends View{ Paint p=new Paint(1); float b=72; Gauge(Context c){super(c);} void setBpm(float x){b=x;invalidate();} protected void onDraw(Canvas c){ float w=getWidth(),h=getHeight(),s=Math.min(w,h),pad=dp(12); RectF r=new RectF(pad,pad,s-pad,s-pad); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(14)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.argb(48,255,255,255)); c.drawArc(r,135,270,false,p); p.setShader(new LinearGradient(0,0,w,h,accent,accent2,Shader.TileMode.CLAMP)); c.drawArc(r,135,Math.min(270,b/180f*270f),false,p); p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(text); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(36)); c.drawText(String.valueOf(Math.round(b)),w/2,h/2+dp(12),p); p.setTextSize(dp(12)); p.setColor(muted); c.drawText("bpm",w/2,h/2+dp(34),p); }}
    public final class Trend extends View{ Paint p=new Paint(1); List<Float> data=hist(72); Trend(Context c){super(c);} void setData(List<Float>d){data=d;invalidate();} protected void onDraw(Canvas c){ float w=getWidth(),h=getHeight(),l=dp(28),r=dp(14),t=dp(18),bt=dp(34); p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(0,0,w,h,Color.argb(45,95,242,209),Color.argb(18,138,180,255),Shader.TileMode.CLAMP)); c.drawRoundRect(new RectF(0,0,w,h),dp(18),dp(18),p); p.setShader(null); float min=45,max=140; for(float v:data){min=Math.min(min,v-8); max=Math.max(max,v+8);} Path path=new Path(); for(int i=0;i<data.size();i++){float x=l+(w-l-r)*i/(data.size()-1f); float y=t+(1-(data.get(i)-min)/(max-min))*(h-t-bt); if(i==0)path.moveTo(x,y); else path.lineTo(x,y);} p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setShader(new LinearGradient(l,0,w-r,0,accent,accent2,Shader.TileMode.CLAMP)); c.drawPath(path,p); p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(muted); p.setTextSize(dp(11)); c.drawText("last 60 seconds",l,h-dp(12),p); }}

    public static final class Reading{ String id="fab01",name="Fabric Patch A",src="demo",mode="stored snapshot",uid="04-A2-17-8C-93-21-80",uri=DEFAULT_URI,alert="Passive NTAG213 opens the app and carries a stored snapshot."; long time=System.currentTimeMillis(); double bpm=72,spo2=98,temp=32.6,hrv=48,conf=.96,quality=94,battery=86,motion=8; List<Float> history=new ArrayList<>(); static Reading demo(){ Reading r=new Reading(); r.history=list(70,71,72,73,72,74,72,72); return r;} static Reading motion(){ Reading r=new Reading(); r.id="fab02"; r.name="Fabric Patch B"; r.bpm=104; r.spo2=97; r.temp=33.1; r.hrv=32; r.conf=.84; r.quality=78; r.battery=74; r.motion=24; r.uid="04-91-2C-7E-11-90-80"; r.uri="wpatch://read?p=fab02&b=104&o=97&t=331&v=32&q=78"; r.history=list(96,99,101,103,106,105,104,104); r.alert="Motion demo: moderate artifact."; return r;} static List<Float> list(float...v){ List<Float> out=new ArrayList<>(); for(float x:v)out.add(x); return out;} String json(){return "{\n  \"schema\": \"fabric-ntag213-heart.v1\",\n  \"patchId\": \""+id+"\",\n  \"source\": \""+src+"\",\n  \"mode\": \""+mode+"\",\n  \"heartRateBpm\": "+bpm+",\n  \"spo2Percent\": "+spo2+",\n  \"skinTempC\": "+temp+",\n  \"hrvRmssdMs\": "+hrv+",\n  \"qualityPercent\": "+quality+",\n  \"uri\": \""+uri+"\"\n}";} }
}
