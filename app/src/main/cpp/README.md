# LEO semantic JNI adapter

`leo_semantic_jni.cpp` implements only session open, bounded embedding inference and close.
It uses the existing Sherpa runtime through its C API function table; no prebuilt Java JNI or second ORT core is packaged.
The Kotlin owner serializes encode/close. Every tensor, shape, session option, environment and session has explicit cleanup. Inference is CPU-only, two intra-op threads, sequences limited to 128 tokens and output checked as `[1, sequence, 768]` float32.

The unchanged `include/onnxruntime_c_api.h` is from Microsoft ONNX Runtime v1.22.0:
https://github.com/microsoft/onnxruntime/blob/v1.22.0/include/onnxruntime/core/session/onnxruntime_c_api.h
SHA-256: d683537d0fdc29e977b5520f7f15d87a0ac212ae6d94fa9be8893a52655621ae
MIT license: `app/src/main/assets/licenses/onnxruntime-MIT.txt`.

Linux tests build this exact adapter in `prepare_semantic_test.py` and share Sherpa's runtime when `NIKO_NATIVE_LIB_DIR` is set. Android uses CMake/NDK. C API compatibility is exercised through actual inference, not inferred from matching filenames. Acoustic performance and device RAM remain pending physical validation.
