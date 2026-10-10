// LEO's bounded sentence-embedding adapter. ONNX C API header: Microsoft, MIT.
#include <jni.h>
#include <dlfcn.h>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include "onnxruntime_c_api.h"

namespace {
const OrtApi* api() {
    static std::once_flag once;
    static const OrtApi* result = nullptr;
    static std::string error;
    std::call_once(once, [] {
        // Keep the handle for process lifetime: sessions and Sherpa share the runtime.
        void* library = dlopen("libonnxruntime.so", RTLD_NOW | RTLD_LOCAL);
        if (!library) { error = "No se pudo cargar el runtime local"; return; }
        auto getBase = reinterpret_cast<const OrtApiBase* (*)()>(dlsym(library, "OrtGetApiBase"));
        if (!getBase) { error = "El runtime no ofrece la API de inferencia"; return; }
        result = getBase()->GetApi(ORT_API_VERSION);
        if (!result) error = "Versión incompatible de la API de inferencia";
    });
    if (!result) throw std::runtime_error(error);
    return result;
}
void checked(OrtStatus* status) {
    if (!status) return;
    const std::string message = api()->GetErrorMessage(status);
    api()->ReleaseStatus(status);
    throw std::runtime_error(message);
}
void fail(JNIEnv* env, const std::exception& error) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
struct Session {
    OrtEnv* environment = nullptr;
    OrtSessionOptions* options = nullptr;
    OrtSession* session = nullptr;
    ~Session() {
        const auto* a = api();
        if (session) a->ReleaseSession(session);
        if (options) a->ReleaseSessionOptions(options);
        if (environment) a->ReleaseEnv(environment);
    }
};
struct Run {
    OrtMemoryInfo* memory = nullptr;
    OrtValue* ids = nullptr;
    OrtValue* mask = nullptr;
    OrtValue* output = nullptr;
    OrtTensorTypeAndShapeInfo* shape = nullptr;
    ~Run() {
        const auto* a = api();
        if (shape) a->ReleaseTensorTypeAndShapeInfo(shape);
        if (output) a->ReleaseValue(output);
        if (mask) a->ReleaseValue(mask);
        if (ids) a->ReleaseValue(ids);
        if (memory) a->ReleaseMemoryInfo(memory);
    }
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_niko_assistant_memory_embedding_LeoOrtBridge_open(JNIEnv* env, jobject, jstring path) {
    Session* holder = nullptr;
    try {
        const auto* a = api(); // Check before allocating objects with API-dependent destructors.
        holder = new Session();
        checked(a->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "LEO-memory", &holder->environment));
        checked(a->CreateSessionOptions(&holder->options));
        checked(a->SetIntraOpNumThreads(holder->options, 2));
        checked(a->SetInterOpNumThreads(holder->options, 1));
        const char* chars = env->GetStringUTFChars(path, nullptr);
        if (!chars) { delete holder; return 0; }
        OrtStatus* status = a->CreateSession(holder->environment, chars, holder->options, &holder->session);
        env->ReleaseStringUTFChars(path, chars);
        checked(status);
        return reinterpret_cast<jlong>(holder);
    } catch (const std::exception& error) { delete holder; fail(env, error); return 0; }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_niko_assistant_memory_embedding_LeoOrtBridge_meanEmbedding(JNIEnv* env, jobject, jlong handle, jlongArray input) {
    try {
        auto* holder = reinterpret_cast<Session*>(handle);
        if (!holder || !holder->session) throw std::runtime_error("Sesión semántica cerrada");
        const auto* a = api();
        const jsize length = env->GetArrayLength(input);
        if (length < 2 || length > 128) throw std::runtime_error("Secuencia fuera de rango");
        std::vector<int64_t> ids(length), mask(length, 1);
        env->GetLongArrayRegion(input, 0, length, reinterpret_cast<jlong*>(ids.data()));
        if (env->ExceptionCheck()) return nullptr;
        const int64_t dimensions[] = {1, length};
        Run run;
        checked(a->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &run.memory));
        checked(a->CreateTensorWithDataAsOrtValue(run.memory, ids.data(), ids.size() * sizeof(int64_t), dimensions, 2,
                                                 ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &run.ids));
        checked(a->CreateTensorWithDataAsOrtValue(run.memory, mask.data(), mask.size() * sizeof(int64_t), dimensions, 2,
                                                 ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &run.mask));
        const char* names[] = {"input_ids", "attention_mask"};
        const OrtValue* values[] = {run.ids, run.mask};
        const char* outputs[] = {"last_hidden_state"};
        checked(a->Run(holder->session, nullptr, names, values, 2, outputs, 1, &run.output));
        checked(a->GetTensorTypeAndShape(run.output, &run.shape));
        size_t rank = 0;
        checked(a->GetDimensionsCount(run.shape, &rank));
        ONNXTensorElementDataType type;
        checked(a->GetTensorElementType(run.shape, &type));
        if (rank != 3 || type != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) throw std::runtime_error("Salida semántica incompatible");
        int64_t shape[3];
        checked(a->GetDimensions(run.shape, shape, 3));
        if (shape[0] != 1 || shape[1] != length || shape[2] != 768) throw std::runtime_error("Dimensiones semánticas incompatibles");
        float* hidden = nullptr;
        checked(a->GetTensorMutableData(run.output, reinterpret_cast<void**>(&hidden)));
        float pooled[768] = {};
        for (int row = 0; row < length; ++row) for (int column = 0; column < 768; ++column)
            pooled[column] += hidden[row * 768 + column] / static_cast<float>(length);
        jfloatArray result = env->NewFloatArray(768);
        if (result) env->SetFloatArrayRegion(result, 0, 768, pooled);
        return result;
    } catch (const std::exception& error) { fail(env, error); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_niko_assistant_memory_embedding_LeoOrtBridge_close(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Session*>(handle);
}
