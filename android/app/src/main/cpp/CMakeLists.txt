cmake_minimum_required(VERSION 3.22.1)
project(smriti_native CXX C)
set(CMAKE_CXX_STANDARD 17)

# Align native ELF load segments to 16 KB (Android 15+ / Pixel 16 KB page size).
add_link_options("-Wl,-z,max-page-size=16384")

# Repo root = app/src/main/cpp -> up 4 -> smriti/
set(LLAMA_DIR   ${CMAKE_SOURCE_DIR}/../../../../llama.cpp)
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/../../../../whisper.cpp)
find_library(LOG_LIB log)

# --- llama.cpp builds ONE ggml (target `ggml`) + `llama`; whisper reuses this same ggml.
#     (Two ggml — one from whisper, one from llama — would collide as duplicate CMake targets,
#     so we drop whisper's FetchContent(ggml) and compile whisper against llama's ggml.) ---
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI       OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
# GGML_CPU_KLEIDIAI ON (arm64) — ARM int4/int8 matmul kernels, the big Q4 speedup on-device. It was
# briefly disabled while chasing empty answers, but the real cause was a double-BOS in the prompt
# (fixed in llama_jni.cpp), so KleidiAI is safe and back on. (Explicit FORCE to override cached OFF.)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
endif()
add_subdirectory(${LLAMA_DIR} build-llama)

# --- whisper JNI (libsmriti_whisper.so), compiled against llama's ggml ---
add_library(smriti_whisper SHARED ${WHISPER_DIR}/src/whisper.cpp jni.cpp)
target_compile_definitions(smriti_whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(smriti_whisper PRIVATE WHISPER_VERSION="1.7.6-smriti")
target_include_directories(smriti_whisper PRIVATE
    ${WHISPER_DIR} ${WHISPER_DIR}/src ${WHISPER_DIR}/include
    ${LLAMA_DIR}/ggml/include ${LLAMA_DIR}/ggml/src ${LLAMA_DIR}/ggml/src/ggml-cpu)
target_link_libraries(smriti_whisper ${LOG_LIB} android ggml)

# --- llama JNI (libsmriti_llama.so) — RAG generator ---
add_library(smriti_llama SHARED llama_jni.cpp)
target_include_directories(smriti_llama PRIVATE ${LLAMA_DIR}/include ${LLAMA_DIR}/ggml/include)
target_link_libraries(smriti_llama ${LOG_LIB} android llama)
