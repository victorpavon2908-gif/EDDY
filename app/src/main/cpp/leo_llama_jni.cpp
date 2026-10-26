#include <jni.h>
#include "llama.h"
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::atomic<int64_t> cancelled{0};
std::mutex inference_mutex;
struct Request {
    int64_t id;
    std::chrono::steady_clock::time_point deadline;
    bool stopped() const { return cancelled.load() >= id || std::chrono::steady_clock::now() >= deadline; }
};
bool abort_eval(void * data) { return static_cast<Request *>(data)->stopped(); }
bool load_progress(float, void * data) { return !abort_eval(data); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_niko_assistant_inference_local_LeoLlamaBridge_cancel(JNIEnv *, jobject, jlong id) {
    auto old = cancelled.load();
    while (old < id && !cancelled.compare_exchange_weak(old, id)) {}
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_niko_assistant_inference_local_LeoLlamaBridge_generate(JNIEnv * env, jobject, jstring file, jbyteArray input, jlong id, jint limit) {
    // No overlapping KV caches or model handles. Busy requests fall back immediately.
    std::unique_lock<std::mutex> lock(inference_mutex, std::try_to_lock);
    if (!lock.owns_lock() || !file || !input || limit < 1 || limit > 1024) return nullptr;
    Request request{id, std::chrono::steady_clock::now() + std::chrono::seconds(180)};
    if (request.stopped()) return nullptr;
    try {
        const auto size = env->GetArrayLength(input);
        if (size < 1 || size > 24000) return nullptr;
        std::string prompt(size, '\0');
        env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte *>(&prompt[0]));
        const char * raw_path = env->GetStringUTFChars(file, nullptr);
        if (!raw_path) return nullptr;
        std::string path(raw_path);
        env->ReleaseStringUTFChars(file, raw_path);
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_backend_init(); });
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0; mp.use_mmap = true;
        mp.progress_callback = load_progress; mp.progress_callback_user_data = &request;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(path.c_str(), mp), llama_model_free);
        if (!model || request.stopped()) return nullptr;
        const auto * vocab = llama_model_get_vocab(model.get());
        const int count = -llama_tokenize(vocab, prompt.data(), size, nullptr, 0, true, true);
        if (count <= 0 || count + limit > 2048) return nullptr;
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), size, tokens.data(), count, true, true) != count) return nullptr;
        auto cp = llama_context_default_params();
        cp.n_ctx = 2048; cp.n_batch = 256; cp.n_ubatch = 128;
        cp.n_threads = 4; cp.n_threads_batch = 4;
        cp.abort_callback = abort_eval; cp.abort_callback_data = &request;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx) return nullptr;
        for (int offset = 0; offset < count; offset += 256) {
            if (request.stopped()) return nullptr;
            auto batch = llama_batch_get_one(tokens.data() + offset, std::min(256, count - offset));
            if (llama_decode(ctx.get(), batch) != 0) return nullptr;
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
        std::string answer;
        for (int i = 0; i < limit && !request.stopped(); ++i) {
            auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) {
                if (request.stopped() || answer.empty()) return nullptr;
                auto out = env->NewByteArray(answer.size());
                if (out) env->SetByteArrayRegion(out, 0, answer.size(), reinterpret_cast<const jbyte *>(answer.data()));
                return out;
            }
            char piece[512];
            const int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (n < 0 || answer.size() + n > 16000) return nullptr;
            answer.append(piece, n);
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(ctx.get(), batch) != 0) return nullptr;
        }
        // Truncated, cancelled and timed-out answers are never spoken as complete.
        return nullptr;
    } catch (...) { return nullptr; }
}
