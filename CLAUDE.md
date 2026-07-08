# Smriti — working notes for Claude

Smriti (स्मृति) is a local-first **memory-capture layer**. See **ROADMAP.md** for the
architecture, the smaran schema contract, and the build order; **README.md** for setup.

## Git workflow (IMPORTANT)

- **Never commit directly to `main`.** Always work on a feature branch (`feat/…`, `fix/…`).
- **Never open a PR or merge without explicit approval — ask every time.** The flow is:

  branch → implement → **verify on the connected Pixel** → *wait for the user* →
  PR (only when told) → *wait* → merge (only when told) → release.

  Do not run `gh pr create` or any merge until the user says so.
- End commit messages with the `Co-Authored-By: Claude …` trailer.
- Releases: namespaced semver tags per client — `android/vX.Y.Z`, `firefox/vX.Y.Z`,
  `macos/vX.Y.Z` — each a GitHub Release carrying that client's artifact. Bump Android
  `versionName` in `android/app/build.gradle.kts`.

## Build & verify (Android)

- Build/install: **`cd android && ./gradlew :app:installDebug`** (never from the repo root).
- `whisper.cpp` is a git submodule at `android/whisper.cpp`; the Whisper model is gitignored
  (fetch separately — see README).
- **Verify on the Pixel before claiming anything works** — install + `adb` screenshot/logcat.
  Don't report a feature as done until it's confirmed on-device.

## Conventions

- Clients are plain folders (`android/`, `macos/`, `firefox/`) in this one repo — no per-client
  repos, no client submodules. One shared schema contract (see ROADMAP.md).
- No external data APIs / OAuth: sync is plain `.jsonl` files in a Drive folder via SAF;
  delivery between components is OS IPC (intents) / native messaging — never a server.
- Keep the app UI free of "nishparadox" branding (it's shareable). The Sanskrit flavor lives in
  the app identity (Smriti / smaran), not in build/release plumbing (tags stay `android/…`).
