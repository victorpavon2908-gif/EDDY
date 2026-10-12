cmake_minimum_required(VERSION 3.22.1)
# Select before project() initializes the NDK toolchain, including standalone CMake builds.
if(NOT DEFINED ANDROID_STL)
    set(ANDROID_STL c++_shared CACHE STRING "LEO Android C++ runtime")
endif()
project(leo_semantic LANGUAGES CXX)
add_library(leo_semantic_jni SHARED leo_semantic_jni.cpp)
target_compile_features(leo_semantic_jni PRIVATE cxx_std_17)
target_include_directories(leo_semantic_jni PRIVATE include)
target_link_libraries(leo_semantic_jni PRIVATE dl)
if(ANDROID)
    if(NOT ANDROID_STL STREQUAL "c++_shared")
        message(FATAL_ERROR "LEO requires -DANDROID_STL=c++_shared. Clear app/.cxx and reconfigure.")
    endif()
    # Explicit linkage also works when compiler detection cached incomplete implicit libraries.
    target_link_libraries(leo_semantic_jni PRIVATE c++_shared)
    target_link_options(leo_semantic_jni PRIVATE "-Wl,--no-undefined")
endif()
# The only inference runtime is the one already provided by Sherpa. Resolve its stable
# C API at runtime; do not link a versioned prebuilt Java bridge or package another core.
target_link_options(leo_semantic_jni PRIVATE "-Wl,-z,max-page-size=16384")
