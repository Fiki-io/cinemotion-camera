cmake_minimum_required(VERSION 3.22.1)
project("cinemotion-native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/mgc_eis_reconstruction
)

add_library(
    cinemotion-native
    SHARED
    mgc_eis_reconstruction_jni.cpp
    mgc_eis_reconstruction/mgc_eis_reconstruction.cpp
    mgc_eis_reconstruction/mgc_type18_engine.cpp
    mgc_eis_reconstruction/mgc_type18_gyro.cpp
    mgc_eis_reconstruction/mgc_type18_lookahead.cpp
    mgc_eis_reconstruction/mgc_type18_projection.cpp
)

target_compile_options(
    cinemotion-native
    PRIVATE
    -O3
    -ffast-math
    -Wall
)

target_link_libraries(
    cinemotion-native
    log
    android
)
