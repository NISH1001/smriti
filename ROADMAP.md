# Smriti — Roadmap & Architecture

**Smriti** (स्मृति, *memory*) is a **memory-capture layer**: it captures *smaran*
(स्मरण, *a memory / recollection*) from wherever your attention already is — audio you
hear, text you read, notes you type — and files them into a single, local-first,
human-readable store that syncs across your devices with **no server and no API**.

Part of the **nishparadox** ecosystem (alongside `mastishka`, `vishrama`, `expensalizer`),
following its plain-file Drive protocol.

---

## Principles (non-negotiable)

- **Local-first, on-device.** Transcription (Whisper) runs on the device. Nothing is sent
  to a cloud service for processing.
- **No API, no OAuth, no server.** Sync is plain files in a Drive folder. Delivery between
  components is OS-level IPC (Android intents) or native messaging (stdio) — never a
  listening HTTP server.
- **Human-readable & exportable.** Everything is JSON Lines (`.jsonl`) you can read, grep,
  and export to Markdown.
- **Explicit scope.** Audio capture only runs for a **user-selected whitelist** of apps —
  no accidental triggers, no wasted battery.
- **Verify on device.** Nothing is "done" until it's confirmed on the Pixel.

---

## The big picture

Smriti is **one app with several clients**, all writing the same records into the same
Drive folder. It is *not* several apps — so, unlike the rest of the ecosystem (one repo per
app), Smriti's clients share **one schema contract** that must never drift.

```
  Android Firefox ──share/deeplink──▶ ┐
  Android audio   ──snip → whisper──▶ ├─ Android app ─┐
  Typed notes     ──────────────────▶ ┘               │
                                                       ├─▶  Drive/nishparadox/smriti/snips/<device>.jsonl
  Desktop Firefox ──addon (native msg)─▶ macOS app  ──┤          (every client unions all *.jsonl at read time)
  Mic / meeting   ──whisper (endgame)──▶ macOS app  ──┘
```

The **Drive folder is the bus.** The Google Drive app (Android) / mounted Drive filesystem
(macOS) does the actual cloud sync — Smriti only ever writes plain files. Adding a new
client means adding one more *writer* of its own `<device>.jsonl`; nothing else changes.

---

## The schema contract

This is the load-bearing part. Every client — Android, macOS, the Firefox addon's
receiver — reads and writes this exact shape.

### Storage layout

```
My Drive/nishparadox/smriti/snips/<device>.jsonl
```

- **One file per device.** A device writes **only its own** file (single writer) and reads
  every other `*.jsonl` **read-only**, unioning them at read time (dedupe by `id`).
- This is what makes it safe with no server and no locking: no two writers ever touch the
  same file, so there are no merge conflicts — only appends and read-time union.

### Device id

`<model-slug>-<6-hex of ANDROID_ID>` — e.g. `pixel-9-7cd72c`. macOS will use
`<host-slug>-<6-hex>` — e.g. `macbook-9f3a1c`. Stable per device, no account needed.

### The smaran record

```json
{
  "id": 1730000000000,
  "createdAt": 1730000000000,
  "type": "web",
  "text": "the transcript, the clipped text, or the note you typed",
  "source": "wikipedia.org",
  "device": "pixel-9-7cd72c",
  "status": "done",
  "durationS": 0,
  "metadata": { "url": "https://en.wikipedia.org/…", "title": "…", "app": "org.mozilla.firefox" }
}
```

| field       | meaning |
|-------------|---------|
| `id`        | unique per device (creation epoch-ms); dedupe key across the union |
| `createdAt` | epoch-ms; drives ordering (newest first) |
| `type`      | `SmaranType` — how it was captured (see below) |
| `text`      | the note body: transcript, clipped text, or typed text |
| `source`    | short **display** label — an app name (`Audiobookshelf`) or a domain (`wikipedia.org`) |
| `device`    | which device authored it |
| `status`    | pipeline state — `recording` → `transcribing` → `done` (or `failed`) |
| `durationS` | audio/voice length in seconds; `0` for text-like types (kept top-level & typed) |
| `metadata`  | open JSON, **string values**; the escape valve for type-specific provenance (url, title, app, author…) |

**Design rule:** `source` is a short label for the list; the *precise* origin (full URL,
package id, book/chapter title) goes in `metadata`. New types and new provenance fields slot
into `metadata` with **zero protocol migration**.

### Types

```
SmaranType { AUDIO, VOICE, WEB, TEXT, NOTE, UNKNOWN }
```

| type      | `text` is…         | `source`            | `metadata` keys              | born as      |
|-----------|--------------------|---------------------|------------------------------|--------------|
| **AUDIO** | Whisper transcript | app (`Audiobookshelf`) | `app`, `title`, `author` | `recording` |
| **VOICE** | Whisper transcript | `Voice note`        | — (duration is top-level)    | `recording`  |
| **WEB**   | clipped selection  | domain              | `url`, `title`, `app` (browser) | `done`     |
| **TEXT**  | shared text        | app                 | `app`                        | `done`       |
| **NOTE**  | text you typed     | `You`               | — (none)                     | `done`       |
| **UNKNOWN** | (fallback)       | as written          | as written                   | as written   |

- **AUDIO/VOICE** run the pipeline (`recording → transcribing → done`); **WEB/TEXT/NOTE**
  are born `done` (no processing).
- **AUDIO** vs **VOICE**: AUDIO is playback you *heard* (from a whitelisted app); VOICE is
  your *own* voice you recorded.
- **WEB** vs **TEXT**: WEB is clipped from a page and carries a URL; TEXT is shared in from a
  non-web app (source app only, no URL).
