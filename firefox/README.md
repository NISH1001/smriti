# Smriti — Firefox addon

A minimal WebExtension: **select text → Save to Smriti**. It sends the selection plus the
page's URL and title to the Smriti Android app via the `smriti://save` deep link
(see [../docs/save-protocol.md](../docs/save-protocol.md)). No network, no accounts.

Two triggers (whichever the Firefox build exposes):
- **Selection context menu** → "Save to Smriti"
- **Toolbar/menu button** (reads the current selection)

## Install / test

Firefox for Android **release** only installs *signed* add-ons from AMO. So there are two paths:

### A. Test now (dev) — Firefox Nightly + web-ext
No AMO account needed; the add-on loads temporarily over USB.

1. Install **Firefox Nightly** (Play Store, package `org.mozilla.fenix`).
2. Nightly → **Settings → Remote debugging via USB → ON**.
3. Plug in the phone (USB debugging enabled), then from the repo root:
   ```bash
   npx web-ext run -t firefox-android --adb-device <serial> \
     --firefox-apk org.mozilla.fenix --source-dir firefox
   ```
   (`adb devices` shows `<serial>`.) The add-on installs into Nightly for the session.

### B. Daily use — AMO unlisted (signed)
Works on your normal Firefox, permanent.

1. Create an addons.mozilla.org account → generate API credentials (JWT issuer/secret).
2. Sign it:
   ```bash
   npx web-ext sign -s firefox --channel=unlisted \
     --api-key=<issuer> --api-secret=<secret>
   ```
3. Install the resulting signed `.xpi` via its AMO link on the phone.

## Notes
- The Smriti app shows its own "Saved to Smriti" toast — that's your confirmation.
- Bulk capture (`smriti://save?items=[…]`) is supported by the app; the "highlight basket →
  send N" UI is a planned next iteration of this addon.
