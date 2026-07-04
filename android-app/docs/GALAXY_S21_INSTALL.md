# Install on Samsung Galaxy S21 without Play Store

This app is for urgent demonstration, so you can sideload the APK instead of waiting for Play Store review.

## Option A — Install from Android Studio

1. Enable Developer options on the Galaxy S21.
2. Enable USB debugging.
3. Connect the phone by USB.
4. Open `android-app/` in Android Studio.
5. Press Run.

Android Studio will build and install the debug APK directly.

## Option B — Install APK manually

1. Build the debug APK in Android Studio.
2. Copy `app-debug.apk` to the phone.
3. Open the APK from the Files app.
4. Android may ask for permission to install unknown apps from that source.
5. Allow the permission only for your trusted file source.
6. Install and open the app once.

## Enable NFC

On the Galaxy S21:

```text
Settings > Connections > NFC and contactless payments > On
```

## First test

1. Open the WristPatch NFC app.
2. Press **Write NTAG213**.
3. Touch the blank NTAG213 label to the phone until the app says success.
4. Move the label away.
5. Lock or go to the home screen.
6. Touch the label again.
7. The WristPatch NFC app should open and show the heartbeat dashboard.

## If it does not open

- Confirm NFC is enabled.
- Confirm the app was opened once after install.
- Rewrite the tag with `wpatch://read?p=fab01&b=72&o=98&t=326&v=48&q=94`.
- Do not use a plain text record. Use an NDEF URI/URL record.
- Keep the NTAG213 antenna away from metal, wet conductive gels, and strong bends.
- If another app opens, erase and rewrite the tag with the custom `wpatch://` URI.