- **NOTE**: authored inside Smriti from scratch — active capture.

### Cross-client versioning rules

- **Enum in code, string on disk.** Serialize `type`/`status` as their lowercase names
  (`"web"`, `"done"`); parse **case-insensitively** so older uppercase values still read.
- **Tolerant parse.** An unknown `type` written by a newer/other client (e.g. `"meeting"`)
  must not crash — it falls back to `UNKNOWN` and still renders (plain text + generic icon).
- **Single-writer safety.** Because each device only rewrites its own file, an unknown type
  from another client is only ever *displayed* by us, never rewritten — we can't corrupt it.
- **Backward migration.** Older records have no `type`/`metadata`: infer `type` on read
  (`durationS > 0` → `AUDIO`, else `TEXT`) and default `metadata` to `{}`. `durationS` was
  already top-level, so it needs no migration.

---

## Build order

Each step is independently shippable and verifiable on the Pixel.

### ✅ Done (v0)
- Android app: audio snip (whitelisted apps) → on-device Whisper → note.
- Share-to-Smriti (`ACTION_SEND` text/plain) → `TEXT` smaran.
- Race-free Drive sync (SAF + `DocumentsContract`, create-once, debounced, self-heal).
- Queue with live status, edit/delete/bulk/undo, dark minimal UI.

### Step 1 — Schema refactor *(next)*
- Add `SmaranType` enum + `metadata` to the record.
- `toJson`/`fromJson`: serialize + tolerant parse + migrate existing records.
- Verify on the Pixel that existing snips still read — **no data loss**.

### Step 2 — Manual notes + filters
- `+` button on Recent → compose → save `type: NOTE` (reuses the detail-dialog editor).
- Recent: filter chips (All / Audio / Web / Note …) + per-type icons.

### Step 3 — Android web capture (the Firefox-Android feature)
- `smriti://save` deep-link receiver activity + manifest scheme.
- Saves `type: WEB` with `url` + `title` in `metadata`.
- Provable with no browser: `adb shell am start -a android.intent.action.VIEW -d "smriti://save?text=…&url=…&title=…&source=Firefox"`.

### Step 4 — Firefox addon
- Minimal WebExtension (`firefox/`): toolbar button → `window.getSelection()` +
  `location.href` + `document.title` → opens `smriti://save?…`.
- Toolbar button (not selection context-menu) — the reliable trigger on Firefox Android.

### Step 5 — End-to-end test on the phone
- Firefox **Nightly** + `web-ext run -t firefox-android` over the existing USB bridge loads
  the **unsigned** addon temporarily — no AMO needed for development.
- Select text → tap → lands in Smriti **with the URL**.
- **Distribution (later):** publish to **AMO unlisted** for a shareable install link that
  works on release Firefox and for friends.

### Enrichment — book/chapter title from Audiobookshelf
- Read "now playing" metadata via Android's **media session**
  (`MediaSessionManager.getActiveSessions` → `MediaController.getMetadata`), *not* the
  Audiobookshelf server API (that would need the network — against the no-API rule).
- Requires **Notification Listener** access (one-time grant). Opt-in; snips work without it.
- Symmetry: WEB smarans carry `url` + `title`; AUDIO smarans carry `app` + book `title`.

---

## Repo structure

Smriti's clients share one repo — plain folders, **no submodules for clients** (one schema
contract, atomic cross-client changes, `git clone` gives you everything):

```
smriti/
  android/       ← the Android app (Gradle project + vendored whisper.cpp submodule)
  macos/         ← the desktop hub (later)
  firefox/       ← the WebExtension (later)
  docs/          ← specs/ + plans/
  protocol/      ← extracted schema contract (SCHEMA.md) once macOS starts
  README.md ROADMAP.md
```

- **Build:** `cd android && ./gradlew :app:installDebug`. `whisper.cpp` is a git submodule
  and the model is gitignored — see [README.md](README.md) for the clone + setup recipe.
- **Releases:** namespaced semver tags per client — `android/vX.Y.Z`, `firefox/vX.Y.Z`,
  `macos/vX.Y.Z` — each with a GitHub Release carrying that client's artifact (APK / `.xpi` /
  `.app`). Clients release on independent cadences from the one repo.

---

## Endgame (roadmap, not now)

- **macOS hub.** A lightweight tool that writes `<device>.jsonl` to the mounted Drive folder
  (plain filesystem — none of the Android SAF complexity). Doubles as the Firefox addon's
  native-messaging sink. First cut is a CLI (`smriti add` / `smriti list`); a menu-bar app
  later.
- **Meeting notes.** On macOS, capture system/mic audio (ScreenCaptureKit / a virtual audio
  device) → Whisper transcript → optional **llama.cpp / Ollama** summarization → `type: VOICE`
  (or a future `MEETING`) smaran. On-device, no API. This is the horizon, not the near term.

---

## Open questions

- `durationS` unit: seconds (int, top-level) — keep consistent across audio & voice.
- **Model delivery (future optimization):** the Whisper model is bundled in the APK (~60 MB,
  fully offline) for now. Later, switch to first-run download (~5 MB APK + fetch
  `ggml-tiny.en-q5_1.bin` from Hugging Face into app storage, with progress + checksum) —
  smaller download, faster dev installs, and enables letting users pick a model size.
- Manual-note type value: `NOTE` (chosen) with `source: "You"`.
- Whether `MEETING` becomes its own type or rides on `VOICE` with `metadata.kind: "meeting"`.
- macOS Drive path discovery (`~/Library/CloudStorage/GoogleDrive-…` vs `~/Google Drive`).
