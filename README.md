# Smriti

**स्मृति** — a local-first **memory-capture layer**. It captures *smaran* (स्मरण) — audio you
hear, text you read, notes you type — into a plain-file store that syncs across your devices
with **no server and no API**. Part of the **nishparadox** ecosystem.

See **[ROADMAP.md](ROADMAP.md)** for the architecture, the schema contract, and the plan.

## Repo layout

```
android/   Android app — Kotlin/Compose + on-device Whisper (vendored whisper.cpp submodule)
macos/     desktop hub (planned)
firefox/   WebExtension (planned)
docs/      design specs & plans
```

Clients live as plain folders in this one repo (no per-client repos), sharing one schema
contract. Releases are namespaced tags: `android/vX.Y.Z`, `firefox/vX.Y.Z`, `macos/vX.Y.Z`.

## Build the Android app

`whisper.cpp` is a **git submodule**, and the Whisper model is **not** committed (it's large
and gitignored). So a fresh setup is two steps beyond cloning:

```bash
# 1. clone with the submodule
git clone --recursive https://github.com/NISH1001/smriti.git
#    (already cloned without --recursive? then run:)
git submodule update --init --recursive

# 2. fetch the model (~32 MB) into the assets dir:
#      ggml-tiny.en-q5_1.bin  ->  android/app/src/main/assets/models/
#    (a standard whisper.cpp model — e.g. Hugging Face: ggerganov/whisper.cpp)

# 3. build & install on a connected device
cd android
./gradlew :app:installDebug
```

> A plain `git pull` does **not** update the submodule. When syncing to another device,
> re-run `git submodule update --init --recursive`.
