# NFC Tag Setup for Samsung Galaxy S21

This project is designed for the simplest tap-to-open flow: the NFC tag contains a URL record that points to the GitHub Pages dashboard.

## 1. Deploy the dashboard

Recommended public URL when the folder is merged into `burhanbeycan.github.io`:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/
```

Confirm that the dashboard opens normally in Chrome on the Galaxy S21 before writing the NFC tag.

## 2. Choose payload mode

### Mode A — Full data in the NFC URL

Use this for demos where the complete reading should travel with the tag:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?d=<base64url-json>
```

Pros: no backend and no separate JSON file.  
Cons: URL can become too large for low-memory tags; URL may expose health-like data in browser history and logs.

### Mode B — Short patch/session ID

Use this for a small NFC record:

```text
https://burhanbeycan.github.io/nfc-wrist-patch/?patch=WP-HYDROGEL-001
```

The dashboard loads:

```text
data/patches/WP-HYDROGEL-001.json
```

Pros: fits small tags.  
Cons: static GitHub Pages data is public. For private studies, replace this with an authenticated backend.

## 3. Generate the URL

```bash
node nfc-wrist-patch/tools/build-nfc-url.mjs \
  nfc-wrist-patch/data/demo-reading.json \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/
```

Short registry mode:

```bash
node nfc-wrist-patch/tools/build-nfc-url.mjs \
  nfc-wrist-patch/data/demo-reading.json \
  --base https://burhanbeycan.github.io/nfc-wrist-patch/ \
  --patch-only
```

## 4. Write a URL NDEF record

Three practical options:

1. Use the dashboard button **Write tag with Web NFC** in Chrome for Android.
2. Use `tools/write-tag-web-nfc.html` after it is deployed to the same GitHub Pages site.
3. Use a phone app such as an NFC writer and select **URL / URI record**.

Do not lock the tag read-only until the URL has been tested.

## 5. Test on Galaxy S21

1. Enable NFC in Android settings.
2. Keep the tag away from metal during first tests.
3. Wake/unlock the phone if the OS does not scan while locked.
4. Tap the phone's NFC antenna area near the wrist patch/tag.
5. The browser should open the dashboard and display the reading.

## 6. Troubleshooting

- **Nothing opens:** confirm NFC is enabled; try an ordinary URL tag to check the phone; confirm the tag is NDEF-formatted.
- **Wrong app opens:** rewrite the tag as a URL/URI record instead of a text record.
- **Payload decode error:** regenerate the URL with `tools/build-nfc-url.mjs`; avoid manual edits to the `?d=` value.
- **Tag memory error:** use `--patch-only`, an NTAG216-class tag, or a dynamic NFC chip with larger memory.
- **Data looks old:** a passive tag stores only the last written snapshot. Live sensing requires electronics that update the tag or send data through BLE/backend.

## Production notes

For real human subject data, do not use public GitHub Pages JSON files. Use a random session identifier on the tag and serve the actual health data from an authenticated API with signing, encryption, and audit logging.
