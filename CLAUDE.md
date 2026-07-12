# Smriti — working notes for Claude

Smriti (स्मृति) is a local-first **memory-capture layer**. See **ROADMAP.md** for the
architecture, the smaran schema contract, and the build order; **README.md** for setup.

## Git workflow (IMPORTANT)

- **Never commit directly to `main`.** Always work on a **client-namespaced** branch:
  `<client>/<type>/<name>` — e.g. `android/feat/github-connector`, `android/fix/…`,
  `firefox/feat/…`, `macos/feat/…`. This mirrors the release-tag namespacing (branches live in
  `refs/heads/android/…`, tags in `refs/tags/android/v…` — separate namespaces, no collision).
  Repo-wide changes (ROADMAP, tooling, cross-client) use `repo/feat/…`.
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
- `adb` isn't on `PATH` here — it's at `~/Library/Android/sdk/platform-tools/adb`.
- **`adb` shell quoting (has bitten us more than once):** everything after `adb shell` is re-parsed
  by the *device* shell, so an unquoted `&`, `>`, `<`, or `|` acts **on-device**, not on the Mac.
  - **Deep links:** `am start -d 'smriti://save?a=1&b=2'` silently drops everything after the first
    `&` (the device shell backgrounds it) — only `a=1` reaches the app. Wrap the **whole** remote
    command so the URI survives intact, and URL-encode spaces as `%20`:
    `adb shell "am start -a android.intent.action.VIEW -d 'smriti://save?text=hi%20there&url=https://x&title=T'"`
  - **Writing app files:** `adb shell "run-as $PKG sh -c 'cat > files/x'"` — quote the command so the
    `>` redirect runs inside `run-as`, not in the outer device shell.

## Conventions

- Clients are plain folders (`android/`, `macos/`, `firefox/`) in this one repo — no per-client
  repos, no client submodules. One shared schema contract (see ROADMAP.md).
- No external data APIs / OAuth: sync is plain `.jsonl` files in a Drive folder via SAF;
  delivery between components is OS IPC (intents) / native messaging — never a server.
- Keep the app UI free of "nishparadox" branding (it's shareable). The Sanskrit flavor lives in
  the app identity (Smriti / smaran), not in build/release plumbing (tags stay `android/…`).

## Storage & sync

- **Truth is plain files, never a DB.** Each smaran is one JSON object. The local store is
  `files/smarans.jsonl` (one smaran per line); it syncs to `Drive/…/smriti/smarans/<device>.jsonl`.
  Local and Drive share **one per-record codec** (`SnipStore.toJson`/`fromJson`) so formats can't
  drift. (Legacy `snips.json` — a single JSON array — is migrated to jsonl on first load.)
- **One writer per device.** A device writes only its own `<device>.jsonl`; every client unions
  all `*.jsonl` at read time and dedupes by `id`. No locks, no merges, no server.
- **Rigid local writes** live in `notes/SmaranStorage.kt` (`JsonlStorage`): serialize to `.tmp`,
  `fsync`, copy the prior file to `.bak`, then **atomically rename** over `smarans.jsonl` — a crash
  mid-write can never leave a torn file. Reads are **line-tolerant**: a corrupt line is skipped, and
  if the whole file is unreadable it recovers from `.bak`.
- The store sits behind the `SmaranStorage` interface **specifically so it can move to SQLite/FTS
  later** without touching `SnipStore`. Any search index (in-memory FTS5, etc.) is a *derived,
  rebuildable* cache — the `.jsonl` stays the source of truth; never make the index authoritative.
- The capture pipeline's "snip" naming (a snip = the act of clipping audio) is **not** the storage
  vocabulary — the stored records are "smarans" of a `SmaranType` (audio/voice/web/text/note).
