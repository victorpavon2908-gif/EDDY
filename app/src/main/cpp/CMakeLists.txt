cmake_minimum_required(VERSION 3.22.1)
project(leo_semantic LANGUAGES CXX)
add_library(leo_semantic_jni SHARED leo_semantic_jni.cpp)
target_compile_features(leo_semantic_jni PRIVATE cxx_std_17)
target_include_directories(leo_semantic_jni PRIVATE include)
target_link_libraries(leo_semantic_jni PRIVATE dl)
# The only inference runtime is the one already provided by Sherpa. Resolve its stable
# C API at runtime; do not link a versioned prebuilt Java bridge or package another core.
target_link_options(leo_semantic_jni PRIVATE "-Wl,-z,max-page-size=16384")
