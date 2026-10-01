cmake_minimum_required(VERSION 3.22.1)
project(appsandbox_runtime LANGUAGES C CXX)

add_library(appsandbox_runtime SHARED
    src/main/cpp/native_runtime_bridge.cpp
)

target_compile_options(appsandbox_runtime PRIVATE
    -Wall
    -Wextra
    -Werror
    $<$<STREQUAL:${ANDROID_ABI},arm64-v8a>:-mbranch-protection=standard>
)

target_link_libraries(appsandbox_runtime PRIVATE log dl)
