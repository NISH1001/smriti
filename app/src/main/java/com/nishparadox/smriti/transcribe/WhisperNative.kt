package com.nishparadox.smriti.transcribe

/** JNI bridge to whisper.cpp. Symbol names must match app/src/main/cpp/jni.cpp. */
object WhisperNative {
    init { System.loadLibrary("smriti_whisper") }
    external fun init(modelPath: String): Long
    external fun transcribe(ctx: Long, audio: FloatArray, threads: Int): String
    external fun free(ctx: Long)
}
