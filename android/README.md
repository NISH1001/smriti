# Smriti — Android

The Android client of [Smriti](../README.md). Captures *smaran* (memories) two ways and files
them into the shared nishparadox Drive store:

- **Hear it** — capture playback audio from whitelisted apps (audiobooks, podcasts) and
  transcribe it **on-device** with Whisper.
- **Read it** — share/deep-link any text into Smriti as a note.

Local-first throughout: transcription runs on the phone; sync is plain `.jsonl` files in a
Google Drive folder via SAF — no Google API, no account, no server.

## Requirements

- Android 14+ (`minSdk 34`, `targetSdk 35`), arm64 device. Built & verified on **Pixel 9**.
- JDK 17, Android SDK (platform 35), NDK 27.1.
- The Whisper model is **not** in the repo (gitignored, ~32 MB) — fetch it separately.

## Setup & build

```bash
# from the repo root: fetch the whisper.cpp submodule
git submodule update --init --recursive

# place the model (a standard whisper.cpp ggml model — e.g. Hugging Face: ggerganov/whisper.cpp)
#   ggml-tiny.en-q5_1.bin  ->  android/app/src/main/assets/models/

cd android
./gradlew :app:installDebug        # build + install on a connected device
./gradlew :app:testDebugUnitTest   # unit tests
```

Native code (whisper.cpp + ggml) builds via CMake `externalNativeBuild` into
`libsmriti_whisper.so`. Load segments are aligned to 16 KB (`-Wl,-z,max-page-size=16384`) for
the Android 15+ / Pixel 16 KB page size.

## Module layout

```
app/src/main/
  java/com/nishparadox/smriti/
    MainActivity.kt     Compose UI — home queue + Settings
    ShareActivity.kt    ACTION_SEND text -> TEXT smaran
    capture/            MediaProjection audio capture, ring buffer, snip controller
    transcribe/         whisper.cpp JNI wrapper
    notes/              Snip model, SnipStore (source of truth), DriveSync (SAF), DeviceId
    update/             GitHub-releases update check
    trigger/            floating snip bubble
    settings/ apps/ ui/ output/
  cpp/                  CMakeLists.txt + jni.cpp (whisper JNI)
  assets/models/        Whisper model (gitignored)
```

## Release

Debug-signed APKs published as GitHub Releases tagged `android/vX.Y.Z`. The app checks that tag
series on launch and offers a download link when a newer version exists. Full convention in the
root [ROADMAP.md](../ROADMAP.md).
