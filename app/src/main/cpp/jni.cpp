#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define TAG "SMRITI_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_nishparadox_smriti_transcribe_WhisperNative_init(
        JNIEnv* env, jobject /*thiz*/, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) LOGE("failed to load model");
    else LOGI("model loaded");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nishparadox_smriti_transcribe_WhisperNative_transcribe(
        JNIEnv* env, jobject /*thiz*/, jlong ctxPtr, jfloatArray audio, jint threads) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (!ctx) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(audio);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(audio, 0, n, samples.data());

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = threads;
    params.no_context = true;
    params.single_segment = false;

    if (whisper_full(ctx, params, samples.data(), n) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }
    int nseg = whisper_full_n_segments(ctx);
    std::string out;
    for (int i = 0; i < nseg; i++) {
        const char* seg = whisper_full_get_segment_text(ctx, i);
        if (seg) out += seg;
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nishparadox_smriti_transcribe_WhisperNative_free(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong ctxPtr) {
    if (ctxPtr) whisper_free(reinterpret_cast<whisper_context*>(ctxPtr));
}
