package com.nishparadox.smriti.transcribe

/** Engine-agnostic transcription. Swap implementations (Whisper now, Moonshine later). */
interface Transcriber { fun transcribe(pcm: FloatArray): String }
