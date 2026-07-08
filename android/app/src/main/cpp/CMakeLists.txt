cmake_minimum_required(VERSION 3.22.1)
project(smriti_whisper CXX C)
set(CMAKE_CXX_STANDARD 17)

# Align native ELF load segments to 16 KB (Android 15+ / Pixel 16 KB page size).
# Applies to all targets defined below, including ggml pulled in via FetchContent.
add_link_options("-Wl,-z,max-page-size=16384")

# Repo root (app/src/main/cpp -> up 4 -> smriti/) holds the vendored whisper.cpp
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../whisper.cpp)

set(SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/jni.cpp
)

find_library(LOG_LIB log)

add_library(smriti_whisper SHARED ${SOURCE_FILES})
target_compile_definitions(smriti_whisper PUBLIC GGML_USE_CPU)
# whisper.cpp/src/whisper.cpp references WHISPER_VERSION, normally injected by its own build.
target_compile_definitions(smriti_whisper PRIVATE WHISPER_VERSION="1.7.6-smriti")

# Pull in ggml from the vendored source tree (no download).
include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

target_include_directories(smriti_whisper PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)

target_link_libraries(smriti_whisper ${LOG_LIB} android ggml)
