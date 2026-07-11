# --- JNI: the whisper native lib resolves these by exact class + method name, so R8 must not
#     rename/strip them (else UnsatisfiedLinkError at transcription time). ---
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.nishparadox.smriti.transcribe.WhisperNative { *; }

# org.json is part of the platform; nothing to keep. Compose / AndroidX / kotlinx ship their
# own consumer rules, so no extra keeps are needed for them.
