# Shruti (श्रुति) — Design v1

> *śruti* — "that which is heard." An eyes-free audio-snipping notebook for walks.

**Status:** Design locked (v1) · **Date:** 2026-07-05 · **Owner:** NISH1001
**Ecosystem:** personal suite alongside [mastishka](https://github.com/NISH1001/mastishka) (meditation), [vishrama](https://github.com/NISH1001/vishrama) (rest), expensalizer. Shares the philosophy: **local-first, human-readable plain-text, cloud-drive sync, no servers/accounts, on-device & private.**

---

## 1. Problem & one-line solution

While listening to an audiobook/podcast on a walk (headphones, phone pocketed, screen off, not actively taking notes), I want to grab a passage the moment it strikes me — *with the few seconds of lead-up context I already heard* — and have it become a searchable text note, without stopping or looking at the phone.

**Shruti** runs in the background, continuously buffers the audio being played, and on **one physical-button press** saves *the last few seconds + a short forward window* as a clip that is **transcribed on-device** into a **Markdown note**, synced to Google Drive.

## 0. v0 Prototype scope — build & validate this FIRST (ad-hoc, no Drive)

Prove the core loop on the real Pixel before any polish or persistence:

- Start a listening session (MediaProjection consent) → ring buffer holds last **N** seconds.
- **One tap** to snip. *v0 trigger = a notification "Snip" action button* (zero extra permissions, fastest to validate). Volume-Up long-press is added immediately **after** the core works.
- Snip captures **total K seconds** = `N` pre-roll + `(K − N)` forward → **auto-stops**.
- Transcribe on-device (whisper `tiny.en`).
- **Show the transcript inline as a notification.** No Room DB, no Drive, no list UI in v0.
- **Only two settings:** `N` (pre-roll seconds) and `K` (total seconds).

**Success test:** on a real walk with Audiobookshelf + Pixel Buds, one tap yields a correct transcript notification. **Only if this works** do we proceed to: persistence + list UI, Google Drive sync (folder convention `nishparadox/shruti/`, mirroring mastishka's `nishparadox/mastishka/`), Volume-Up long-press + Quick Tap + "Hey Google, snip" triggers, and share-sheet. Everything in §4–§14 below is the full v1 target that v0 grows into.

## 2. Goals / Non-goals

**Goals (v1)**
- One-action, eyes-free capture with **pre-roll context** (the "-5s rewind").
- **On-device** transcription — audio never leaves the phone for STT (privacy, offline on walks).
- **Minimal** footprint: tiny model, tiny app, tiny clips, one screen.
- **Local-first Markdown** notes + audio, background-synced to **Google Drive**.
- Works reliably for **Audiobookshelf**; other sources are probe-gated (see §9).

**Non-goals (v1)**
- No live streaming captions (we transcribe the finalized clip, not a live feed).
- No cloud STT, no accounts, no login.
- No guaranteed YouTube / YT Music support (OS may block capture — decided by the probe).
- No waveform/audio editing, no multi-language UI (English-first).
- No external hardware dependencies (no Flic, no BT remotes).
- No Play Store distribution in v1 → **personal sideload build** (frees us from accessibility-service and foreground-service policy constraints).

## 3. Target device

**Google Pixel 9 (codename `tokay`), Android 17 (API 37).** Confirmed connected via wireless debugging. Design must honor Android 14+ foreground-service *type* rules (`FOREGROUND_SERVICE_MEDIA_PROJECTION`) and per-session `MediaProjection` consent. Tensor G4 makes on-device STT fast. Installed media apps for the §9 probe: Audiobookshelf (`com.audiobookshelf.app`), YouTube (`com.google.android.youtube`), YouTube Music (`com.google.android.apps.youtube.music`, plus a ReVanced build).

## 4. The capture model (one-tap, auto-stop)

```
 continuous ring buffer  ──►  holds last N seconds of played audio   (N = pre-roll, default 5s, configurable 3–15s)
        │
   ONE trigger press  ──►  clip.start = now − N          ← the pre-roll context
        │                  haptic buzz #1 (started)
   audio keeps appending live for M seconds  (M = forward window, default 15s, configurable 5–60s)
        │
   auto-stop at now + M   ──►  clip.end                  ← no "end" action required
        │                  haptic buzz #2 (saved)
   ►  enqueue clip  ►  on-device transcription (background)  ►  Markdown note + audio saved  ►  Drive sync
```

- **Single action.** Press once → a fixed `[−N, +M]` window is captured and auto-stops. No second press needed.
- **Extend:** pressing again *during* an active capture extends by another `M` (adjacent segments auto-merged into one note).
- **Haptics** (phone vibration, felt through pocket) confirm start and save — the eyes-free feedback channel.
- Only the **raw PCM ring buffer** runs continuously; it is tiny (5s @ 16 kHz mono PCM16 ≈ 160 KB RAM). **No continuous transcription** → low battery.

## 5. Triggers (all point at one `SnipEntryPoint`; no external hardware)

| Trigger | Mechanism | Eyes-free | Notes |
|---|---|---|---|
| **Volume-Up long-press** *(primary)* | AccessibilityService intercepts long-press (~600 ms) of `KEYCODE_VOLUME_UP`, consumes it; short press passes through to normal volume | ✅ pocket/screen-off | Needs accessibility permission (fine for sideload). Primary day-one trigger. |
| **Quick Tap** (double-tap phone back) | Pixel gesture set to "Open app" → launches a trivial `SnipActivity` that fires + finishes | ✅ | No permission; Pixel-native. |
| **Notification button** | Persistent capture-session notification with a "Snip" action, tappable from lock screen | ⚠️ needs screen wake | Reliable fallback. |
| **"Hey Google, snip"** | Google Assistant Routine / App Action → deep-link into `SnipActivity` | ✅ (voice) | Hands-free via Buds; audible. |

**Explicitly ruled out** (documented so we don't revisit): power button (any press — Android reserves it), Pixel Buds taps (hard-wired to media keys owned by the player), Bluetooth media buttons (would steal the media session and break the player's play/pause).

## 6. Architecture & components

Single Android app, **Kotlin + Jetpack Compose** (matches mastishka). Isolated, single-purpose units:

- **`CaptureService`** (foreground service, type `mediaProjection` + `microphone`): owns the `MediaProjection` session and the `AudioPlaybackCapture` `AudioRecord`. Writes PCM into `RingBuffer`. Emits session state. Shows the persistent notification.
- **`RingBuffer`**: fixed-size circular PCM buffer (holds pre-roll seconds). Thread-safe single-writer/single-reader. Pure, unit-testable.
- **`SnipController`**: on trigger, snapshots `[now−N]` from `RingBuffer` and keeps appending live PCM until `now+M` (or extend). Produces a finalized `ClipPcm`. Pure logic over an audio-source interface (testable without a real mic).
- **`TriggerLayer`**:
  - `VolumeKeyAccessibilityService` (primary)
  - `SnipActivity` (invisible; entry point for Quick Tap / Assistant deep link — fires snip, finishes)
  - notification action → same `SnipEntryPoint`
  - All converge on one `SnipEntryPoint.requestSnip()` interface.
- **`Transcriber`**: wraps **whisper.cpp** via JNI. `transcribe(pcm: ShortArray): String`. Model = `tiny.en` Q5_1 (~31 MB), swappable in settings (`base.en` ~57 MB). Runs in a background worker.
- **`TranscriptionWorker`** (foreground/expedited `WorkManager` job): dequeues finalized clips, encodes audio, calls `Transcriber`, writes the note. Survives app being backgrounded.
- **`SnipRepository`**: persists snips (Room index + files on disk). Source of truth for the UI list. Exposes a `Flow<List<Snip>>`.
- **`AudioEncoder`**: PCM → compressed Opus/AAC (`.m4a`, ~60 KB / 20 s).
- **`MarkdownWriter`**: renders a `Snip` to a `.md` file (see §8 format).
- **`DriveSync`**: background mirror of the notes folder to a Google Drive app folder. Offline-tolerant, retries on connectivity. Mastishka-style file sync.
- **`SourceTagger`**: reads the active media session via `MediaSessionManager` (needs Notification-Listener access) to tag notes with app + title/author/chapter when available.
- **UI (Compose)**: `SnipListScreen` (list + play + share + delete), `SessionControl` (start/stop listening session), `SettingsScreen`.

### Data flow
`CaptureService → RingBuffer → SnipController (on trigger) → ClipPcm → TranscriptionWorker (AudioEncoder + Transcriber + SourceTagger) → SnipRepository → MarkdownWriter → DriveSync`, with `SnipListScreen` observing `SnipRepository`.

## 7. Session & permissions lifecycle

1. User taps **Start listening session** → system `MediaProjection` consent (Android 14: fresh consent per session; privacy indicator shows while active) → `CaptureService` starts, ring buffer fills.
2. Snip freely via any trigger during the session.
3. **Stop session** ends capture and the foreground service.

**Permissions:** `RECORD_AUDIO`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS`, MediaProjection consent, Accessibility service (Vol-Up), optional Notification-Listener (source tagging), Google Drive OAuth scope (app folder). `VIBRATE`.

## 8. Storage & sync

- **Local-first.** Each snip = one audio file + one Markdown file, plus a Room row (index/search).
- Audio: compressed `.m4a` (Opus/AAC), ~60 KB per 20 s → ~30 MB per 500 snips. Storage is a non-issue.
- **Google Drive** background sync of the notes folder (like mastishka's plain-text Drive sync). Uploads when connected; walk-friendly.
- Optional **"delete local audio after upload"** setting (default OFF — clips are tiny).
- **Share sheet** button per snip → send transcript/audio into Keep or anywhere on demand (this is the "Keep integration" — manual, robust; no brittle Keep API).

**Markdown note format:**
```markdown
---
id: 2026-07-05T14-32-10Z
source_app: Audiobookshelf
title: "Book Title — Ch. 12"
timestamp: 2026-07-05T14:32:10Z
duration_s: 20
preroll_s: 5
audio: 2026-07-05T14-32-10Z.m4a
model: whisper-tiny.en-q5_1
---

<transcript text here>
```

## 9. Source detection & the capture probe (Step 0)

Capture is source-agnostic (captures whatever plays). Notes are tagged via `MediaSessionManager`.

**Probe (first implementation task):** a minimal build that starts `AudioPlaybackCapture` and reports, per running media app, whether non-silent audio is actually captured. Run on the target Pixel against **Audiobookshelf, YouTube, YT Music**. Result decides which sources v1 claims to support. Expectation: ABS works; YouTube/YT Music may be blocked by their capture policy — if so, documented as unsupported (their fallback, mic capture, fails with headphones and is out of scope).

## 10. Transcription details

- **Engine:** whisper.cpp (ggml) via JNI. **Model `tiny.en` Q5_1 (~31 MB)** default; setting to switch to `base.en`.
- **Mode:** batch over the short finalized clip (no streaming). ~1–3 s on Tensor for a 20 s clip.
- Model bundled or downloaded on first run; stored in app files.
- Android's built-in `SpeechRecognizer` is rejected: it is microphone-only and cannot accept our captured PCM.

## 11. Error handling

- **Capture blocked / silent** (opted-out source): detect near-silent buffer on snip → haptic "error" pattern + notification "couldn't capture this source."
- **MediaProjection revoked / session died:** stop cleanly, notify, offer restart.
- **Transcription failure:** keep the audio clip, mark note `transcript: (failed — tap to retry)`; audio is never lost.
- **Drive offline:** queue locally, sync later; never block capture on network.
- **Accessibility service killed by OEM:** detect and prompt re-enable; Quick Tap / notification remain as fallbacks.

## 12. Testing strategy

- **Unit:** `RingBuffer` (wrap-around, pre-roll correctness), `SnipController` (window math, extend/merge) against a fake audio source; `MarkdownWriter` output.
- **Instrumented:** `CaptureService` capture on the Pixel (the probe doubles as the first integration test); `TranscriptionWorker` end-to-end on a canned clip; trigger→snip→note happy path.
- **Manual field test:** real walk with Audiobookshelf + Pixel Buds, screen off.

## 13. Milestones (each independently verifiable)

1. **Probe** — confirm ABS (and check YouTube/YT Music) capturability on the Pixel.
2. **Capture core** — `CaptureService` + `RingBuffer` + session UI; verify pre-roll buffer.
3. **Snip + trigger** — `SnipController` + Volume-Up accessibility trigger + haptics; produce a saved clip.
4. **Transcription** — whisper.cpp JNI + `TranscriptionWorker`; clip → Markdown note.
5. **Storage + list UI** — `SnipRepository` + `SnipListScreen` (play/share/delete) + settings.
6. **Drive sync** — background mirror + share-sheet.
7. **Extra triggers** — Quick Tap entry activity, notification action, "Hey Google, snip".

## 14. Open questions / risks

- Volume-Up long-press delivery to an AccessibilityService **while screen is off** — verify on the target Pixel early (fallbacks exist if flaky).
- `MediaProjection` per-session re-consent friction on Android 14 — acceptable (once per walk), but confirm UX.
- Whisper `tiny.en` accuracy on proper nouns in audiobooks — `base.en` fallback in settings.
- OEM background-service killing — Pixel is lenient; note for any future Samsung build.
