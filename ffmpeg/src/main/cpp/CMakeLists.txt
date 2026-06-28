cmake_minimum_required(VERSION 3.22.1)
project("ffmpeg")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Prebuilt FFmpeg .so files
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavcodec.so)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavformat.so)

add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavfilter.so)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavutil.so)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libswscale.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libswresample.so)

add_library(${CMAKE_PROJECT_NAME} SHARED
        jni_bridge.cpp
        decoder.cpp
        encoder.cpp
        filter_graph.cpp
        compositor.cpp
        temporal.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ../../../../ffmpeg/src/main/cpp/ffmpeg/include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        avcodec
        avformat
        avfilter
        avutil
        swscale
        swresample
        android
        log)