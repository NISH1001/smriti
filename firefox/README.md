# Smriti — Firefox addon

A minimal in-page **annotation basket**: highlight snippets as you read, collect them, then
send the batch to Smriti in one shot. Built for Firefox **Android** (works without menus, which
Firefox Android doesn't surface reliably for extensions).

## How it works

A content script injects two floating pills (bottom-right):

- **＋ Save to Smriti** — appears when you select text. Tap it to add the selection (with the
  page's URL + title) to a basket stored locally. Collect as many as you like, across pages.
- **⬆ Send N to Smriti** — sends the whole basket at once via
  `smriti://save?items=[…]` (see [../docs/save-protocol.md](../docs/save-protocol.md)), then
  clears it. The Smriti app shows a "Saved N to Smriti" toast — that's your confirmation.

No network, no accounts, no on-page persistent highlights (that's a possible later step). Each
snippet keeps its own provenance, so highlights from different pages batch together fine.

## Install / test

Firefox for Android **release** only installs *signed* add-ons from AMO, so:

### A. Test now (dev) — Firefox Nightly + web-ext
```bash
npx web-ext run -t firefox-android \
  --adb-bin "$ANDROID_HOME/platform-tools/adb" --adb-device <serial> \
  --firefox-apk org.mozilla.fenix --source-dir firefox
```
(Nightly needs **Settings → Remote debugging via USB → ON**; `adb devices` shows `<serial>`.)
Loads temporarily; **reload the web page** after install so the content script injects.

### B. Daily use — AMO unlisted (signed)
```bash
npx web-ext sign -s firefox --channel=unlisted --api-key=<issuer> --api-secret=<secret>
```
Install the resulting signed `.xpi` via its AMO link on the phone.

## Desktop (later)

The **same addon** runs on Firefox Desktop unchanged — it's a standard WebExtension. The only
platform-specific part is who handles `smriti://save`: on Android it's the app (via the
intent); on desktop the planned **macOS client registers the `smriti://` URL scheme** and
catches it → Drive. One addon, one protocol, per-platform handler.

## Known limits (minimal version)
- Very large batches can exceed the deep-link URL size — send in smaller batches for now
  (auto-chunking is a planned addition).
- No basket-management UI yet (remove individual items / preview) — it sends everything.
