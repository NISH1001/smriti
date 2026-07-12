#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "SMRITI_LLAMA"
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// One loaded model + its context/sampler. Handle is the pointer, passed to Kotlin as a jlong.
// Generation is incremental: startGen() prefills the prompt, nextToken() streams one token at a
// time (so Kotlin can emit tokens to a Flow) — mirrors llama.cpp's own Android example.
struct RagState {
    llama_model*        model = nullptr;
    llama_context*      ctx   = nullptr;
    llama_sampler*      smpl  = nullptr;
    const llama_vocab*  vocab = nullptr;
    int  n_gen = 0;
    int  n_max = 512;
    bool done  = true;
};

static bool g_backend = false;

extern "C" JNIEXPORT jstring JNICALL
Java_com_nishparadox_smriti_rag_LlamaNative_systemInfo(JNIEnv* env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nishparadox_smriti_rag_LlamaNative_loadModel(
        JNIEnv* env, jobject, jstring jpath, jint nCtx, jint nThreads) {
    if (!g_backend) {
        llama_backend_init();
        llama_log_set([](enum ggml_log_level, const char* text, void*) { LOGi("%s", text); }, nullptr);
        g_backend = true;
    }
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model* model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) { LOGe("model load failed"); return 0; }

    llama_context_params cp = llama_context_default_params();
    // Size the window to the model's OWN trained context, capped by nCtx to bound KV-cache RAM on
    // a phone — never hardcode a window the model wasn't trained for, nor allocate a 32K cache for a
    // 32K-trained model. nCtx is the cap; the effective window is min(trained, cap).
    const int train = llama_model_n_ctx_train(model);
    const uint32_t nctx = (uint32_t) ((train > 0 && train < nCtx) ? train : nCtx);
    cp.n_ctx = nctx;
    cp.n_batch = 512;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;
    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) { LOGe("ctx init failed"); llama_model_free(model); return 0; }

    // Mild sampling for grounded QA: top-p + low temp.
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.4f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    RagState* st = new RagState();
    st->model = model; st->ctx = ctx; st->smpl = smpl;
    st->vocab = llama_model_get_vocab(model);
    LOGi("model loaded (n_ctx=%u [train=%d, cap=%d], threads=%d)", nctx, train, (int) nCtx, (int) nThreads);
    return (jlong) st;
}

static std::string apply_template(RagState* st, const char* sys, const char* usr) {
    const char* tmpl = llama_model_chat_template(st->model, nullptr);
    llama_chat_message msgs[2] = {{"system", sys}, {"user", usr}};
    std::vector<char> buf(8192);
    int n = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), (int) buf.size());
    if (n > (int) buf.size()) {
        buf.resize(n);
        n = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), (int) buf.size());
    }
    if (n < 0) return {};
    return std::string(buf.data(), n);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nishparadox_smriti_rag_LlamaNative_startGen(
        JNIEnv* env, jobject, jlong handle, jstring jsys, jstring jusr, jint maxTokens) {
    RagState* st = (RagState*) handle;
    if (!st) return JNI_FALSE;
    const char* sys = env->GetStringUTFChars(jsys, nullptr);
    const char* usr = env->GetStringUTFChars(jusr, nullptr);
    std::string prompt = apply_template(st, sys, usr);
    env->ReleaseStringUTFChars(jsys, sys);
    env->ReleaseStringUTFChars(jusr, usr);
    if (prompt.empty()) { LOGe("empty templated prompt"); return JNI_FALSE; }

    llama_memory_clear(llama_get_memory(st->ctx), true);   // fresh conversation each query
    llama_sampler_reset(st->smpl);
    st->n_gen = 0; st->n_max = maxTokens; st->done = false;

    // add_special=false: the chat template already emits BOS ({{ bos_token }}); adding another
    // here produces a double-BOS that makes some models (e.g. Gemma) emit EOG as the first token.
    // parse_special=true so the template's <bos>/<start_of_turn>/<end_of_turn> are still tokenized.
    int need = -llama_tokenize(st->vocab, prompt.c_str(), (int) prompt.size(), nullptr, 0, false, true);
    std::vector<llama_token> tokens(need);
    if (llama_tokenize(st->vocab, prompt.c_str(), (int) prompt.size(),
                       tokens.data(), need, false, true) < 0) { return JNI_FALSE; }

    // Fit the prompt to the context window at the TOKEN level, reserving room to generate. Keep the
    // head (instruction + top-ranked notes) and the tail (the question + assistant-turn marker),
    // dropping the middle — so the whole window is used and the question is never lost.
    const int budget = (int) llama_n_ctx(st->ctx) - st->n_max;
    if (budget > 0 && (int) tokens.size() > budget) {
        const int tail = budget > 96 ? 80 : 0;   // question + turn markers live at the very end
        const int head = budget - tail;
        std::vector<llama_token> fit;
        fit.reserve(budget);
        fit.insert(fit.end(), tokens.begin(), tokens.begin() + head);
        if (tail > 0) fit.insert(fit.end(), tokens.end() - tail, tokens.end());
        LOGi("prompt %d tok > budget %d — fit to %d (n_ctx=%d, reserve=%d)",
             (int) tokens.size(), budget, (int) fit.size(), (int) llama_n_ctx(st->ctx), st->n_max);
        tokens.swap(fit);
    }

    // Prefill in n_batch-sized chunks — a prompt longer than n_batch aborts inside llama_decode.
    const int n_batch = 512;
    const int total = (int) tokens.size();
    for (int i = 0; i < total; i += n_batch) {
        const int chunk = (total - i) < n_batch ? (total - i) : n_batch;
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        if (llama_decode(st->ctx, batch) != 0) { LOGe("prefill decode failed at %d", i); return JNI_FALSE; }
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nishparadox_smriti_rag_LlamaNative_nextToken(JNIEnv* env, jobject, jlong handle) {
    RagState* st = (RagState*) handle;
    if (!st || st->done) return nullptr;
    llama_token tok = llama_sampler_sample(st->smpl, st->ctx, -1);
    if (llama_vocab_is_eog(st->vocab, tok)) { st->done = true; return nullptr; }
    if (st->n_gen >= st->n_max) { st->done = true; return nullptr; }
    llama_sampler_accept(st->smpl, tok);

    char buf[256];
    int n = llama_token_to_piece(st->vocab, tok, buf, sizeof(buf), 0, true);
    std::string piece = (n > 0) ? std::string(buf, n) : std::string();
    st->n_gen++;

    llama_batch batch = llama_batch_get_one(&tok, 1);       // advance so the next sample continues
    int dret = llama_decode(st->ctx, batch);
    if (dret != 0) { LOGe("DIAG stop: decode ret=%d at n_gen=%d", dret, st->n_gen); st->done = true; }
    return env->NewStringUTF(piece.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nishparadox_smriti_rag_LlamaNative_freeModel(JNIEnv*, jobject, jlong handle) {
    RagState* st = (RagState*) handle;
    if (!st) return;
    if (st->smpl)  llama_sampler_free(st->smpl);
    if (st->ctx)   llama_free(st->ctx);
    if (st->model) llama_model_free(st->model);
    delete st;
}
