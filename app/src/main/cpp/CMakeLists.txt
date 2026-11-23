cmake_minimum_required(VERSION 3.22.1)
project(uaplay_core LANGUAGES C)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
add_library(uaplay_core SHARED
  net_curl.c
  native_bridge.c
  core/catalog.c
  core/iptv.c
  core/library.c
  core/movy_stream.c
  core/i18n.c
)
target_include_directories(uaplay_core PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/core
  ${CMAKE_CURRENT_SOURCE_DIR}/include
)
target_compile_options(uaplay_core PRIVATE -O2 -Wall -Wno-unused-function -Wno-format-truncation)
target_link_libraries(uaplay_core log android)
